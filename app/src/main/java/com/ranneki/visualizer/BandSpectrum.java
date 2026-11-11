package com.ranneki.visualizer;

/** Logarithmic frequency-band utilities shared by FFT analysis and custom FX mapping. */
final class BandSpectrum {
    static final int COUNT = 32;
    static final float MIN_HZ = 25f;
    static final float MAX_HZ = 10000f;
    private static final double LOG_SPAN = Math.log(MAX_HZ / MIN_HZ);

    private BandSpectrum() { }

    static int index(float hz) {
        if (hz <= MIN_HZ) return 0;
        if (hz >= MAX_HZ) return COUNT - 1;
        return Math.min(COUNT - 1, Math.max(0,
                (int) (Math.log(hz / MIN_HZ) / LOG_SPAN * COUNT)));
    }

    static float lower(int i) {
        return (float) (MIN_HZ * Math.exp(LOG_SPAN * i / COUNT));
    }

    static float upper(int i) {
        return (float) (MIN_HZ * Math.exp(LOG_SPAN * (i + 1) / COUNT));
    }

    /** Weighted mean of bands intersecting the selected Hz range (not stem separation). */
    static float range(float[] levels, float fromHz, float toHz) {
        if (levels == null || levels.length != COUNT) return 0f;
        float lo = Math.max(MIN_HZ, Math.min(fromHz, toHz));
        float hi = Math.min(MAX_HZ, Math.max(fromHz, toHz));
        if (hi <= lo) return levels[index(Math.min(MAX_HZ, Math.max(MIN_HZ, lo)))];
        double total = 0d, weight = 0d;
        for (int i = 0; i < COUNT; i++) {
            float overlap = Math.max(0f, Math.min(hi, upper(i)) - Math.max(lo, lower(i)));
            if (overlap > 0f) {
                total += levels[i] * overlap;
                weight += overlap;
            }
        }
        return weight <= 0 ? 0f : (float)(total / weight);
    }

    static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
