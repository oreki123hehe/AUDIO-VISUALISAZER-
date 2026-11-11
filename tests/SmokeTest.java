package com.ranneki.visualizer;
public final class SmokeTest {
  public static void main(String[] args) {
    float[] r=new float[1024],i=new float[1024];
    for(int k=0;k<r.length;k++)r[k]=(float)Math.sin(2*Math.PI*16*k/r.length);
    Fft.forward(r,i);
    int best=0;double magnitude=0;
    for(int k=1;k<512;k++){double p=r[k]*r[k]+i[k]*i[k];if(p>magnitude){magnitude=p;best=k;}}
    if(best!=16)throw new AssertionError("FFT peak expected 16, got "+best);
    System.out.println("FFT smoke test passed");
  }
}
