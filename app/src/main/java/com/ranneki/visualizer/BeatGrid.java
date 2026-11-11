package com.ranneki.visualizer;

import java.util.ArrayList;
import java.util.List;

/** On-device coarse musical pulse estimator from detected low-band onsets.
 * A visual editing guide, NOT an isolated instrument/stem or sample-accurate beat tracker. */
public final class BeatGrid {
    private final int bpm;
    private final long[] beats;
    private BeatGrid(int bpm,long[] beats){this.bpm=bpm;this.beats=beats;}
    public int bpm(){return bpm;}
    public long[] positions(){return beats.clone();}
    public static BeatGrid detect(long[] times,float[] kick,float[] snare){
        if(times==null||kick==null||snare==null||times.length!=kick.length||times.length!=snare.length||times.length<40)
            return new BeatGrid(0,new long[0]);
        List<Long> events=new ArrayList<>();List<Float> scores=new ArrayList<>();
        long last=-10_000;
        for(int i=1;i<times.length-1;i++){
            float intensity=kick[i]+snare[i]*.20f;
            if(intensity<.23f||intensity<kick[i-1]+snare[i-1]*.20f||
                    intensity<kick[i+1]+snare[i+1]*.20f||times[i]-last<90)continue;
            events.add(times[i]);scores.add(intensity);last=times[i];
        }
        if(events.size()<4)return new BeatGrid(0,new long[0]);
        int bestBpm=0;double best=0;
        for(int candidate=70;candidate<=190;candidate++){
            double period=60000.0/candidate;double tolerance=Math.min(55,period*.16);
            double value=0,possible=0;
            for(int i=0;i<events.size();i++){
                long at=events.get(i);
                if(at>60_000)break; // score the first minute, bounded runtime
                float amp=scores.get(i);possible+=amp;
                for(int j=i+1;j<events.size();j++){
                    double dt=events.get(j)-at;
                    if(dt>period*5.3)break;
                    int multiples=(int)Math.round(dt/period);
                    if(multiples<1||multiples>5)continue;
                    double error=Math.abs(dt-multiples*period);
                    if(error<tolerance)
                        value+=amp*scores.get(j)*(1-error/tolerance)/Math.sqrt(multiples);
                }
            }
            if(possible>0 && value/possible>best){best=value/possible;bestBpm=candidate;}
        }
        if(bestBpm==0||best<.22)return new BeatGrid(0,new long[0]);
        long period=Math.max(1,Math.round(60000.0/bestBpm));
        long anchor=events.get(0);
        long duration=Math.max(0,times[times.length-1]);
        long shift=anchor%period;
        int count=(int)Math.min(100_000,1+Math.max(0,duration-shift)/period);
        long[] beatPositions=new long[count];
        for(int i=0;i<count;i++)beatPositions[i]=shift+i*period;
        return new BeatGrid(bestBpm,beatPositions);
    }
}
