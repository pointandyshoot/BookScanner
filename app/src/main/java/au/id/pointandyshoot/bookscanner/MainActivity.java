package au.id.pointandyshoot.bookscanner;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
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
import au.id.pointandyshoot.bookscanner.core.WantedBook;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Two screens: live camera and the locally stored wanted list. */
public final class MainActivity extends ComponentActivity {
    private final ExecutorService scanExecutor=Executors.newSingleThreadExecutor();
    private final ExecutorService fileExecutor=Executors.newSingleThreadExecutor();
    private final List<WantedBook> books=new ArrayList<>();
    private WantedStore store;
    private ScanEngine scanner;
    private ProcessCameraProvider provider;
    private Camera camera;
    private PreviewView preview;
    private MatchOverlay overlay;
    private TextView status;
    private LinearLayout root;
    private Button pauseButton,torchButton;
    private boolean listScreen=false,resumed=false,paused=false,torch=false,destroyed=false;
    private int cameraGeneration=0;
    private PowerManager power;
    private PowerManager.OnThermalStatusChangedListener thermalListener;
    private ActivityResultLauncher<String> permission;
    private ActivityResultLauncher<String[]> importFile;
    private ActivityResultLauncher<String> exportFile;
    private String pendingExport;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        store=new WantedStore(this);
        scanner=new ScanEngine((hits,w,h,time,message,generation)->runOnUiThread(()->{
            if(destroyed || !resumed || listScreen || paused || generation!=scanner.generation())return;
            if(overlay!=null)overlay.update(hits,w,h,time);
            if(status!=null)status.setText(message);
        }));
        permission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{
            if(granted)startCamera();else showPermissionHelp();
        });
        importFile=registerForActivityResult(new ActivityResultContracts.OpenDocument(),this::importList);
        exportFile=registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),this::exportList);
        try {books.addAll(store.load());}catch(Exception e){message("Couldn’t open your saved list. Import a backup before making changes.");}
        scanner.setBooks(books);
        power=getSystemService(PowerManager.class);
        thermalListener=scanner::setThermal;
        power.addThermalStatusListener(getMainExecutor(),thermalListener);
        scanner.setThermal(power.getCurrentThermalStatus());
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){if(listScreen)showScanner();else finish();}
        });
        if(state!=null){paused=state.getBoolean("paused");}
        if(state!=null && state.getBoolean("list"))showList();else showScanner();
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putBoolean("paused",paused);out.putBoolean("list",listScreen);
        super.onSaveInstanceState(out);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(235,244,239));t.setPadding(dp(12),dp(8),dp(12),dp(8));return t;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    private void weighted(LinearLayout row,View v){row.addView(v,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));}
    private void installRoot(){
        root=vertical();root.setBackgroundColor(Color.rgb(17,25,22));setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
    private void showScanner(){
        stopCamera();listScreen=false;installRoot();
        LinearLayout header=horizontal();weighted(header,text("BookScanner",24));
        header.addView(button("Wanted ("+books.size()+")",this::showList));root.addView(header);
        status=text(books.isEmpty()?"Add books or authors to your wanted list to begin.":"Sweep slowly across a shelf",15);root.addView(status);
        FrameLayout frame=new FrameLayout(this);
        preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        overlay=new MatchOverlay(this);
        frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));frame.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        preview.setOnTouchListener((v,event)->{
            if(event.getAction()==android.view.MotionEvent.ACTION_UP){
                v.performClick();
                if(camera!=null){
                    MeteringPoint point=preview.getMeteringPointFactory().createPoint(event.getX(),event.getY());
                    camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point)
                            .setAutoCancelDuration(3,TimeUnit.SECONDS).build());
                }
            }
            return true;
        });
        LinearLayout controls=horizontal();
        pauseButton=button(paused?"Resume":"Pause",()->{paused=!paused;pauseButton.setText(paused?"Resume":"Pause");
            scanner.setEnabled(resumed&&!paused);overlay.clear();updateKeepAwake();
            status.setText(paused?"Paused":books.isEmpty()?"Add wanted books to begin":"Sweep slowly across a shelf");});
        torchButton=button("Torch",()->{if(camera!=null&&camera.getCameraInfo().hasFlashUnit()){
            torch=!torch;camera.getCameraControl().enableTorch(torch);torchButton.setText(torch?"Torch on":"Torch");}});
        weighted(controls,pauseButton);weighted(controls,torchButton);weighted(controls,button("Options",this::options));root.addView(controls);
        LinearLayout zoom=horizontal();zoom.addView(text("Zoom",14));
        SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setContentDescription("Camera zoom");
        zoom.addView(slider,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(zoom);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){if(user&&camera!=null){
                ZoomState z=camera.getCameraInfo().getZoomState().getValue();
                if(z!=null)camera.getCameraControl().setZoomRatio(Math.max(z.getMinZoomRatio(),1+(Math.min(3,z.getMaxZoomRatio())-1)*p/100f));
            }}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });
        root.addView(text("Amber: potential match • Green: read again\nTap the view to focus. Check highlighted books yourself.",12));
        scanner.setBooks(books);scanner.setSpineMode(getPreferences(0).getBoolean("spines",false));
        if(resumed)preview.post(this::startCamera);
    }
    private void startCamera(){
        if(destroyed||listScreen||!resumed||preview==null)return;
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            if(getPreferences(0).getBoolean("cameraAsked",false))showPermissionHelp();
            else {getPreferences(0).edit().putBoolean("cameraAsked",true).apply();permission.launch(Manifest.permission.CAMERA);}
            return;
        }
        if(preview.getWidth()==0){preview.post(this::startCamera);return;}
        int token=++cameraGeneration;
        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            if(destroyed||listScreen||!resumed||token!=cameraGeneration)return;
            try {
                provider=future.get();provider.unbindAll();
                Preview cameraPreview=new Preview.Builder().setTargetRotation(preview.getDisplay().getRotation()).build();
                cameraPreview.setSurfaceProvider(preview.getSurfaceProvider());
                ResolutionSelector resolution=new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(new Size(1280,960),ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build();
                ImageAnalysis analysis=new ImageAnalysis.Builder().setResolutionSelector(resolution)
                        .setTargetRotation(preview.getDisplay().getRotation())
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(scanExecutor,scanner);
                ViewPort viewport=preview.getViewPort();
                if(viewport==null)throw new IllegalStateException("Viewfinder isn’t ready");
                UseCaseGroup group=new UseCaseGroup.Builder().addUseCase(cameraPreview).addUseCase(analysis).setViewPort(viewport).build();
                camera=provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,group);
                torchButton.setEnabled(camera.getCameraInfo().hasFlashUnit());torch=false;torchButton.setText("Torch");
                scanner.setEnabled(!paused);updateKeepAwake();
                status.setText(paused?"Paused":books.stream().noneMatch(b->b.enabled)?"Add or enable wanted books to begin":"Sweep slowly • tap to focus");
            }catch(Exception e){status.setText("Camera unavailable. Close other camera apps and tap Resume to retry.");
                pauseButton.setText("Retry camera");pauseButton.setOnClickListener(v->showScanner());}
        },getMainExecutor());
    }
    private void stopCamera(){
        cameraGeneration++;if(scanner!=null)scanner.setEnabled(false);
        if(overlay!=null)overlay.clear();if(provider!=null)provider.unbindAll();camera=null;torch=false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void updateKeepAwake(){
        if(resumed&&!paused&&!listScreen&&books.stream().anyMatch(b->b.enabled))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void showPermissionHelp(){
        if(status==null||listScreen)return;
        status.setText("Camera permission is needed for live scanning. Your wanted list is still available.");
        pauseButton.setText("Camera permission");pauseButton.setOnClickListener(v->{
            if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))permission.launch(Manifest.permission.CAMERA);
            else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
        });
    }
    private void options(){
        boolean current=getPreferences(0).getBoolean("spines",false);
        new AlertDialog.Builder(this).setTitle("Scanning options")
                .setMultiChoiceItems(new String[]{"Experimental spine crops (may miss books)"},new boolean[]{current},(d,i,checked)->{
                    getPreferences(0).edit().putBoolean("spines",checked).apply();scanner.setSpineMode(checked);overlay.clear();})
                .setPositiveButton("Done",null).setNeutralButton("About",(d,w)->new AlertDialog.Builder(this)
                        .setTitle("BookScanner 0.1")
                        .setMessage("Offline ML Kit text recognition. No photos, scan history or vibration.\n\nPreprocessing inspired by Sappelen/BookSpineScanner (CC0 1.0). Independently implemented for Android.\n\nGoogle ML Kit is governed by Google’s ML Kit terms. AndroidX: Apache 2.0. See repository notices for source links.")
                        .setPositiveButton("Done",null).show()).show();
    }
    private void showList(){
        stopCamera();listScreen=true;installRoot();
        LinearLayout header=horizontal();weighted(header,text("Wanted books",24));header.addView(button("Scan",this::showScanner));root.addView(header);
        root.addView(text("Add a title, a series pattern, or any book by an author. Tap an entry to edit it.",14));
        LinearLayout actions=horizontal();weighted(actions,button("Add",()->editBook(null)));
        weighted(actions,button("Import",()->importFile.launch(new String[]{"application/json","text/plain","application/octet-stream"})));
        weighted(actions,button("Export",()->{try{pendingExport=WantedStore.encode(books);exportFile.launch("bookscanner-wanted.json");}catch(Exception e){message(e.getMessage());}}));root.addView(actions);
        ScrollView scroll=new ScrollView(this);LinearLayout entries=vertical();scroll.addView(entries);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(books.isEmpty())entries.addView(text("Your list is empty.\n\nExamples:\nEon — Greg Bear\nAny book — Jackie French\nThe No. 1 Ladies* — Alexander McCall Smith",18));
        for(WantedBook book:books){
            LinearLayout row=horizontal();CheckBox enabled=new CheckBox(this);enabled.setChecked(book.enabled);enabled.setContentDescription("Scan for "+book.label());
            enabled.setOnCheckedChangeListener((v,checked)->{
                int index=books.indexOf(book);if(index>=0){books.set(index,new WantedBook(book.id,book.title,book.author,book.aliases,checked));persist();showList();}});
            row.addView(enabled);
            Button item=button(book.label()+(book.title.equals("*")||book.author.isBlank()?"":"\n"+book.author),()->editBook(book));
            item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);weighted(row,item);entries.addView(row);
        }
    }
    private EditText field(LinearLayout form,String label,String hint,String value){
        form.addView(text(label,14));EditText input=new EditText(this);input.setHint(hint);input.setText(value);
        input.setTextSize(16);input.setPadding(dp(12),dp(8),dp(12),dp(8));form.addView(input);return input;
    }
    private void editBook(WantedBook existing){
        if(existing==null&&books.size()>=1000){message("Maximum 1,000 entries.");return;}
        LinearLayout form=vertical();form.setPadding(dp(12),0,dp(12),0);
        EditText title=field(form,"Title or pattern","Leave blank for any book by the author",existing==null?"":existing.title.equals("*")?"":existing.title);
        EditText author=field(form,"Author","e.g. Jackie French",existing==null?"":existing.author);
        EditText aliases=field(form,"Alternative titles (one per line)","Optional aliases",existing==null?"":String.join("\n",existing.aliases));
        aliases.setMinLines(2);aliases.setGravity(Gravity.TOP);
        form.addView(text("* matches any text; ? matches one character. A series can only match words actually printed on its spine.",12));
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"Add wanted book":"Edit wanted book")
                .setView(scroll).setPositiveButton("Save",null).setNegativeButton("Cancel",null)
                .setNeutralButton(existing==null?"":"Delete",(d,w)->{
                    if(existing!=null)new AlertDialog.Builder(this).setMessage("Remove "+existing.label()+"?")
                            .setPositiveButton("Remove",(a,b)->{books.remove(existing);persist();showList();}).setNegativeButton("Cancel",null).show();}).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                List<String> names=new ArrayList<>();for(String name:aliases.getText().toString().split("\n"))if(!name.isBlank())names.add(name.trim());
                WantedBook book=new WantedBook(existing==null?null:existing.id,title.getText().toString(),author.getText().toString(),names,existing==null||existing.enabled);
                if(existing==null)books.add(book);else books.set(books.indexOf(existing),book);
                persist();dialog.dismiss();showList();
            }catch(IllegalArgumentException e){title.setError(e.getMessage());}
        }));dialog.show();
    }
    private void persist(){try{store.save(books);scanner.setBooks(books);}catch(Exception e){message("Couldn’t save: "+e.getMessage());}}
    private void importList(Uri uri){
        if(uri==null)return;
        fileExecutor.execute(()->{
            try(InputStream input=getContentResolver().openInputStream(uri)){
                if(input==null)throw new IOException("Couldn’t open file");
                ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){if(output.size()+count>WantedStore.MAX_BYTES)throw new IOException("File exceeds 1 MB");output.write(buffer,0,count);}
                List<WantedBook> imported=WantedStore.decode(output.toString(StandardCharsets.UTF_8.name()));
                runOnUiThread(()->{if(!destroyed)new AlertDialog.Builder(this).setTitle("Import "+imported.size()+" entries?")
                        .setMessage("This replaces the current wanted list. Export it first if you want to keep a backup.")
                        .setPositiveButton("Replace list",(d,w)->{books.clear();books.addAll(imported);persist();showList();})
                        .setNegativeButton("Cancel",null).show();});
            }catch(Exception e){runOnUiThread(()->message("Import failed: "+e.getMessage()));}
        });
    }
    private void exportList(Uri uri){
        if(uri==null)return;
        final String data;
        try { data=pendingExport!=null?pendingExport:WantedStore.encode(books); }
        catch(Exception e){message("Export interrupted. Please try again.");return;}
        pendingExport=null;
        fileExecutor.execute(()->{
            try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")){
                if(output==null)throw new IOException("Couldn’t write file");output.write(data.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(()->message("Wanted list exported"));
            }catch(Exception e){runOnUiThread(()->message("Export failed: "+e.getMessage()));}
        });
    }
    private void message(String message){if(!destroyed)Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();resumed=true;if(!listScreen&&preview!=null)preview.post(this::startCamera);}
    @Override protected void onPause(){resumed=false;stopCamera();super.onPause();}
    @Override protected void onDestroy(){destroyed=true;stopCamera();power.removeThermalStatusListener(thermalListener);scanner.close(scanExecutor);fileExecutor.shutdown();super.onDestroy();}
}
