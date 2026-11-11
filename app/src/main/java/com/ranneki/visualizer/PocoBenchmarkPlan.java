package com.ranneki.visualizer;

/** Repeatable device-side render workload, never a simulated Snapdragon performance result. */
public final class PocoBenchmarkPlan {
    public static final class Case {
        public final String label;
        public final int width,height,fps,rasterPercent;
        Case(String label,int width,int height,int fps,int rasterPercent){
            this.label=label;this.width=width;this.height=height;
            this.fps=fps;this.rasterPercent=rasterPercent;
        }
        public ExportProfile profile(){return new ExportProfile(width,height,fps,rasterPercent,20,14);}
    }
    public static final Case[] CASES={
        new Case("720p30-quality",720,1280,30,100),
        new Case("1080p30-quality",1080,1920,30,100),
        new Case("1080p60-balanced",1080,1920,60,75)
    };
    private PocoBenchmarkPlan(){}
}
