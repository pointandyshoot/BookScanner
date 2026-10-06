package au.id.pointandyshoot.bookscanner;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import au.id.pointandyshoot.bookscanner.core.WantedBook;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Retained per-screen queue. Only one full photo and one OCR engine are decoded at a time. */
public final class PhotoSession extends AndroidViewModel {
    static final int LIMIT=8;
    enum State { CAPTURING, QUEUED, READING, DONE, FAILED, CANCELLED }
    static final class Shot {
        final File file;final String name;
        State state=State.CAPTURING;int percent,width,height;String stage="Capturing";
        List<LiveTracker.Detection> hits=List.of();
        Shot(File file,String name){this.file=file;this.name=name;}
    }
    final List<Shot> shots=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final File directory;
    private final List<WantedBook> wanted;
    private OcrReader reader;
    private boolean busy,paused;
    private volatile int epoch;
    private volatile boolean closed;
    private Runnable listener;
    PhotoSession(Application app,List<WantedBook> wanted){
        super(app);this.wanted=List.copyOf(wanted);
        directory=new File(app.getCacheDir(),"shelf-photos-"+UUID.randomUUID());
        if(!directory.mkdirs())throw new IllegalStateException("Couldn’t create temporary photo storage");
    }
    void listen(Runnable listener){this.listener=listener;changed();}
    boolean paused(){return paused;}
    private void changed(){if(listener!=null&&!closed)listener.run();}
    Shot reserve(){
        if(closed||shots.size()>=LIMIT)return null;
        Shot shot=new Shot(new File(directory,UUID.randomUUID()+".jpg"),"Photo "+(shots.size()+1));
        shots.add(shot);changed();return shot;
    }
    void captured(Shot shot){
        if(closed||!shots.contains(shot)){shot.file.delete();return;}
        shot.state=paused?State.CANCELLED:State.QUEUED;shot.stage=paused?"Stopped":"Waiting";changed();pump();
    }
    void failed(Shot shot,String error){
        if(closed||!shots.contains(shot)){shot.file.delete();return;}
        shot.state=State.FAILED;shot.stage=error;changed();
    }
    void importPhoto(Uri uri){
        Shot shot=reserve();if(shot==null)return;shot.stage="Importing";changed();
        worker.execute(()->{
            try(InputStream input=getApplication().getContentResolver().openInputStream(uri);
                OutputStream output=new FileOutputStream(shot.file)){
                if(input==null)throw new IOException("Couldn’t open photo");
                byte[] buffer=new byte[65536];long total=0;int n;
                while((n=input.read(buffer))!=-1){total+=n;if(total>64L*1024*1024)throw new IOException("Photo exceeds 64 MB");output.write(buffer,0,n);}
                main.post(()->captured(shot));
            }catch(Exception e){main.post(()->failed(shot,"Import failed. Try another photo."));}
        });
    }
    static Bitmap decode(File file,int edge) throws IOException {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file),(decoder,info,source)->{
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();
            float scale=Math.min(1,edge/(float)Math.max(w,h));
            decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));
        });
    }
    private void pump(){
        if(closed||busy||paused)return;
        Shot next=null;for(Shot shot:shots)if(shot.state==State.QUEUED){next=shot;break;}
        if(next==null)return;
        Shot shot=next;int token=epoch;busy=true;shot.state=State.READING;shot.stage="Opening photo";changed();
        worker.execute(()->{
            Bitmap bitmap=null;
            try{
                if(!valid(token))return;
                if(!NativeVision.initialise())throw new IOException("OCR could not start");
                if(reader==null)reader=new OcrReader(getApplication().getAssets());
                bitmap=decode(shot.file,4096);int w=bitmap.getWidth(),h=bitmap.getHeight();
                post(token,()->{shot.width=w;shot.height=h;changed();});
                final long[] last={0};
                reader.readStill(bitmap,wanted,()->valid(token),progress->{
                    long now=android.os.SystemClock.elapsedRealtime();
                    if(now-last[0]<100&&progress.percent!=100)return;last[0]=now;
                    post(token,()->{shot.percent=progress.percent;shot.stage=progress.stage;changed();});
                },hits->post(token,()->{shot.hits=hits;changed();}));
                post(token,()->{shot.state=State.DONE;shot.percent=100;shot.stage=shot.hits.isEmpty()?"Complete · no hints":"Complete · "+shot.hits.size()+" hints";changed();});
            }catch(Exception|LinkageError|OutOfMemoryError e){post(token,()->{shot.state=State.FAILED;shot.stage="Read failed. Retry this photo.";changed();});}
            finally{if(bitmap!=null)bitmap.recycle();main.post(()->{busy=false;pump();changed();});}
        });
    }
    private boolean valid(int token){return !closed&&epoch==token;}
    private void post(int token,Runnable action){main.post(()->{if(valid(token))action.run();});}
    void stopProcessing(){
        epoch++;paused=true;
        for(Shot shot:shots)if(shot.state==State.READING||shot.state==State.QUEUED){shot.state=State.CANCELLED;shot.stage="Stopped · partial hints retained";}
        changed();
    }
    void resumeProcessing(){
        paused=false;for(Shot shot:shots)if(shot.state==State.CANCELLED){shot.state=State.QUEUED;shot.percent=0;shot.hits=List.of();shot.stage="Waiting";}
        changed();pump();
    }
    void retry(Shot shot){
        if(shot.state!=State.FAILED)return;
        shot.state=paused?State.CANCELLED:State.QUEUED;shot.percent=0;shot.hits=List.of();shot.stage="Waiting";changed();pump();
    }
    void clear(){
        epoch++;paused=false;for(Shot shot:shots)shot.file.delete();shots.clear();changed();
    }
    @Override protected void onCleared(){
        closed=true;epoch++;listener=null;
        worker.execute(()->{if(reader!=null)reader.close();File[] files=directory.listFiles();if(files!=null)for(File file:files)file.delete();directory.delete();});
        worker.shutdown();
    }
}
