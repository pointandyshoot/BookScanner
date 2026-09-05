package au.id.pointandyshoot.bookscanner.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class GeometryTest {
    @Test public void unrotatesAllFourCorners(){
        assertArrayEquals(new float[]{0,0},Geometry.unrotate(0,0,0,100,200),.001f);
        assertArrayEquals(new float[]{0,0},Geometry.unrotate(200,0,90,100,200),.001f);
        assertArrayEquals(new float[]{100,200},Geometry.unrotate(0,100,90,100,200),.001f);
        assertArrayEquals(new float[]{0,0},Geometry.unrotate(100,200,180,100,200),.001f);
        assertArrayEquals(new float[]{0,0},Geometry.unrotate(0,100,270,100,200),.001f);
    }
    @Test public void overlapSeparatesNeighbouringSpines(){
        assertEquals(1,Geometry.overlap(new float[]{0,0,10,100},new float[]{0,0,10,100}),.001);
        assertEquals(0,Geometry.overlap(new float[]{0,0,10,100},new float[]{11,0,21,100}),.001);
    }
}
