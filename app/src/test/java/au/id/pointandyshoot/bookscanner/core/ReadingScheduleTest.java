package au.id.pointandyshoot.bookscanner.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReadingScheduleTest {
    @Test public void firstReadKeepsRightAngleAndAddsMeasuredCorrection(){assertArrayEquals(new float[]{90,67.5f},new ReadingSchedule().next(22.5f),.001f);}
    @Test public void negativeTiltCorrectsInOppositeDirection(){assertArrayEquals(new float[]{90,107},new ReadingSchedule().next(-17),.001f);}
    @Test public void missingEdgesStillExploreNonRightAngles(){ReadingSchedule s=new ReadingSchedule();assertEquals(105,s.next(Float.NaN)[1],.001);assertEquals(165,s.next(Float.NaN)[1],.001);}
    @Test public void allQuadrantsContinueAfterSuccessfulReads(){ReadingSchedule s=new ReadingSchedule();java.util.Set<Integer> seen=new java.util.HashSet<>();for(int i=0;i<12;i++){float[] a=s.next(20);seen.add((int)a[0]);s.result(a[0],true);}assertEquals(java.util.Set.of(0,90,180,270),seen);}
}
