package com.ranneki.visualizer;
public final class FrameMetricsTest {
    public static void main(String[] args) {
        FrameMetrics f=new FrameMetrics();
        if(f.recentP95Ms()!=0)throw new AssertionError("empty sample window");
        for(int i=0;i<1000;i++)f.record(10_000_000L,1_000_000L);
        if(f.frames()!=1000 || Math.abs(f.averageRenderMs()-10)>1e-9 || f.recentP95Ms()!=11)
            throw new AssertionError("basic render stats");
        if(Math.abs(f.throughputFps(10_000_000_000L)-100)>1e-9)
            throw new AssertionError("fps calculation");
        f.record(35_000_000,5_000_000);
        if(f.maxFrameMs()!=40)throw new AssertionError("max frame time");
        try {f.record(-1,0);throw new AssertionError("must reject bad samples");}
        catch(IllegalArgumentException expected){}
        System.out.println("PASS: frame timing diagnostics and bounded p95 window");
    }
}
