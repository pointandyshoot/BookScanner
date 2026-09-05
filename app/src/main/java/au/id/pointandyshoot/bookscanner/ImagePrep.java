package au.id.pointandyshoot.bookscanner;

import android.graphics.*;
import androidx.camera.core.ImageProxy;
import java.nio.ByteBuffer;
import java.util.*;

final class ImagePrep {
    private ImagePrep() {}
    static Bitmap uprightLuma(ImageProxy image) {
        Rect crop = image.getCropRect();
        ImageProxy.PlaneProxy y = image.getPlanes()[0];
        ByteBuffer data = y.getBuffer().duplicate();
        int base = data.position();
        int[] pixels = new int[crop.width()*crop.height()];
        for (int row=0; row<crop.height(); row++) {
            int offset = base + (crop.top+row)*y.getRowStride() + crop.left*y.getPixelStride();
            for (int col=0; col<crop.width(); col++) {
                int l = data.get(offset+col*y.getPixelStride()) & 255;
                pixels[row*crop.width()+col] = Color.rgb(l,l,l);
            }
        }
        Bitmap raw = Bitmap.createBitmap(pixels, crop.width(), crop.height(), Bitmap.Config.ARGB_8888);
        int rotation = image.getImageInfo().getRotationDegrees();
        if (rotation == 0) return raw;
        Bitmap upright = rotate(raw, rotation);
        if (upright != raw) raw.recycle();
        return upright;
    }
    static Bitmap rotate(Bitmap input, int degrees) {
        if (degrees == 0) return input;
        Matrix m = new Matrix(); m.postRotate(degrees);
        return Bitmap.createBitmap(input, 0, 0, input.getWidth(), input.getHeight(), m, true);
    }
    /** Histogram stretch inspired by Sappelen/BookSpineScanner (CC0); see THIRD_PARTY_NOTICES.md.
     * Percentile endpoints avoid a single white sticker/black border disabling enhancement.
     */
    static Bitmap contrast(Bitmap input) {
        int w=input.getWidth(), h=input.getHeight();
        int[] pixels = new int[w*h], histogram = new int[256];
        input.getPixels(pixels,0,w,0,0,w,h);
        for (int p : pixels) histogram[Color.red(p)]++;
        int low=0, high=255, sum=0;
        for (;low<254;low++) { sum+=histogram[low]; if(sum>=pixels.length/50) break; }
        sum=0;
        for (;high>low;high--) { sum+=histogram[high]; if(sum>=pixels.length/50) break; }
        if (high-low<24) return input; // Do not amplify a near-uniform noisy scene.
        for(int i=0;i<pixels.length;i++) {
            int l=Math.max(0,Math.min(255,(Color.red(pixels[i])-low)*255/(high-low)));
            pixels[i]=Color.rgb(l,l,l);
        }
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    /** Experimental cheap vertical-boundary proposals, not a learned spine detector. */
    static List<Rect> spineBands(Bitmap input) {
        int w=input.getWidth(),h=input.getHeight();
        List<Integer> edges=new ArrayList<>();
        for(int x=4;x<w-4;x+=4) {
            int strong=0, total=0;
            for(int y=h/8;y<7*h/8;y+=8) {
                int a=Color.red(input.getPixel(x-3,y)),b=Color.red(input.getPixel(x+3,y));
                if(Math.abs(a-b)>38) strong++;
                total++;
            }
            if(total>0 && strong>.60*total && (edges.isEmpty() || x-edges.get(edges.size()-1)>w/40)) edges.add(x);
        }
        List<Rect> bands=new ArrayList<>();
        for(int i=1;i<edges.size();i++) {
            int left=edges.get(i-1),right=edges.get(i),width=right-left;
            if(width>=w/35 && width<w/4 && h>width*3)
                bands.add(new Rect(Math.max(0,left-10),0,Math.min(w,right+10),h));
        }
        // Only use a few plausible strips when their total area is materially smaller.
        int area=0; for(Rect b:bands) area+=b.width()*b.height();
        if(bands.size()<2 || bands.size()>4 || area>w*h*.60) return List.of();
        return bands;
    }
}
