package au.id.pointandyshoot.bookscanner;

import android.graphics.*;
import android.os.SystemClock;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import au.id.pointandyshoot.bookscanner.core.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

final class ScanEngine implements ImageAnalysis.Analyzer {
    static final class Hit {
        final RectF box; final String label, reason; final boolean repeated;
        Hit(RectF box,String label,String reason,boolean repeated) {
            this.box=box;this.label=label;this.reason=reason;this.repeated=repeated;
        }
    }
    interface Listener { void result(List<Hit> hits, int width, int height, long capturedAt, String status, int generation); }
    private final TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final Matcher matcher=new Matcher();
    private final Evidence evidence=new Evidence();
    private final Listener listener;
    private final AtomicInteger generation=new AtomicInteger();
    private volatile List<WantedBook> books=List.of();
    private volatile boolean enabled=false, spineMode=false;
    private volatile int thermal=0;
    private long lastFrame=0, frame=0;
    private int rotationIndex=0, preferred=90, emptyFrames=0;
    private boolean hadMatch=false;
    private double averageMs=180;
    private static final int[] ROTATIONS={90,180,270,0};

    ScanEngine(Listener listener) {this.listener=listener;}
    void setBooks(List<WantedBook> entries) {books=List.copyOf(entries);invalidate();}
    void setEnabled(boolean enabled) {this.enabled=enabled;invalidate();}
    void setSpineMode(boolean enabled) {spineMode=enabled;invalidate();}
    void setThermal(int thermal) {this.thermal=thermal;}
    int generation() {return generation.get();}
    private void invalidate() {generation.incrementAndGet();evidence.clear();}
    void close(ExecutorService executor) { enabled=false;invalidate();executor.execute(recognizer::close);executor.shutdown(); }

    @Override public void analyze(ImageProxy image) {
        Bitmap upright=null;
        long start=SystemClock.elapsedRealtime();
        int token=generation.get();
        try {
            long interval=thermal>=3?700:(long)Math.max(140,Math.min(550,averageMs*1.1));
            if(!enabled || books.stream().noneMatch(b->b.enabled) || start-lastFrame<interval) return;
            lastFrame=start;frame++;
            upright=ImagePrep.uprightLuma(image);
            int w=upright.getWidth(),h=upright.getHeight();
            // Reuse a productive angle twice, but probe another every third frame so
            // mixed shelves and a newly encountered spine orientation are never starved.
            int angle=hadMatch && frame%3!=0?preferred:ROTATIONS[rotationIndex++%4];
            boolean enhanced=emptyFrames>=4 && frame%2==0 && thermal<3;
            List<Rect> regions=spineMode && frame%3!=0?ImagePrep.spineBands(upright):List.of();
            if(regions.isEmpty()) regions=List.of(new Rect(0,0,w,h));
            List<Hit> hits=new ArrayList<>();
            for(Rect region:regions) {
                if(!enabled || token!=generation.get()) return;
                readRegion(upright,region,angle,enhanced,start,hits);
            }
            hadMatch=!hits.isEmpty();
            if(hadMatch) {preferred=angle;emptyFrames=0;} else emptyFrames++;
            long elapsed=SystemClock.elapsedRealtime()-start;
            averageMs=.8*averageMs+.2*elapsed;
            // Suppress results from a previous session or a frame too old to point at safely.
            if(enabled && token==generation.get()) listener.result(elapsed>700?List.of():hits,w,h,start,
                    elapsed>700?"Reading slowly — hold steady":
                    thermal>=3?"Phone warm — scanning more slowly":
                    hits.isEmpty()?"Sweep slowly • move closer for small text":"Potential match — check the boxed spine",token);
        } catch(Exception error) {
            if(enabled && token==generation.get()) listener.result(List.of(),1,1,start,"Couldn’t read this frame — try again",token);
        } finally {
            if(upright!=null) upright.recycle();
            image.close();
        }
    }
    private void readRegion(Bitmap upright,Rect region,int angle,boolean enhanced,long time,List<Hit> hits) throws Exception {
        Bitmap crop=Bitmap.createBitmap(upright,region.left,region.top,region.width(),region.height());
        Bitmap prepared=enhanced?ImagePrep.contrast(crop):crop;
        Bitmap rotated=ImagePrep.rotate(prepared,angle);
        try {
            Text result=Tasks.await(recognizer.process(InputImage.fromBitmap(rotated,0)));
            for(Text.TextBlock block:result.getTextBlocks()) {
                // Per-line matching prevents ML Kit's occasional shelf-wide block from combining titles.
                for(Text.Line line:block.getLines()) addMatches(line.getText(),line.getBoundingBox(),region,angle,time,hits);
                // A compact block can combine a multi-line title / author on the same spine.
                Rect r=block.getBoundingBox();
                if(r!=null && compact(block)) addMatches(block.getText(),r,region,angle,time,hits);
            }
        } finally {
            if(rotated!=prepared) rotated.recycle();
            if(prepared!=crop) prepared.recycle();
            if(crop!=upright) crop.recycle();
        }
    }
    private static boolean compact(Text.TextBlock block) {
        List<Text.Line> lines=block.getLines();
        if(lines.size()<2 || lines.size()>5) return false;
        Rect bounds=block.getBoundingBox();
        float typical=0;
        for(Text.Line line:lines) {
            Rect r=line.getBoundingBox(); if(r==null)return false;
            typical+=r.height();
        }
        typical/=lines.size();
        // Reject blocks containing widely separated or side-by-side spines in OCR coordinates.
        return bounds!=null && bounds.height()<typical*(lines.size()+1.8) && bounds.width()>bounds.height()*1.3;
    }
    private void addMatches(String text,Rect rect,Rect region,int angle,long time,List<Hit> hits) {
        if(rect==null || text.length()>500) return;
        RectF box=new RectF(Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE);
        for(float[] p:new float[][]{{rect.left,rect.top},{rect.right,rect.top},{rect.right,rect.bottom},{rect.left,rect.bottom}}) {
            float[] q=Geometry.unrotate(p[0],p[1],angle,region.width(),region.height());
            box.left=Math.min(box.left,q[0]+region.left);box.right=Math.max(box.right,q[0]+region.left);
            box.top=Math.min(box.top,q[1]+region.top);box.bottom=Math.max(box.bottom,q[1]+region.top);
        }
        for(Matcher.Match match:matcher.find(text,books)) {
            float[] coords={box.left,box.top,box.right,box.bottom};
            boolean duplicate=false;
            for(Hit hit:hits) if(hit.label.equals(match.book.label()) && Geometry.overlap(coords,
                    new float[]{hit.box.left,hit.box.top,hit.box.right,hit.box.bottom})>.35) {duplicate=true;break;}
            if(duplicate)continue;
            int count=evidence.observe(match.book.id,coords,time,frame);
            hits.add(new Hit(new RectF(box),match.book.label(),match.reason,count>=2));
            if(hits.size()>=20)return;
        }
    }
}
