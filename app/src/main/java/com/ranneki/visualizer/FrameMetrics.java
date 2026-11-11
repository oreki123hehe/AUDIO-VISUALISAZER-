package com.ranneki.visualizer;

import java.util.Arrays;
import java.util.Locale;

/** Allocation-free frame recording; report construction happens outside the render loop. */
public final class FrameMetrics {
    private static final int RECENT = 512;
    private final long[] samples = new long[RECENT];
    private long count, totalRenderNs, totalSubmitNs, maxFrameNs;
    private int index;

    /** Add CPU scene rendering and GPU upload/swap timings for one completed frame. */
    public void record(long renderNs, long submitNs) {
        if (renderNs < 0 || submitNs < 0) throw new IllegalArgumentException("Negative frame time");
        totalRenderNs += renderNs;
        totalSubmitNs += submitNs;
        long total = renderNs + submitNs;
        if (total > maxFrameNs) maxFrameNs = total;
        samples[index++ % RECENT] = total;
        count++;
    }

    public long frames() { return count; }
    public double averageRenderMs() { return count == 0 ? 0 : totalRenderNs / 1e6 / count; }
    public double averageSubmitMs() { return count == 0 ? 0 : totalSubmitNs / 1e6 / count; }
    public double maxFrameMs() { return maxFrameNs / 1e6; }
    /** Recent-window p95, not a lifetime percentile if the export exceeds 512 frames. */
    public double recentP95Ms() {
        int size = (int) Math.min(count, RECENT);
        if (size == 0) return 0;
        long[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);
        return sorted[Math.max(0, (int) Math.ceil(size * .95) - 1)] / 1e6;
    }
    public double throughputFps(long elapsedNs) {
        return elapsedNs <= 0 ? 0 : (count * 1e9 / elapsedNs);
    }
    public String summary(long elapsedNs) {
        return String.format(Locale.US,
                "Frames: %d | throughput: %.1f fps\nCanvas CPU: %.2f ms/frame | EGL GPU submit: %.2f ms/frame\nRecent p95 total: %.2f ms | peak: %.2f ms",
                count, throughputFps(elapsedNs), averageRenderMs(), averageSubmitMs(),
                recentP95Ms(), maxFrameMs());
    }
}
