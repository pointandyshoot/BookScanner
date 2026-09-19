package au.id.pointandyshoot.bookscanner.core;

import java.util.*;

/** Alert once per visual track, re-arming after genuine absence rather than every OCR read. */
public final class AppearanceGate {
    public static final long ABSENCE_MS = 1500;
    private final Map<String, Long> lastVisible = new HashMap<>();
    public boolean update(Set<String> visible, long now) {
        boolean newlyVisible=false;
        for(String id:visible) {
            Long last=lastVisible.put(id,now);
            newlyVisible |= last==null || now-last>=ABSENCE_MS;
        }
        lastVisible.entrySet().removeIf(e->now-e.getValue()>60_000);
        return newlyVisible;
    }
    public void clear(){lastVisible.clear();}
}
