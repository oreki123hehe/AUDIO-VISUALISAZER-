package com.ranneki.visualizer;

import java.util.Map;
import java.util.TreeMap;

/** Bounded integer keyframe automation with deterministic linear interpolation. */
final class AutomationTrack {
    private static final int MAX_POINTS = 128;
    private final TreeMap<Long,Integer> points = new TreeMap<>();
    private final int minValue,maxValue;

    AutomationTrack(){this(0,100);}
    AutomationTrack(int min,int max){
        if(min>max)throw new IllegalArgumentException("Invalid automation range");
        minValue=min;maxValue=max;
    }

    int count() { return points.size(); }
    void put(long ms, int percent) {
        long position = Math.max(0, ms);
        if (!points.containsKey(position) && points.size() >= MAX_POINTS) return;
        points.put(position, Math.max(minValue, Math.min(maxValue, percent)));
    }
    void removeNear(long ms, long toleranceMs) {
        Long best = null;
        long distance = Math.max(0, toleranceMs) + 1;
        for (Long point : points.keySet()) {
            long d = Math.abs(point - ms);
            if (d < distance) { best = point; distance = d; }
        }
        if (best != null) points.remove(best);
    }
    void clear() { points.clear(); }
    int valueAt(long ms, int defaultValue) {
        if (points.isEmpty()) return defaultValue;
        Map.Entry<Long,Integer> before = points.floorEntry(ms);
        Map.Entry<Long,Integer> after = points.ceilingEntry(ms);
        if (before == null) return points.firstEntry().getValue();
        if (after == null) return points.lastEntry().getValue();
        if (before.getKey().equals(after.getKey())) return before.getValue();
        double t = (ms - before.getKey()) / (double)(after.getKey() - before.getKey());
        return (int)Math.round(before.getValue() + (after.getValue() - before.getValue()) * t);
    }
    long[] positions() {
        long[] result=new long[points.size()];
        int i=0;
        for(Long ms:points.keySet()) result[i++]=ms;
        return result;
    }
    String encode() {
        StringBuilder output = new StringBuilder();
        for (Map.Entry<Long,Integer> e : points.entrySet()) {
            if (output.length() > 0) output.append(';');
            output.append(e.getKey()).append(':').append(e.getValue());
        }
        return output.toString();
    }
    void decode(String text) {
        clear();
        if (text == null || text.length() > 5000) return;
        for (String part : text.split(";")) {
            String[] pair = part.split(":");
            if (pair.length != 2) continue;
            try {
                long ms = Long.parseLong(pair[0]);
                int value = Integer.parseInt(pair[1]);
                if (ms >= 0 && value >= minValue && value <= maxValue) put(ms, value);
            } catch(NumberFormatException ignored) { }
        }
    }
}
