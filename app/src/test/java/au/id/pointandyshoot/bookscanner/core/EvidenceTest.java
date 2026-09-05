package au.id.pointandyshoot.bookscanner.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class EvidenceTest {
    @Test public void duplicateWithinSameFrameDoesNotConfirm(){Evidence e=new Evidence();float[] b={0,0,10,100};assertEquals(1,e.observe("a",b,10,1));assertEquals(1,e.observe("a",b,10,1));assertEquals(2,e.observe("a",b,300,2));}
    @Test public void adjacentBooksDoNotShareEvidence(){Evidence e=new Evidence();e.observe("a",new float[]{0,0,10,100},10,1);assertEquals(1,e.observe("a",new float[]{12,0,22,100},100,2));}
    @Test public void oldObservationsExpire(){Evidence e=new Evidence();float[] b={0,0,10,100};e.observe("a",b,10,1);assertEquals(1,e.observe("a",b,1000,2));}
    @Test public void sessionResetClearsEvidence(){Evidence e=new Evidence();float[] b={0,0,10,100};e.observe("a",b,10,1);e.clear();assertEquals(1,e.observe("a",b,100,2));}
}
