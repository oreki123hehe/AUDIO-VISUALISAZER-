package com.ranneki.visualizer;

import java.util.Map;
import java.util.TreeMap;

/** Bounded integer keyframe automation with subpixel interpolation and per-segment easing. */
final class AutomationTrack {
    private static final int MAX_POINTS = 128;
    private final TreeMap<Long,Integer> points = new TreeMap<>();
    private final TreeMap<Long,MotionCurve> curves = new TreeMap<>();
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
    /** Atomically move an existing keyframe without deleting a nearby neighbour.
     * An occupied destination is rejected so dragging never destroys another point. */
    boolean move(long fromMs,long toMs) {
        if(!points.containsKey(fromMs))return false;
        long destination=Math.max(0L,toMs);
        if(destination==fromMs)return true;
        if(points.containsKey(destination))return false;
        int value=points.remove(fromMs);
        points.put(destination,value);
        MotionCurve curve=curves.remove(fromMs);if(curve!=null)curves.put(destination,curve);
        return true;
    }
    void removeNear(long ms, long toleranceMs) {
        Long best = null;
        long distance = Math.max(0, toleranceMs) + 1;
        for (Long point : points.keySet()) {
            long d = Math.abs(point - ms);
            if (d < distance) { best = point; distance = d; }
        }
        if (best != null) {points.remove(best);curves.remove(best);}
    }
    void clear() { points.clear();curves.clear(); }
    boolean setCurve(long ms,MotionCurve curve){Long key=points.floorKey(ms);if(key==null||points.higherKey(key)==null)return false;curves.put(key,curve.copy());return true;}
    MotionCurve curveAt(long ms){Long key=points.floorKey(ms);MotionCurve c=key==null?null:curves.get(key);return c==null?new MotionCurve():c.copy();}
    void setAllCurves(MotionCurve curve){for(Long key:points.keySet())if(points.higherKey(key)!=null)curves.put(key,curve.copy());}
    int valueAt(long ms, int defaultValue) {return Math.round(sample(ms,defaultValue));}
    float sample(long ms, float defaultValue) {
        if (points.isEmpty()) return defaultValue;
        Map.Entry<Long,Integer> before = points.floorEntry(ms);
        Map.Entry<Long,Integer> after = points.ceilingEntry(ms);
        if (before == null) return points.firstEntry().getValue();
        if (after == null) return points.lastEntry().getValue();
        if (before.getKey().equals(after.getKey())) return before.getValue();
        double t = (ms - before.getKey()) / (double)(after.getKey() - before.getKey());
        MotionCurve curve=curves.get(before.getKey());
        float eased=curve==null?(float)t:curve.map((float)t);
        return before.getValue() + (after.getValue() - before.getValue()) * eased;
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
            MotionCurve curve=curves.get(e.getKey());if(curve!=null&&curve.type!=0)output.append(':').append(curve.encode());
        }
        return output.toString();
    }
    void decode(String text) {
        clear();
        if (text == null || text.length() > 20000) return;
        for (String part : text.split(";")) {
            String[] pair = part.split(":");
            if (pair.length < 2 || pair.length > 3) continue;
            try {
                long ms = Long.parseLong(pair[0]);
                int value = Integer.parseInt(pair[1]);
                if (ms >= 0 && value >= minValue && value <= maxValue) {
                    MotionCurve c=pair.length==3?MotionCurve.decode(pair[2]):new MotionCurve();
                    put(ms, value);if(points.containsKey(ms))curves.put(ms,c);
                }
            } catch(IllegalArgumentException ignored) { }
        }
    }
}
