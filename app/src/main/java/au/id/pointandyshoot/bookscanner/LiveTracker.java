package au.id.pointandyshoot.bookscanner;

import au.id.pointandyshoot.bookscanner.core.Geometry;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;
import org.opencv.calib3d.Calib3d;
import java.util.*;

/** Single analysis-thread owner. Tracks text texture between OCR reads; retains no disk images. */
final class LiveTracker implements AutoCloseable {
    static final long GRACE_MS=1000,HISTORY_MS=3000;
    static final class Detection {
        final String id,label,reason;final float[] quad;
        Detection(String id,String label,String reason,float[] quad){this.id=id;this.label=label;this.reason=reason;this.quad=quad.clone();}
    }
    static final class Visible {
        final Detection detection;final boolean repeated,tracking;final String appearanceId;
        Visible(Detection d,boolean repeated,boolean tracking,String appearanceId){detection=d;this.repeated=repeated;this.tracking=tracking;this.appearanceId=appearanceId;}
    }
    private static final class Track {
        Detection detection;float[] quad;Mat template;long lastGood,lastRead,lastReadSequence;int reads=1;String appearanceId;
        Track(Detection d,float[] q,Mat t,long now,long seq){detection=d;quad=q;template=t;lastGood=lastRead=now;lastReadSequence=seq;}
    }
    private static final class Lost {
        final String entry,key;final float[] quad;final long at;
        Lost(Track t,long at){entry=t.detection.id;key=t.appearanceId;quad=t.quad.clone();this.at=at;}
    }
    private static final class Step {
        final long from,to,time;final double[] matrix;
        Step(long from,long to,long time,double[] matrix){this.from=from;this.to=to;this.time=time;this.matrix=matrix;}
    }
    private static final class Flow {
        final List<Point> from=new ArrayList<>(),to=new ArrayList<>();
    }
    private final List<Track> tracks=new ArrayList<>();
    private final List<Lost> recentlyLost=new ArrayList<>();
    private long nextAppearance=0;
    private final Deque<Step> history=new ArrayDeque<>();
    private Mat previous;
    private long sequence,now;
    private int sourceWidth,sourceHeight;

