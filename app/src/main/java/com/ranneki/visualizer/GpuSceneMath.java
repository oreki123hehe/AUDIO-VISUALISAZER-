package com.ranneki.visualizer;

/** Aspect/crop calculations shared by the GPU preview and encoder. Pure Java for CI tests. */
public final class GpuSceneMath {
    private GpuSceneMath() {}
    public static float[] fittedRect(boolean portrait,int width,int height){
        if(width<=0||height<=0)throw new IllegalArgumentException("Invalid surface dimensions");
        float aspect=portrait?9f/16f:16f/9f;
        float fittedW=Math.min(width,height*aspect),fittedH=fittedW/aspect;
        return new float[]{(width-fittedW)*.5f/width,(height-fittedH)*.5f/height,
                fittedW/width,fittedH/height};
    }
    /** Crop coordinates use top-left pixel origin (as used by Android Bitmap/GLUtils). */
    public static float[] centerCrop(int imageWidth,int imageHeight,boolean portrait){
        if(imageWidth<=0||imageHeight<=0)throw new IllegalArgumentException("Invalid artwork dimensions");
        float source=(float)imageWidth/imageHeight,target=portrait?9f/16f:16f/9f;
        float u0=0,v0=0,u1=1,v1=1;
        if(source>target){float fraction=target/source;u0=(1f-fraction)*.5f;u1=u0+fraction;}
        else{float fraction=source/target;v0=(1f-fraction)*.5f;v1=v0+fraction;}
        return new float[]{u0,v0,u1,v1};
    }
}
