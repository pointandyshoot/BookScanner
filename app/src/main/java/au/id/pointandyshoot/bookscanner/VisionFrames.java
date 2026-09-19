package au.id.pointandyshoot.bookscanner;

import android.graphics.Bitmap;
import android.graphics.Rect;
import androidx.camera.core.ImageProxy;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.nio.ByteBuffer;
import java.util.*;

final class VisionFrames {
    static final class Gray {
        final byte[] bytes; final int width,height,sourceWidth,sourceHeight;
        Gray(byte[] bytes,int width,int height,int sw,int sh){this.bytes=bytes;this.width=width;this.height=height;sourceWidth=sw;sourceHeight=sh;}
        Mat mat(){Mat m=new Mat(height,width,CvType.CV_8UC1);m.put(0,0,bytes);return m;}
    }
    static Gray trackingImage(ImageProxy image){
        Rect c=image.getCropRect();int rotation=image.getImageInfo().getRotationDegrees();
        boolean swap=rotation==90||rotation==270;
        float scale=Math.min(1,640f/Math.max(c.width(),c.height()));
        int w=Math.max(1,Math.round(c.width()*scale)),h=Math.max(1,Math.round(c.height()*scale));
        byte[] bytes=new byte[w*h];ImageProxy.PlaneProxy p=image.getPlanes()[0];ByteBuffer b=p.getBuffer().duplicate();int base=b.position();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int sx=c.left+Math.min(c.width()-1,(int)(x/scale)),sy=c.top+Math.min(c.height()-1,(int)(y/scale));
            int dx=x,dy=y;
            if(rotation==90){dx=h-1-y;dy=x;}else if(rotation==180){dx=w-1-x;dy=h-1-y;}else if(rotation==270){dx=y;dy=w-1-x;}
            bytes[dy*(swap?h:w)+dx]=b.get(base+sy*p.getRowStride()+sx*p.getPixelStride());
        }
        return new Gray(bytes,swap?h:w,swap?w:h,swap?c.height():c.width(),swap?c.width():c.height());
    }
    /** Dominant baseline/spine tilt modulo 90 degrees; independent local tiles handle mixed shelves. */
    static float lean(Bitmap bitmap){
        Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,bitmap.getWidth()*640/Math.max(bitmap.getWidth(),bitmap.getHeight())),Math.max(1,bitmap.getHeight()*640/Math.max(bitmap.getWidth(),bitmap.getHeight())),true);
        Mat rgba=new Mat(),gray=new Mat(),edges=new Mat(),lines=new Mat();
        try{
            Utils.bitmapToMat(small,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.Canny(gray,edges,45,130);
            Imgproc.HoughLinesP(edges,lines,1,Math.PI/180,24,35,12);
            double[] weights=new double[91];
            for(int i=0;i<lines.rows();i++){
                double[] l=lines.get(i,0);double dx=l[2]-l[0],dy=l[3]-l[1];
                double angle=Math.toDegrees(Math.atan2(dy,dx));while(angle>45)angle-=90;while(angle< -45)angle+=90;
                weights[Math.max(0,Math.min(90,(int)Math.round(angle)+45))]+=Math.hypot(dx,dy);
            }
            int best=45;double peak=0;
            for(int i=2;i<89;i++){double sum=0;for(int j=i-2;j<=i+2;j++)sum+=weights[j];if(sum>peak){peak=sum;best=i;}}
            if(peak<60)return Float.NaN;
            double weighted=0,total=0;for(int j=Math.max(0,best-2);j<=Math.min(90,best+2);j++){weighted+=(j-45)*weights[j];total+=weights[j];}
            return total==0?Float.NaN:(float)(weighted/total);
        }finally{rgba.release();gray.release();edges.release();lines.release();if(small!=bitmap)small.recycle();}
    }
    private VisionFrames(){}
}
