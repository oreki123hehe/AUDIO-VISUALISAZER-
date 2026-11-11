package com.ranneki.visualizer;

/** Deterministic exponential envelope; control values expressed in milliseconds. */
final class ReactiveEnvelope {
    private ReactiveEnvelope() { }

    static float step(float previous, float input, float threshold,
                      float attackMs, float releaseMs, float elapsedMs) {
        float floor = BandSpectrum.clamp(threshold);
        float target = input <= floor ? 0f :
                BandSpectrum.clamp((input - floor) / Math.max(.001f, 1f - floor));
        float tau = target > previous ? attackMs : releaseMs;
        if (tau <= 0f) return target;
        float dt = Math.max(0f, Math.min(250f, elapsedMs));
        float alpha = (float)(1.0 - Math.exp(-dt / tau));
        return BandSpectrum.clamp(previous + (target - previous) * alpha);
    }
}
