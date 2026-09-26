package au.id.pointandyshoot.bookscanner;

import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import au.id.pointandyshoot.bookscanner.core.WantedBook;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AngledOcrTest {
    @BeforeClass public static void nativeRuntime(){assertTrue(NativeVision.initialise());}
    @Test public void arbitraryRotationAndCropMapBackToSource(){
        Bitmap b=Bitmap.createBitmap(900,700,Bitmap.Config.ARGB_8888);
        try(ReadingImage reading=new ReadingImage(b,new Rect(100,150,700,450),37,1.5f)){
            Matrix forward=new Matrix();assertTrue(reading.toSource.invert(forward));
            float[] p={110,180,650,400};float[] transformed=p.clone();forward.mapPoints(transformed);reading.toSource.mapPoints(transformed);
            assertArrayEquals(p,transformed,.01f);
        }finally{b.recycle();}
    }
    @Test public void readsSmallAuthorTextOnTiltedHighResolutionFrame() throws Exception {
        Bitmap base=Bitmap.createBitmap(1920,1440,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(base);canvas.drawColor(Color.WHITE);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(42);
        canvas.drawText("TERRY PRATCHETT",550,700,paint);
        WantedBook wanted=new WantedBook("public-example","*","Terry Pratchett",List.of(),true);
        try(OcrReader reader=new OcrReader(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets())){
            for(float tilt:new float[]{-27,33,73}){
                try(ReadingImage scene=new ReadingImage(base,new Rect(0,0,1920,1440),tilt,1)){
                    List<LiveTracker.Detection> hits=reader.readAtAngle(scene.bitmap,new Rect(0,0,scene.bitmap.getWidth(),scene.bitmap.getHeight()),0,1,List.of(wanted));
                    assertFalse("No author at tilt "+tilt,hits.isEmpty());
                    float[] bounds=LiveTracker.bounds(hits.get(0).quad);
                    assertTrue(bounds[0]>=0&&bounds[2]<=scene.bitmap.getWidth()+2);
                }
            }
        }finally{base.recycle();}
    }
    @Test public void splitAuthorSurnameCanHighlightWithoutFullName() throws Exception {
        Bitmap scene=Bitmap.createBitmap(1200,900,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(scene);canvas.drawColor(Color.rgb(45,38,38));
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.rgb(215,195,160));
        paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        paint.setTextSize(19);canvas.drawText("STEPHENIE",400,380,paint);
        paint.setTextSize(30);canvas.drawText("MEYER",400,418,paint);
        WantedBook wanted=new WantedBook("split-example","*","Stephenie Meyer",List.of(),true);
        try(OcrReader reader=new OcrReader(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets())){
            List<LiveTracker.Detection> hits=reader.readAtAngle(scene,new Rect(0,0,1200,900),0,1,List.of(wanted));
            assertFalse("A split author name should produce an immediate hint",hits.isEmpty());
        }finally{scene.recycle();}
    }
}
