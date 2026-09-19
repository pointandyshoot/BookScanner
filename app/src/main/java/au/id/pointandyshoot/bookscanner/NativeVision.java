package au.id.pointandyshoot.bookscanner;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;

final class NativeVision {
    private static boolean ready;
    static synchronized boolean initialise(){
        if(ready)return true;
        try {ready=OpenCVLoader.initLocal();if(ready)Core.setNumThreads(2);}
        catch(UnsatisfiedLinkError | RuntimeException ignored){ready=false;}
        return ready;
    }
    private NativeVision(){}
}
