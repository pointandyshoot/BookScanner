package au.id.pointandyshoot.bookscanner;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LiveTrackerTest {
    private static final float[] BOX={120,130,480,130,480,260,120,260};
    @BeforeClass public static void nativeRuntime(){assertTrue(NativeVision.initialise());}
    private Mat shelf(){
        Mat m=new Mat(480,640,CvType.CV_8UC1,Scalar.all(240));
        Imgproc.rectangle(m,new Point(118,128),new Point(482,262),Scalar.all(25),2);
        Imgproc.putText(m,"A WANTED BOOK",new Point(135,170),Imgproc.FONT_HERSHEY_SIMPLEX,.85,Scalar.all(10),2);
        Imgproc.putText(m,"TERRY PRATCHETT",new Point(135,215),Imgproc.FONT_HERSHEY_SIMPLEX,.8,Scalar.all(30),2);
        for(int i=0;i<8;i++)Imgproc.circle(m,new Point(40+i*70,350+i%2*30),8,Scalar.all(60),2);
        return m;
    }
    private VisionFrames.Gray gray(Mat m){byte[] data=new byte[m.rows()*m.cols()];m.get(0,0,data);return new VisionFrames.Gray(data,m.cols(),m.rows(),m.cols(),m.rows());}
    private List<LiveTracker.Detection> detection(){return List.of(new LiveTracker.Detection("wanted","A wanted book","Title match",BOX));}
    private Mat shifted(Mat base,int i){
        Mat transform=Imgproc.getRotationMatrix2D(new Point(320,240),i*.12,1+i*.0005);
        transform.put(0,2,transform.get(0,2)[0]+i*.8);transform.put(1,2,transform.get(1,2)[0]+i*.2);
        Mat next=new Mat();Imgproc.warpAffine(base,next,transform,base.size(),Imgproc.INTER_LINEAR,Core.BORDER_CONSTANT,Scalar.all(240));transform.release();return next;
    }
    @Test public void followsTranslationRotationAndScaleForSecondsWithoutOcr(){
        Mat base=shelf();try(LiveTracker tracker=new LiveTracker()){
            VisionFrames.Gray first=gray(base);tracker.frame(first,1,0);tracker.detections(detection(),first,1,0);
            assertEquals(1,tracker.visible().size());
            for(int i=1;i<=50;i++){Mat next=shifted(base,i);try{tracker.frame(gray(next),i+1,i*70);}finally{next.release();}
                assertEquals("Lost track at frame "+i,1,tracker.visible().size());}
            float[] b=LiveTracker.bounds(tracker.visible().get(0).detection.quad);
            assertTrue("Box should move right",(b[0]+b[2])/2>325);
            assertTrue(tracker.visible().get(0).tracking);
        }finally{base.release();}
    }
    @Test public void delayedOcrIsMovedIntoCurrentFrame(){
        Mat base=shelf();try(LiveTracker tracker=new LiveTracker()){
            VisionFrames.Gray first=gray(base);tracker.frame(first,1,0);
            for(int i=1;i<=8;i++){Mat next=shifted(base,i);try{tracker.frame(gray(next),i+1,i*70);}finally{next.release();}}
            tracker.detections(detection(),first,1,0);assertEquals(1,tracker.visible().size());
            float[] b=LiveTracker.bounds(tracker.visible().get(0).detection.quad);assertTrue((b[0]+b[2])/2>303);
        }finally{base.release();}
    }
    @Test public void blankFrameCannotKeepAnOldBookAlive(){
        Mat base=shelf(),blank=new Mat(480,640,CvType.CV_8UC1,Scalar.all(240));
        try(LiveTracker tracker=new LiveTracker()){
            VisionFrames.Gray first=gray(base);tracker.frame(first,1,0);tracker.detections(detection(),first,1,0);
            for(int i=1;i<=20;i++)tracker.frame(gray(blank),i+1,i*100);
            assertTrue(tracker.visible().isEmpty());
        }finally{base.release();blank.release();}
    }
    @Test public void separateBooksWithSameWantedEntryKeepSeparateStableIdentities(){
        Mat base=shelf();try(LiveTracker tracker=new LiveTracker()){
            VisionFrames.Gray first=gray(base);tracker.frame(first,1,0);
            List<LiveTracker.Detection> two=List.of(detection().get(0),new LiveTracker.Detection("wanted","Same author","Author match",new float[]{20,330,130,330,130,400,20,400}));
            tracker.detections(two,first,1,0);assertEquals(2,tracker.visible().size());
            String a=tracker.visible().get(0).appearanceId,b=tracker.visible().get(1).appearanceId;
            assertNotEquals(a,b);tracker.detections(two,first,1,0);
            assertEquals(a,tracker.visible().get(0).appearanceId);assertEquals(b,tracker.visible().get(1).appearanceId);
        }finally{base.release();}
    }
    @Test public void oldResultsAndSessionResetCannotResurrectBoxes(){
        Mat base=shelf();try(LiveTracker tracker=new LiveTracker()){
            VisionFrames.Gray first=gray(base);tracker.frame(first,1,0);tracker.frame(first,2,4000);
            tracker.detections(detection(),first,1,0);assertTrue(tracker.visible().isEmpty());
            tracker.detections(detection(),first,2,4000);assertEquals(1,tracker.visible().size());
            tracker.clear();assertTrue(tracker.visible().isEmpty());
        }finally{base.release();}
    }
}
