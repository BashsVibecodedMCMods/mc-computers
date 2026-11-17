package dev.computah.display;

/** Where the display sits on a monitor face, shared by the renderer and touch handling. Units are blocks. */
public final class ScreenGeometry {
    public static final double MARGIN = 2 / 16.0;

    private ScreenGeometry() {
    }

    /** Returns {x, y, width, height} of the image area (top-left origin), keeping the image's aspect ratio. */
    public static double[] displayRect(int blocksWide, int blocksHigh, int imageWidth, int imageHeight) {
        double aw = blocksWide - 2 * MARGIN, ah = blocksHigh - 2 * MARGIN;
        double aspect = (double) imageWidth / Math.max(1, imageHeight);
        double w = aw, h = aw / aspect;
        if (h > ah) {
            h = ah;
            w = ah * aspect;
        }
        return new double[]{MARGIN + (aw - w) / 2, MARGIN + (ah - h) / 2, w, h};
    }

    /** Converts a point on the face (blocks from the top-left) to 1-based fractional cell coordinates, or null. */
    public static double[] toCell(int blocksWide, int blocksHigh, DisplayBuffer b, double x, double y) {
        double[] r = displayRect(blocksWide, blocksHigh, b.cols() * Font.WIDTH, b.rows() * Font.HEIGHT);
        double fx = (x - r[0]) / r[2], fy = (y - r[1]) / r[3];
        if (fx < 0 || fy < 0 || fx >= 1 || fy >= 1) return null;
        return new double[]{fx * b.cols() + 1, fy * b.rows() + 1};
    }
}
