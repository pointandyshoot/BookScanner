package au.id.pointandyshoot.bookscanner;

import android.graphics.*;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import au.id.pointandyshoot.bookscanner.core.*;
import java.util.*;
import java.util.function.*;

/** Owned exclusively by the OCR executor; no camera buffers or tracking state are held here. */
final class OcrReader implements AutoCloseable {
    private final TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final Matcher matcher=new Matcher();
    private final ReadingSchedule schedule=new ReadingSchedule();
    private int step=0,session=-1;
    void read(Bitmap upright,List<WantedBook> books,int token,boolean spineMode,float[] recheck,
              BooleanSupplier valid,Consumer<List<LiveTracker.Detection>> publish) throws Exception {
        if(session!=token){schedule.reset();step=0;session=token;}
        int w=upright.getWidth(),h=upright.getHeight();
        float lean=VisionFrames.lean(upright);float[] angles=schedule.next(lean);
        List<LiveTracker.Detection> hits=readAtAngle(upright,new Rect(0,0,w,h),angles[0],1,books);
        if(!valid.getAsBoolean())return;publish.accept(List.copyOf(hits));schedule.result(angles[0],!hits.isEmpty());
        Rect region;
        if(recheck!=null&&step%3==0){
            int pad=Math.max(40,Math.round(Math.max(recheck[2]-recheck[0],recheck[3]-recheck[1])*.15f));
            region=new Rect(Math.max(0,(int)recheck[0]-pad),Math.max(0,(int)recheck[1]-pad),Math.min(w,(int)recheck[2]+pad),Math.min(h,(int)recheck[3]+pad));
        }else{
            int cw=Math.round(w*.65f),ch=Math.round(h*.65f),tile=(step+step/4)%4;
            int left=tile%2==0?0:w-cw,top=tile<2?0:h-ch;region=new Rect(left,top,left+cw,top+ch);
            if(spineMode){List<Rect> bands=ImagePrep.spineBands(upright);if(!bands.isEmpty()&&step%3!=0)region=bands.get(step%bands.size());}
        }
        step++;
        if(region.width()<2||region.height()<2||!valid.getAsBoolean())return;
        Bitmap crop=Bitmap.createBitmap(upright,region.left,region.top,region.width(),region.height());
        float localLean;
        try{localLean=VisionFrames.lean(crop);}finally{if(crop!=upright)crop.recycle();}
        // Local tilt can differ from the shelf's dominant tilt. No assumption of right-angle text.
        float angle=Float.isFinite(localLean)&&Math.abs(localLean)>=3?angles[0]-localLean:angles[1];
        hits.addAll(readAtAngle(upright,region,angle,1.5f,books));
        if(valid.getAsBoolean())publish.accept(List.copyOf(hits));
    }
    List<LiveTracker.Detection> readAtAngle(Bitmap source,Rect region,float angle,float scale,List<WantedBook> books) throws Exception {
        try(ReadingImage input=new ReadingImage(source,region,angle,scale)){
            Text text=Tasks.await(recognizer.process(InputImage.fromBitmap(input.bitmap,0)));
            List<LiveTracker.Detection> hits=new ArrayList<>();
            for(Text.TextBlock block:text.getTextBlocks()){
                for(Text.Line line:block.getLines())add(line.getText(),line.getCornerPoints(),line.getBoundingBox(),input,books,hits);
                if(compact(block))add(block.getText(),block.getCornerPoints(),block.getBoundingBox(),input,books,hits);
            }
            return hits;
        }
    }
    private void add(String text,Point[] corners,Rect bounds,ReadingImage input,List<WantedBook> books,List<LiveTracker.Detection> hits){
        if(bounds==null||text.length()>500||hits.size()>=20)return;
        float[] quad=input.map(corners,bounds),box=LiveTracker.bounds(quad);
        for(Matcher.Match match:matcher.find(text,books)){
            boolean duplicate=false;
            for(LiveTracker.Detection d:hits)if(d.id.equals(match.book.id)&&Geometry.overlap(box,LiveTracker.bounds(d.quad))>.3){duplicate=true;break;}
            if(!duplicate)hits.add(new LiveTracker.Detection(match.book.id,match.book.label(),match.reason,quad));
        }
    }
    private static boolean compact(Text.TextBlock block){
        List<Text.Line> lines=block.getLines();if(lines.size()<2||lines.size()>5)return false;
        float typical=0;
        for(Text.Line line:lines){Rect r=line.getBoundingBox();if(r==null)return false;typical+=r.height();}
        Rect r=block.getBoundingBox();return r!=null&&r.height()<typical/lines.size()*(lines.size()+1.8)&&r.width()>r.height()*1.3;
    }
    @Override public void close(){recognizer.close();}
}
