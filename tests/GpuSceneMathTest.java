package com.ranneki.visualizer;

public final class GpuSceneMathTest {
    private static void eq(float a,float b){if(Math.abs(a-b)>0.0001f)throw new AssertionError(a+" != "+b);}
    public static void main(String[]args){
        float[] same=GpuSceneMath.fittedRect(true,720,1280);
        eq(same[0],0f);eq(same[1],0f);eq(same[2],1f);eq(same[3],1f);
        float[] wide=GpuSceneMath.fittedRect(true,1920,1080);
        if(!(wide[0]>.30f&&wide[2]<.33f))throw new AssertionError("Portrait letterbox missing");
        float[] art=GpuSceneMath.centerCrop(1600,900,true);
        if(!(art[0]>.30f&&art[2]<.70f))throw new AssertionError("Artwork center crop wrong");
        float[] tall=GpuSceneMath.centerCrop(900,1600,false);
        if(!(tall[1]>.30f&&tall[3]<.70f))throw new AssertionError("Landscape center crop wrong");
        try{GpuSceneMath.centerCrop(0,200,true);throw new AssertionError("Must reject 0 width");}
        catch(IllegalArgumentException expected){}
        System.out.println("GpuSceneMathTest PASS");
    }
}
