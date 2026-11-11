package com.ranneki.visualizer;

public final class PreviewPacingTest {
    public static void main(String[] args){
        PreviewPacing meter=new PreviewPacing();
        for(int i=0;i<60;i++)meter.record(1_000_000_000L+i*16_666_667L);
        assert Math.abs(meter.observedFps()-60)<.1f : meter.summary();
        assert meter.p95FrameGapMs()>16f && meter.p95FrameGapMs()<17f : meter.summary();
        meter.reset();
        assert meter.observedFps()==0;
        System.out.println("PreviewPacingTest PASS");
    }
}
