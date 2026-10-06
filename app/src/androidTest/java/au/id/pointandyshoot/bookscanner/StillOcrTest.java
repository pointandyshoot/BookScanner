package au.id.pointandyshoot.bookscanner;

import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import au.id.pointandyshoot.bookscanner.core.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StillOcrTest {
    @Test public void stillSearchPublishesProgressAndMapsBothOrientationsToOriginalPhoto() throws Exception {
        assertTrue(NativeVision.initialise());
        Bitmap scene=Bitmap.createBitmap(1152,1536,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(scene);c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(35);
        c.save();c.translate(150,130);c.rotate(90);c.drawText("TERRY PRATCHETT",0,0,p);c.restore();
        c.drawText("STEPHEN KING",550,1280,p);
        List<WantedBook> wanted=List.of(new WantedBook("vertical","*","Terry Pratchett",List.of(),true),new WantedBook("horizontal","*","Stephen King",List.of(),true));
        int[] last={-1},updates={0};Set<String> found=new HashSet<>();
        try(OcrReader reader=new OcrReader(InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets())){
            reader.readStill(scene,wanted,()->true,progress->{assertTrue(progress.percent>=last[0]);assertTrue(progress.percent<=100);last[0]=progress.percent;updates[0]++;},hits->{
                for(var hit:hits){found.add(hit.id);float[] b=LiveTracker.bounds(hit.quad);
                    if(hit.id.equals("vertical")){assertTrue(b[0]<250);assertTrue(b[1]<750);}
                    else {assertTrue(b[0]>500);assertTrue(b[1]>1150);}
                }
            });
            assertEquals(100,last[0]);assertTrue(updates[0]>12);assertTrue(found.contains("vertical"));assertTrue(found.contains("horizontal"));
        }finally{scene.recycle();}
    }
    @Test public void cancellingStopsBeforeFurtherSectionReadsAndNeverReportsComplete() throws Exception {
        assertTrue(NativeVision.initialise());Bitmap scene=Bitmap.createBitmap(700,1000,Bitmap.Config.ARGB_8888);new Canvas(scene).drawColor(Color.WHITE);
        AtomicBoolean valid=new AtomicBoolean(true);int[] calls={0};
        try(OcrReader reader=new OcrReader(InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets())){
            reader.readStill(scene,List.of(),valid::get,p->{calls[0]++;assertTrue(p.percent<100);valid.set(false);},hits->fail("Cancelled job must not publish hints"));
            assertEquals(1,calls[0]);
        }finally{scene.recycle();}
    }
    @Test public void reviewDecodeHonoursJpegOrientationAndBoundsMemory() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bitmap source=Bitmap.createBitmap(800,500,Bitmap.Config.ARGB_8888);new Canvas(source).drawColor(Color.RED);
        var file=java.io.File.createTempFile("public-still-test",".jpg",context.getCacheDir());
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){source.compress(Bitmap.CompressFormat.JPEG,90,out);}source.recycle();
        android.media.ExifInterface exif=new android.media.ExifInterface(file.getAbsolutePath());
        exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6");exif.saveAttributes();
        try {Bitmap decoded=PhotoSession.decode(file,400);assertEquals(250,decoded.getWidth());assertEquals(400,decoded.getHeight());assertEquals(Bitmap.Config.ARGB_8888,decoded.getConfig());decoded.recycle();}
        finally{file.delete();}
    }
}
