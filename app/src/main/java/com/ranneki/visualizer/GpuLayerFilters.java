package com.ranneki.visualizer;

import android.opengl.GLES20;
import java.nio.*;

/** Two reusable transparent targets; filters run in user order before final layer blending. */
final class GpuLayerFilters implements AutoCloseable {
    private static final String VERTEX="attribute vec2 aPos;varying vec2 uv;void main(){uv=aPos*.5+.5;gl_Position=vec4(aPos,0,1);}";
    private static final String FRAGMENT="precision highp float;varying vec2 uv;uniform sampler2D uImage;"+
        "uniform int uType;uniform float uAmount,uTime,uDesignH;uniform vec2 uPixel;uniform vec4 uRect,uCurrent,uPrevious;"+
        "vec4 sampleAt(vec2 p){if(p.x<0.0||p.y<0.0||p.x>1.0||p.y>1.0)return vec4(0);return texture2D(uImage,p);}"+
        "vec2 rotate(vec2 p,float a){return vec2(p.x*cos(a)-p.y*sin(a),p.x*sin(a)+p.y*cos(a));}"+
        "vec4 blurred(vec2 p){vec2 d=uPixel*(uAmount*18.0);vec4 c=sampleAt(p)*.2;"+
        "c+=(sampleAt(p+vec2(d.x,0))+sampleAt(p-vec2(d.x,0))+sampleAt(p+vec2(0,d.y))+sampleAt(p-vec2(0,d.y)))*.1;"+
        "c+=(sampleAt(p+d)+sampleAt(p-d)+sampleAt(p+vec2(d.x,-d.y))+sampleAt(p+vec2(-d.x,d.y)))*.075;"+
        "c+=(sampleAt(p+vec2(d.x*2.0,0))+sampleAt(p-vec2(d.x*2.0,0))+sampleAt(p+vec2(0,d.y*2.0))+sampleAt(p-vec2(0,d.y*2.0)))*.025;return c;}"+
        "void main(){vec4 c=sampleAt(uv);"+
        "if(uType==0)c=blurred(uv);"+
        "else if(uType==1){vec4 b=blurred(uv)*uAmount;float a=min(1.0,c.a+b.a);c=vec4(min(vec3(a),c.rgb+b.rgb),a);}"+
        "else if(uType==2){vec2 d=vec2(uPixel.x*uAmount*9.0,0);vec4 r=sampleAt(uv+d),b=sampleAt(uv-d);c=vec4(r.r,c.g,b.b,max(c.a,max(r.a,b.a)));}"+
        "else if(uType==3){vec2 d=vec2(sin(uv.y*28.0+uTime*2.0),cos(uv.x*21.0+uTime))*uAmount*.025;c=sampleAt(uv+d);}"+
        "else if(uType==4){float y=dot(c.rgb,vec3(.2126,.7152,.0722));c.rgb=clamp(mix(vec3(y),c.rgb,uAmount*2.0),vec3(0),vec3(c.a));}"+
        "else if(uType==5)c.rgb=min(vec3(c.a),c.rgb*exp2((uAmount-.5)*4.0));"+
        "else if(uType==6){vec2 size=vec2(400.0,uDesignH),center=size*.5;vec2 p=(vec2(uv.x,1.0-uv.y)-uRect.xy)/uRect.zw*size-center;c=vec4(0);"+
        "for(int i=0;i<9;i++){float t=float(i)/8.0*uAmount;vec4 pose=mix(uCurrent,uPrevious,t);"+
        "vec2 local=rotate(p-pose.xy,-pose.w)/max(.02,pose.z);vec2 world=rotate(local*uCurrent.z,uCurrent.w)+uCurrent.xy+center;"+
        "vec2 q=uRect.xy+world/size*uRect.zw;c+=sampleAt(vec2(q.x,1.0-q.y))/9.0;}}gl_FragColor=c;}";
    private final int program;
    private final FloatBuffer quad;
    private final int[] textures=new int[2],buffers=new int[2];
    private int width,height,target;
    GpuLayerFilters(){program=GpuSceneRenderer.program(VERTEX,FRAGMENT);quad=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();quad.put(new float[]{-1,-1,1,-1,-1,1,1,1}).flip();}
    void start(int w,int h){
        int[] previous=new int[1];GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING,previous,0);target=previous[0];
        if(w!=width||h!=height){release();width=w;height=h;
            GLES20.glGenFramebuffers(2,buffers,0);
            for(int i=0;i<2;i++){textures[i]=GpuSceneRenderer.createTexture();GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,buffers[i]);GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,textures[i],0);
                if(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)!=GLES20.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Layer filter framebuffer unavailable");
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,buffers[0]);GLES20.glViewport(0,0,w,h);GLES20.glDisable(GLES20.GL_BLEND);GLES20.glClearColor(0,0,0,0);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }
    void finish(GpuSceneFrame.Item item,GpuSceneFrame scene){
        int current=0;
        if(item.motionBlur>0&&item.pose!=null&&item.previousPose!=null){pass(textures[current],buffers[1-current],6,item.motionBlur,item,scene,false);current=1-current;}
        for(int i=0;i<item.filters.length;i+=2){pass(textures[current],buffers[1-current],(int)item.filters[i],item.filters[i+1],item,scene,false);current=1-current;}
        pass(textures[current],target,-1,0,item,scene,true);
    }
    private void pass(int texture,int framebuffer,int type,float amount,GpuSceneFrame.Item item,GpuSceneFrame scene,boolean blend){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer);GLES20.glViewport(0,0,width,height);
        if(blend){GLES20.glEnable(GLES20.GL_BLEND);int mode=item.blend();
            GLES20.glBlendFunc(mode==3?GLES20.GL_DST_COLOR:GLES20.GL_ONE,mode==1?GLES20.GL_ONE:mode==2?GLES20.GL_ONE_MINUS_SRC_COLOR:GLES20.GL_ONE_MINUS_SRC_ALPHA);
        }else GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glUniform1i(loc("uImage"),0);GLES20.glUniform1i(loc("uType"),type);GLES20.glUniform1f(loc("uAmount"),amount);
        GLES20.glUniform1f(loc("uTime"),scene.effects.timestampMs/1000f);float h=scene.effects.portrait?711:225;
        GLES20.glUniform1f(loc("uDesignH"),h);float[] rect=GpuSceneRenderer.fittedRect(scene.effects.portrait,width,height);
        GLES20.glUniform4fv(loc("uRect"),1,rect,0);GLES20.glUniform2f(loc("uPixel"),rect[2]/400f,rect[3]/h);
        if(item.pose!=null){GLES20.glUniform4fv(loc("uCurrent"),1,item.pose.array(),0);GLES20.glUniform4fv(loc("uPrevious"),1,item.previousPose.array(),0);}
        int a=GLES20.glGetAttribLocation(program,"aPos");quad.position(0);GLES20.glEnableVertexAttribArray(a);GLES20.glVertexAttribPointer(a,2,GLES20.GL_FLOAT,false,0,quad);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(a);GLES20.glDisable(GLES20.GL_BLEND);
    }
    private int loc(String name){return GLES20.glGetUniformLocation(program,name);}
    private void release(){GLES20.glDeleteTextures(2,textures,0);GLES20.glDeleteFramebuffers(2,buffers,0);java.util.Arrays.fill(textures,0);java.util.Arrays.fill(buffers,0);}
    public void close(){release();GLES20.glDeleteProgram(program);}
}
