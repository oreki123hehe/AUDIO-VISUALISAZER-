package com.ranneki.visualizer;
public final class GpuEffectFrameTest {
    public static void main(String[] args){
        GpuEffectFrame.Layer layer=new GpuEffectFrame.Layer(1,3,2,4,300,.8f,.75f,1.25f,2f,
                .12f,-.05f,1.1f,20f,45f,.6f,new float[]{0,0,1,0,1,1,0,1});
        GpuEffectFrame frame=new GpuEffectFrame(4000,true,new GpuEffectFrame.Layer[]{layer});
        assert frame.layers.length==1&&frame.layers[0].particleCount==300;
        assert frame.layers[0].polygon.length==8;
        float[] points={0,0,1,0,1,1};
        GpuEffectFrame.Layer copy=new GpuEffectFrame.Layer(0,0,0,4,4,1,1,1,1,0,0,1,0,0,1,points);
        points[0]=999;
        assert copy.polygon[0]==0;
        boolean failed=false;
        try {new GpuEffectFrame.Layer(0,10,0,0,2,1,1,1,1,0,0,1,0,0,1,null);} catch(IllegalArgumentException ok){failed=true;}
        assert failed;
        System.out.println("GpuEffectFrameTest PASS");
    }
}
