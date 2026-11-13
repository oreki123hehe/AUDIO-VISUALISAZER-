package com.ranneki.visualizer;

/** One independently positioned spectrum element, including its attached cropped logo. */
final class VisualizerFrame {
    final int id,style,bars,color,accent,blend,mask;
    final float radius,height,thickness,glow,opacity,x,y,scale,rotation,maskRadius;
    final float[] bands,polygon;
    float[] right=new float[0],path=new float[0];
    final GpuSceneFrame.Sprite logo;
    VisualizerFrame(int id,int style,int bars,int color,int accent,int blend,int mask,
            float radius,float height,float thickness,float glow,float opacity,float x,float y,
            float scale,float rotation,float maskRadius,float[] bands,float[] polygon,GpuSceneFrame.Sprite logo){
        this.id=id;this.style=style;this.bars=bars;this.color=color;this.accent=accent;this.blend=blend;this.mask=mask;
        this.radius=radius;this.height=height;this.thickness=thickness;this.glow=glow;this.opacity=opacity;
        this.x=x;this.y=y;this.scale=scale;this.rotation=rotation;this.maskRadius=maskRadius;
        this.bands=bands;this.polygon=polygon;this.logo=logo;
    }
}
