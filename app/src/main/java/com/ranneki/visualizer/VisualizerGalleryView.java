package com.ranneki.visualizer;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Catalog thumbnails use actual mesh geometry, with a fixed illustrative spectrum. */
final class VisualizerGalleryView extends View {
    private final int style;private final VisualizerMesh mesh=new VisualizerMesh();private final Paint paint=new Paint(3);private final Path path=new Path();
    VisualizerGalleryView(Context c,int style){super(c);this.style=style;setContentDescription(VisualizerMesh.STYLES[style]);}
    protected void onDraw(Canvas c){super.onDraw(c);float[] signal=new float[32];for(int i=0;i<signal.length;i++)signal[i]=.15f+.6f*(float)Math.pow(Math.sin(i*.42),2);
        mesh.build(style,signal,72,45,23,1.5f);c.drawColor(0xff0b1020);c.save();c.translate(getWidth()/2f,getHeight()/2f);float scale=Math.min(getWidth()/190f,getHeight()/150f);c.scale(scale,scale);
        paint.setShader(new LinearGradient(-70,-40,70,40,0xffb58aff,0xff45e7f0,Shader.TileMode.CLAMP));
        path.reset();for(int i=0;i<mesh.floats;i+=12){path.moveTo(mesh.vertices[i],mesh.vertices[i+1]);path.lineTo(mesh.vertices[i+4],mesh.vertices[i+5]);path.lineTo(mesh.vertices[i+8],mesh.vertices[i+9]);path.close();}c.drawPath(path,paint);c.restore();}
}
