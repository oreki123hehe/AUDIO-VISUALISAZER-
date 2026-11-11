package com.ranneki.visualizer;
/** Stable rational timestamps. Frame N is exactly N/FPS seconds, without cumulative drift. */
public final class FrameTimebase {
    private FrameTimebase() {}
    public static long frameCount(long durationMs,int fps) {
        if(fps<1||durationMs<0)throw new IllegalArgumentException("invalid frame duration/rate");
        return (durationMs*fps+999)/1000;
    }
    public static long frameMillis(long index,int fps) {
        if(fps<1||index<0)throw new IllegalArgumentException("invalid index/rate");
        return index*1000L/fps;
    }
    public static long presentationNs(long index,int fps) {
        if(fps<1||index<0)throw new IllegalArgumentException("invalid index/rate");
        return index*1_000_000_000L/fps;
    }
    public static long audioSampleUs(long samples,int sampleRate) {
        if(sampleRate<1||samples<0)throw new IllegalArgumentException("invalid samples/rate");
        return samples*1_000_000L/sampleRate;
    }
}
