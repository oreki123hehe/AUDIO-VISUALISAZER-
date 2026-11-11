package com.ranneki.visualizer;

/** Immutable CPU -> GLES frame description; no Android dependency.
 * Particle positions/shapes are evaluated by the vertex/fragment shaders.
 * Timeline, parameter curves, frequency routing and envelope values are evaluated once in ReactiveView.
 */
public final class GpuEffectFrame {
    public static final GpuEffectFrame EMPTY = new GpuEffectFrame(0L,true,new Layer[0]);
    public final long timestampMs;
    public final boolean portrait;
    public final Layer[] layers;
    public GpuEffectFrame(long timestampMs, boolean portrait, Layer[] layers) {
        if (timestampMs < 0 || layers == null || layers.length > 48)
            throw new IllegalArgumentException("Invalid GPU frame");
        this.timestampMs=timestampMs;this.portrait=portrait;this.layers=layers.clone();
        for(Layer layer : this.layers) if(layer==null)throw new IllegalArgumentException("Null GPU layer");
    }
    public static final class Layer {
        public final int id,type,blend,mask,particleCount;
        public final float power,opacity,size,speed,shiftX,shiftY,scale,rotation,direction,maskRadius;
        public final float[] polygon;
        public Layer(int id,int type,int blend,int mask,int particleCount,
                float power,float opacity,float size,float speed,
                float shiftX,float shiftY,float scale,float rotation,float direction,
                float maskRadius,float[] polygon) {
            if(id<0||id>=48||type<0||type>=10 || particleCount<0||particleCount>1024
                    ||blend<0||blend>3||mask<0||mask>4)
                throw new IllegalArgumentException("Invalid GPU layer metadata");
            for(float value:new float[]{power,opacity,size,speed,shiftX,shiftY,scale,rotation,direction,maskRadius})
                if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite GPU parameter");
            if(polygon!=null&&(polygon.length<6||polygon.length>64||polygon.length%2!=0))
                throw new IllegalArgumentException("Invalid mask polygon");
            this.id=id;this.type=type;this.blend=blend;this.mask=mask;this.particleCount=particleCount;
            this.power=Math.max(0f,Math.min(1f,power));
            this.opacity=Math.max(0f,Math.min(1f,opacity));
            this.size=Math.max(.1f,Math.min(8f,size));
            this.speed=Math.max(.05f,Math.min(16f,speed));
            this.shiftX=shiftX;this.shiftY=shiftY;
            this.scale=Math.max(.05f,Math.min(4f,scale));
            this.rotation=rotation;this.direction=direction;this.maskRadius=Math.max(0f,Math.min(1f,maskRadius));
            this.polygon=polygon==null?new float[0]:polygon.clone();
        }
    }
}
