package com.ranneki.visualizer;

/** Pure-Java smoke test: check frequency bin and FFT amplitude. */
public class SmokeTest {
    public static void main(String[] args) {
        int n=1024, signalBin=16;
        float[] re=new float[n], im=new float[n];
        for (int i=0;i<n;i++) re[i]=(float)Math.sin(2*Math.PI*signalBin*i/n);
        Fft.forward(re,im);
        int maxBin=0;
        double max=0;
        for(int k=1;k<n/2;k++) {
            double amplitude=Math.hypot(re[k],im[k]);
            if(amplitude>max) {max=amplitude;maxBin=k;}
        }
        if(maxBin!=signalBin || max<500)
            throw new AssertionError("FFT frequency peak mismatch: bin="+maxBin+" amplitude="+max);
        System.out.println("PASS: FFT dominant bin = "+maxBin+", amplitude = "+max);
    }
}
