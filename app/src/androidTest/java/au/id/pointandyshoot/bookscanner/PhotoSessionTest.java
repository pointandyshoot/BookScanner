package au.id.pointandyshoot.bookscanner;

import android.app.Application;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PhotoSessionTest {
    @Test public void lateCaptureCannotRestoreClearedOrClosedBatch(){
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(()->{
            PhotoSession session=new PhotoSession((Application)instrumentation.getTargetContext().getApplicationContext(),List.of());
            var shot=session.reserve();assertNotNull(shot);session.clear();
            try{Files.write(shot.file.toPath(),new byte[]{1,2,3});}catch(Exception e){throw new AssertionError(e);}
            session.captured(shot);assertTrue(session.shots.isEmpty());assertFalse(shot.file.exists());
            var second=session.reserve();assertNotNull(second);session.onCleared();session.captured(second);assertFalse(second.file.exists());
        });
    }
    @Test public void queueLimitCountsCapturesAndStopResumePreservesReservation(){
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(()->{
            PhotoSession session=new PhotoSession((Application)instrumentation.getTargetContext().getApplicationContext(),List.of());
            for(int i=0;i<PhotoSession.LIMIT;i++)assertNotNull(session.reserve());assertNull(session.reserve());
            session.stopProcessing();assertTrue(session.paused());assertEquals(PhotoSession.LIMIT,session.shots.size());
            session.resumeProcessing();assertFalse(session.paused());session.onCleared();
        });
    }
}
