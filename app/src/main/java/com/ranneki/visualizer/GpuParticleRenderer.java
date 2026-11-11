package com.ranneki.visualizer;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** GLES2 point-sprite implementation for ALL ten built-in reactive FX families.
 * No per-particle Canvas draw or Bitmap upload. Geometry and movement are evaluated on GPU.
 * Only immutable layer uniforms/parameters cross from Android audio/timeline to the GL thread.
 */
final class GpuParticleRenderer implements AutoCloseable {
    private static final int MAX_PARTICLES=512;
    private static final int[] BASE_COUNT={260,230,340,36,180,210,220,108,64,100};
    private static final String VERTEX =
        "precision mediump float;\n"+
        "attribute vec4 aSeed;\n"+
        "uniform vec2 uResolution; uniform vec4 uViewRect;\n"+
        "uniform float uDesignH,uTime,uType,uPower,uVelocity,uSize,uPointMax;\n"+
        "uniform vec2 uTranslate,uCosSin; uniform float uScale,uDirection;\n"+
        "varying float vSeed; varying float vAlpha;\n"+
        "void main(){\n"+
        " float t=uTime*uVelocity;\n"+
        " float x=aSeed.x; float y=aSeed.y; float r=aSeed.w;\n"+
        " if(uType<0.5){ x=fract(x+t*(0.013+0.006*r)); y=fract(y-t*(0.022+0.008*aSeed.z)); }\n"+
        " else if(uType<1.5){ x=fract(x+0.025*sin(t*2.0+r*6.0)); y=1.0-fract(y+t*(0.11+uPower*0.14+r*0.08)); }\n"+
        " else if(uType<2.5){ x=fract(x+t*0.040); y=fract(y+t*(0.27+uPower*.17+0.10*r)); }\n"+
        " else if(uType<3.5){ x=0.5+(x-0.5)*0.75; y=0.72+(y-0.5)*0.20; }\n"+
        " else if(uType<4.5){ x=fract(x+0.020*sin(t*0.7+r*6.0)); y=1.0-fract(y+t*(0.035+0.03*r)); }\n"+
        " else if(uType<5.5){ x=fract(x+0.022*sin(t*0.9+r*13.0)); y=fract(y+t*(0.025+0.025*r)); }\n"+
        " else if(uType<6.5){ x=fract(x+t*(r-0.5)*0.12); y=1.0-fract(y+t*(0.18+0.11*r)); }\n"+
        " else if(uType<7.5){ x=0.18+0.64*floor(aSeed.x*5.0)/5.0+0.052*sin(y*44.0+t*33.0+r*3.0); y=fract(y+t*0.15); }\n"+
        " else if(uType<8.5){ x=0.5+(x-0.5)*0.15; y=0.5+(y-0.5)*0.16; }\n"+
        " else { x=fract(x+0.05*sin(t*6.0+r*17.0)); y=fract(y+t*0.14); }\n"+
        " float angle=uDirection*6.2831853;\n"+
        " if(uDirection>=0.0){ x+=0.035*t*cos(angle); y+=0.035*t*sin(angle); }\n"+
        " vec2 dp=(vec2(x,y)-vec2(.5)) * vec2(400.0,uDesignH)*uScale;\n"+
        " vec2 rotated=vec2(dp.x*uCosSin.x-dp.y*uCosSin.y,dp.x*uCosSin.y+dp.y*uCosSin.x);\n"+
        " vec2 uv=rotated/vec2(400.0,uDesignH)+vec2(.5)+uTranslate;\n"+
        " vec2 screen=uViewRect.xy+uv*uViewRect.zw;\n"+
        " gl_Position=vec4(screen.x*2.0-1.0,1.0-screen.y*2.0,0.0,1.0);\n"+
        " float point=3.0+3.0*r;\n"+
        " if(uType<0.5)point=1.4+2.0*r+uPower*8.0;\n"+
        " else if(uType<1.5)point=5.0+9.0*r+uPower*18.0;\n"+
        " else if(uType<2.5)point=10.0+uPower*22.0;\n"+
        " else if(uType<3.5)point=14.0+uPower*85.0+r*15.0;\n"+
        " else if(uType<4.5)point=9.0+17.0*r+uPower*21.0;\n"+
        " else if(uType<5.5)point=2.0+2.8*r+uPower*4.0;\n"+
        " else if(uType<6.5)point=3.0+8.0*r+uPower*9.0;\n"+
        " else if(uType<7.5)point=18.0+uPower*33.0;\n"+
        " else if(uType<8.5)point=12.0+uPower*105.0;\n"+
        " else point=7.0+uPower*36.0;\n"+
        " float px=max(1.0,min(uResolution.x/400.0,uResolution.y/uDesignH));\n"+
        " gl_PointSize=clamp(point*uSize*uScale*px,1.0,uPointMax);\n"+
        " vSeed=r; vAlpha=(0.46+0.54*aSeed.z)*(.42+.58*uPower);\n"+
        "}\n";
    private static final String FRAGMENT =
        "precision mediump float;\n"+
        "uniform vec2 uResolution,uTranslate,uCosSin; uniform vec4 uViewRect;\n"+
        "uniform float uType,uPower,uOpacity,uScale,uDesignH,uMaskRadius;\n"+
        "uniform int uMask,uPolyCount; uniform vec2 uPoly[32];\n"+
        "varying float vSeed; varying float vAlpha;\n"+
        "bool insidePoly(vec2 q){\n"+
        " bool inside=false;\n"+
        " for(int i=0;i<32;i++){\n"+
        "  if(i<uPolyCount){\n"+
        "   int k=(i==0)?(uPolyCount-1):(i-1);\n"+
        "   vec2 a=uPoly[i]; vec2 b=uPoly[k];\n"+
        "   if((a.y>q.y)!=(b.y>q.y)) {\n"+
        "    float cut=(b.x-a.x)*(q.y-a.y)/(b.y-a.y)+a.x;\n"+
        "    if(q.x<cut) inside=!inside;\n"+
        "   }\n"+
        "  }\n"+
        " } return inside;\n"+
        "}\n"+
        "void main(){\n"+
        " vec2 screen=vec2(gl_FragCoord.x/uResolution.x,1.0-gl_FragCoord.y/uResolution.y);\n"+
        " vec2 q=(screen-uViewRect.xy)/uViewRect.zw-vec2(.5)-uTranslate;\n"+
        " vec2 dp=q*vec2(400.0,uDesignH);\n"+
        " vec2 inv=vec2(dp.x*uCosSin.x+dp.y*uCosSin.y,-dp.x*uCosSin.y+dp.y*uCosSin.x);\n"+
        " vec2 local=inv/(uScale*vec2(400.0,uDesignH))+vec2(.5);\n"+
        " if(local.x<0.0||local.x>1.0||local.y<0.0||local.y>1.0) discard;\n"+
        " if(uMask==1 && length((local-vec2(.5))*vec2(400.0,uDesignH))>uMaskRadius*240.0) discard;\n"+
        " if(uMask==2 && local.y>uMaskRadius) discard;\n"+
        " if(uMask==3 && local.y<1.0-uMaskRadius) discard;\n"+
        " if(uMask==4 && uPolyCount>=3 && !insidePoly(local)) discard;\n"+
        " vec2 p=gl_PointCoord*2.0-1.0; float d=length(p);\n"+
        " float a=1.0-smoothstep(.6,1.0,d);\n"+
        " vec3 c=vec3(.85,.78,1.0);\n"+
        " if(uType<.5) {a*=.60;c=vec3(.82,.78,1.0);} \n"+
        " else if(uType<1.5){ a*=1.0-smoothstep(.6,1.0,abs(p.y)); a*=.74;c=mix(vec3(1.0,.24,.04),vec3(1.0,.80,.14),vSeed); }\n"+
        " else if(uType<2.5){a=(1.0-smoothstep(.04,.23,abs(p.x+.22*p.y)))*(1.0-smoothstep(.7,1.0,abs(p.y)));a*=.58;c=vec3(.58,.77,1.0);}\n"+
        " else if(uType<3.5){a=(1.0-smoothstep(.035,.11,abs(d-.77)))*(.40+.58*uPower);c=vec3(.29,.77,1.0);}\n"+
        " else if(uType<4.5){a*=.22;c=vec3(.61,.60,.70);}\n"+
        " else if(uType<5.5){a*=.83;c=vec3(.92,.96,1.0);}\n"+
        " else if(uType<6.5){a=1.0-smoothstep(.1,.35,abs(p.x+.5*p.y));a*=1.0-smoothstep(.75,1.0,abs(p.y));c=vec3(1.0,.73,.18);}\n"+
        " else if(uType<7.5){a=1.0-smoothstep(.03,.19,abs(p.x+.20*sin(p.y*13.0+vSeed*4.0))); a*=.87*uPower;c=vec3(.62,.82,1.0);}\n"+
        " else if(uType<8.5){a=pow(1.0-smoothstep(.0,1.0,d),1.7)*(.22+.55*uPower);c=vec3(.62,.40,1.0);}\n"+
        " else {a=(1.0-step(.18,abs(p.y)))*(.18+.62*uPower);c=vec3(1.0,.22,.83);}\n"+
        " a=clamp(a*vAlpha*uOpacity,0.0,1.0); if(a<.004)discard;\n"+
        " gl_FragColor=vec4(c,a);\n"+
        "}\n";

