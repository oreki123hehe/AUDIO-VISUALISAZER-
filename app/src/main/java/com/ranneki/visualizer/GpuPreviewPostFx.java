package com.ranneki.visualizer;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Same configurable GLSL postprocess used during export, applied to preview GLES FBO.
 * Owner: the GLSurfaceView render thread. No Canvas or CPU readback is used. */
final class GpuPreviewPostFx implements AutoCloseable {
    private static final String VERTEX="attribute vec4 aPosition;attribute vec2 aTex;"+
            "varying vec2 vTex;void main(){gl_Position=aPosition;vTex=aTex;}";
    private final FloatBuffer points,uv;
    private int program,position,texCoord,texture,time,kick,bass,snare,treble,pulse,chromatic,vignette,resolution;
    private String current="";
    private boolean closed;
    GpuPreviewPostFx(String source){
        points=floats(new float[]{-1,-1,1,-1,-1,1,1,1});
        uv=floats(new float[]{0,0,1,0,0,1,1,1});
        update(source);
    }
    private static FloatBuffer floats(float[] values){
        FloatBuffer f=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        f.put(values).position(0);return f;
    }
    private static int shader(int kind,String source){
        int id=GLES20.glCreateShader(kind);
        GLES20.glShaderSource(id,source);GLES20.glCompileShader(id);
        int[] ok=new int[1];GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0){String why=GLES20.glGetShaderInfoLog(id);GLES20.glDeleteShader(id);
            throw new IllegalStateException("Preview GPU shader compile failed: "+why);}
        return id;
    }
    void update(String code){
        String validated=ShaderSource.validate(code);
        if(validated.equals(current))return;
        int v=0,f=0,p=0;
        try{
            v=shader(GLES20.GL_VERTEX_SHADER,VERTEX);
            f=shader(GLES20.GL_FRAGMENT_SHADER,validated);
            p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);
            GLES20.glLinkProgram(p);
            int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);
            if(ok[0]==0)throw new IllegalStateException("Preview shader link failed: "+GLES20.glGetProgramInfoLog(p));
            if(program!=0)GLES20.glDeleteProgram(program);
            program=p;p=0;current=validated;
            position=GLES20.glGetAttribLocation(program,"aPosition");
            texCoord=GLES20.glGetAttribLocation(program,"aTex");
            texture=GLES20.glGetUniformLocation(program,"uTexture");
            time=GLES20.glGetUniformLocation(program,"uTime");
            kick=GLES20.glGetUniformLocation(program,"uKick");
            bass=GLES20.glGetUniformLocation(program,"uBass");
            snare=GLES20.glGetUniformLocation(program,"uSnare");
            treble=GLES20.glGetUniformLocation(program,"uTreble");
            pulse=GLES20.glGetUniformLocation(program,"uPulse");
            chromatic=GLES20.glGetUniformLocation(program,"uChromatic");
            vignette=GLES20.glGetUniformLocation(program,"uVignette");
            resolution=GLES20.glGetUniformLocation(program,"uResolution");
        }finally{
            if(v!=0)GLES20.glDeleteShader(v);
            if(f!=0)GLES20.glDeleteShader(f);
            if(p!=0)GLES20.glDeleteProgram(p);
        }
    }
    void draw(int sceneTexture,int width,int height,GpuSceneFrame frame){
        if(closed)throw new IllegalStateException("Preview postprocess already closed");
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
        GLES20.glViewport(0,0,width,height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(program);
        GLES20.glUniform1f(time,frame.effects.timestampMs/1000f);
        GLES20.glUniform1f(kick,frame.kick);
        GLES20.glUniform1f(bass,frame.bass);
        GLES20.glUniform1f(snare,frame.snare);
        GLES20.glUniform1f(treble,frame.treble);
        GLES20.glUniform1f(pulse,Math.min(1f,frame.kick*.75f+frame.bass*.25f));
        GLES20.glUniform1f(chromatic,.35f);
        GLES20.glUniform1f(vignette,.22f);
        GLES20.glUniform2f(resolution,width,height);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glEnableVertexAttribArray(texCoord);
        points.position(0);uv.position(0);
        GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,0,points);
        GLES20.glVertexAttribPointer(texCoord,2,GLES20.GL_FLOAT,false,0,uv);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,sceneTexture);
        GLES20.glUniform1i(texture,0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(position);
        GLES20.glDisableVertexAttribArray(texCoord);
        int err=GLES20.glGetError();
        if(err!=GLES20.GL_NO_ERROR)throw new IllegalStateException("Preview postprocess GL error "+err);
    }
    @Override public void close(){if(!closed){closed=true;if(program!=0)GLES20.glDeleteProgram(program);program=0;}}
}
