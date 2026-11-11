package com.ranneki.visualizer;

/** Estimates peak app-cache storage during the current multi-file H264/AAC mux pipeline. */
public final class ExportBudget {
    private ExportBudget() {}
    public static long requiredBytes(int bitrate, long durationMs) {
        if(bitrate<=0 || durationMs<=0) throw new IllegalArgumentException("Invalid export budget");
        long seconds=(durationMs+999L)/1000L;
        // Video-only temporary + final muxed temporary + AAC transcode temporary.
        // Use a generous 2x video allocation margin for variable-rate hardware encoders.
        long videoBytes=(long)bitrate*seconds/8L;
        long audioBytes=24_000L*seconds;
        if(videoBytes>Long.MAX_VALUE/4L-audioBytes)
            throw new IllegalArgumentException("Export too long");
        return Math.max(64L*1024*1024,videoBytes*4L+audioBytes*3L+32L*1024*1024);
    }
    public static void check(long freeBytes,int bitrate,long durationMs) {
        long need=requiredBytes(bitrate,durationMs);
        if(freeBytes<need)
            throw new IllegalStateException("Cache storage tidak cukup: perlu sekitar " +
                    (need/(1024*1024))+" MB; kosong "+(Math.max(0,freeBytes)/(1024*1024))+" MB");
    }
}
