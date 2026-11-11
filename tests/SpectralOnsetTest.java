package com.ranneki.visualizer;

public final class SpectralOnsetTest {
    public static void main(String[] args) {
        SpectralOnset d=new SpectralOnset();
        float[] bands=new float[BandSpectrum.COUNT];
        d.next(bands,0);d.next(bands,40);
        for(int i=3;i<11;i++)bands[i]=.85f;
        SpectralOnset.Event low=d.next(bands,80);
        assert low.kick>.2f : "low band impulse not detected";
        assert low.snare<.01f : "low band impulse should not be snare";
        assert d.next(bands,100).kick==0f : "steady bass cannot retrigger";
        for(int i=15;i<25;i++)bands[i]=.8f;
        SpectralOnset.Event mid=d.next(bands,240);
        assert mid.snare>.2f : "snare band impulse not detected";
        System.out.println("SpectralOnsetTest PASS");
    }
}
