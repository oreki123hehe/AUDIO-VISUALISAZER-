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

/** GLES preview of the complete scene and its shared postprocessing shader. */
final class GpuPreviewView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private final ReactiveView scene;
    private volatile GpuSceneFrame frame;
    private volatile boolean available;
    private volatile String rendererName="not initialized";
    private volatile String error="";
    private final AtomicLong frames=new AtomicLong();
    private final AtomicLong points=new AtomicLong();
    private final AtomicLong drawNs=new AtomicLong();
    private GpuParticleRenderer gpu;
    private GpuSceneRenderer compositor;
    private GpuPreviewPostFx postFx;
    private int width,height;
    private String lastShader=ShaderSource.CLASSIC;
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
    private void submit(GpuSceneFrame next){
        frame=next;
        if(available)requestRender();
    }
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
        try {
            gpu=new GpuParticleRenderer();
            compositor=new GpuSceneRenderer();
            postFx=new GpuPreviewPostFx(scene.getShaderSource());
            lastShader=scene.getShaderSource();
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
        GpuSceneFrame snapshot=frame;
        if(!available||gpu==null||snapshot==null)return;
        try {
            long started=System.nanoTime();
            compositor.begin(snapshot,width,height,true);
            int rendered=compositor.composite(snapshot,gpu,width,height,false,()->scene.postInvalidate());
            compositor.spectrum(snapshot,width,height);
            compositor.end();
            if(!snapshot.fragmentShader.equals(lastShader)){
                try{postFx.update(snapshot.fragmentShader);lastShader=snapshot.fragmentShader;}
                catch(RuntimeException err){error="Preview shader: "+err.getMessage();}
            }
            postFx.draw(compositor.texture(),width,height,snapshot);
            drawNs.addAndGet(System.nanoTime()-started);
            frames.incrementAndGet();points.addAndGet(rendered);
        } catch(RuntimeException e){
            error=e.getMessage();available=false;
            new Handler(Looper.getMainLooper()).post(()->scene.setGpuPreviewActive(false));
        }
    }
    void resetMetrics(){frames.set(0);drawNs.set(0);points.set(0);}
    String report(){
        long n=frames.get();
        return "GLES GPU particles: "+(available?"active":"inactive")+" | "+rendererName+
                "\nGPU overlay frames: "+n+" | avg GLES submit: "+
                (n==0?0:Math.round(drawNs.get()/1e6/n))+" ms | points: "+points.get()+
                (error.isEmpty()?"":"\nGPU error: "+error)+
                "\nGPU compositor: "+(compositor==null?"not initialized":compositor.stats())+
                "\nTiming measures CPU-side GLES submission, not hardware shader execution.";
    }
    void detach(){
        scene.setGpuFrameListener(null);scene.setGpuPreviewActive(false);
        try{queueEvent(()->{
            if(postFx!=null){postFx.close();postFx=null;}
            if(compositor!=null){compositor.close();compositor=null;}
            if(gpu!=null){gpu.close();gpu=null;}
        });}catch(IllegalStateException ignored){}
    }
}
