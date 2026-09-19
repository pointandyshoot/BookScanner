package au.id.pointandyshoot.bookscanner;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.*;

final class MatchOverlay extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<ScanEngine.Hit> hits=List.of();
    private int imageWidth=1,imageHeight=1;
    private long expires=0;
    private final Runnable expire=()->{hits=List.of();invalidate();};
    MatchOverlay(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void update(List<ScanEngine.Hit> hits,int width,int height,long frameTime){
        this.hits=List.copyOf(hits);imageWidth=width;imageHeight=height;
        expires=frameTime+600;removeCallbacks(expire);
        postDelayed(expire,Math.max(0,expires-SystemClock.elapsedRealtime()));invalidate();
    }
    void clear(){hits=List.of();removeCallbacks(expire);invalidate();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(expire);super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(SystemClock.elapsedRealtime()>expires)return;
        float scale=Math.min((float)getWidth()/imageWidth,(float)getHeight()/imageHeight);
        float dx=(getWidth()-imageWidth*scale)/2,dy=(getHeight()-imageHeight*scale)/2;
        float density=getResources().getDisplayMetrics().density;
        for(ScanEngine.Hit hit:hits){
            RectF r=new RectF(dx+hit.box.left*scale,dy+hit.box.top*scale,dx+hit.box.right*scale,dy+hit.box.bottom*scale);
            r.inset(-4*density,-4*density);
            paint.setColor(hit.repeated?Color.rgb(145,215,172):Color.rgb(255,201,97));
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth((hit.repeated?3:2)*density);
            paint.setAlpha(hit.tracking?255:140);
            Path outline=new Path();
            outline.moveTo(dx+hit.quad[0]*scale,dy+hit.quad[1]*scale);
            for(int i=2;i<8;i+=2)outline.lineTo(dx+hit.quad[i]*scale,dy+hit.quad[i+1]*scale);
            outline.close();canvas.drawPath(outline,paint);paint.setAlpha(255);
            paint.setTextSize(13*density);paint.setStyle(Paint.Style.FILL);
            String label=hit.label+(hit.reason.startsWith("Author only")?" · check title":"");
            while(label.length()>3 && paint.measureText(label)>getWidth()-24*density) label=label.substring(0,label.length()-2)+"…";
            float x=Math.max(8*density,Math.min(r.left,getWidth()-paint.measureText(label)-12*density));
            float y=Math.max(22*density,r.top-7*density);
            int colour=paint.getColor();paint.setColor(Color.argb(225,17,25,22));
            canvas.drawRoundRect(x-4*density,y-17*density,x+paint.measureText(label)+4*density,y+5*density,3*density,3*density,paint);
            paint.setColor(colour);canvas.drawText(label,x,y,paint);
        }
    }
}
