package au.id.pointandyshoot.bookscanner.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class TextNeighbourTest {
    private float[] box(float x,float y,float w,float h){return new float[]{x,y,x+w,y,x+w,y+h,x,y+h};}
    @Test public void stackedNameLinesCanJoin(){assertTrue(TextNeighbour.canJoin(box(10,10,100,15),box(25,30,70,20)));}
    @Test public void distantLinesCannotJoin(){assertFalse(TextNeighbour.canJoin(box(10,10,100,15),box(10,150,100,15)));}
    @Test public void neighbouringColumnsCannotJoin(){assertFalse(TextNeighbour.canJoin(box(10,10,100,15),box(140,30,100,15)));}
    @Test public void crossedDirectionsCannotJoin(){assertFalse(TextNeighbour.canJoin(box(10,10,100,15),box(20,20,15,100)));}
    @Test public void rotatedPairCanJoin(){assertTrue(TextNeighbour.canJoin(box(10,10,15,100),box(30,25,20,70)));}
}
