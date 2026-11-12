package com.ranneki.visualizer;

import android.graphics.Bitmap;

/** Immutable per-frame GPU scene input. Static backdrop texture is cached in the GL context.
 * No full-screen CPU Canvas or Bitmap is needed when fastPath is true. */
final class GpuSceneFrame {
    final GpuEffectFrame effects;
    final Bitmap backdrop;
    final float[] spectrum;
    final boolean fastPath, spectrumEnabled;
    final int spectrumScale;
    final float kick, bass, snare,treble,shakeX, shakeY;
    final String fragmentShader;
    final Item[] drawOrder;
    float chromatic=.35f,vignette=.22f;
    GpuSceneFrame(GpuEffectFrame effects, Bitmap backdrop, float[] spectrum,
            boolean fastPath, boolean spectrumEnabled, int spectrumScale, float kick, float bass,
            float snare,float treble,float shakeX,float shakeY,String fragmentShader,Item[] drawOrder) {
        this.effects=effects;
        this.backdrop=backdrop;
        this.spectrum=spectrum.clone();
        this.fastPath=fastPath;
        this.spectrumEnabled=spectrumEnabled;
        this.spectrumScale=spectrumScale;
        this.kick=kick;
        this.bass=bass;
        this.shakeX=shakeX;this.shakeY=shakeY;
        this.snare=snare;this.treble=treble;this.fragmentShader=fragmentShader;
        this.drawOrder=drawOrder.clone();
    }
    static final class Item {
        final GpuEffectFrame.Layer effect;
        final Sprite sprite;
        final VisualizerFrame visualizer;
        Item(GpuEffectFrame.Layer layer){effect=layer;sprite=null;visualizer=null;}
        Item(Sprite layer){effect=null;sprite=layer;visualizer=null;}
        Item(VisualizerFrame layer){effect=null;sprite=null;visualizer=layer;}
    }
    static final class Sprite {
        final int id,type,blend,mask,color;
        final Bitmap image;
        final VideoFrameLayer video;
        final String text;
        final long timestampMs,startMs;
        final float width,height,opacity,shiftX,shiftY,scale,rotation,maskRadius;
        final float[] polygon;
        float[] crop={0,0,1,1};
        int localShape=0;
        Sprite(int id,int type,Bitmap image,VideoFrameLayer video,String text,int color,
                long timestampMs,long startMs,float width,float height,float opacity,
                float shiftX,float shiftY,float scale,float rotation,int blend,int mask,
                float maskRadius,float[] polygon){
            this.id=id;this.type=type;this.image=image;this.video=video;this.text=text;
            this.color=color;this.timestampMs=timestampMs;this.startMs=startMs;
            this.width=width;this.height=height;this.opacity=opacity;
            this.shiftX=shiftX;this.shiftY=shiftY;this.scale=scale;this.rotation=rotation;
            this.blend=blend;this.mask=mask;this.maskRadius=maskRadius;
            this.polygon=polygon==null?new float[0]:polygon.clone();
        }
    }
}
