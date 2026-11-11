package com.ranneki.visualizer;

public final class Fft {
    private Fft() {}
    /** In-place radix-2 FFT. Array length must be a power of two. */
    public static void forward(float[] real, float[] imag) {
        int n=real.length;
        for(int i=1,j=0;i<n;i++){
            int bit=n>>1;
            for(; (j&bit)!=0;bit>>=1) j^=bit;
            j^=bit;
            if(i<j){float a=real[i];real[i]=real[j];real[j]=a;a=imag[i];imag[i]=imag[j];imag[j]=a;}
        }
        for(int len=2;len<=n;len<<=1){
            double step=-2.0*Math.PI/len;
            float wrStep=(float)Math.cos(step), wiStep=(float)Math.sin(step);
            for(int start=0;start<n;start+=len){
                float wr=1,wi=0;
                for(int j=0;j<len/2;j++){
                    int a=start+j,b=a+len/2;
                    float vr=real[b]*wr-imag[b]*wi,vi=real[b]*wi+imag[b]*wr;
                    real[b]=real[a]-vr;imag[b]=imag[a]-vi;
                    real[a]+=vr;imag[a]+=vi;
                    float nr=wr*wrStep-wi*wiStep;
                    wi=wr*wiStep+wi*wrStep;wr=nr;
                }
            }
        }
    }
}
