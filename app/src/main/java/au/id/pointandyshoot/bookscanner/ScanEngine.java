package au.id.pointandyshoot.bookscanner;

import android.graphics.*;
import android.os.SystemClock;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import au.id.pointandyshoot.bookscanner.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Fast camera/tracking lane plus one bounded, independent OCR lane. */
final class ScanEngine implements ImageAnalysis.Analyzer {
    static final class Hit {
        final float[] quad;final RectF box;final String label,reason;final boolean repeated,tracking;
        Hit(LiveTracker.Visible v){quad=v.detection.quad.clone();float[] b=LiveTracker.bounds(quad);box=new RectF(b[0],b[1],b[2],b[3]);label=v.detection.label;reason=v.detection.reason;repeated=v.repeated;tracking=v.tracking;}
    }
    interface Listener {void result(List<Hit> hits,int width,int height,long at,String status,int generation,boolean newAppearance);}
    private static final class Batch {
        final List<LiveTracker.Detection> hits;final VisionFrames.Gray capture;final long sequence,time;final int token;
        Batch(List<LiveTracker.Detection> hits,VisionFrames.Gray capture,long sequence,long time,int token){this.hits=hits;this.capture=capture;this.sequence=sequence;this.time=time;this.token=token;}
    }
    private final ExecutorService analysisExecutor,ocrExecutor=Executors.newSingleThreadExecutor();
    private final OcrReader reader=new OcrReader();
    private final LiveTracker tracker=new LiveTracker();
    private final AppearanceGate appearance=new AppearanceGate();
    private final AtomicInteger generation=new AtomicInteger();
    private final AtomicBoolean busy=new AtomicBoolean();
    private final AtomicReference<Batch> pending=new AtomicReference<>();
    private final Listener listener;
    private volatile List<WantedBook> books=List.of();
    private volatile boolean enabled,closing,spineMode;
    private volatile int thermal;
    private volatile String lastError="";
    private long lastFrame,lastOcr,sequence;
    private int activeGeneration=-1;
    private final boolean visionReady;

    ScanEngine(Listener listener,ExecutorService analysisExecutor){this.listener=listener;this.analysisExecutor=analysisExecutor;visionReady=NativeVision.initialise();}
    void setBooks(List<WantedBook> entries){books=List.copyOf(entries);invalidate();}
    void setEnabled(boolean enabled){this.enabled=enabled;invalidate();}
    void setSpineMode(boolean enabled){spineMode=enabled;}
    void setThermal(int value){thermal=value;}
    int generation(){return generation.get();}
    private void invalidate(){
        generation.incrementAndGet();pending.set(null);
        if(!closing)analysisExecutor.execute(()->{tracker.clear();appearance.clear();lastFrame=lastOcr=0;activeGeneration=-1;});
    }
    void close(ExecutorService executor){
        enabled=false;closing=true;generation.incrementAndGet();pending.set(null);
        executor.execute(tracker::close);executor.shutdown();
        ocrExecutor.execute(reader::close);ocrExecutor.shutdown();
    }
    @Override public void analyze(ImageProxy image){
        long now=SystemClock.elapsedRealtime();int token=generation.get();Bitmap original=null;
        try{
            if(!enabled||closing||books.stream().noneMatch(b->b.enabled))return;
            if(!visionReady){listener.result(List.of(),1,1,now,"Tracking could not start. Restart the app.",token,false);return;}
            if(now-lastFrame<(thermal>=3?120:65))return;lastFrame=now;
            if(activeGeneration!=token){tracker.clear();activeGeneration=token;}
            VisionFrames.Gray gray=VisionFrames.trackingImage(image);long seq=++sequence;
            tracker.frame(gray,seq,now);
            Batch batch=pending.getAndSet(null);
            if(batch!=null&&batch.token==token)tracker.detections(batch.hits,batch.capture,batch.sequence,batch.time);
            List<Hit> hits=new ArrayList<>();Set<String> present=new HashSet<>();
            for(LiveTracker.Visible v:tracker.visible()){hits.add(new Hit(v));present.add(v.detection.id);}
            boolean alert=appearance.update(present,now);
            if(enabled&&token==generation.get())listener.result(hits,gray.sourceWidth,gray.sourceHeight,now,
                    !hits.isEmpty()?"Potential match — tracking highlighted text":!lastError.isEmpty()?lastError:
                    thermal>=3?"Phone warm — detail reads slowed":"Sweep slowly • tap to focus • angled text enabled",token,alert);
            if(now-lastOcr<(thermal>=3?1100:250)||!busy.compareAndSet(false,true))return;
            try{original=ImagePrep.uprightLuma(image);}catch(RuntimeException e){busy.set(false);throw e;}
            final Bitmap captureBitmap=original;final List<WantedBook> wanted=books;
            final float[] recheck=tracker.recheckRegion();final boolean crops=spineMode;
            lastOcr=now;
            ocrExecutor.execute(()->{
                try{
                    if(valid(token))reader.read(captureBitmap,wanted,token,crops,recheck,()->valid(token),
                            detections->{if(valid(token))pending.set(new Batch(detections,gray,seq,now,token));});
                    lastError="";
                }catch(Exception error){if(valid(token))lastError="Couldn’t read text — hold steady or improve lighting";}
                finally{captureBitmap.recycle();busy.set(false);}
            });
            original=null; // Ownership transferred to the OCR worker.
        }catch(RuntimeException error){
            tracker.clear();
            if(valid(token))listener.result(List.of(),1,1,now,"Tracking lost — hold steady to reacquire",token,false);
        }finally{if(original!=null)original.recycle();image.close();}
    }
    private boolean valid(int token){return enabled&&!closing&&generation.get()==token;}
}
