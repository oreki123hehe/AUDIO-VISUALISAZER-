package com.ranneki.visualizer;
public class PocoBenchmarkPlanTest {
    public static void main(String[] args){
        assert PocoBenchmarkPlan.CASES.length==3;
        for(PocoBenchmarkPlan.Case p:PocoBenchmarkPlan.CASES){
            assert p.profile().width==p.width;
            assert p.profile().height==p.height;
            assert p.profile().fps==p.fps;
            assert p.profile().rasterPercent==p.rasterPercent;
        }
        assert PocoBenchmarkPlan.CASES[2].fps==60;
        System.out.println("PocoBenchmarkPlanTest PASS");
    }
}
