package au.id.pointandyshoot.bookscanner.core;

/** Shelf-wide coverage without waiting for nine narrow tiles to cycle. */
public final class DiscoverySchedule {
    public static final class Plan {
        public final boolean full,detail;
        public final float top,bottom,angle;
        public final String scope;
        private Plan(boolean full,boolean detail,float top,float bottom,float angle,String scope){
            this.full=full;this.detail=detail;this.top=top;this.bottom=bottom;this.angle=angle;this.scope=scope;
        }
    }
    private int step;
    public Plan next(){
        int job=step++;
        // One larger detector pass per eight jobs, alternating the shelf band.
        boolean detail=job%16==6||job%16==15;
        // Rotating before detection lets vertical names become whole text lines.
        if(job%4==0){float angle=job%8==0?90:0;return new Plan(true,false,0,1,angle,"full "+(int)angle+"°");}
        // Two lower reads give arriving books another chance before the upper read.
        boolean lower=job%4!=3;
        return new Plan(false,detail,lower?.4f:0,lower?1:.6f,90,lower?"lower 90°":"upper 90°");
    }
    public void reset(){step=0;}
}
