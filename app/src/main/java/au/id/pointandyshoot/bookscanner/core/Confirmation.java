package au.id.pointandyshoot.bookscanner.core;
import java.util.ArrayDeque;
import java.util.Deque;
/** One spatial track; only strong OCR on distinct captured frames can confirm it. */
public final class Confirmation {
    private final Deque<Long> strongTimes=new ArrayDeque<>();
    private long lastFrame=-1;
    private boolean lastWasStrong,confirmed;
    public void observe(long frame,long capturedAt,boolean strong){
        if(frame<lastFrame||confirmed)return;
        while(!strongTimes.isEmpty()&&capturedAt-strongTimes.peekFirst()>5000)strongTimes.removeFirst();
        if(frame!=lastFrame){lastFrame=frame;lastWasStrong=false;}
        if(strong&&!lastWasStrong){strongTimes.addLast(capturedAt);lastWasStrong=true;}
        confirmed=strongTimes.size()>=3;
    }
    public boolean confirmed(){return confirmed;}
}
