package au.id.pointandyshoot.bookscanner.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class DiscoveryScheduleTest {
    @Test public void bothShelvesGetDetailWithinFirstFourJobs(){
        DiscoverySchedule schedule=new DiscoverySchedule();
        DiscoverySchedule.Plan full=schedule.next(),lower=schedule.next(),repeat=schedule.next(),upper=schedule.next();
        assertTrue(full.full);assertEquals(90,full.angle,0);
        assertEquals(.4f,lower.top,0);assertEquals(1,lower.bottom,0);
        assertEquals(lower.top,repeat.top,0);assertEquals(lower.bottom,repeat.bottom,0);
        assertEquals(0,upper.top,0);assertEquals(.6f,upper.bottom,0);
        assertTrue("Shelf bands must overlap",lower.top<upper.bottom);
    }
    @Test public void uprightTextKeepsARegularFullViewRead(){
        DiscoverySchedule schedule=new DiscoverySchedule();
        for(int i=0;i<24;i++){
            DiscoverySchedule.Plan plan=schedule.next();
            assertEquals(i%4==0,plan.full);
            assertEquals(i%8==4?0:90,plan.angle,0);
        }
    }
    @Test public void newSessionStartsWithRotatedDiscovery(){
        DiscoverySchedule schedule=new DiscoverySchedule();for(int i=0;i<7;i++)schedule.next();schedule.reset();
        assertEquals("full 90°",schedule.next().scope);
    }
}
