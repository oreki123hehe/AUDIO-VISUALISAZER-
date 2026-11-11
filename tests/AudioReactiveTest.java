package com.ranneki.visualizer;

/** Executable, Android-SDK-free checks for custom band mapping and envelope response. */
public class AudioReactiveTest {
    private static void near(float actual,float wanted,float tolerance,String label) {
        if(!Float.isFinite(actual)||Math.abs(actual-wanted)>tolerance)
            throw new AssertionError(label+": "+actual+" != "+wanted);
    }
    public static void main(String[] args) {
        if(BandSpectrum.index(25)!=0 || BandSpectrum.index(10000)!=31)
            throw new AssertionError("Band endpoint index");
        float[] bands=new float[32];
        bands[BandSpectrum.index(80f)]=.85f;
        float kick=BandSpectrum.range(bands,70,90);
        float other=BandSpectrum.range(bands,2000,4000);
        if(!(kick>.3f && other==0f)) throw new AssertionError("Custom frequency mapping");
        near(ReactiveEnvelope.step(0f,.25f,.5f,20,180,16),0f,.0001f,"Threshold gate");
        float a=ReactiveEnvelope.step(0f,1f,.2f,20,200,16);
        float b=ReactiveEnvelope.step(a,0f,.2f,20,200,16);
        if(!(a>0f && a<1f && b>0f && b<a))
            throw new AssertionError("Envelope attack/release");
        near(ReactiveEnvelope.step(.5f,0f,0f,10,0,16),0f,.0001f,"Zero-release");
        System.out.println("PASS: custom frequency mapping, threshold, attack/release");
    }
}
