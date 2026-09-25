package au.id.pointandyshoot.bookscanner.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConfirmationTest {
    @Test public void repeatedWeakReadNeverConfirms(){Confirmation c=new Confirmation();for(int i=0;i<30;i++)c.observe(i,i*100,false);assertFalse(c.confirmed());}
    @Test public void threeSeparateStrongFramesConfirm(){Confirmation c=new Confirmation();c.observe(1,0,true);c.observe(2,1000,true);assertFalse(c.confirmed());c.observe(3,2000,true);assertTrue(c.confirmed());}
    @Test public void cropsOfOneFrameCountOnce(){Confirmation c=new Confirmation();for(int i=0;i<20;i++)c.observe(1,0,true);assertFalse(c.confirmed());}
    @Test public void weakFrameCanBeUpgradedOnce(){Confirmation c=new Confirmation();c.observe(1,0,false);c.observe(1,0,true);c.observe(1,0,true);c.observe(2,100,true);assertFalse(c.confirmed());c.observe(3,200,true);assertTrue(c.confirmed());}
    @Test public void staleEvidenceExpires(){Confirmation c=new Confirmation();c.observe(1,0,true);c.observe(2,1000,true);c.observe(3,7000,true);assertFalse(c.confirmed());}
    @Test public void delayedOlderFrameDoesNotCount(){Confirmation c=new Confirmation();c.observe(2,1000,true);c.observe(1,0,true);c.observe(3,2000,true);assertFalse(c.confirmed());}
    @Test public void weakReadsCannotFillStrongQuota(){Confirmation c=new Confirmation();c.observe(1,0,true);c.observe(2,100,false);c.observe(3,200,false);assertFalse(c.confirmed());}
}