    private final int program,position;
    private final int[] u=new int[18];
    private final FloatBuffer seeds;
    private final float[] maxPoint={1f,128f};
    private final float[] polyUpload=new float[64];
    private boolean released;
    GpuParticleRenderer() {
        int v=0,f=0,p=0;
        try{
            v=shader(GLES20.GL_VERTEX_SHADER,VERTEX);
            f=shader(GLES20.GL_FRAGMENT_SHADER,FRAGMENT);
            p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);
            GLES20.glLinkProgram(p);
            int[] linked=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,linked,0);
            if(linked[0]==0)throw new IllegalStateException("GPU particle shader linking failed: "+GLES20.glGetProgramInfoLog(p));
        }catch(RuntimeException ex){
            if(p!=0)GLES20.glDeleteProgram(p);
            throw ex;
        }finally{
            if(v!=0)GLES20.glDeleteShader(v);if(f!=0)GLES20.glDeleteShader(f);
        }
        program=p;
        position=GLES20.glGetAttribLocation(program,"aSeed");
        String[] names={"uResolution","uViewRect","uDesignH","uTime","uType","uPower",
            "uVelocity","uSize","uPointMax","uTranslate","uCosSin","uScale",
            "uDirection","uOpacity","uMask","uMaskRadius","uPolyCount","uPoly[0]"};
        for(int i=0;i<names.length;i++)u[i]=GLES20.glGetUniformLocation(program,names[i]);
        ByteBuffer mem=ByteBuffer.allocateDirect(MAX_PARTICLES*4*4).order(ByteOrder.nativeOrder());
        seeds=mem.asFloatBuffer();
        // Stable pseudo-random seed: frame N is deterministic regardless of skipped preview frames.
        int state=0x1a34b19;
        for(int i=0;i<MAX_PARTICLES;i++){
            for(int k=0;k<4;k++){
                state^=state<<13;state^=state>>>17;state^=state<<5;
                seeds.put((state>>>8&0xffffff)/16777216f);
            }
        }
        seeds.position(0);
        GLES20.glGetFloatv(GLES20.GL_ALIASED_POINT_SIZE_RANGE,maxPoint,0);
        if(maxPoint[1]<1f)maxPoint[1]=32f;
    }
    private static int shader(int kind,String code) {
        int id=GLES20.glCreateShader(kind);
        GLES20.glShaderSource(id,code);GLES20.glCompileShader(id);
        int[] ok=new int[1];GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0){String log=GLES20.glGetShaderInfoLog(id);GLES20.glDeleteShader(id);
            throw new IllegalStateException("GPU particle GLSL compile failed: "+log);}
        return id;
    }
    /** Uses current EGL context; does not clear the framebuffer (caller controls background). */
    int draw(GpuEffectFrame frame,int width,int height){
        if(released||frame==null||width<=0||height<=0)return 0;
        float aspect=frame.portrait?9f/16f:16f/9f;
        float fittedW=Math.min(width,height*aspect),fittedH=fittedW/aspect;
        float left=(width-fittedW)*.5f,top=(height-fittedH)*.5f;
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(u[0],width,height);
        GLES20.glUniform4f(u[1],left/width,top/height,fittedW/width,fittedH/height);
        GLES20.glUniform1f(u[2],frame.portrait?711f:225f);
        GLES20.glUniform1f(u[3],frame.timestampMs/1000f);
        GLES20.glUniform1f(u[8],Math.min(192f,maxPoint[1]));
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glEnableVertexAttribArray(position);
        seeds.position(0);
        GLES20.glVertexAttribPointer(position,4,GLES20.GL_FLOAT,false,0,seeds);
        int total=0;
        for(GpuEffectFrame.Layer layer:frame.layers){
            int count=Math.min(MAX_PARTICLES,Math.round(BASE_COUNT[layer.type]*layer.particleCount/512f));
            if(count<=0||layer.opacity<=0)continue;
            if(layer.blend==1)GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);
            else if(layer.blend==2)GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_COLOR);
            else if(layer.blend==3)GLES20.glBlendFunc(GLES20.GL_DST_COLOR,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            else GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glUniform1f(u[4],layer.type);
            GLES20.glUniform1f(u[5],layer.power);
            GLES20.glUniform1f(u[6],layer.speed);
            GLES20.glUniform1f(u[7],layer.size);
            GLES20.glUniform2f(u[9],layer.shiftX,layer.shiftY);
            double radians=Math.toRadians(layer.rotation);
            GLES20.glUniform2f(u[10],(float)Math.cos(radians),(float)Math.sin(radians));
            GLES20.glUniform1f(u[11],layer.scale);
            GLES20.glUniform1f(u[12],layer.direction);
            GLES20.glUniform1f(u[13],layer.opacity);
            GLES20.glUniform1i(u[14],layer.mask);
            GLES20.glUniform1f(u[15],layer.maskRadius);
            GLES20.glUniform1i(u[16],layer.polygon.length/2);
            java.util.Arrays.fill(polyUpload,0f);
            System.arraycopy(layer.polygon,0,polyUpload,0,layer.polygon.length);
            GLES20.glUniform2fv(u[17],32,polyUpload,0);
            GLES20.glDrawArrays(GLES20.GL_POINTS,0,count);
            total+=count;
        }
        GLES20.glDisableVertexAttribArray(position);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDisable(GLES20.GL_BLEND);
        int err=GLES20.glGetError();
        if(err!=GLES20.GL_NO_ERROR)throw new IllegalStateException("GLES particle render error: "+err);
        return total;
    }
    @Override public void close(){if(!released){released=true;GLES20.glDeleteProgram(program);}}
}
