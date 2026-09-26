package au.id.pointandyshoot.bookscanner;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
/** Single OCR-executor owner. CPU inference; no image files or network access. */
final class PpOcrNative {
    static { System.loadLibrary("bookocr"); }
    static native long open(AssetManager assets);
    static native float[] detect(long handle,Bitmap bitmap,int width,int height);
    static native float[] recognise(long handle,Bitmap crop);
    static native void close(long handle);
    private PpOcrNative() {}
}
