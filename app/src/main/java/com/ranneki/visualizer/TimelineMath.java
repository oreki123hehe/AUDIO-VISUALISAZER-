package com.ranneki.visualizer;

/** Pure, bounded timeline geometry shared by Android UI and JVM tests. */
public final class TimelineMath {
    private TimelineMath() {}
    public static float fraction(long positionMs,long durationMs) {
        if(durationMs<=0) return 0f;
        return (float)Math.max(0d,Math.min(1d,positionMs/(double)durationMs));
    }
    public static long positionAt(float normalized,long durationMs) {
        if(durationMs<=0 || Float.isNaN(normalized)) return 0;
        return Math.round(Math.max(0d,Math.min(1d,normalized)) * durationMs);
    }
    public static float spanFraction(long startMs,long endMs,long durationMs) {
        if(durationMs<=0) return 0f;
        long cappedEnd=endMs==Long.MAX_VALUE?durationMs:Math.max(0,Math.min(durationMs,endMs));
        long cappedStart=Math.max(0,Math.min(durationMs,startMs));
        return fraction(Math.max(0,cappedEnd-cappedStart),durationMs);
    }
    public static boolean intersects(long startMs,long endMs,long durationMs) {
        return durationMs>0 && startMs<durationMs && endMs>=0 && endMs>=startMs;
    }
}
