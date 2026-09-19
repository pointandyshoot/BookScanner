package au.id.pointandyshoot.bookscanner.core;

/** Right-angle discovery remains frequent; each job also gets a tilted detail read. */
public final class ReadingSchedule {
    private final OrientationSchedule coarse=new OrientationSchedule();
    private int step=0;
    private static final int[] FALLBACK={15,-15,30,-30,45,-45};
    public float[] next(float measuredLean) {
        int angle=coarse.next();
        float correction=Float.isFinite(measuredLean)&&Math.abs(measuredLean)>=3
                ? -Math.max(-45,Math.min(45,measuredLean)) : FALLBACK[step%FALLBACK.length];
        step++;
        return new float[]{angle,angle+correction};
    }
    public void result(float coarseAngle,boolean matched){coarse.result(Math.round(coarseAngle),matched);}
    public void reset(){coarse.reset();step=0;}
}
