package com.ranneki.visualizer;
public final class ClipTimingTest {
    public static void main(String[] args){ClipTiming c=new ClipTiming();c.inMs=500;c.outMs=1500;c.speed=2;
        if(c.sourceAt(200,100,2000)!=700||c.sourceAt(700,100,2000)!=700)throw new AssertionError("Trim/speed/loop mapping");
        c.loop=false;if(c.sourceAt(2000,100,2000)!=1499)throw new AssertionError("Hold beyond out");c.fadeIn=200;c.fadeOut=200;
        if(c.alpha(100,0,1000)!=.5f||c.alpha(900,0,1000)!=.5f||c.alpha(1000,0,1000)!=0)throw new AssertionError("Fade/end boundary");
        c.offsetMs=300;if(ClipTiming.decode(c.encode()).sourceAt(100,100,2000)!=800)throw new AssertionError("Split phase persistence");
        ClipTiming.decode("0,1000,1.0,0,0,true");
        try{ClipTiming.decode("1000,500,1,0,0,true");throw new AssertionError("Invalid range accepted");}catch(IllegalArgumentException good){}
        float[] re=new float[4096],im=new float[4096];for(int i=0;i<re.length;i++)re[i]=(float)Math.sin(i*.2);float[] original=re.clone();Fft.forward(re,im);Fft.inverse(re,im);for(int i=0;i<re.length;i++)if(Math.abs(original[i]-re[i])>.0002)throw new AssertionError("FFT reconstruction");
        System.out.println("PASS: clip trims, split phase, loop, speed, fades and inverse FFT reconstruction");
    }
}
