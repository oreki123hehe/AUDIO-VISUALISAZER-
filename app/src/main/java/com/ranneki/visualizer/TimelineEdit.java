package com.ranneki.visualizer;

import java.util.Arrays;

/** Pure timeline-edit geometry. The editor and JVM tests use identical rules. */
public final class TimelineEdit {
    private TimelineEdit() { }

    /** Returns input unchanged when no beat falls inside the requested window. */
    public static long snapToBeat(long positionMs, long[] beats, long toleranceMs) {
        long candidate = Math.max(0L, positionMs);
        if (beats == null || beats.length == 0 || toleranceMs < 0) return candidate;
        int found = Arrays.binarySearch(beats, candidate);
        if (found >= 0) return beats[found];
        int next = -found - 1;
        long best = candidate;
        long bestDistance = toleranceMs == Long.MAX_VALUE ? toleranceMs : toleranceMs + 1L;
        for (int i = Math.max(0, next - 1); i < Math.min(beats.length, next + 1); i++) {
            long beat = beats[i];
            if (beat < 0) continue;
            long distance = Math.abs(candidate - beat);
            if (distance < bestDistance) { bestDistance = distance; best = beat; }
        }
        return best;
    }

    /** Move a trimmed lane without stretching it or leaving the audio duration. */
    public static long[] shiftRange(long startMs, long endMs, long deltaMs, long durationMs) {
        long duration = Math.max(0L, durationMs);
        long start = Math.max(0L, Math.min(duration, startMs));
        long end = endMs == Long.MAX_VALUE ? duration : Math.max(start, Math.min(duration, endMs));
        long width = end - start;
        long proposed = (long)Math.max(0d, Math.min((double)(duration-width), (double)start+deltaMs));
        return new long[]{proposed, proposed + width};
    }

    /** Scroll through all 48 layers while only drawing the visible subset. */
    public static int clampFirstLane(int first, int layerCount, int visibleLanes) {
        return Math.max(0, Math.min(Math.max(0, layerCount - Math.max(1, visibleLanes)), first));
    }
}
