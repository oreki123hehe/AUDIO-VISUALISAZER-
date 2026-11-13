package com.ranneki.visualizer;

/** In-place radix-2 forward FFT; no external libraries or native dependencies. */
final class Fft {
    static void inverse(float[] re,float[] im){
        for(int i=0;i<im.length;i++)im[i]=-im[i];forward(re,im);
        for(int i=0;i<re.length;i++){re[i]/=re.length;im[i]=-im[i]/re.length;}
    }
    static void forward(float[] re,float[] im) {
            int n=re.length;
            for(int i=1,j=0;i<n;i++) {
                int bit=n>>1;
                for(; (j&bit)!=0;bit>>=1) j^=bit;
                j^=bit;
                if(i<j) {
                    float t=re[i];re[i]=re[j];re[j]=t;
                    t=im[i];im[i]=im[j];im[j]=t;
                }
            }
            for(int len=2;len<=n;len<<=1) {
                double angle=-2*Math.PI/len;
                float wlenR=(float)Math.cos(angle),wlenI=(float)Math.sin(angle);
                for(int i=0;i<n;i+=len) {
                    float wr=1,wi=0;
                    for(int j=0;j<len/2;j++) {
                        int a=i+j,b=a+len/2;
                        float vr=re[b]*wr-im[b]*wi;
                        float vi=re[b]*wi+im[b]*wr;
                        re[b]=re[a]-vr;im[b]=im[a]-vi;
                        re[a]+=vr;im[a]+=vi;
                        float next=wr*wlenR-wi*wlenI;
                        wi=wr*wlenI+wi*wlenR;wr=next;
                    }
                }
            }
        }
    }

