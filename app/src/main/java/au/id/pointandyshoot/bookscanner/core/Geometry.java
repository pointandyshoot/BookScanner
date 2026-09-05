package au.id.pointandyshoot.bookscanner.core;

/** Continuous image coordinates (not pixel indices), clockwise right-angle transforms. */
public final class Geometry {
    private Geometry() {}
    public static float[] unrotate(float x, float y, int degrees, int width, int height) {
        return switch (degrees) {
            case 0 -> new float[]{x, y};
            case 90 -> new float[]{y, height - x};
            case 180 -> new float[]{width - x, height - y};
            case 270 -> new float[]{width - y, x};
            default -> throw new IllegalArgumentException("Right angles only");
        };
    }
    public static float overlap(float[] a, float[] b) {
        float w = Math.max(0, Math.min(a[2], b[2]) - Math.max(a[0], b[0]));
        float h = Math.max(0, Math.min(a[3], b[3]) - Math.max(a[1], b[1]));
        float intersection = w * h;
        float union = (a[2]-a[0])*(a[3]-a[1]) + (b[2]-b[0])*(b[3]-b[1]) - intersection;
        return union <= 0 ? 0 : intersection / union;
    }
}
