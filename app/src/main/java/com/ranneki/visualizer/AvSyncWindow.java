package com.ranneki.visualizer;

/** Sanity-checks decoded MP4 timestamps. These checks detect gross mux drift,
 * not perceptual audio/visual alignment (which still needs listening tests). */
public final class AvSyncWindow {
    private AvSyncWindow() {}
    public static void validate(long videoFirstUs,long videoLastUs,
            long audioFirstUs,long audioLastUs,long requestedDurationMs) {
        if(videoFirstUs<0||audioFirstUs<0||videoLastUs<videoFirstUs||audioLastUs<audioFirstUs)
            throw new IllegalStateException("Timestamp sample audio/video tidak valid");
        if(Math.abs(videoFirstUs-audioFirstUs)>300_000L)
            throw new IllegalStateException("Audio/video mulai berbeda lebih dari 300 ms");
        // Last encoded video sample starts one frame before the duration endpoint.
        if(requestedDurationMs>1500 && videoLastUs<(requestedDurationMs-700L)*1000L)
            throw new IllegalStateException("Track video berakhir terlalu cepat");
        if(requestedDurationMs>1500 && audioLastUs<(requestedDurationMs-750L)*1000L)
            throw new IllegalStateException("Track audio berakhir terlalu cepat");
        if(Math.abs(videoLastUs-audioLastUs)>750_000L)
            throw new IllegalStateException("Akhir audio/video bergeser lebih dari 750 ms");
    }
}
