package com.ranneki.visualizer;

import java.util.Arrays;

/** Timing of actual Canvas onDraw calls; unlike a choreographer callback this
 * does not count callbacks that never rendered a frame. No Android dependencies. */
public final class PreviewPacing {
    private final long[] recentIntervalsNs=new long[256];
    private int count=0,stored=0,write=0;
    private long firstNs=0,lastNs=0;
    public void reset(){count=stored=write=0;firstNs=lastNs=0;}
    public void record(long whenNs){
        if(whenNs<=0 || (lastNs>0&&whenNs<=lastNs))return;
        if(firstNs==0)firstNs=whenNs;
        if(lastNs!=0){
            recentIntervalsNs[write++%recentIntervalsNs.length]=whenNs-lastNs;
            if(stored<recentIntervalsNs.length)stored++;
        }
        lastNs=whenNs;count++;
    }
    public float observedFps(){
        if(count<2 || lastNs<=firstNs)return 0f;
        return (count-1)*1_000_000_000f/(lastNs-firstNs);
    }
    public float p95FrameGapMs(){
        if(stored==0)return 0f;
        long[] sorted=Arrays.copyOf(recentIntervalsNs,stored);
        Arrays.sort(sorted);
        return sorted[Math.min(sorted.length-1,(int)Math.ceil(.95*sorted.length)-1)]/1_000_000f;
    }
    public String summary(){
        return "Drawn frames="+count+", observed FPS="+
                String.format(java.util.Locale.US,"%.1f",observedFps())+
                ", frame gap p95="+
                String.format(java.util.Locale.US,"%.1f",p95FrameGapMs())+" ms";
    }
}
