package com.ranneki.visualizer;

/** Immutable render profile; output resolution and rasterization resolution can differ. */
public final class ExportProfile {
    public final int width, height, fps, rasterPercent;
    public final int chromaticPercent, vignettePercent;

    public ExportProfile(int width, int height, int fps, int rasterPercent,
                         int chromaticPercent, int vignettePercent) {
        if (width < 128 || height < 128 || (width & 1) != 0 || (height & 1) != 0)
            throw new IllegalArgumentException("Output resolution must be even, >= 128");
        if (fps != 30 && fps != 60)
            throw new IllegalArgumentException("Only 30 or 60 FPS is supported");
        if (rasterPercent < 50 || rasterPercent > 100)
            throw new IllegalArgumentException("Render quality outside 50..100%");
        this.width = width; this.height = height; this.fps = fps;
        this.rasterPercent = rasterPercent;
        this.chromaticPercent = clamp(chromaticPercent);
        this.vignettePercent = clamp(vignettePercent);
    }
    private static int clamp(int x) { return Math.max(0, Math.min(100, x)); }
    public int rasterWidth() { return even(Math.max(128, Math.round(width * rasterPercent / 100f))); }
    public int rasterHeight() { return even(Math.max(128, Math.round(height * rasterPercent / 100f))); }
    private static int even(int n) { return n - (n & 1); }
    public int bitrate() {
        // At most 25 Mbps, with a sensible minimum for 540p previews.
        return Math.max(1_800_000, Math.min(25_000_000, (int)(width * (long)height * fps * .15)));
    }
}
