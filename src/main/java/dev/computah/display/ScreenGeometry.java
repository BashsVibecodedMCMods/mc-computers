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

    /**
     * The text resolution that fills a monitor of the given size in blocks without letterboxing: the most rows (and
     * so the most detail) whose cells (8x16 pixels) match the shape of the display area, within the given maximum.
     * Returns {cols, rows}. Like OpenComputers, screens still start at their maximum resolution; programs apply this
     * with {@code gpu.setResolution(gpu.getFitResolution())}.
     */
    public static int[] fitResolution(int blocksWide, int blocksHigh, int maxCols, int maxRows) {
        double aw = Math.max(1, blocksWide) - 2 * MARGIN, ah = Math.max(1, blocksHigh) - 2 * MARGIN;
        double colsPerRow = 2 * aw / ah; // cols * WIDTH / (rows * HEIGHT) == aw / ah, with HEIGHT = 2 * WIDTH
        int rows = (int) Math.max(1, Math.min(maxRows, Math.floor(maxCols / colsPerRow)));
        int cols = (int) Math.max(1, Math.min(maxCols, Math.round(colsPerRow * rows)));
        return new int[]{cols, rows};
    }

    /** Converts a point on the face (blocks from the top-left) to 1-based fractional cell coordinates, or null. */
    public static double[] toCell(int blocksWide, int blocksHigh, DisplayBuffer b, double x, double y) {
        double[] r = displayRect(blocksWide, blocksHigh, b.cols() * Font.WIDTH, b.rows() * Font.HEIGHT);
        double fx = (x - r[0]) / r[2], fy = (y - r[1]) / r[3];
        if (fx < 0 || fy < 0 || fx >= 1 || fy >= 1) return null;
        return new double[]{fx * b.cols() + 1, fy * b.rows() + 1};
    }
}
