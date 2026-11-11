package com.ranneki.visualizer;
public final class FrameTimebaseTest {
    public static void main(String[] args) {
        if(FrameTimebase.frameCount(1000,30)!=30)throw new AssertionError("1s=30 frames");
        if(FrameTimebase.frameCount(1350,30)!=41)throw new AssertionError("ceil short clip");
        if(FrameTimebase.frameCount(1000,60)!=60)throw new AssertionError("1s=60 frames");
        if(FrameTimebase.presentationNs(59,60)!=983333333L)throw new AssertionError("60 FPS PTS");
        long prev=-1;
        for(long i=0;i<18000;i++){
            long pts=FrameTimebase.presentationNs(i,30);
            if(pts<=prev)throw new AssertionError("nonmonotonic PTS: "+i);
            prev=pts;
        }
        if(FrameTimebase.presentationNs(17999,30)!=599966666666L)
            throw new AssertionError("no floating-point frame drift");
        if(FrameTimebase.audioSampleUs(48000,48000)!=1_000_000L)
            throw new AssertionError("sample timestamp mismatch");
        System.out.println("PASS: video frame PTS and audio sample clocks");
    }
}
