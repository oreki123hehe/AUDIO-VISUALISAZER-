package com.ranneki.visualizer;

/** Adaptive, normalized spectral-flux onsets. Does NOT isolate kick/snare stems.
 * Operates on consecutive 32-band analysis windows in chronological order. */
public final class SpectralOnset {
    public static final class Event {
        public final float kick,snare;
        Event(float kick,float snare){this.kick=kick;this.snare=snare;}
    }
    private final float[] previous=new float[BandSpectrum.COUNT];
    private float lowAverage=.015f,midAverage=.015f;
    private long lastKick=-10000,lastSnare=-10000;
    public Event next(float[] bands,long timestampMs) {
        if(bands==null||bands.length!=BandSpectrum.COUNT)
            throw new IllegalArgumentException("Require 32 spectral bands");
        float low=0f,mid=0f;
        int lowCount=0,midCount=0;
        for(int i=0;i<bands.length;i++) {
            float v=BandSpectrum.clamp(bands[i]);
            float diff=Math.max(0f,v-previous[i]);
            previous[i]=v;
            float hz=BandSpectrum.lower(i);
            if(hz>=35 && hz<190) {low+=diff;lowCount++;}
            else if(hz>=190 && hz<4500) {mid+=diff;midCount++;}
        }
        low/=Math.max(1,lowCount);mid/=Math.max(1,midCount);
        float kick=0f,snare=0f;
        // Calibrated for spectral bands after robust 85th-percentile normalization.
        if(low>.075f && low>lowAverage*1.9f && timestampMs-lastKick>=120) {
            kick=BandSpectrum.clamp(.2f+low*1.5f);lastKick=timestampMs;
        }
        if(mid>.085f && mid>midAverage*2.0f && timestampMs-lastSnare>=100) {
            snare=BandSpectrum.clamp(.15f+mid*1.35f);lastSnare=timestampMs;
        }
        lowAverage=lowAverage*.88f+low*.12f;
        midAverage=midAverage*.88f+mid*.12f;
        return new Event(kick,snare);
    }
}
