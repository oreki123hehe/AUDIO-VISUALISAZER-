package com.ranneki.visualizer;

import android.opengl.GLES20;
import java.nio.*;

/** Three reusable targets; small specialized shader programs are cached per effect. */
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
        "else if(uType==100){vec2 size=vec2(400.0,uDesignH),center=size*.5;vec2 p=(vec2(uv.x,1.0-uv.y)-uRect.xy)/uRect.zw*size-center;c=vec4(0);"+
        "for(int i=0;i<9;i++){float t=float(i)/8.0*uAmount;vec4 pose=mix(uCurrent,uPrevious,t);"+
        "vec2 local=rotate(p-pose.xy,-pose.w)/max(.02,pose.z);vec2 world=rotate(local*uCurrent.z,uCurrent.w)+uCurrent.xy+center;"+
        "vec2 q=uRect.xy+world/size*uRect.zw;c+=sampleAt(vec2(q.x,1.0-q.y))/9.0;}}"+
        "else if(uType==6){c=vec4(0);for(int i=0;i<13;i++)c+=sampleAt(uv+vec2(float(i)-6.0,0)*uPixel*uAmount*4.0)/13.0;}"+
        "else if(uType==7){c=vec4(0);for(int i=0;i<13;i++)c+=sampleAt(.5+(uv-.5)*(1.0-float(i)*uAmount*.008))/13.0;}"+
        "else if(uType==8){c=vec4(0);for(int i=0;i<13;i++)c+=sampleAt(.5+rotate(uv-.5,(float(i)-6.0)*uAmount*.015))/13.0;}"+
        "else if(uType==9){vec2 p=uv-.5;float d=length(p);c=sampleAt(.5+rotate(p,uAmount*5.0*max(0.0,1.0-d*2.0)));}"+
        "else if(uType==10){float cells=max(6.0,400.0/(1.0+uAmount*45.0));c=sampleAt((floor(uv*cells)+.5)/cells);}"+
        "else if(uType==11)c=sampleAt(mix(uv,vec2(abs(uv.x-.5)*2.0,uv.y),uAmount));"+
        "else if(uType==12){vec2 p=uv-.5;float a=atan(p.y,p.x),n=2.0+floor(uAmount*10.0),sector=6.2831853/n;a=abs(mod(a,sector)-sector*.5);c=sampleAt(.5+length(p)*vec2(cos(a),sin(a)));}"+
        "else if(uType==13){float levels=max(2.0,32.0-uAmount*30.0);c.rgb=floor(c.rgb/max(.0001,c.a)*levels+.5)/levels*c.a;}"+
        "else if(uType==14)c.rgb=mix(c.rgb,vec3(c.a)-c.rgb,uAmount);"+
        "else if(uType==15){vec3 s=vec3(dot(c.rgb,vec3(.393,.769,.189)),dot(c.rgb,vec3(.349,.686,.168)),dot(c.rgb,vec3(.272,.534,.131)));c.rgb=min(vec3(c.a),mix(c.rgb,s,uAmount));}"+
        "else if(uType==16)c.rgb=clamp((c.rgb-c.a*.5)*exp2((uAmount-.5)*4.0)+c.a*.5,vec3(0),vec3(c.a));"+
        "else if(uType==17){vec3 k=normalize(vec3(1));float a=(uAmount-.5)*6.2831853;c.rgb=clamp(c.rgb*cos(a)+cross(k,c.rgb)*sin(a)+k*dot(k,c.rgb)*(1.0-cos(a)),vec3(0),vec3(c.a));}"+
        "else if(uType==18){vec4 b=(sampleAt(uv+vec2(uPixel.x,0))+sampleAt(uv-vec2(uPixel.x,0))+sampleAt(uv+vec2(0,uPixel.y))+sampleAt(uv-vec2(0,uPixel.y)))*.25;c.rgb=clamp(c.rgb+(c.rgb-b.rgb)*uAmount*2.0,vec3(0),vec3(c.a));}"+
        "else if(uType==19)c.rgb*=1.0-uAmount*.45*(.5+.5*sin(uv.y/uPixel.y*3.14159265));"+
        "else if(uType==20){vec2 p=uv-.5;c=sampleAt(.5+p*(1.0+uAmount*dot(p,p)*1.5));}"+
        "else if(uType==21){vec2 p=uv;float l=dot(c.rgb,vec3(.299,.587,.114));p+=vec2(sin(uv.y*50.0+uTime),cos(uv.x*36.0-uTime))*l*uAmount*.08;c=sampleAt(p);}"+
        "else if(uType==101)c*=uAmount;"+
        "else if(uType==102){vec4 b=blurred(uv);vec3 color=c.a>.99?c.rgb/max(.0001,c.a):b.rgb/max(.0001,b.a);c=vec4(color*b.a,b.a);}"+
        "gl_FragColor=c;}";
    private int program;
    private final int[] programs=new int[104];
    private final FloatBuffer quad;
    private final int[] textures=new int[3],buffers=new int[3];
    private int width,height,target;private boolean temporal;
    GpuLayerFilters(){quad=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();quad.put(new float[]{-1,-1,1,-1,-1,1,1,1}).flip();}
    void start(int w,int h){
        int[] previous=new int[1];GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING,previous,0);target=previous[0];
        if(w!=width||h!=height){release();width=w;height=h;
            GLES20.glGenFramebuffers(3,buffers,0);
            for(int i=0;i<3;i++){textures[i]=GpuSceneRenderer.createTexture();GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,buffers[i]);GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,textures[i],0);
                if(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)!=GLES20.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Layer filter framebuffer unavailable");
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,buffers[0]);GLES20.glViewport(0,0,w,h);GLES20.glDisable(GLES20.GL_BLEND);GLES20.glClearColor(0,0,0,0);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }
    void beginTemporal(){temporal=true;GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,buffers[2]);GLES20.glClearColor(0,0,0,0);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);}
    void sample(){GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,buffers[0]);GLES20.glDisable(GLES20.GL_BLEND);GLES20.glClearColor(0,0,0,0);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);}
    void accumulate(GpuSceneFrame.Item item,GpuSceneFrame scene,float weight){pass(textures[0],buffers[2],101,weight,item,scene,true);}
    void finish(GpuSceneFrame.Item item,GpuSceneFrame scene){
        int current=temporal?2:0;
        if(!temporal&&item.motionBlur>0&&item.pose!=null&&item.previousPose!=null){pass(textures[current],buffers[1-current],100,item.motionBlur,item,scene,false);current=1-current;}
        for(int i=0;i<item.filters.length;i+=2){int next=current==0?1:0;pass(textures[current],buffers[next],(int)item.filters[i],item.filters[i+1],item,scene,false);current=next;}
        if(item.feather>0){int next=current==0?1:0;pass(textures[current],buffers[next],102,item.feather/18f,item,scene,false);current=next;}
        pass(textures[current],target,-1,0,item,scene,true);temporal=false;
    }
    private void pass(int texture,int framebuffer,int type,float amount,GpuSceneFrame.Item item,GpuSceneFrame scene,boolean blend){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer);GLES20.glViewport(0,0,width,height);
        if(blend){GLES20.glEnable(GLES20.GL_BLEND);int mode=type==101?1:item.blend();
            GLES20.glBlendFunc(mode==3?GLES20.GL_DST_COLOR:GLES20.GL_ONE,mode==1?GLES20.GL_ONE:mode==2?GLES20.GL_ONE_MINUS_SRC_COLOR:GLES20.GL_ONE_MINUS_SRC_ALPHA);
        }else GLES20.glDisable(GLES20.GL_BLEND);
        int slot=type+1;if(slot<0||slot>=programs.length)throw new IllegalArgumentException("Unsupported filter type");
        if(programs[slot]==0)programs[slot]=GpuSceneRenderer.program(VERTEX,FRAGMENT.replace("uniform int uType;","const int uType="+type+";"));
        program=programs[slot];
        GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glUniform1i(loc("uImage"),0);GLES20.glUniform1i(loc("uType"),type);GLES20.glUniform1f(loc("uAmount"),amount);
        GLES20.glUniform1f(loc("uTime"),scene.effects.timestampMs/1000f);float h=scene.effects.portrait?711:225;
        GLES20.glUniform1f(loc("uDesignH"),h);float[] rect=GpuSceneRenderer.fittedRect(scene.effects.portrait,width,height);
        GLES20.glUniform4fv(loc("uRect"),1,rect,0);GLES20.glUniform2f(loc("uPixel"),rect[2]/400f,rect[3]/h);
        if(item.pose!=null){GLES20.glUniform4fv(loc("uCurrent"),1,item.pose.array(),0);GLES20.glUniform4fv(loc("uPrevious"),1,item.previousPose.array(),0);}
        int a=GLES20.glGetAttribLocation(program,"aPos");quad.position(0);GLES20.glEnableVertexAttribArray(a);GLES20.glVertexAttribPointer(a,2,GLES20.GL_FLOAT,false,0,quad);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(a);GLES20.glDisable(GLES20.GL_BLEND);
    }
    private int loc(String name){return GLES20.glGetUniformLocation(program,name);}
    private void release(){GLES20.glDeleteTextures(3,textures,0);GLES20.glDeleteFramebuffers(3,buffers,0);java.util.Arrays.fill(textures,0);java.util.Arrays.fill(buffers,0);}
    public void close(){release();for(int shader:programs)if(shader!=0)GLES20.glDeleteProgram(shader);java.util.Arrays.fill(programs,0);program=0;}
}
