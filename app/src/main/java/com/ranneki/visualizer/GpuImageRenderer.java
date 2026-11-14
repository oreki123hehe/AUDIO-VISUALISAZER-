package com.ranneki.visualizer;

import android.graphics.Bitmap;
import android.opengl.*;
import java.nio.ByteBuffer;

/** PNG uses the same scene compositor and shader as preview/MP4, in a private EGL context. */
final class GpuImageRenderer {
    static Bitmap render(ReactiveView scene,int width,int height,long timeMs){
        EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        EGLContext context=EGL14.EGL_NO_CONTEXT;EGLSurface surface=EGL14.EGL_NO_SURFACE;
        GpuSceneRenderer compositor=null;GpuParticleRenderer particles=null;GpuPreviewPostFx post=null;
        try{
            int[] version=new int[2];
            if(!EGL14.eglInitialize(display,version,0,version,1))throw new IllegalStateException("PNG EGL unavailable");
            int[] attributes={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                    EGL14.EGL_RENDERABLE_TYPE,4,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_NONE};
            EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
            if(!EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0)||count[0]==0)
                throw new IllegalStateException("PNG EGL configuration unavailable");
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,configs[0],
                    new int[]{EGL14.EGL_WIDTH,width,EGL14.EGL_HEIGHT,height,EGL14.EGL_NONE},0);
            if(context==EGL14.EGL_NO_CONTEXT||surface==EGL14.EGL_NO_SURFACE||!EGL14.eglMakeCurrent(display,surface,surface,context))
                throw new IllegalStateException("PNG EGL context unavailable");
            compositor=new GpuSceneRenderer();particles=new GpuParticleRenderer();post=new GpuPreviewPostFx(scene.getShaderSource());
            scene.resetExport();GpuSceneFrame frame=scene.renderGpuOnlyExportFrame(timeMs);
            compositor.begin(frame,width,height,true);compositor.composite(frame,particles,width,height,true,null);compositor.end();
            post.draw(compositor.texture(),width,height,frame);
            ByteBuffer pixels=ByteBuffer.allocateDirect(width*height*4);
            GLES20.glReadPixels(0,0,width,height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
            if(GLES20.glGetError()!=GLES20.GL_NO_ERROR)throw new IllegalStateException("PNG GPU readback failed");
            int[] colors=new int[width*height];
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                int i=(y*width+x)*4;
                colors[(height-1-y)*width+x]=((pixels.get(i+3)&255)<<24)|((pixels.get(i)&255)<<16)|
                        ((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255);
            }
            return Bitmap.createBitmap(colors,width,height,Bitmap.Config.ARGB_8888);
        }finally{
            if(context!=EGL14.EGL_NO_CONTEXT){
                if(post!=null)post.close();if(compositor!=null)compositor.close();if(particles!=null)particles.close();
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);
                EGL14.eglDestroyContext(display,context);
            }
            EGL14.eglReleaseThread();EGL14.eglTerminate(display);
        }
    }
}
