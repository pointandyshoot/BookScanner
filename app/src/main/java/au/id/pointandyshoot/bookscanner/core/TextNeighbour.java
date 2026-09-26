package au.id.pointandyshoot.bookscanner.core;

/** Conservative same-orientation line pairing, evaluated in the original image coordinates. */
public final class TextNeighbour {
    private TextNeighbour() {}
    public static boolean canJoin(float[] a,float[] b) {
        double[] x=shape(a),y=shape(b);
        double alignment=Math.abs(x[2]*y[2]+x[3]*y[3]);
        if(alignment<.96 || Math.max(x[5],y[5])>3*Math.min(x[5],y[5]))return false;
        double dx=y[0]-x[0],dy=y[1]-x[1];
        double along=Math.abs(dx*x[2]+dy*x[3]);
        double across=Math.abs(-dx*x[3]+dy*x[2]);
        // Parallel lines stacked across their baselines; not adjacent words on another spine.
        return along<=.35*Math.max(x[4],y[4]) &&
                across>=.3*Math.min(x[5],y[5]) && across<=1.8*Math.max(x[5],y[5]);
    }
    private static double[] shape(float[] q) {
        double ax=q[2]-q[0],ay=q[3]-q[1],bx=q[6]-q[0],by=q[7]-q[1];
        double a=Math.hypot(ax,ay),b=Math.hypot(bx,by);
        if(b>a){double t=ax;ax=bx;bx=t;t=ay;ay=by;by=t;t=a;a=b;b=t;}
        return new double[]{(q[0]+q[2]+q[4]+q[6])/4.,(q[1]+q[3]+q[5]+q[7])/4.,ax/Math.max(1,a),ay/Math.max(1,a),a,b};
    }
}
