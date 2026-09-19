package au.id.pointandyshoot.bookscanner;

import android.graphics.*;

/** Explicit invertible crop/scale/rotation transform for arbitrary-angle OCR. */
final class ReadingImage implements AutoCloseable {
    final Bitmap bitmap;
    final Matrix toSource=new Matrix();
    ReadingImage(Bitmap source,Rect region,float angle,float requestedScale) {
        float scale=Math.min(requestedScale,2600f/Math.max(region.width(),region.height()));
        Matrix forward=new Matrix();
        forward.setTranslate(-region.left,-region.top);
        forward.postScale(scale,scale);
        forward.postRotate(angle);
        RectF bounds=new RectF(region);forward.mapRect(bounds);
        forward.postTranslate(-bounds.left,-bounds.top);
        if(!forward.invert(toSource))throw new IllegalArgumentException("Invalid image transform");
        bitmap=Bitmap.createBitmap(Math.max(1,(int)Math.ceil(bounds.width())),Math.max(1,(int)Math.ceil(bounds.height())),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
        canvas.concat(forward);canvas.clipRect(region);
        canvas.drawBitmap(source,0,0,new Paint(Paint.FILTER_BITMAP_FLAG));
    }
    float[] map(Point[] corners,Rect fallback) {
        float[] points;
        if(corners!=null&&corners.length==4) {
            points=new float[8];for(int i=0;i<4;i++){points[i*2]=corners[i].x;points[i*2+1]=corners[i].y;}
        } else {
            points=new float[]{fallback.left,fallback.top,fallback.right,fallback.top,fallback.right,fallback.bottom,fallback.left,fallback.bottom};
        }
        toSource.mapPoints(points);return points;
    }
    @Override public void close(){bitmap.recycle();}
}
