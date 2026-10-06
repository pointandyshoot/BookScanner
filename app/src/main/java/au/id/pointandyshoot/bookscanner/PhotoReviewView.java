package au.id.pointandyshoot.bookscanner;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.List;

/** A single transform drives both photo and static hints, including pinch/pan/zoom. */
final class PhotoReviewView extends View {
    private Bitmap bitmap;
    private int sourceWidth=1,sourceHeight=1;
    private List<LiveTracker.Detection> hits=List.of();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private float zoom=1,panX,panY;
    private LiveTracker.Detection selectedHint;
    private final ScaleGestureDetector scaling;
    private final GestureDetector gestures;
    PhotoReviewView(Context context){
        super(context);setContentDescription("Photo discoveries. Pinch to zoom, drag to pan, double tap to reset.");
        scaling=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){
                float before=zoom;zoom=Math.max(1,Math.min(8,zoom*detector.getScaleFactor()));float ratio=zoom/before;
                panX=ratio*panX+(1-ratio)*(detector.getFocusX()-getWidth()/2f);
                panY=ratio*panY+(1-ratio)*(detector.getFocusY()-getHeight()/2f);
                if(zoom==1){panX=panY=0;}invalidate();return true;
            }
        });
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy){if(!scaling.isInProgress()&&zoom>1){panX-=dx;panY-=dy;invalidate();}return true;}
            @Override public boolean onDoubleTap(MotionEvent e){reset();return true;}
            @Override public boolean onSingleTapUp(MotionEvent e){performClick();return true;}
        });
    }
    void photo(Bitmap value,int width,int height){
        Bitmap old=bitmap;bitmap=value;sourceWidth=Math.max(1,width);sourceHeight=Math.max(1,height);reset();
        if(old!=null&&old!=value)old.recycle();
    }
    void hints(List<LiveTracker.Detection> value,int width,int height){hits=List.copyOf(value);if(width>0&&height>0){sourceWidth=width;sourceHeight=height;}invalidate();}
    void clear(){photo(null,1,1);hits=List.of();}
    private void reset(){zoom=1;panX=panY=0;selectedHint=null;invalidate();}
    void focus(LiveTracker.Detection hit){
        if(bitmap==null)return;float[] b=LiveTracker.bounds(hit.quad);
        float fit=Math.min(getWidth()/(float)sourceWidth,getHeight()/(float)sourceHeight);
        if(fit<=0)return;
        zoom=Math.max(1,Math.min(8,Math.min(getWidth()*.65f/(Math.max(1,b[2]-b[0])*fit),getHeight()*.65f/(Math.max(1,b[3]-b[1])*fit))));
        selectedHint=hit;
        panX=-( (b[0]+b[2])/2-sourceWidth/2f)*fit*zoom;
        panY=-( (b[1]+b[3])/2-sourceHeight/2f)*fit*zoom;invalidate();
    }
    @Override public boolean onTouchEvent(MotionEvent event){scaling.onTouchEvent(event);gestures.onTouchEvent(event);return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(Color.rgb(10,15,13));if(bitmap==null)return;
        float fit=Math.min(getWidth()/(float)sourceWidth,getHeight()/(float)sourceHeight),scale=fit*zoom;
        float dx=getWidth()/2f+panX-sourceWidth*scale/2,dy=getHeight()/2f+panY-sourceHeight*scale/2;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
        canvas.drawBitmap(bitmap,null,new RectF(dx,dy,dx+sourceWidth*scale,dy+sourceHeight*scale),paint);
        float density=getResources().getDisplayMetrics().density;
        for(int index=0;index<hits.size();index++){
            LiveTracker.Detection hit=hits.get(index);
            float[] b=LiveTracker.bounds(hit.quad);if(dx+b[2]*scale<0||dx+b[0]*scale>getWidth()||dy+b[3]*scale<0||dy+b[1]*scale>getHeight())continue;
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(13*density);String label=hit==selectedHint?hit.label+(hit.reason.contains("check title")?" · check title":" · possible"):Integer.toString(index+1);
            while(label.length()>3&&paint.measureText(label)>getWidth()-20*density)label=label.substring(0,label.length()-2)+"…";
            float x=Math.max(8*density,Math.min(dx+b[0]*scale,getWidth()-paint.measureText(label)-8*density));
            float y=Math.max(22*density,Math.min(getHeight()-6*density,dy+b[1]*scale-6*density));
            paint.setColor(Color.argb(230,17,25,22));canvas.drawRect(x-3*density,y-17*density,x+paint.measureText(label)+3*density,y+5*density,paint);
            paint.setColor(Color.rgb(255,160,48));canvas.drawText(label,x,y,paint);
        }
        // Draw outlines after every label so a label cannot hide a neighbouring match.
        for(LiveTracker.Detection hit:hits){
            float[] b=LiveTracker.bounds(hit.quad);
            RectF box=new RectF(dx+b[0]*scale,dy+b[1]*scale,dx+b[2]*scale,dy+b[3]*scale);
            if(box.right<0||box.left>getWidth()||box.bottom<0||box.top>getHeight())continue;
            if(box.width()<12*density)box.inset(-(12*density-box.width())/2,0);
            if(box.height()<12*density)box.inset(0,-(12*density-box.height())/2);
            if(hit==selectedHint){paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(45,255,160,48));canvas.drawRect(box,paint);}
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth((hit==selectedHint?7:6)*density);paint.setColor(Color.rgb(12,18,15));canvas.drawRect(box,paint);
            paint.setStrokeWidth((hit==selectedHint?4:3)*density);paint.setColor(Color.rgb(255,160,48));canvas.drawRect(box,paint);
        }
    }
}
