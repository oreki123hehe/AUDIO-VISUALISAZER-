package com.ranneki.visualizer;

import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Owns the codec input Surface; uploads Canvas frames to GLES without a server. */
final class CanvasEncoderSurface implements AutoCloseable {
    private android.opengl.EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private android.opengl.EGLContext context=EGL14.EGL_NO_CONTEXT;
    private android.opengl.EGLSurface window=EGL14.EGL_NO_SURFACE;
    private final Surface surface;
    private int program,texture,position,texCoord,pulseUniform,chromaticUniform,vignetteUniform,textureUniform;
    private FloatBuffer points,uv;
    private int width,height;
    private int timeUniform,kickUniform,bassUniform,snareUniform,trebleUniform,resolutionUniform;
    private boolean textureAllocated=false;
    CanvasEncoderSurface(Surface codecSurface,int width,int height,String fragmentSource) {
        this.surface=codecSurface;this.width=width;this.height=height;
        try {
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(display==EGL14.EGL_NO_DISPLAY)throw new IllegalStateException("EGL display unavailable");
        int[] version=new int[2];
        if(!EGL14.eglInitialize(display,version,0,version,1))throw new IllegalStateException("EGL init failed");
        int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
            EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,4,0x3142,1,EGL14.EGL_NONE};
        android.opengl.EGLConfig[] configs=new android.opengl.EGLConfig[1];int[] count=new int[1];
        if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)||count[0]<1)
            throw new IllegalStateException("Encoder EGL config unavailable");
        int[] ctxAttrs={EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE};
        context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,ctxAttrs,0);
        if(context==EGL14.EGL_NO_CONTEXT)throw new IllegalStateException("EGL context unavailable");
        window=EGL14.eglCreateWindowSurface(display,configs[0],surface,new int[]{EGL14.EGL_NONE},0);
        if(window==EGL14.EGL_NO_SURFACE)throw new IllegalStateException("Codec EGL window unavailable");
        if(!EGL14.eglMakeCurrent(display,window,window,context))throw new IllegalStateException("EGL makeCurrent failed");
        String vertex="attribute vec4 aPosition; attribute vec2 aTex; varying vec2 vTex;"+
                "void main(){ gl_Position=aPosition; vTex=aTex; }";
        String fragment=ShaderSource.validate(fragmentSource);
        int vs=shader(GLES20.GL_VERTEX_SHADER,vertex),fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment);
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);
        GLES20.glLinkProgram(program);int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
        GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
        if(ok[0]==0)throw new IllegalStateException("GPU shader link failed: "+GLES20.glGetProgramInfoLog(program));
        position=GLES20.glGetAttribLocation(program,"aPosition");
        texCoord=GLES20.glGetAttribLocation(program,"aTex");
        pulseUniform=GLES20.glGetUniformLocation(program,"uPulse");
        chromaticUniform=GLES20.glGetUniformLocation(program,"uChromatic");
        vignetteUniform=GLES20.glGetUniformLocation(program,"uVignette");
        textureUniform=GLES20.glGetUniformLocation(program,"uTexture");
        timeUniform=GLES20.glGetUniformLocation(program,"uTime");
        kickUniform=GLES20.glGetUniformLocation(program,"uKick");
        bassUniform=GLES20.glGetUniformLocation(program,"uBass");
        snareUniform=GLES20.glGetUniformLocation(program,"uSnare");
        trebleUniform=GLES20.glGetUniformLocation(program,"uTreble");
        resolutionUniform=GLES20.glGetUniformLocation(program,"uResolution");
        points=floats(new float[]{-1,-1,1,-1,-1,1,1,1});
        // GLUtils uploads Android Bitmap with its top row at the texture's bottom.
        uv=floats(new float[]{0,1,1,1,0,0,1,0});
        int[] textures=new int[1];GLES20.glGenTextures(1,textures,0);texture=textures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        } catch (RuntimeException error) {
            // Invalid user GLSL must never leave an EGL context or codec surface behind.
            try {close();} catch(Exception ignored) { }
            throw error;
        }
    }
    private static FloatBuffer floats(float[] a) {
        FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(a).position(0);return b;
    }
    private static int shader(int kind,String src) {
        int shader=GLES20.glCreateShader(kind);GLES20.glShaderSource(shader,src);GLES20.glCompileShader(shader);
        int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) {String why=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);
            throw new IllegalStateException("GPU GLSL compile failed: "+why);}
        return shader;
    }
    void submit(Bitmap frame,long ptsNs,float pulse,float chromatic,float vignette,
            float timeSeconds,AudioAnalyzer.Features audio) {
        GLES20.glViewport(0,0,width,height);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        if(!textureAllocated) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,frame,0);
            textureAllocated=true;
        } else GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,frame);
        GLES20.glUseProgram(program);
        GLES20.glUniform1f(pulseUniform,Math.max(0f,Math.min(1f,pulse)));
        GLES20.glUniform1f(chromaticUniform,Math.max(0f,Math.min(1f,chromatic)));
        GLES20.glUniform1f(vignetteUniform,Math.max(0f,Math.min(1f,vignette)));
        GLES20.glUniform1f(timeUniform,timeSeconds);
        GLES20.glUniform1f(kickUniform,audio.kick);
        GLES20.glUniform1f(bassUniform,audio.bass);
        GLES20.glUniform1f(snareUniform,audio.snare);
        GLES20.glUniform1f(trebleUniform,audio.highs);
        GLES20.glUniform2f(resolutionUniform,width,height);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glEnableVertexAttribArray(texCoord);
        points.position(0);uv.position(0);
        GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,0,points);
        GLES20.glVertexAttribPointer(texCoord,2,GLES20.GL_FLOAT,false,0,uv);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1i(textureUniform,0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(position);
        GLES20.glDisableVertexAttribArray(texCoord);
        int error=GLES20.glGetError();
        if(error!=GLES20.GL_NO_ERROR)throw new IllegalStateException("OpenGL error "+error);
        EGLExt.eglPresentationTimeANDROID(display,window,ptsNs);
        if(!EGL14.eglSwapBuffers(display,window))throw new IllegalStateException("Encoder surface swap failed");
    }
    @Override public void close() {
        if(display!=EGL14.EGL_NO_DISPLAY) {
            if(context!=EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(display,window,window,context);
                if(texture!=0)GLES20.glDeleteTextures(1,new int[]{texture},0);
                if(program!=0)GLES20.glDeleteProgram(program);
            }
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            EGL14.eglReleaseThread();EGL14.eglTerminate(display);
            display=EGL14.EGL_NO_DISPLAY;
        }
        surface.release();
    }
}
