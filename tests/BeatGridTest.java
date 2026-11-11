package com.ranneki.visualizer;
public final class BeatGridTest {
    public static void main(String[] args){
        verify(90);verify(120);verify(160);
        long[] ms=new long[200];float[] empty=new float[200];
        for(int i=0;i<ms.length;i++)ms[i]=i*20;
        if(BeatGrid.detect(ms,empty,empty).bpm()!=0)throw new AssertionError("Silent beat detection");
        System.out.println("BeatGridTest OK");
    }
    static void verify(int bpm){
        long[] ms=new long[1500];float[] kick=new float[ms.length],snare=new float[ms.length];
        long period=Math.round(60000.0/bpm);
        for(int i=0;i<ms.length;i++){
            ms[i]=i*20;
            if(ms[i]>100 && Math.floorMod(ms[i]-120,period)<20)kick[i]=1f;
        }
        BeatGrid grid=BeatGrid.detect(ms,kick,snare);
        if(Math.abs(grid.bpm()-bpm)>2)throw new AssertionError(bpm+" got "+grid.bpm());
        if(grid.positions().length<5)throw new AssertionError("Too few beats");
    }
}
