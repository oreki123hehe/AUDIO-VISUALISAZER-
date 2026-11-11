package com.ranneki.visualizer;
public final class TimelineMathTest {
    private static void eq(boolean result,String name){if(!result)throw new AssertionError(name);}
    public static void main(String[] args){
        eq(TimelineMath.positionAt(.5f,60000)==30000,"center");
        eq(TimelineMath.positionAt(-4,60000)==0,"left clamp");
        eq(TimelineMath.positionAt(4,60000)==60000,"right clamp");
        eq(TimelineMath.positionAt(Float.NaN,60000)==0,"NaN clamp");
        eq(Math.abs(TimelineMath.fraction(15000,60000)-.25f)<.001f,"quarter");
        eq(TimelineMath.fraction(400,0)==0f,"empty duration");
        eq(Math.abs(TimelineMath.spanFraction(15000,Long.MAX_VALUE,60000)-.75f)<.001,"open end");
        eq(!TimelineMath.intersects(90000,Long.MAX_VALUE,60000),"out of view");
        eq(!TimelineMath.intersects(22000,12000,60000),"invalid range");
        System.out.println("PASS TimelineMathTest");
    }
}
