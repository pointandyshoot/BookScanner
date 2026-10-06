package au.id.pointandyshoot.bookscanner;

import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PhotoOverlayTest {
    private int orange(Bitmap image,int x0,int y0,int x1,int y1){
        int count=0;for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){int p=image.getPixel(x,y);if(Color.red(p)>220&&Color.green(p)>100&&Color.green(p)<205&&Color.blue(p)<90)count++;}return count;
    }
    @Test public void sourceCoordinatesPaintVisibleBoxOverReducedReviewAndFocusedPhoto(){
        var instrumentation=InstrumentationRegistry.getInstrumentation();instrumentation.runOnMainSync(()->{
            var view=new PhotoReviewView(instrumentation.getTargetContext());view.layout(0,0,400,400);
            Bitmap photo=Bitmap.createBitmap(2000,1500,Bitmap.Config.ARGB_8888);new Canvas(photo).drawColor(Color.GRAY);
            var hit=new LiveTracker.Detection("public-overlay","Example author","author",new float[]{1000,1200,1200,1200,1200,1800,1000,1800},false);
            view.photo(photo,4000,3000);view.hints(List.of(hit),4000,3000);
            Bitmap image=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
            // Full source -> 400 px view: x=100..120, y=170..230, with letterboxing.
            assertTrue("Visible box at the source match",orange(image,90,185,130,225)>30);
            assertEquals("No outline in a different shelf area",0,orange(image,270,280,310,320));
            view.focus(hit);view.draw(new Canvas(image));
            assertTrue("Focused box surrounds the match at the view centre",orange(image,130,165,270,235)>30);
            image.recycle();view.clear();
        });
    }
}
