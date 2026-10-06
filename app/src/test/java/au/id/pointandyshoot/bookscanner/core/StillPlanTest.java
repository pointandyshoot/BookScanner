package au.id.pointandyshoot.bookscanner.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class StillPlanTest {
    @Test public void sectionsCoverEveryPixelWithoutGaps(){
        for(int[] size:new int[][]{{1152,1536},{1536,1152},{4032,3024},{3024,4032},{4096,1000},{200,300}}){
            var sections=StillPlan.sections(size[0],size[1]);
            boolean[][] covered=new boolean[size[1]][size[0]];
            for(int[] b:sections){
                assertTrue(b[0]>=0&&b[1]>=0&&b[2]<=size[0]&&b[3]<=size[1]);
                assertTrue(b[2]>b[0]&&b[3]>b[1]);
                for(int y=b[1];y<b[3];y++)for(int x=b[0];x<b[2];x++)covered[y][x]=true;
            }
            for(boolean[] row:covered)for(boolean pixel:row)assertTrue("Unsearched photo pixel",pixel);
        }
    }
    @Test public void widerPhotosRetainAtLeastSixDetailSections(){
        assertEquals(6,StillPlan.sections(1152,1536).size());
        assertEquals(6,StillPlan.sections(1536,1152).size());
        for(int[] b:StillPlan.sections(4032,3024)){
            assertTrue(b[2]-b[0]<=1400);assertTrue(b[3]-b[1]<=1400);
        }
    }
}