    void frame(VisionFrames.Gray image,long seq,long time){
        Mat next=image.mat();
        try {
            if(previous!=null&&(previous.cols()!=next.cols()||previous.rows()!=next.rows()
                    ||sourceWidth!=image.sourceWidth||sourceHeight!=image.sourceHeight))clear();
            sourceWidth=image.sourceWidth;sourceHeight=image.sourceHeight;now=time;
            recentlyLost.removeIf(l->time-l.at>1500);
            if(previous!=null){
                Flow flow=flow(previous,next);double[] global=fit(flow.from,flow.to);
                if(global==null){Mat difference=new Mat();try{Core.absdiff(previous,next,difference);
                    if(Core.mean(difference).val[0]<2.5)global=new double[]{1,0,0,0,1,0};}finally{difference.release();}}
                history.addLast(new Step(sequence,seq,time,global));
                while(!history.isEmpty()&&(time-history.peekFirst().time>HISTORY_MS||history.size()>60))history.removeFirst();
                Iterator<Track> iterator=tracks.iterator();
                while(iterator.hasNext()){
                    Track t=iterator.next();List<Point> from=new ArrayList<>(),to=new ArrayList<>();
                    for(int i=0;i<flow.from.size();i++)if(inside(flow.from.get(i),t.quad)){from.add(flow.from.get(i));to.add(flow.to.get(i));}
                    double[] local=fit(from,to);double[] motion=local!=null?local:global;
                    if(motion!=null){
                        float[] candidate=transform(t.quad,motion);
                        if(visibility(candidate,next.cols(),next.rows())<.30){t.template.release();iterator.remove();continue;}
                        double correlation=correlation(next,candidate,t.template);
                        if(correlation>=(local==null?.68:.52))t.lastGood=time;
                        t.quad=candidate;
                    }
                    if(time-t.lastGood>GRACE_MS){recentlyLost.add(new Lost(t,time));t.template.release();iterator.remove();}
                }
            }
            if(previous!=null)previous.release();previous=next;next=null;sequence=seq;
        }finally{if(next!=null)next.release();}
    }
    void detections(List<Detection> observations,VisionFrames.Gray capture,long captureSequence,long capturedAt){
        if(previous==null||now-capturedAt>HISTORY_MS||capture.width!=previous.cols()||capture.height!=previous.rows())return;
        Mat reference=capture.mat();
        try{
            for(Detection d:observations){
                float[] original=scale(d.quad,(float)capture.width/capture.sourceWidth,(float)capture.height/capture.sourceHeight);
                float[] q=replay(original,captureSequence);
                if(q==null||visibility(q,previous.cols(),previous.rows())<.6)continue;
                Mat template=patch(reference,original);
                if(template==null)continue;
                if(captureSequence!=sequence&&correlation(previous,q,template)<.52){template.release();continue;}
                Track existing=null;
                for(Track t:tracks)if(t.detection.id.equals(d.id)&&Geometry.overlap(bounds(t.quad),bounds(q))>.12){existing=t;break;}
                if(existing!=null){
                    if(captureSequence>existing.lastReadSequence)existing.reads++;
                    existing.template.release();existing.template=template;existing.quad=q;existing.lastGood=now;
                    existing.lastRead=capturedAt;existing.lastReadSequence=captureSequence;
                    if(!d.reason.startsWith("Author only")||existing.detection.reason.startsWith("Author only"))existing.detection=d;
                }else if(tracks.size()<12){
                    Track added=new Track(d,q,template,now,captureSequence);
                    added.appearanceId="track-"+(++nextAppearance);
                    Iterator<Lost> lost=recentlyLost.iterator();
                    while(lost.hasNext()) {Lost old=lost.next();if(old.entry.equals(d.id)&&Geometry.overlap(bounds(old.quad),bounds(q))>.2){added.appearanceId=old.key;lost.remove();break;}}
                    tracks.add(added);
                }else template.release();
            }
        }finally{reference.release();}
    }
    List<Visible> visible(){
        if(previous==null)return List.of();List<Visible> result=new ArrayList<>();
        for(Track t:tracks)result.add(new Visible(new Detection(t.detection.id,t.detection.label,t.detection.reason,
                scale(t.quad,(float)sourceWidth/previous.cols(),(float)sourceHeight/previous.rows())),t.reads>1,now-t.lastGood<150,t.appearanceId));
        return result;
    }
    float[] recheckRegion(){
        if(previous==null)return null;
        Track oldest=null;for(Track t:tracks)if(now-t.lastRead>2500&&(oldest==null||t.lastRead<oldest.lastRead))oldest=t;
        return oldest==null?null:bounds(scale(oldest.quad,(float)sourceWidth/previous.cols(),(float)sourceHeight/previous.rows()));
    }
    private float[] replay(float[] q,long from){
        if(from==sequence)return q;
        long expected=from;
        for(Step s:history){
            if(s.to<=from)continue;
            if(s.from!=expected||s.matrix==null)return null;
            q=transform(q,s.matrix);expected=s.to;
        }
        return expected==sequence?q:null;
    }
    private Flow flow(Mat a,Mat b){
        MatOfPoint corners=new MatOfPoint();Mat mask=Mat.zeros(a.size(),CvType.CV_8UC1);
        MatOfPoint2f input=new MatOfPoint2f(),output=new MatOfPoint2f(),back=new MatOfPoint2f();
        MatOfByte ok=new MatOfByte(),backOk=new MatOfByte();MatOfFloat error=new MatOfFloat(),backError=new MatOfFloat();
        try{
            List<Point> points=new ArrayList<>();Imgproc.goodFeaturesToTrack(a,corners,160,.01,6);points.addAll(corners.toList());
            for(Track t:tracks){
                mask.setTo(Scalar.all(0));MatOfPoint polygon=polygon(t.quad);
                try{Imgproc.fillConvexPoly(mask,polygon,Scalar.all(255));}finally{polygon.release();}
                Imgproc.goodFeaturesToTrack(a,corners,36,.008,3,mask);points.addAll(corners.toList());
            }
            Flow result=new Flow();if(points.size()<6)return result;
            input.fromList(points);
            Video.calcOpticalFlowPyrLK(a,b,input,output,ok,error,new Size(21,21),3);
            Video.calcOpticalFlowPyrLK(b,a,output,back,backOk,backError,new Size(21,21),3);
            Point[] end=output.toArray(),reverse=back.toArray();byte[] valid=ok.toArray(),validBack=backOk.toArray();float[] errors=error.toArray();
            for(int i=0;i<points.size();i++)if(valid[i]!=0&&validBack[i]!=0&&errors[i]<30
                    &&Math.hypot(points.get(i).x-reverse[i].x,points.get(i).y-reverse[i].y)<1.5
                    &&end[i].x>=0&&end[i].x<b.cols()&&end[i].y>=0&&end[i].y<b.rows()){
                result.from.add(points.get(i));result.to.add(end[i]);
            }
            return result;
        }finally{corners.release();mask.release();input.release();output.release();back.release();ok.release();backOk.release();error.release();backError.release();}
    }
    private static double[] fit(List<Point> from,List<Point> to){
        if(from.size()<6)return null;
        MatOfPoint2f a=new MatOfPoint2f(),b=new MatOfPoint2f();Mat inliers=new Mat(),affine=null;
        try{
            a.fromList(from);b.fromList(to);affine=Calib3d.estimateAffinePartial2D(a,b,inliers,Calib3d.RANSAC,2.0);
            if(affine.empty()||Core.countNonZero(inliers)<Math.max(5,from.size()*.55))return null;
            double[] m=new double[6];affine.get(0,0,m);double scale=Math.hypot(m[0],m[3]);
            if(!Double.isFinite(scale)||scale<.75||scale>1.3||Math.abs(m[2])>180||Math.abs(m[5])>180)return null;
            return m;
        }finally{a.release();b.release();inliers.release();if(affine!=null)affine.release();}
    }
    static float[] transform(float[] quad,double[] m){
        float[] q=quad.clone();for(int i=0;i<q.length;i+=2){q[i]=(float)(m[0]*quad[i]+m[1]*quad[i+1]+m[2]);q[i+1]=(float)(m[3]*quad[i]+m[4]*quad[i+1]+m[5]);}return q;
    }
    private static float[] scale(float[] quad,float sx,float sy){float[] q=quad.clone();for(int i=0;i<q.length;i+=2){q[i]*=sx;q[i+1]*=sy;}return q;}
    static float[] bounds(float[] q){float[] b={Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE};for(int i=0;i<q.length;i+=2){b[0]=Math.min(b[0],q[i]);b[1]=Math.min(b[1],q[i+1]);b[2]=Math.max(b[2],q[i]);b[3]=Math.max(b[3],q[i+1]);}return b;}
    private static boolean inside(Point p,float[] q){boolean inside=false;for(int i=0,j=3;i<4;j=i++){double xi=q[2*i],yi=q[2*i+1],xj=q[2*j],yj=q[2*j+1];if((yi>p.y)!=(yj>p.y)&&p.x<(xj-xi)*(p.y-yi)/(yj-yi)+xi)inside=!inside;}return inside;}
    private static double visibility(float[] q,int w,int h){float[] b=bounds(q);double area=(b[2]-b[0])*(b[3]-b[1]);return area<=1?0:Math.max(0,Math.min(w,b[2])-Math.max(0,b[0]))*Math.max(0,Math.min(h,b[3])-Math.max(0,b[1]))/area;}
    private static MatOfPoint polygon(float[] q){Point[] pts=new Point[4];for(int i=0;i<4;i++)pts[i]=new Point(q[i*2],q[i*2+1]);return new MatOfPoint(pts);}
    private static Mat patch(Mat image,float[] q){
        float[] b=bounds(q);if(b[2]-b[0]<3||b[3]-b[1]<3)return null;
        Point[] pts=new Point[4];for(int i=0;i<4;i++)pts[i]=new Point(q[2*i],q[2*i+1]);
        MatOfPoint2f src=new MatOfPoint2f(pts),dst=new MatOfPoint2f(new Point(0,0),new Point(95,0),new Point(95,31),new Point(0,31));Mat matrix=null,result=new Mat();
        try{matrix=Imgproc.getPerspectiveTransform(src,dst);Imgproc.warpPerspective(image,result,matrix,new Size(96,32),Imgproc.INTER_LINEAR,Core.BORDER_CONSTANT,Scalar.all(255));return result;}
        catch(RuntimeException e){result.release();return null;}
        finally{src.release();dst.release();if(matrix!=null)matrix.release();}
    }
    private static double correlation(Mat image,float[] q,Mat reference){
        Mat candidate=patch(image,q);if(candidate==null)return 0;Mat score=new Mat();MatOfDouble mean=new MatOfDouble(),std=new MatOfDouble();
        try{Core.meanStdDev(candidate,mean,std);if(std.toArray()[0]<5)return 0;
            Imgproc.matchTemplate(candidate,reference,score,Imgproc.TM_CCOEFF_NORMED);return score.get(0,0)[0];
        }finally{candidate.release();score.release();mean.release();std.release();}
    }
    void clear(){for(Track t:tracks)t.template.release();tracks.clear();recentlyLost.clear();history.clear();if(previous!=null){previous.release();previous=null;}}
    @Override public void close(){clear();}
}
