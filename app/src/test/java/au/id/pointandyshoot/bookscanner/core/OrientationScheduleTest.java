package au.id.pointandyshoot.bookscanner.core;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class OrientationScheduleTest {
    @Test public void triesRightAnglesInRequestedOrder(){OrientationSchedule s=new OrientationSchedule();for(int a:new int[]{90,180,270,0,90})assertEquals(a,s.next());}
    @Test public void matchesNeverStarveExploration(){OrientationSchedule s=new OrientationSchedule();Set<Integer> explored=new HashSet<>();for(int i=0;i<12;i++){int a=s.next();explored.add(a);s.result(a,true);}assertEquals(Set.of(0,90,180,270),explored);}
    @Test public void sessionResetStartsWithVerticalText(){OrientationSchedule s=new OrientationSchedule();s.next();s.result(180,true);s.reset();assertEquals(90,s.next());}
}
