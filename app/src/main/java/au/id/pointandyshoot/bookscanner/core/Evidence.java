package au.id.pointandyshoot.bookscanner.core;

import java.util.*;

/** Repeated reads affect styling only. No image retention or cross-frame text stitching. */
public final class Evidence {
    private static final class Seen {
        String id; float[] box; long time, frame; int count;
        Seen(String id, float[] box, long time, long frame) {
            this.id=id; this.box=box.clone(); this.time=time; this.frame=frame; count=1;
        }
    }
    private final List<Seen> seen = new ArrayList<>();
    public synchronized int observe(String id, float[] box, long now, long frame) {
        seen.removeIf(s -> now - s.time > 900);
        for (Seen s : seen) {
            if (s.id.equals(id) && Geometry.overlap(s.box, box) >= .35) {
                if (frame != s.frame) s.count++;
                s.frame=frame; s.time=now; s.box=box.clone(); return s.count;
            }
        }
        seen.add(new Seen(id, box, now, frame));
        if (seen.size() > 200) seen.remove(0);
        return 1;
    }
    public synchronized void clear() { seen.clear(); }
}
