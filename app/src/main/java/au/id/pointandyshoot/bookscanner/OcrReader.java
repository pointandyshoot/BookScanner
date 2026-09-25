package au.id.pointandyshoot.bookscanner;

import android.content.res.AssetManager;
import android.graphics.*;
import android.os.SystemClock;
import au.id.pointandyshoot.bookscanner.core.*;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;

/** PP-OCRv4 Mobile: detect -> straighten line crops -> recognise -> match. Single executor owner. */
final class OcrReader implements AutoCloseable {
    private final AssetManager assets;
    private long handle;
    private final List<String> keys=new ArrayList<>();
    private final Matcher matcher=new Matcher();
    private int step,session=-1;
    OcrReader(AssetManager assets){this.assets=assets;}
    private void initialise() throws IOException {
        if(handle!=0)return;
        keys.clear();keys.add("");
        try(BufferedReader in=new BufferedReader(new InputStreamReader(assets.open("ppocrv4/keys.txt"),StandardCharsets.UTF_8))){
            String line;while((line=in.readLine())!=null)keys.add(line);
        }
        keys.add(" ");
        if(keys.size()!=6625)throw new IOException("PP-OCRv4 dictionary mismatch");
        handle=PpOcrNative.open(assets);
    }
    void read(Bitmap upright,List<WantedBook> books,int token,boolean spineMode,float[] recheck,
              BooleanSupplier valid,Consumer<List<LiveTracker.Detection>> publish) throws Exception {
        if(session!=token){step=0;session=token;}
        initialise();int w=upright.getWidth(),h=upright.getHeight();Rect region=new Rect(0,0,w,h);
        // Alternate coverage and native-resolution detail. Revisit tentative tracks preferentially.
        if(step%2==1){
            if(recheck!=null){
                int pad=Math.max(48,Math.round(Math.max(recheck[2]-recheck[0],recheck[3]-recheck[1])*.25f));
                region=new Rect(Math.max(0,(int)recheck[0]-pad),Math.max(0,(int)recheck[1]-pad),Math.min(w,(int)recheck[2]+pad),Math.min(h,(int)recheck[3]+pad));
            }else{
                int cw=Math.round(w*.65f),ch=Math.round(h*.65f),tile=(step/2)%4;
                int left=tile%2==0?0:w-cw,top=tile<2?0:h-ch;region=new Rect(left,top,left+cw,top+ch);
                if(spineMode){List<Rect> bands=ImagePrep.spineBands(upright);if(!bands.isEmpty())region=bands.get((step/2)%bands.size());}
            }
        }
        try(ReadingImage input=new ReadingImage(upright,region,0,1)){readRegions(input,books,valid,publish,step++);}
    }
    List<LiveTracker.Detection> readAtAngle(Bitmap source,Rect region,float angle,float scale,List<WantedBook> books) throws Exception {
        initialise();try(ReadingImage input=new ReadingImage(source,region,angle,scale)){return readRegions(input,books,()->true,hits->{},0);}
    }
    private List<LiveTracker.Detection> readRegions(ReadingImage input,List<WantedBook> books,BooleanSupplier valid,
                                                   Consumer<List<LiveTracker.Detection>> publish,int offset){
        long started=SystemClock.elapsedRealtime();List<float[]> regions=detect(input.bitmap);
        List<LiveTracker.Detection> hits=new ArrayList<>();if(regions.isEmpty())return hits;
        int start=(offset*7)%regions.size();
        for(int i=0;i<Math.min(24,regions.size())&&valid.getAsBoolean();i++){
            if(i>0&&SystemClock.elapsedRealtime()-started>1800)break;
            float[] q=regions.get((start+i)%regions.size());Bitmap crop=rectify(input.bitmap,q);
            try{
                Reading normal=recognise(crop);Matrix turn=new Matrix();turn.postRotate(180);
                Bitmap flipped=Bitmap.createBitmap(crop,0,0,crop.getWidth(),crop.getHeight(),turn,true);Reading reverse;
                try{reverse=recognise(flipped);}finally{if(flipped!=crop)flipped.recycle();}
                // Do not pick the direction by its similarity to the wanted list.
                Reading chosen=reverse.confidence>normal.confidence?reverse:normal;
                if(chosen.confidence<.50||chosen.text.isBlank()||chosen.text.length()>500)continue;
                float[] mapped=q.clone();input.toSource.mapPoints(mapped);
                List<Matcher.Match> matches=matcher.find(chosen.text,books);
                boolean ambiguous=matches.size()>1&&matches.get(1).score>=matches.get(0).score-.06;
                for(Matcher.Match match:matches){
                    if(hits.size()>=20)break;
                    boolean strong=match.strong&&chosen.confidence>=.80&&!ambiguous;
                    LiveTracker.Detection d=new LiveTracker.Detection(match.book.id,match.book.label(),ambiguous?"Possible — multiple wanted entries":match.reason,mapped,strong);
                    int duplicate=-1;
                    for(int k=0;k<hits.size();k++)if(hits.get(k).id.equals(d.id)&&Geometry.overlap(LiveTracker.bounds(mapped),LiveTracker.bounds(hits.get(k).quad))>.3){duplicate=k;break;}
                    if(duplicate<0)hits.add(d);else if(strong&&!hits.get(duplicate).strong)hits.set(duplicate,d);
                }
                if(!hits.isEmpty()&&valid.getAsBoolean())publish.accept(List.copyOf(hits));
            }finally{crop.recycle();}
        }
        if(valid.getAsBoolean())publish.accept(List.copyOf(hits));return hits;
    }
    private List<float[]> detect(Bitmap bitmap){
        float scale=Math.min(1,1280f/Math.max(bitmap.getWidth(),bitmap.getHeight()));
        int w=Math.max(32,Math.round(bitmap.getWidth()*scale/32)*32),h=Math.max(32,Math.round(bitmap.getHeight()*scale/32)*32);
        float[] output=PpOcrNative.detect(handle,bitmap,w,h);int mw=(int)output[0],mh=(int)output[1];
        Mat probability=new Mat(mh,mw,CvType.CV_32FC1),binary=new Mat(),hierarchy=new Mat(),empty=new Mat();
        List<MatOfPoint> contours=new ArrayList<>();List<float[]> result=new ArrayList<>();
        try{
            probability.put(0,0,Arrays.copyOfRange(output,2,output.length));
            Imgproc.threshold(probability,binary,.3,255,Imgproc.THRESH_BINARY);binary.convertTo(binary,CvType.CV_8UC1);
            Imgproc.findContours(binary,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
            contours.sort(Comparator.comparingDouble((MatOfPoint c)->Imgproc.contourArea(c)).reversed());
            for(int i=0;i<Math.min(300,contours.size())&&result.size()<96;i++){
                MatOfPoint contour=contours.get(i);if(Imgproc.contourArea(contour)<12)continue;
                org.opencv.core.Rect bounds=Imgproc.boundingRect(contour);
                Mat mask=Mat.zeros(bounds.height,bounds.width,CvType.CV_8UC1),local=probability.submat(bounds);
                MatOfPoint2f points=new MatOfPoint2f(contour.toArray());
                try{
                    Imgproc.drawContours(mask,List.of(contour),0,Scalar.all(255),-1,Imgproc.LINE_8,empty,0,new org.opencv.core.Point(-bounds.x,-bounds.y));
                    if(org.opencv.core.Core.mean(local,mask).val[0]<.50)continue;
                    RotatedRect rect=Imgproc.minAreaRect(points);double a=rect.size.width,b=rect.size.height;
                    if(Math.min(a,b)<3)continue;
                    // Rectangular DB expansion, area * unclip ratio / perimeter on each side.
                    double pad=a*b*1.5/(2*(a+b));rect.size.width+=2*pad;rect.size.height+=2*pad;
                    org.opencv.core.Point[] p=new org.opencv.core.Point[4];rect.points(p);
                    Arrays.sort(p,Comparator.comparingDouble(v->Math.atan2(v.y-rect.center.y,v.x-rect.center.x)));
                    int first=0;for(int k=1;k<4;k++)if(p[k].x+p[k].y<p[first].x+p[first].y)first=k;
                    float[] q=new float[8];
                    for(int k=0;k<4;k++){org.opencv.core.Point pt=p[(first+k)%4];q[k*2]=(float)(pt.x*bitmap.getWidth()/mw);q[k*2+1]=(float)(pt.y*bitmap.getHeight()/mh);}
                    result.add(q);
                }finally{points.release();mask.release();local.release();}
            }
        }finally{for(MatOfPoint c:contours)c.release();probability.release();binary.release();hierarchy.release();empty.release();}
        return result;
    }
    private static Bitmap rectify(Bitmap source,float[] q){
        float w=(float)Math.max(Math.hypot(q[2]-q[0],q[3]-q[1]),Math.hypot(q[4]-q[6],q[5]-q[7]));
        float h=(float)Math.max(Math.hypot(q[6]-q[0],q[7]-q[1]),Math.hypot(q[4]-q[2],q[5]-q[3]));float[] ordered=q.clone();
        if(h>w){for(int i=0;i<4;i++){ordered[2*i]=q[2*((i+1)%4)];ordered[2*i+1]=q[2*((i+1)%4)+1];}float t=w;w=h;h=t;}
        int width=Math.max(16,Math.min(1536,Math.round(48*w/Math.max(1,h))));Bitmap result=Bitmap.createBitmap(width,48,Bitmap.Config.ARGB_8888);
        Matrix transform=new Matrix();
        if(!transform.setPolyToPoly(ordered,0,new float[]{0,0,width,0,width,48,0,48},0,4)){result.recycle();throw new IllegalStateException("Invalid text quadrilateral");}
        Canvas canvas=new Canvas(result);canvas.drawColor(Color.WHITE);canvas.concat(transform);canvas.drawBitmap(source,0,0,new Paint(Paint.FILTER_BITMAP_FLAG));return result;
    }
    private static final class Reading {
        final String text;final float confidence;
        Reading(String text,float confidence){this.text=text;this.confidence=confidence;}
    }
    private Reading recognise(Bitmap crop){
        float[] raw=PpOcrNative.recognise(handle,crop);StringBuilder text=new StringBuilder();int previous=-1,count=0;float total=0;
        for(int i=0;i<raw.length;i+=2){int index=(int)raw[i];float confidence=raw[i+1];
            if(index>0&&index<keys.size()&&index!=previous&&Float.isFinite(confidence)){text.append(keys.get(index));total+=confidence;count++;}previous=index;}
        return new Reading(text.toString(),count==0?0:total/count);
    }
    @Override public void close(){if(handle!=0){PpOcrNative.close(handle);handle=0;}keys.clear();}
}
