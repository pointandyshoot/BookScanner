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
    private String runtime="CPU FP32";
    private final List<String> keys=new ArrayList<>();
    private final Matcher matcher=new Matcher();
    private final DiscoverySchedule discovery=new DiscoverySchedule();
    private int step,session=-1;
    static final class Stats {
        final int regions,visited,readable,matches;final long detectorMs,totalMs;final String scope;
        final int detectorWidth,detectorHeight;final String runtime;
        Stats(int regions,int visited,int readable,int matches,long detectorMs,long totalMs,String scope,int dw,int dh,String runtime){
            this.regions=regions;this.visited=visited;this.readable=readable;this.matches=matches;
            this.detectorMs=detectorMs;this.totalMs=totalMs;this.scope=scope;
            this.detectorWidth=dw;this.detectorHeight=dh;this.runtime=runtime;
        }
    }
    private volatile Stats lastStats;
    Stats stats(){return lastStats;}

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
        runtime=PpOcrNative.fp16Enabled(handle)?"CPU FP16":"CPU FP32";
    }
    void read(Bitmap upright,List<WantedBook> books,int token,boolean spineMode,float[] recheck,
              BooleanSupplier valid,Consumer<List<LiveTracker.Detection>> publish) throws Exception {
        if(session!=token){step=0;discovery.reset();session=token;}
        initialise();int w=upright.getWidth(),h=upright.getHeight();DiscoverySchedule.Plan plan=discovery.next();
        Rect region=new Rect(0,Math.round(h*plan.top),w,Math.round(h*plan.bottom));String scope=plan.scope;
        // Prioritise discovery at native detail; existing hints get only occasional rereads.
        if(!plan.full){
            if(recheck!=null&&step%12==11){
                int pad=Math.max(48,Math.round(Math.max(recheck[2]-recheck[0],recheck[3]-recheck[1])*.25f));
                region=new Rect(Math.max(0,(int)recheck[0]-pad),Math.max(0,(int)recheck[1]-pad),Math.min(w,(int)recheck[2]+pad),Math.min(h,(int)recheck[3]+pad));
                scope="recheck 90°";
            }else if(spineMode&&step%12==10){
                List<Rect> bands=ImagePrep.spineBands(upright);
                if(!bands.isEmpty()){region=bands.get((step/12)%bands.size());scope="spine 90°";}
            }
        }
        try(ReadingImage input=new ReadingImage(upright,region,plan.angle,1)){
            readRegions(input,books,valid,publish,step++,(plan.detail?"detail ":"fast ")+scope,plan.detail);
        }
    }
    List<LiveTracker.Detection> readAtAngle(Bitmap source,Rect region,float angle,float scale,List<WantedBook> books) throws Exception {
        initialise();try(ReadingImage input=new ReadingImage(source,region,angle,scale)){return readRegions(input,books,()->true,hits->{},0,"test fast",false);}
    }
    static final class StillProgress {
        final int percent;final String stage;
        StillProgress(int percent,String stage){this.percent=percent;this.stage=stage;}
    }
    void readStill(Bitmap source,List<WantedBook> books,BooleanSupplier valid,
                   Consumer<StillProgress> progress,Consumer<List<LiveTracker.Detection>> publish) throws Exception {
        initialise();List<int[]> sections=StillPlan.sections(source.getWidth(),source.getHeight());
        List<LiveTracker.Detection> all=new ArrayList<>();int total=sections.size()*2;
        for(int orientation=0;orientation<2&&valid.getAsBoolean();orientation++){
            for(int i=0;i<sections.size()&&valid.getAsBoolean();i++){
                int section=orientation*sections.size()+i;int[] b=sections.get(i);
                progress.accept(new StillProgress(Math.round(100f*section/total),"Finding text · section "+(section+1)+" / "+total));
                if(!valid.getAsBoolean())break;
                try(ReadingImage input=new ReadingImage(source,new Rect(b[0],b[1],b[2],b[3]),orientation==0?90:0,1)){
                    readRegions(input,books,valid,hits->{
                        for(LiveTracker.Detection hit:hits){
                            int duplicate=-1;
                            for(int k=0;k<all.size();k++)if(all.get(k).id.equals(hit.id)&&Geometry.overlap(LiveTracker.bounds(all.get(k).quad),LiveTracker.bounds(hit.quad))>.15){duplicate=k;break;}
                            if(duplicate<0){if(all.size()<200)all.add(hit);}
                            else if(hit.strong&&!all.get(duplicate).strong)all.set(duplicate,hit);
                        }
                        if(valid.getAsBoolean())publish.accept(List.copyOf(all));
                    },0,"still",true,true,(done,count)->progress.accept(new StillProgress(
                            Math.min(99,Math.round(100f*(section+(count==0?1:done/(float)count))/total)),
                            "Reading text · section "+(section+1)+" / "+total+" · "+done+" / "+count)));
                }
            }
        }
        if(valid.getAsBoolean()){progress.accept(new StillProgress(100,"Complete"));publish.accept(List.copyOf(all));}
    }
    private List<LiveTracker.Detection> readRegions(ReadingImage input,List<WantedBook> books,BooleanSupplier valid,
                                                   Consumer<List<LiveTracker.Detection>> publish,int offset,String scope,boolean detail){
        return readRegions(input,books,valid,publish,offset,scope,detail,false,(done,count)->{});
    }
    private List<LiveTracker.Detection> readRegions(ReadingImage input,List<WantedBook> books,BooleanSupplier valid,
            Consumer<List<LiveTracker.Detection>> publish,int offset,String scope,boolean detail,boolean thorough,
            BiConsumer<Integer,Integer> progress){
        int edge=detail?960:768;float scale=Math.min(1,(float)edge/Math.max(input.bitmap.getWidth(),input.bitmap.getHeight()));
        int dw=Math.max(32,Math.round(input.bitmap.getWidth()*scale/32)*32),dh=Math.max(32,Math.round(input.bitmap.getHeight()*scale/32)*32);
        long started=SystemClock.elapsedRealtime();List<float[]> regions=detect(input.bitmap,dw,dh);
        long detectorDone=SystemClock.elapsedRealtime();
        List<LiveTracker.Detection> hits=new ArrayList<>();List<TextClue> clues=new ArrayList<>();
        int visited=0,readable=0;
        if(regions.isEmpty()){
            lastStats=new Stats(0,0,0,0,detectorDone-started,detectorDone-started,scope,dw,dh,runtime);
            if(valid.getAsBoolean()){publish.accept(List.of());progress.accept(0,0);}
            return hits;
        }
        // Fast passes rotate through large legible lines, not tiny publisher glyphs.
        int pool=detail?regions.size():Math.min(16,regions.size()),start=thorough?0:(offset*5)%pool;
        for(int i=0;i<Math.min(thorough?pool:detail?24:12,pool)&&valid.getAsBoolean();i++){
            // A slow detector must not consume the entire recognition budget.
            if(!thorough&&i>=(detail?4:2)&&SystemClock.elapsedRealtime()-detectorDone>(detail?800:400))break;
            visited++;
            float[] q=regions.get((start+i)%pool);Bitmap crop=rectify(input.bitmap,q);
            try{
                Reading chosen=recognise(crop);
                // A very clear reading needs no second inference. Otherwise try the other direction.
                if(chosen.confidence<.93){
                    Matrix turn=new Matrix();turn.postRotate(180);
                    Bitmap flipped=Bitmap.createBitmap(crop,0,0,crop.getWidth(),crop.getHeight(),turn,true);
                    try{Reading reverse=recognise(flipped);if(reverse.confidence>chosen.confidence)chosen=reverse;}
                    finally{if(flipped!=crop)flipped.recycle();}
                }
                if(chosen.confidence<.35||chosen.text.isBlank()||chosen.text.length()>500)continue;
                readable++;
                float[] mapped=q.clone();input.toSource.mapPoints(mapped);
                List<Matcher.Match> matches=new ArrayList<>(matcher.find(chosen.text,books));
                // Join only nearby, similarly oriented lines. Keep the box anchored to the actual text.
                for(TextClue clue:clues)if(TextNeighbour.canJoin(clue.quad,mapped)){
                    mergeMatches(matches,matcher.find(clue.text+" "+chosen.text,books));
                    mergeMatches(matches,matcher.find(chosen.text+" "+clue.text,books));
                }
                clues.add(new TextClue(chosen.text,mapped));
                for(Matcher.Match match:matches){
                    if(hits.size()>=(thorough?200:20))break;
                    boolean strong=match.strong;
                    LiveTracker.Detection d=new LiveTracker.Detection(match.book.id,match.book.label(),match.reason,mapped,strong);
                    int duplicate=-1;
                    for(int k=0;k<hits.size();k++)if(hits.get(k).id.equals(d.id)&&Geometry.overlap(LiveTracker.bounds(mapped),LiveTracker.bounds(hits.get(k).quad))>.3){duplicate=k;break;}
                    if(duplicate<0)hits.add(d);else if(strong&&!hits.get(duplicate).strong)hits.set(duplicate,d);
                }
                if(!hits.isEmpty()&&valid.getAsBoolean())publish.accept(List.copyOf(hits));
            }finally{crop.recycle();if(valid.getAsBoolean())progress.accept(visited,pool);}
        }
        lastStats=new Stats(regions.size(),visited,readable,hits.size(),
                detectorDone-started,SystemClock.elapsedRealtime()-started,scope,dw,dh,runtime);
        if(valid.getAsBoolean())publish.accept(List.copyOf(hits));return hits;
    }
    private static final class TextClue {
        final String text;final float[] quad;
        TextClue(String text,float[] quad){this.text=text;this.quad=quad;}
    }
    private static void mergeMatches(List<Matcher.Match> into,List<Matcher.Match> extra){
        for(Matcher.Match m:extra){
            int at=-1;for(int i=0;i<into.size();i++)if(into.get(i).book.id.equals(m.book.id)){at=i;break;}
            if(at<0)into.add(m);else if(m.score>into.get(at).score)into.set(at,m);
        }
    }
    private List<float[]> detect(Bitmap bitmap,int w,int h){
        float[] output=PpOcrNative.detect(handle,bitmap,w,h);int mw=(int)output[0],mh=(int)output[1];
        Mat probability=new Mat(mh,mw,CvType.CV_32FC1),binary=new Mat(),hierarchy=new Mat(),empty=new Mat();
        List<MatOfPoint> contours=new ArrayList<>();List<float[]> result=new ArrayList<>();
        try{
            probability.put(0,0,Arrays.copyOfRange(output,2,output.length));
            Imgproc.threshold(probability,binary,.2,255,Imgproc.THRESH_BINARY);binary.convertTo(binary,CvType.CV_8UC1);
            Imgproc.findContours(binary,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
            contours.sort(Comparator.comparingDouble((MatOfPoint c)->Imgproc.contourArea(c)).reversed());
            for(int i=0;i<Math.min(300,contours.size())&&result.size()<96;i++){
                MatOfPoint contour=contours.get(i);if(Imgproc.contourArea(contour)<12)continue;
                org.opencv.core.Rect bounds=Imgproc.boundingRect(contour);
                Mat mask=Mat.zeros(bounds.height,bounds.width,CvType.CV_8UC1),local=probability.submat(bounds);
                MatOfPoint2f points=new MatOfPoint2f(contour.toArray());
                try{
                    Imgproc.drawContours(mask,List.of(contour),0,Scalar.all(255),-1,Imgproc.LINE_8,empty,0,new org.opencv.core.Point(-bounds.x,-bounds.y));
                    if(org.opencv.core.Core.mean(local,mask).val[0]<.40)continue;
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
