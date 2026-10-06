package au.id.pointandyshoot.bookscanner;

import android.Manifest;
import android.graphics.*;
import android.view.*;
import android.widget.Button;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StillUiTest {
    private View find(View root,String label){
        if(root instanceof Button b&&b.getText().toString().equals(label))return root;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),label);if(found!=null)return found;}
        return null;
    }
    private PhotoReviewView review(View root){
        if(root instanceof PhotoReviewView r)return r;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){var found=review(group.getChildAt(i));if(found!=null)return found;}
        return null;
    }
    @Test public void photoQueueAndReviewSurviveActivityRecreation() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        instrumentation.getUiAutomation().grantRuntimePermission(instrumentation.getTargetContext().getPackageName(),Manifest.permission.CAMERA);
        try(ActivityScenario<StillActivity> scenario=ActivityScenario.launch(StillActivity.class)){
            scenario.onActivity(activity->{
                var model=new ViewModelProvider(activity).get(PhotoSession.class);var shot=model.reserve();assertNotNull(shot);
                Bitmap bitmap=Bitmap.createBitmap(300,400,Bitmap.Config.ARGB_8888);new Canvas(bitmap).drawColor(Color.GRAY);
                try(var out=new java.io.FileOutputStream(shot.file)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);}catch(Exception e){throw new AssertionError(e);}finally{bitmap.recycle();}
                model.failed(shot,"Generated test photo");var button=find(activity.getWindow().getDecorView(),"Review");assertNotNull(button);button.performClick();
            });
            scenario.recreate();instrumentation.waitForIdleSync();
            scenario.onActivity(activity->{
                var model=new ViewModelProvider(activity).get(PhotoSession.class);assertEquals(1,model.shots.size());assertTrue(model.shots.get(0).file.exists());
                assertNotNull(find(activity.getWindow().getDecorView(),"Camera"));
                var view=review(activity.getWindow().getDecorView());assertNotNull(view);assertEquals(View.VISIBLE,view.getVisibility());assertTrue(view.getWidth()>0&&view.getHeight()>0);
            });
        }
    }
    @Test public void reviewPrefersPhotoWithDiscoveriesWhenFirstPhotoHasNone(){
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        instrumentation.getUiAutomation().grantRuntimePermission(instrumentation.getTargetContext().getPackageName(),Manifest.permission.CAMERA);
        try(ActivityScenario<StillActivity> scenario=ActivityScenario.launch(StillActivity.class)){
            scenario.onActivity(activity->{
                var model=new ViewModelProvider(activity).get(PhotoSession.class);
                var first=model.reserve();var second=model.reserve();
                for(var shot:new PhotoSession.Shot[]{first,second}){
                    Bitmap bitmap=Bitmap.createBitmap(300,400,Bitmap.Config.ARGB_8888);new Canvas(bitmap).drawColor(Color.GRAY);
                    try(var out=new java.io.FileOutputStream(shot.file)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);}catch(Exception e){throw new AssertionError(e);}finally{bitmap.recycle();}
                    shot.state=PhotoSession.State.DONE;shot.width=300;shot.height=400;
                }
                second.hits=java.util.List.of(new LiveTracker.Detection("public-review","Example author","author",new float[]{100,150,150,150,150,300,100,300},false));
                var button=find(activity.getWindow().getDecorView(),"Review");assertNotNull(button);button.performClick();
                assertNotNull("Review opens the matching second photo",find(activity.getWindow().getDecorView(),"Hints (1)"));
            });
        }
    }

}
