package com.ranneki.visualizer;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicLong;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Transparent GLES2 overlay. The CPU View retains images/text/video, while GPU draws all 10 FX. */
final class GpuPreviewView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private final ReactiveView scene;
    private volatile GpuEffectFrame frame=GpuEffectFrame.EMPTY;
    private volatile boolean available;
    private volatile String rendererName="not initialized";
    private volatile String error="";
    private final AtomicLong frames=new AtomicLong();
    private final AtomicLong points=new AtomicLong();
    private final AtomicLong drawNs=new AtomicLong();
    private GpuParticleRenderer gpu;
    private int width,height;
    GpuPreviewView(Context context,ReactiveView source){
        super(context);this.scene=source;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8,8,8,8,0,0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        scene.setGpuFrameListener(this::submit);
    }
    private void submit(GpuEffectFrame next){
        frame=next;
        if(available)requestRender();
    }
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
        try {
            gpu=new GpuParticleRenderer();
            rendererName=GLES20.glGetString(GLES20.GL_VENDOR)+" / "+GLES20.glGetString(GLES20.GL_RENDERER);
            available=true;error="";
            new Handler(Looper.getMainLooper()).post(()->scene.setGpuPreviewActive(true));
        } catch(RuntimeException e){
            error=e.getMessage();available=false;
            new Handler(Looper.getMainLooper()).post(()->scene.setGpuPreviewActive(false));
        }
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h){
        width=w;height=h;GLES20.glViewport(0,0,w,h);
    }
    @Override public void onDrawFrame(GL10 gl){
        GLES20.glViewport(0,0,width,height);
        GLES20.glClearColor(0,0,0,0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if(!available||gpu==null)return;
        try {
            long started=System.nanoTime();
            int rendered=gpu.draw(frame,width,height);
            drawNs.addAndGet(System.nanoTime()-started);
            frames.incrementAndGet();points.addAndGet(rendered);
        } catch(RuntimeException e){
            error=e.getMessage();available=false;
            new Handler(Looper.getMainLooper()).post(()->scene.setGpuPreviewActive(false));
        }
    }
    String report(){
        long n=frames.get();
        return "GLES GPU particles: "+(available?"active":"inactive")+" | "+rendererName+
                "\nGPU overlay frames: "+n+" | avg GLES submit: "+
                (n==0?0:Math.round(drawNs.get()/1e6/n))+" ms | points: "+points.get()+
                (error.isEmpty()?"":"\nGPU error: "+error)+
                "\nTiming measures CPU-side GLES submission, not hardware shader execution.";
    }
    void detach(){scene.setGpuFrameListener(null);scene.setGpuPreviewActive(false);}
}
