package au.id.pointandyshoot.bookscanner;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.*;
import android.util.Size;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.*;
import androidx.camera.core.resolutionselector.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.*;
import java.util.*;
import java.util.concurrent.*;

/** Capture a shelf now; review discoveries while the retained queue reads each photo. */
public final class StillActivity extends ComponentActivity {
    private PhotoSession session;
    private PreviewView preview;
    private PhotoReviewView review;
    private FrameLayout frame;
    private TextView batchText,workText,photoText;
    private ProgressBar batchProgress,photoProgress;
    private Button captureButton,burstButton,reviewButton,previousButton,nextButton,stopButton,discoveriesButton,torchButton;
    private ProcessCameraProvider provider;
    private Camera camera;
    private ImageCapture capture;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService displayWorker=Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<String> permission;
    private ActivityResultLauncher<String[]> importPhotos;
    private boolean visible,showingPhoto,askedPermission,capturing,torch;
    private volatile boolean destroyed;
    private int selected=-1,burstRemaining,cameraToken;
    private volatile int displayToken;
    private PhotoSession.Shot loaded,loading;
    private LiveTracker.Detection pendingFocus;
    private final Runnable nextBurst=this::capturePhoto;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        List<au.id.pointandyshoot.bookscanner.core.WantedBook> books;
        try{books=new WantedStore(this).load();}catch(Exception e){books=List.of();}
        final var wanted=books;
        session=new ViewModelProvider(this,new ViewModelProvider.Factory(){
            @Override public <T extends ViewModel>T create(Class<T> model){return model.cast(new PhotoSession(getApplication(),wanted));}
        }).get(PhotoSession.class);
        if(state!=null){selected=state.getInt("photo",-1);showingPhoto=state.getBoolean("review");}
        permission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{if(granted)startCamera();else render();});
        importPhotos=registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(),uris->{
            for(var uri:uris){if(session.shots.size()>=PhotoSession.LIMIT){toast("Batch limit is eight photos. Clear this batch to add more.");break;}session.importPhoto(uri);}
            render();
        });
        build();session.listen(this::render);
        if(books.stream().noneMatch(b->b.enabled))toast("Add or enable wanted books on the live screen first.");
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){if(showingPhoto)showCamera();else leave();}
        });
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(String value){TextView t=new TextView(this);t.setText(value);t.setTextSize(14);t.setTextColor(Color.rgb(235,244,239));t.setPadding(dp(10),dp(5),dp(10),dp(5));return t;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    private void weighted(LinearLayout row,View child){row.addView(child,new LinearLayout.LayoutParams(0,-2,1));}
    private ProgressBar meter(String description){ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setMax(100);p.setContentDescription(description);return p;}
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(17,25,22));setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{var bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});ViewCompat.requestApplyInsets(root);
        LinearLayout header=row();TextView title=text("Shelf photos");title.setTextSize(22);weighted(header,title);header.addView(button("Live",this::leave));root.addView(header);
        batchText=text("");root.addView(batchText);batchProgress=meter("Batch processing progress");root.addView(batchProgress,new LinearLayout.LayoutParams(-1,dp(8)));
        workText=text("");root.addView(workText);photoProgress=meter("Current photo processing progress");root.addView(photoProgress,new LinearLayout.LayoutParams(-1,dp(8)));
        frame=new FrameLayout(this);preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        review=new PhotoReviewView(this);frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));frame.addView(review,new FrameLayout.LayoutParams(-1,-1));root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        preview.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP){v.performClick();if(camera!=null){var point=preview.getMeteringPointFactory().createPoint(e.getX(),e.getY());camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).setAutoCancelDuration(3,TimeUnit.SECONDS).build());}}return true;});
        photoText=text("");root.addView(photoText);
        LinearLayout navigation=row();previousButton=button("Previous",()->select(selected-1));nextButton=button("Next",()->select(selected+1));discoveriesButton=button("Discoveries",this::discoveries);weighted(navigation,previousButton);weighted(navigation,discoveriesButton);weighted(navigation,nextButton);root.addView(navigation);
        LinearLayout controls=row();captureButton=button("Capture",()->{if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.CAMERA);else{burstRemaining=0;capturePhoto();}});
        burstButton=button("Burst (3)",()->{if(burstRemaining>0){stopBurst();return;}burstRemaining=Math.min(3,PhotoSession.LIMIT-session.shots.size());capturePhoto();});
        reviewButton=button("Review",()->{if(showingPhoto)showCamera();else if(!session.shots.isEmpty())select(Math.max(0,selected));});weighted(controls,captureButton);weighted(controls,burstButton);weighted(controls,reviewButton);root.addView(controls);
        LinearLayout actions=row();weighted(actions,button("Import",()->importPhotos.launch(new String[]{"image/*"})));stopButton=button("Stop",()->{if(session.paused())session.resumeProcessing();else{stopBurst();session.stopProcessing();}});weighted(actions,stopButton);
        weighted(actions,button("Clear",()->{stopBurst();session.clear();displayToken++;loaded=loading=null;review.clear();selected=-1;showCamera();}));root.addView(actions);
        LinearLayout tools=row();torchButton=button("Torch",()->{if(camera!=null&&camera.getCameraInfo().hasFlashUnit()){torch=!torch;camera.getCameraControl().enableTorch(torch);render();}});tools.addView(torchButton);weighted(tools,text("Tap to focus · pan gently during burst\nReview: pinch to zoom · double tap to reset"));root.addView(tools);
    }
    private void render(){
        if(destroyed)return;int done=0,failed=0,sum=0,hints=0;PhotoSession.Shot reading=null;
        for(var shot:session.shots){if(shot.state==PhotoSession.State.DONE)done++;if(shot.state==PhotoSession.State.FAILED)failed++;sum+=shot.state==PhotoSession.State.FAILED?100:shot.percent;hints+=shot.hits.size();if(shot.state==PhotoSession.State.READING)reading=shot;}
        batchText.setText("Batch · "+done+" / "+session.shots.size()+" complete · "+hints+" hints"+(failed>0?" · "+failed+" failed":""));batchProgress.setProgress(session.shots.isEmpty()?0:sum/session.shots.size());
        workText.setText(reading!=null?reading.name+" · "+reading.stage:session.paused()?"Processing stopped · resume when ready":session.shots.isEmpty()?"Capture a shelf or import photos to begin":done+failed==session.shots.size()?"Batch complete · review discoveries":"Waiting for the next photo");
        photoProgress.setIndeterminate(reading!=null&&reading.stage.equals("Opening photo"));photoProgress.setProgress(reading!=null?reading.percent:done+failed==session.shots.size()&&!session.shots.isEmpty()?100:0);
        boolean permissionGranted=ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;
        captureButton.setText(permissionGranted?"Capture":"Camera permission");captureButton.setEnabled(!capturing&&session.shots.size()<PhotoSession.LIMIT&&(!permissionGranted||capture!=null)&&!showingPhoto);
        burstButton.setText(burstRemaining>0?"Stop burst":"Burst (3)");burstButton.setEnabled(burstRemaining>0||(!capturing&&capture!=null&&!showingPhoto&&session.shots.size()<PhotoSession.LIMIT));
        reviewButton.setText(showingPhoto?"Camera":"Review");reviewButton.setEnabled(showingPhoto||!session.shots.isEmpty());stopButton.setText(session.paused()?"Resume reads":"Stop reads");stopButton.setEnabled(!session.shots.isEmpty());
        previousButton.setEnabled(selected>0);nextButton.setEnabled(selected>=0&&selected<session.shots.size()-1);
        var shot=selected>=0&&selected<session.shots.size()?session.shots.get(selected):null;
        photoText.setText(shot==null?"Up to eight photos · temporary storage":shot.name+" / "+session.shots.size()+" · "+shot.hits.size()+" hints · "+shot.stage+(shot.state==PhotoSession.State.FAILED?" · tap Discoveries to retry":""));
        discoveriesButton.setEnabled(shot!=null);discoveriesButton.setText(shot==null?"Discoveries":"Hints ("+shot.hits.size()+")");
        review.setVisibility(showingPhoto?View.VISIBLE:View.GONE);preview.setVisibility(showingPhoto?View.GONE:View.VISIBLE);
        torchButton.setEnabled(!showingPhoto&&camera!=null&&camera.getCameraInfo().hasFlashUnit());torchButton.setText(torch?"Torch on":"Torch");
        if(showingPhoto&&shot!=null){if(loaded==shot)review.hints(shot.hits,shot.width,shot.height);else if(loading!=shot&&shot.state!=PhotoSession.State.CAPTURING)loadReview(shot);}
        if(visible&&(reading!=null||capturing||burstRemaining>0))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void select(int index){
        if(index<0||index>=session.shots.size())return;stopBurst();selected=index;showingPhoto=true;stopCamera();loaded=loading=null;displayToken++;review.clear();render();
    }
    private void loadReview(PhotoSession.Shot shot){
        loading=shot;int token=++displayToken;
        displayWorker.execute(()->{
            Bitmap bitmap=null;
            try{if(token!=displayToken||destroyed)return;bitmap=PhotoSession.decode(shot.file,2048);final Bitmap result=bitmap;bitmap=null;
                runOnUiThread(()->{if(token!=displayToken||destroyed){result.recycle();return;}loaded=shot;loading=null;review.photo(result,shot.width>0?shot.width:result.getWidth(),shot.height>0?shot.height:result.getHeight());review.hints(shot.hits,shot.width>0?shot.width:result.getWidth(),shot.height>0?shot.height:result.getHeight());if(pendingFocus!=null){review.focus(pendingFocus);pendingFocus=null;}});
            }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{if(token==displayToken&&!destroyed){loaded=shot;loading=null;toast("Photo couldn’t open. Retry or clear this batch.");}});}finally{if(bitmap!=null)bitmap.recycle();}
        });
    }
    private void discoveries(){
        if(selected<0||selected>=session.shots.size())return;var shot=session.shots.get(selected);
        if(shot.state==PhotoSession.State.FAILED){session.retry(shot);return;}
        List<LiveTracker.Detection> hits=shot.hits;
        if(hits.isEmpty()){toast("No hints yet. Photos continue processing in the queue.");return;}
        new AlertDialog.Builder(this).setTitle(shot.name+" discoveries").setItems(hits.stream().map(h->h.label+(h.reason.contains("check title")?" · check title":" · possible")).toArray(String[]::new),(d,i)->{if(!showingPhoto)select(selected);if(loaded==shot)review.focus(hits.get(i));else pendingFocus=hits.get(i);}).setPositiveButton("Done",null).show();
    }
    private void showCamera(){showingPhoto=false;displayToken++;loaded=loading=null;review.clear();render();if(visible)preview.post(this::startCamera);}
    private void capturePhoto(){
        if(destroyed||!visible||showingPhoto||capturing||capture==null){stopBurst();return;}
        var shot=session.reserve();if(shot==null){stopBurst();toast("Eight-photo limit reached. Clear this batch to continue.");return;}
        capturing=true;render();capture.setTargetRotation(preview.getDisplay().getRotation());
        capture.takePicture(new ImageCapture.OutputFileOptions.Builder(shot.file).build(),getMainExecutor(),new ImageCapture.OnImageSavedCallback(){
            @Override public void onImageSaved(ImageCapture.OutputFileResults result){capturing=false;session.captured(shot);if(selected<0&&session.shots.contains(shot))selected=session.shots.indexOf(shot);if(burstRemaining>0){burstRemaining--;if(burstRemaining>0&&visible&&!showingPhoto)main.postDelayed(nextBurst,900);}render();}
            @Override public void onError(ImageCaptureException error){capturing=false;session.failed(shot,"Capture failed. Clear and try again.");stopBurst();render();}
        });
    }
    private void stopBurst(){burstRemaining=0;main.removeCallbacks(nextBurst);}
    private void startCamera(){
        if(destroyed||!visible||showingPhoto)return;
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){if(!askedPermission){askedPermission=true;permission.launch(Manifest.permission.CAMERA);}render();return;}
        if(preview.getWidth()==0){preview.post(this::startCamera);return;}int token=++cameraToken;
        var future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            if(destroyed||!visible||showingPhoto||token!=cameraToken)return;
            try{provider=future.get();provider.unbindAll();
                Preview p=new Preview.Builder().setTargetRotation(preview.getDisplay().getRotation()).build();p.setSurfaceProvider(preview.getSurfaceProvider());
                capture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setJpegQuality(95)
                        .setResolutionSelector(new ResolutionSelector.Builder().setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                                .setResolutionStrategy(new ResolutionStrategy(new Size(4032,3024),ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                        .setTargetRotation(preview.getDisplay().getRotation()).build();
                camera=provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,capture);render();
            }catch(Exception e){capture=null;toast("Camera couldn’t start. Import photos or return to Live and retry.");render();}
        },getMainExecutor());
    }
    private void stopCamera(){cameraToken++;capture=null;camera=null;torch=false;if(provider!=null)provider.unbindAll();}
    private void leave(){
        stopBurst();if(session.shots.isEmpty()){finish();return;}
        new AlertDialog.Builder(this).setMessage("Leave Shelf photos? Temporary photos and discoveries will be cleared.")
                .setPositiveButton("Leave",(d,w)->finish()).setNegativeButton("Keep reviewing",null).show();
    }
    private void toast(String value){if(!destroyed)Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("photo",selected);state.putBoolean("review",showingPhoto);super.onSaveInstanceState(state);}
    @Override protected void onResume(){super.onResume();visible=true;render();if(!showingPhoto)preview.post(this::startCamera);}
    @Override protected void onPause(){visible=false;stopBurst();stopCamera();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onPause();}
    @Override protected void onDestroy(){destroyed=true;displayToken++;session.listen(null);stopBurst();stopCamera();review.clear();displayWorker.shutdown();super.onDestroy();}
}
