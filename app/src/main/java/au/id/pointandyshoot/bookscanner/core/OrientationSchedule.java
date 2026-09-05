package au.id.pointandyshoot.bookscanner.core;

/** Bounded right-angle exploration; productive orientations never starve other spines. */
public final class OrientationSchedule {
    private static final int[] ANGLES={90,180,270,0};
    private int index=0,frame=0,preferred=90;
    private boolean matched=false;
    public int next(){frame++;return matched&&frame%3!=0?preferred:ANGLES[index++%ANGLES.length];}
    public void result(int angle,boolean matched){this.matched=matched;if(matched)preferred=angle;}
    public void reset(){index=0;frame=0;preferred=90;matched=false;}
}
