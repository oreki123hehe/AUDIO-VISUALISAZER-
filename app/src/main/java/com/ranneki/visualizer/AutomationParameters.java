package com.ranneki.visualizer;

/** Numeric automation definitions shared by editor and offline renderer. */
final class AutomationParameters {
    static final int STRENGTH=0, SIZE=1, DENSITY=2, VELOCITY=3, OPACITY=4,
            X=5, Y=6, SCALE=7, ROTATION=8, THRESHOLD=9, ATTACK=10,
            RELEASE=11, MIN_HZ=12, MAX_HZ=13, MASK_RADIUS=14, DIRECTION=15;
    static final String[] NAMES={
            "Reaktivitas", "Ukuran", "Jumlah", "Kecepatan", "Opacity",
            "Posisi X", "Posisi Y", "Skala Layer", "Rotasi Layer",
            "Threshold", "Attack (ms)", "Release (ms)", "Min Hz", "Max Hz",
            "Mask Radius", "Arah"
    };
    static final int COUNT=NAMES.length;
    private AutomationParameters() { }
    static int min(int parameter) {
        check(parameter);
        return (parameter==MIN_HZ || parameter==MAX_HZ)?25:0;
    }
    static int max(int parameter) {
        check(parameter);
        switch(parameter){
            case THRESHOLD:return 95;
            case ATTACK:return 1000;
            case RELEASE:return 1500;
            case MIN_HZ:
            case MAX_HZ:return 10000;
            default:return 100;
        }
    }
    static void check(int parameter) {
        if(parameter<0||parameter>=COUNT) throw new IllegalArgumentException("Unknown automation parameter: "+parameter);
    }
    static int clamp(int parameter,int value){return Math.max(min(parameter),Math.min(max(parameter),value));}
    static AutomationTrack newTrack(int parameter){return new AutomationTrack(min(parameter),max(parameter));}
}
