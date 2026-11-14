package com.ranneki.visualizer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** GLES 2 compositor for the common background + reactive FX + spectrum scene.
 * Static artwork and center text are uploaded once per GL context, not per frame.
 * FBO output feeds MediaCodec directly; preview uses the exact same drawing functions.
 * All GL entrypoints must run on the owner EGL thread. */
final class GpuSceneRenderer implements AutoCloseable {
    private static final String VERTEX="attribute vec2 aPos; varying vec2 vPos;"+
            "void main(){vPos=aPos*0.5+0.5;gl_Position=vec4(aPos,0.0,1.0);}";
    private static final String BACKGROUND="precision mediump float;varying vec2 vPos;uniform vec4 uRect;uniform vec2 uShake;"+
            "void main(){vec2 xy=vec2(vPos.x,1.0-vPos.y);vec2 uv=(xy-uRect.xy)/uRect.zw-uShake;"+
            "vec3 c=vec3(7.0,9.0,21.0)/255.0;"+
            "if(uv.x>=0.0&&uv.x<=1.0&&uv.y>=0.0&&uv.y<=1.0){"+
            "vec3 a=vec3(8.0,14.0,39.0)/255.0;vec3 b=vec3(28.0,22.0,58.0)/255.0;"+
            "vec3 d=vec3(9.0,11.0,26.0)/255.0;"+
            "float t=clamp((uv.x+uv.y)*0.48,0.0,1.0);c=t<0.5?mix(a,b,t*2.0):mix(b,d,(t-0.5)*2.0);}"+
            "gl_FragColor=vec4(c,1.0);}";
    private static final String ART="precision mediump float;varying vec2 vPos;"+
            "uniform vec4 uRect,uCrop;uniform vec2 uShake;uniform sampler2D uImage;"+
            "void main(){vec2 xy=vec2(vPos.x,1.0-vPos.y);vec2 p=(xy-uRect.xy)/uRect.zw-uShake;"+
            "if(p.x<0.0||p.x>1.0||p.y<0.0||p.y>1.0)discard;"+
            "vec2 uv=mix(uCrop.xy,uCrop.zw,p);"+
            "vec4 tex=texture2D(uImage,uv);"+
            "gl_FragColor=vec4(mix(tex.rgb,vec3(.0235,.043,.125),.47),tex.a*.86);}";
    private static final String SPRITE_VERTEX="precision mediump float;attribute vec2 aCorner;"+
            "uniform vec4 uSceneRect;uniform vec2 uSize,uShift,uRot,uCamera;"+
            "uniform float uScale,uDesignH,uCameraEnabled;uniform mat3 uProjection;varying vec2 vUV,vDesign;"+
            "void main(){vec2 center=vec2(200.0,uDesignH*0.5);"+
            "vec2 untransformed=center+aCorner*uSize;"+
            "vDesign=untransformed/vec2(400.0,uDesignH);vUV=aCorner+vec2(.5);"+
            "vec2 p=aCorner*uSize*uScale;"+
            "vec2 rotated=vec2(p.x*uRot.x-p.y*uRot.y,p.x*uRot.y+p.y*uRot.x);"+
            "vec2 coord=center+rotated+uShift+uCamera;"+
            "vec2 screen=uSceneRect.xy+coord/vec2(400.0,uDesignH)*uSceneRect.zw;"+
            "gl_Position=vec4(screen.x*2.0-1.0,1.0-screen.y*2.0,0.0,1.0);if(uCameraEnabled>.5){vec3 projected=uProjection*vec3(coord-center,1);gl_Position=vec4(projected.xy,0,projected.z);}}";
    private static final String SPRITE_FRAGMENT="precision mediump float;"+
            "varying vec2 vUV,vDesign;uniform sampler2D uImage;"+
            "uniform float uAlpha,uMaskRadius,uDesignH;uniform vec4 uCrop;uniform int uLocalShape;"+
            "uniform int uMask,uPolyCount;uniform vec2 uPoly[32];"+
            "bool insidePoly(vec2 q){bool inside=false;for(int i=0;i<32;i++){"+
            "if(i<uPolyCount){int k=(i==0)?(uPolyCount-1):(i-1);"+
            "vec2 a=uPoly[i];vec2 b=uPoly[k];"+
            "if((a.y>q.y)!=(b.y>q.y)){float cut=(b.x-a.x)*(q.y-a.y)/(b.y-a.y)+a.x;"+
            "if(q.x<cut)inside=!inside;}}}return inside;}"+
            "void main(){vec2 p=vDesign;"+
            "if(uMask==1&&length((p-vec2(.5))*vec2(400.0,uDesignH))>uMaskRadius*240.0)discard;"+
            "if(uMask==2&&p.y>uMaskRadius)discard;"+
            "if(uMask==3&&p.y<1.0-uMaskRadius)discard;"+
            "if(uMask==4&&uPolyCount>=3&&!insidePoly(p))discard;"+
            "float cover=1.0;"+
            "if(uLocalShape==1)cover=1.0-smoothstep(.485,.5,length(vUV-vec2(.5)));"+
            "if(uLocalShape==2){vec2 d=max(abs(vUV-vec2(.5))-vec2(.34),vec2(0.0));"+
            "cover=1.0-smoothstep(.15,.16,length(d));}"+
            "vec2 q=(vUV-vec2(.5))*2.0;"+
            "if(uLocalShape==4){float edge=max(q.y-.5,max(-1.0-q.y,abs(q.x)-(q.y+1.0)*.57735));cover=1.0-smoothstep(-.025,0.0,edge);}"+
            "if(uLocalShape==5){float edge=max(abs(q.x)-.866,abs(q.y)+abs(q.x)*.57735-1.0);cover=1.0-smoothstep(-.025,0.0,edge);}"+
            "if(uLocalShape==6)cover=1.0-smoothstep(.96,1.0,abs(q.x)+abs(q.y));"+
            "if(uLocalShape==7){q.y=-q.y+.12;float k=q.x*q.x+q.y*q.y-.72;float f=k*k*k-q.x*q.x*q.y*q.y*q.y;cover=1.0-smoothstep(-.02,.02,f);}"+
            "if(uLocalShape==8){float a=atan(q.y,q.x)+1.5707963;float sector=6.2831853/10.0;float z=mod(a+6.2831853,6.2831853)/sector;float i=floor(z);float r0=mod(i,2.0)<.5?1.0:.48;float r1=mod(i+1.0,2.0)<.5?1.0:.48;"+
            "float b=fract(z)*sector;float edge=r0*r1*sin(sector)/(r1*sin(sector-b)+r0*sin(b));cover=1.0-smoothstep(edge-.025,edge,length(q));}"+
            "vec4 src=texture2D(uImage,mix(uCrop.xy,uCrop.zw,vUV));"+
            "gl_FragColor=src*(uAlpha*cover);}";
    private static final String LINE_VERTEX="attribute vec2 aPos;void main(){gl_Position=vec4(aPos,0.0,1.0);}";
    private static final String LINE_FRAG="precision mediump float;uniform vec4 uColor;void main(){gl_FragColor=uColor;}";
    private static final String TEXT_FRAG="precision mediump float;varying vec2 vPos;"+
            "uniform vec4 uRect;uniform sampler2D uImage;"+
            "void main(){vec2 p=(vec2(vPos.x,1.0-vPos.y)-uRect.xy)/uRect.zw;"+
            "if(p.x<0.0||p.x>1.0||p.y<0.0||p.y>1.0)discard;"+
            "gl_FragColor=texture2D(uImage,vec2(p.x,p.y));}";

    private static final String VISUALIZER_VERTEX="precision mediump float;attribute vec4 aPoint;"+
            "uniform vec4 uRect;uniform vec2 uShift,uRot;uniform float uScale,uDesignH;"+
            "varying vec2 vDesign;varying float vGradient,vEdge;"+
            "uniform float uCameraEnabled;uniform mat3 uProjection;"+
            "void main(){vGradient=aPoint.z;vEdge=aPoint.w;"+
            "vDesign=(aPoint.xy+vec2(200.0,uDesignH*.5))/vec2(400.0,uDesignH);"+
            "vec2 p=aPoint.xy*uScale;vec2 r=vec2(p.x*uRot.x-p.y*uRot.y,p.x*uRot.y+p.y*uRot.x);"+
            "vec2 xy=(r+vec2(200.0,uDesignH*.5)+uShift)/vec2(400.0,uDesignH);"+
            "vec2 screen=uRect.xy+xy*uRect.zw;gl_Position=vec4(screen.x*2.0-1.0,1.0-screen.y*2.0,0,1);if(uCameraEnabled>.5){vec3 projected=uProjection*vec3(r+uShift,1);gl_Position=vec4(projected.xy,0,projected.z);}}";
    private static final String VISUALIZER_FRAGMENT="precision mediump float;"+
            "varying vec2 vDesign;varying float vGradient,vEdge;uniform vec3 uColor,uAccent;"+
            "uniform float uAlpha,uMaskRadius,uDesignH;uniform int uMask,uPolyCount;uniform vec2 uPoly[32];"+
            "bool insidePoly(vec2 q){bool yes=false;for(int i=0;i<32;i++){if(i<uPolyCount){"+
            "int k=(i==0)?uPolyCount-1:i-1;vec2 a=uPoly[i],b=uPoly[k];"+
            "if((a.y>q.y)!=(b.y>q.y)){if(q.x<(b.x-a.x)*(q.y-a.y)/(b.y-a.y)+a.x)yes=!yes;}}}return yes;}"+
            "void main(){vec2 p=vDesign;"+
            "if(uMask==1&&length((p-vec2(.5))*vec2(400.0,uDesignH))>uMaskRadius*240.0)discard;"+
            "if(uMask==2&&p.y>uMaskRadius)discard;if(uMask==3&&p.y<1.0-uMaskRadius)discard;"+
            "if(uMask==4&&uPolyCount>=3&&!insidePoly(p))discard;"+
            "float alpha=uAlpha*(1.0-smoothstep(.65,1.0,abs(vEdge)));"+
            "vec3 c=mix(uColor,uAccent,.5-.5*cos(vGradient*6.2831853));gl_FragColor=vec4(c*alpha,alpha);}";
    private final VisualizerMesh visualizerMesh=new VisualizerMesh();
    private final FloatBuffer visualizerBuffer=ByteBuffer.allocateDirect(VisualizerMesh.MAX_FLOATS*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private int visualizerProgram;
    private final FloatBuffer quad;
    private final FloatBuffer ring;
    private final FloatBuffer spriteQuad;
    private final java.util.HashMap<Integer,SpriteTexture> spriteTextures=new java.util.HashMap<>();
    private final float[] polygons=new float[64];
    private int spriteProgram;
    private final boolean[] activeSprites=new boolean[ReactiveView.MAX_LAYERS];
    private int cachedSpriteUploads;
    private int bgProgram,artProgram,lineProgram,textProgram;
    private int backdropTexture, titleTexture;
    private Bitmap loadedBackdrop;
    private int backingWidth,backingHeight,fbo,fboTexture;
    private int frames, backdropUploads;
    private boolean closed,isolating;
    private GpuLayerFilters layerFilters;
    GpuSceneRenderer() {
        quad=floats(new float[]{-1,-1, 1,-1, -1,1, 1,1});
        ring=ByteBuffer.allocateDirect(256*2*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        spriteQuad=floats(new float[]{-.5f,.5f,.5f,.5f,-.5f,-.5f,.5f,-.5f});
        try {
            bgProgram=program(VERTEX,BACKGROUND);
            artProgram=program(VERTEX,ART);
            lineProgram=program(LINE_VERTEX,LINE_FRAG);
            textProgram=program(VERTEX,TEXT_FRAG);
            spriteProgram=program(SPRITE_VERTEX,SPRITE_FRAGMENT);
            visualizerProgram=program(VISUALIZER_VERTEX,VISUALIZER_FRAGMENT);
            titleTexture=makeTitle();
        } catch(RuntimeException e) {close();throw e;}
    }
    private static FloatBuffer floats(float[] a){
        FloatBuffer f=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        f.put(a);f.position(0);return f;
    }
    private static int shader(int kind,String source){
        int id=GLES20.glCreateShader(kind);
        GLES20.glShaderSource(id,source);GLES20.glCompileShader(id);
        int[] status=new int[1];GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0);
        if(status[0]!=1){String error=GLES20.glGetShaderInfoLog(id);GLES20.glDeleteShader(id);
            throw new IllegalStateException("GPU compositor shader error: "+error);}
        return id;
    }
    static int program(String vertex,String fragment){
        int v=shader(GLES20.GL_VERTEX_SHADER,vertex),f=shader(GLES20.GL_FRAGMENT_SHADER,fragment);
        int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);
        GLES20.glLinkProgram(p);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
        int[] status=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,status,0);
        if(status[0]!=1){String error=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);
            throw new IllegalStateException("GPU compositor link error: "+error);}
        return p;
    }
    private static void quad(int program,FloatBuffer verts){
        int attr=GLES20.glGetAttribLocation(program,"aPos");
        verts.position(0);
        GLES20.glEnableVertexAttribArray(attr);
        GLES20.glVertexAttribPointer(attr,2,GLES20.GL_FLOAT,false,0,verts);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(attr);
    }
    static int createTexture(){
        int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);
        if(ids[0]==0)throw new IllegalStateException("Cannot allocate GL texture");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        return ids[0];
    }
    private static void upload(int tex,Bitmap image){
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,tex);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,image,0);
        int err=GLES20.glGetError();
        if(err!=GLES20.GL_NO_ERROR)throw new IllegalStateException("GPU texture upload error: "+err);
    }
    private static int makeTitle(){
        Bitmap bitmap=Bitmap.createBitmap(512,128,Bitmap.Config.ARGB_8888);
        try {
            Canvas c=new Canvas(bitmap);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));p.setTextSize(48);
            c.drawText("RANNEKI",256,65,p);
            p.setColor(0xffaa9bff);p.setTextSize(22);c.drawText("AUDIO REACTIVE",256,102,p);
            int tex=createTexture();upload(tex,bitmap);return tex;
        } finally {bitmap.recycle();}
    }
    static float[] fittedRect(boolean portrait,int w,int h){
        return GpuSceneMath.fittedRect(portrait,w,h);
    }
    void begin(GpuSceneFrame frame,int width,int height,boolean offscreen){
        if(closed)throw new IllegalStateException("GPU compositor released");
        java.util.Arrays.fill(activeSprites,false);
        for(GpuSceneFrame.Item item:frame.drawOrder){
            GpuSceneFrame.Sprite sprite=item.sprite!=null?item.sprite:item.visualizer==null?null:item.visualizer.logo;
            if(sprite!=null)activeSprites[sprite.id]=true;
        }
        java.util.Iterator<java.util.Map.Entry<Integer,SpriteTexture>> slots=spriteTextures.entrySet().iterator();
        while(slots.hasNext()){java.util.Map.Entry<Integer,SpriteTexture> slot=slots.next();
            if(!activeSprites[slot.getKey()]){GLES20.glDeleteTextures(1,new int[]{slot.getValue().texture},0);slots.remove();}}
        if(frame.backdrop==null&&backdropTexture!=0){GLES20.glDeleteTextures(1,new int[]{backdropTexture},0);backdropTexture=0;loadedBackdrop=null;}
        if(offscreen)ensureFbo(width,height);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,offscreen?fbo:0);
        GLES20.glViewport(0,0,width,height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(7f/255f,9f/255f,21f/255f,1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        float[] r=fittedRect(frame.effects.portrait,width,height);
        GLES20.glUseProgram(bgProgram);
        GLES20.glUniform4f(GLES20.glGetUniformLocation(bgProgram,"uRect"),r[0],r[1],r[2],r[3]);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(bgProgram,"uShake"),
                frame.shakeX/400f,frame.shakeY/(frame.effects.portrait?711f:225f));
        quad(bgProgram,quad);
        if(frame.backdrop!=null && !frame.backdrop.isRecycled()){
            if(frame.backdrop!=loadedBackdrop || backdropTexture==0){
                if(backdropTexture!=0)GLES20.glDeleteTextures(1,new int[]{backdropTexture},0);
                backdropTexture=createTexture();upload(backdropTexture,frame.backdrop);
                loadedBackdrop=frame.backdrop;backdropUploads++;
            }
            float[] crop=GpuSceneMath.centerCrop(frame.backdrop.getWidth(),frame.backdrop.getHeight(),frame.effects.portrait);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glUseProgram(artProgram);
            GLES20.glUniform4f(GLES20.glGetUniformLocation(artProgram,"uRect"),r[0],r[1],r[2],r[3]);
            GLES20.glUniform4f(GLES20.glGetUniformLocation(artProgram,"uCrop"),crop[0],crop[1],crop[2],crop[3]);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(artProgram,"uShake"),
                    frame.shakeX/400f,frame.shakeY/(frame.effects.portrait?711f:225f));
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,backdropTexture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(artProgram,"uImage"),0);
            quad(artProgram,quad);
            GLES20.glDisable(GLES20.GL_BLEND);
        }
        frames++;
    }
    private static final class SpriteTexture {
        int texture;
        Bitmap image;
        VideoFrameLayer video;
        long version=Long.MIN_VALUE;
        String textKey;
    }
    private static Bitmap rasterText(String text,int color){
        Bitmap bm=Bitmap.createBitmap(512,256,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bm);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        p.setColor(color);p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
        String[] lines=text.split("\n",-1);
        int n=Math.min(5,lines.length);
        float font=55f;p.setTextSize(font);
        float widest=1f;
        for(int i=0;i<n;i++)widest=Math.max(widest,p.measureText(lines[i]));
        font=Math.min(font,font*476f/widest);p.setTextSize(font);
        p.setShadowLayer(5f,0f,3f,Color.BLACK);
        float spacing=font*1.17f;
        float first=128f-(n-1)*spacing*.5f+font*.34f;
        for(int i=0;i<n;i++)c.drawText(lines[i],256f,first+i*spacing,p);
        return bm;
    }
    private SpriteTexture source(GpuSceneFrame.Sprite item,boolean export,Runnable refresh,int maxSide){
        SpriteTexture slot=spriteTextures.get(item.id);
        if(slot==null){slot=new SpriteTexture();slot.texture=createTexture();spriteTextures.put(item.id,slot);}
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        if(item.type==ReactiveView.IMAGE){
            if(item.image==null||item.image.isRecycled())return null;
            if(slot.image!=item.image){upload(slot.texture,item.image);slot.image=item.image;
                slot.textKey=null;slot.video=null;cachedSpriteUploads++;}
        }else if(item.type==ReactiveView.TEXT){
            String key=item.text+"\u0000"+item.color;
            if(!key.equals(slot.textKey)){
                Bitmap bm=rasterText(item.text,item.color);
                try{upload(slot.texture,bm);}finally{bm.recycle();}
                slot.textKey=key;slot.image=null;slot.video=null;cachedSpriteUploads++;
            }
        }else if(item.type==ReactiveView.VIDEO){
            if(item.video==null)return null;
            if(slot.video!=item.video){slot.video=item.video;slot.version=Long.MIN_VALUE;
                slot.image=null;slot.textKey=null;}
            if(export){
                if(!item.video.uploadGpuExport(slot.texture,item.timestampMs,item.startMs,maxSide))return null;
                cachedSpriteUploads++;
            }else{
                item.video.requestGpuPreview(item.timestampMs,item.startMs,refresh);
                long v=item.video.cachedGpuVersion();
                if(v<0)return null;
                if(slot.version!=v){
                    if(!item.video.uploadGpuPreview(slot.texture))return null;
                    slot.version=v;cachedSpriteUploads++;
                }
            }
        }
        return slot;
    }
    private void sprite(GpuSceneFrame.Sprite item,GpuSceneFrame scene,int width,int height,
            boolean export,Runnable refresh){
        boolean portrait=scene.effects.portrait;
        if(item.opacity<=0f||item.width<=0f||item.height<=0f)return;
        SpriteTexture texture=source(item,export,refresh,Math.max(width,height));
        if(texture==null)return;
        float[] rect=fittedRect(portrait,width,height);
        float h=portrait?711f:225f;
        GLES20.glEnable(GLES20.GL_BLEND);
        premultipliedBlend(item.blend);
        GLES20.glUseProgram(spriteProgram);
        GpuCamera.apply(spriteProgram,scene.camera,item.depth,rect,h);
        GLES20.glUniform4f(GLES20.glGetUniformLocation(spriteProgram,"uSceneRect"),
                rect[0],rect[1],rect[2],rect[3]);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(spriteProgram,"uSize"),item.width,item.height);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(spriteProgram,"uShift"),item.shiftX,item.shiftY);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(spriteProgram,"uCamera"),
                scene.shakeX,scene.shakeY);
        double angle=Math.toRadians(item.rotation);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(spriteProgram,"uRot"),
                (float)Math.cos(angle),(float)Math.sin(angle));
        GLES20.glUniform1f(GLES20.glGetUniformLocation(spriteProgram,"uScale"),item.scale);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(spriteProgram,"uDesignH"),h);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(spriteProgram,"uAlpha"),item.opacity);
        GLES20.glUniform4f(GLES20.glGetUniformLocation(spriteProgram,"uCrop"),item.crop[0],item.crop[1],item.crop[2],item.crop[3]);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(spriteProgram,"uLocalShape"),item.localShape);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(spriteProgram,"uMaskRadius"),item.maskRadius);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(spriteProgram,"uMask"),item.mask);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(spriteProgram,"uPolyCount"),item.polygon.length/2);
        java.util.Arrays.fill(polygons,0f);
        System.arraycopy(item.polygon,0,polygons,0,Math.min(64,item.polygon.length));
        GLES20.glUniform2fv(GLES20.glGetUniformLocation(spriteProgram,"uPoly[0]"),32,polygons,0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture.texture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(spriteProgram,"uImage"),0);
        int attr=GLES20.glGetAttribLocation(spriteProgram,"aCorner");
        spriteQuad.position(0);
        GLES20.glEnableVertexAttribArray(attr);
        GLES20.glVertexAttribPointer(attr,2,GLES20.GL_FLOAT,false,0,spriteQuad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(attr);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDisable(GLES20.GL_BLEND);
    }
    /** Keeps user layer order intact: effect batches are drawn between image/text/video sprites. */
    int composite(GpuSceneFrame scene,GpuParticleRenderer particles,int width,int height,
            boolean export,Runnable refresh){
        int count=0;
        for(GpuSceneFrame.Item item:scene.drawOrder){
            boolean temporal=item.motionBlur>0&&scene.shutter.length>0&&(item.sprite==null||item.sprite.video==null);
            boolean post=item.filters.length>0||item.motionBlur>0||item.feather>0;
            if(post){if(layerFilters==null)layerFilters=new GpuLayerFilters();layerFilters.start(width,height);}
            isolating=post;
            try{
                if(temporal){layerFilters.beginTemporal();
                    for(int j=0;j<scene.shutter.length+1;j++){
                        GpuSceneFrame sample=j==0?scene:scene.shutter[j-1];GpuSceneFrame.Item part=null;
                        for(GpuSceneFrame.Item candidate:sample.drawOrder)if(candidate.id()==item.id()){part=candidate;break;}
                        layerFilters.sample();if(part!=null)count+=drawItem(part,sample,particles,width,height,export,refresh,true);
                        float weight=j==0?1-item.motionBlur*2f/3f:item.motionBlur/3f;layerFilters.accumulate(item,sample,weight);
                    }
                }else count+=drawItem(item,scene,particles,width,height,export,refresh,post);
            }finally{isolating=false;}
            if(post)layerFilters.finish(item,scene);
        }
        return count;
    }
    private int drawItem(GpuSceneFrame.Item item,GpuSceneFrame scene,GpuParticleRenderer particles,int width,int height,boolean export,Runnable refresh,boolean isolated){
        if(item.effect!=null){GpuEffectFrame.Layer f=item.effect;if(isolated)f=new GpuEffectFrame.Layer(f.id,f.type,0,f.mask,f.particleCount,f.power,f.opacity,f.size,f.speed,f.shiftX,f.shiftY,f.scale,f.rotation,f.direction,f.maskRadius,f.polygon.length==0?null:f.polygon);
            f.depth=item.effect.depth;GpuEffectFrame effects=new GpuEffectFrame(scene.effects.timestampMs,scene.effects.portrait,new GpuEffectFrame.Layer[]{f});effects.camera=scene.camera;return particles.draw(effects,width,height);}
        if(item.visualizer!=null)visualizer(item.visualizer,scene,width,height,export,refresh);else sprite(item.sprite,scene,width,height,export,refresh);return 0;
    }
    private void premultipliedBlend(int mode){
        if(isolating)mode=0;
        if(mode==1)GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE);
        else if(mode==2)GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_COLOR);
        else if(mode==3)GLES20.glBlendFunc(GLES20.GL_DST_COLOR,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        else GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }
    private void visualizer(VisualizerFrame frame,GpuSceneFrame scene,int width,int height,boolean export,Runnable refresh){
        if(frame.logo!=null)sprite(frame.logo,scene,width,height,export,refresh);
        GLES20.glEnable(GLES20.GL_BLEND);premultipliedBlend(frame.blend);
        GLES20.glUseProgram(visualizerProgram);
        float[] rect=fittedRect(scene.effects.portrait,width,height);float h=scene.effects.portrait?711:225;
        int p=visualizerProgram;
        GpuCamera.apply(p,scene.camera,frame.depth,rect,h);
        GLES20.glUniform4f(GLES20.glGetUniformLocation(p,"uRect"),rect[0],rect[1],rect[2],rect[3]);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(p,"uShift"),frame.x,frame.y);
        double angle=Math.toRadians(frame.rotation);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(p,"uRot"),(float)Math.cos(angle),(float)Math.sin(angle));
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uScale"),frame.scale);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uDesignH"),h);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uMaskRadius"),frame.maskRadius);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(p,"uMask"),frame.mask);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(p,"uPolyCount"),frame.polygon.length/2);
        java.util.Arrays.fill(polygons,0);System.arraycopy(frame.polygon,0,polygons,0,Math.min(64,frame.polygon.length));
        GLES20.glUniform2fv(GLES20.glGetUniformLocation(p,"uPoly[0]"),32,polygons,0);
        color(p,"uColor",frame.color);color(p,"uAccent",frame.accent);
        int attr=GLES20.glGetAttribLocation(p,"aPoint");GLES20.glEnableVertexAttribArray(attr);
        for(int pass=frame.glow>0?2:0;pass>=0;pass--){
            float widthFactor=pass==0?1:pass==1?3:7;
            float alpha=frame.opacity*(pass==0?1:frame.glow*(pass==1?.22f:.07f));
            visualizerMesh.build(frame.style,frame.bands,frame.right,frame.path,frame.bars,frame.radius,frame.height,frame.thickness*widthFactor);
            visualizerBuffer.clear();visualizerBuffer.put(visualizerMesh.vertices,0,visualizerMesh.floats).flip();
            GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"uAlpha"),alpha);
            GLES20.glVertexAttribPointer(attr,4,GLES20.GL_FLOAT,false,0,visualizerBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,visualizerMesh.floats/4);
        }
        GLES20.glDisableVertexAttribArray(attr);GLES20.glDisable(GLES20.GL_BLEND);
    }
    private static void color(int program,String uniform,int value){
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program,uniform),Color.red(value)/255f,Color.green(value)/255f,Color.blue(value)/255f);
    }
    void spectrum(GpuSceneFrame frame,int width,int height){
        if(!frame.spectrumEnabled)return;
        float[] r=fittedRect(frame.effects.portrait,width,height);
        float designH=frame.effects.portrait?711f:225f;
        float radius=(45+frame.bass*10+frame.kick*24)*(.55f+frame.spectrumScale/100f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(lineProgram);
        for(int ringIndex=0;ringIndex<2;ringIndex++){
            float rad=radius+(ringIndex==0?0:8);
            int n=96;
            ring.clear();
            for(int i=0;i<n;i++){
                float angle=(float)(2*Math.PI*i/n);
                point(ring,r,400,designH,200+(float)Math.cos(angle)*rad+frame.shakeX,
                        designH*.5f+(float)Math.sin(angle)*rad+frame.shakeY);
            }
            ring.flip();
            lineDraw(ring,n,GLES20.GL_LINE_LOOP,ringIndex==0?0.67f:0.8f);
        }
        ring.clear();
        for(int i=0;i<64;i++){
            float a=(float)(2*Math.PI*i/64),x=(float)Math.cos(a),y=(float)Math.sin(a);
            int band=i<32?i:63-i;
            float note=frame.spectrum[Math.min(band,frame.spectrum.length-1)];
            float len=5+note*22+frame.kick*12;
            point(ring,r,400,designH,200+x*(radius+13)+frame.shakeX,
                    designH*.5f+y*(radius+13)+frame.shakeY);
            point(ring,r,400,designH,200+x*(radius+13+len)+frame.shakeX,
                    designH*.5f+y*(radius+13+len)+frame.shakeY);
        }
        ring.flip();lineDraw(ring,128,GLES20.GL_LINES,.72f);
        if(titleTexture!=0){
            float titleW=170f/400*r[2],titleH=44f/designH*r[3];
            float titleX=r[0]+r[2]*.5f-titleW*.5f+frame.shakeX/400f*r[2];
            float titleY=r[1]+r[3]*.5f-titleH*.5f+frame.shakeY/designH*r[3];
            GLES20.glUseProgram(textProgram);
            GLES20.glUniform4f(GLES20.glGetUniformLocation(textProgram,"uRect"),titleX,titleY,titleW,titleH);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,titleTexture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(textProgram,"uImage"),0);
            quad(textProgram,quad);
        }
        GLES20.glDisable(GLES20.GL_BLEND);
    }
    private static void point(FloatBuffer dest,float[] r,float designW,float designH,float x,float y){
        dest.put(2f*(r[0]+r[2]*(x/designW))-1f);
        dest.put(1f-2f*(r[1]+r[3]*(y/designH)));
    }
    private void lineDraw(FloatBuffer verts,int count,int primitive,float alpha){
        int attr=GLES20.glGetAttribLocation(lineProgram,"aPos");
        GLES20.glEnableVertexAttribArray(attr);
        GLES20.glVertexAttribPointer(attr,2,GLES20.GL_FLOAT,false,0,verts);
        GLES20.glUniform4f(GLES20.glGetUniformLocation(lineProgram,"uColor"),.75f,.60f,1.0f,alpha);
        GLES20.glDrawArrays(primitive,0,count);
        GLES20.glDisableVertexAttribArray(attr);
    }
    private void ensureFbo(int w,int h){
        if(fbo!=0 && w==backingWidth && h==backingHeight)return;
        releaseFramebuffer();
        int[] max=new int[1];GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,max,0);
        if(w>max[0]||h>max[0])throw new IllegalArgumentException("GPU max texture size "+max[0]+" smaller than output "+w+"x"+h);
        fboTexture=createTexture();
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
        int[] ids=new int[1];GLES20.glGenFramebuffers(1,ids,0);fbo=ids[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,fboTexture,0);
        int status=GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
        if(status!=GLES20.GL_FRAMEBUFFER_COMPLETE){releaseFramebuffer();
            throw new IllegalStateException("GPU framebuffer incomplete: "+status);}
        backingWidth=w;backingHeight=h;
    }
    int texture(){return fboTexture;}
    void end(){GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);}
    String stats(){return "GPU-only scene frames: "+frames+
            " | cached background texture uploads: "+backdropUploads+
            " | GPU sprite/video texture uploads: "+cachedSpriteUploads;}
    private void releaseFramebuffer(){
        if(fbo!=0)GLES20.glDeleteFramebuffers(1,new int[]{fbo},0);
        if(fboTexture!=0)GLES20.glDeleteTextures(1,new int[]{fboTexture},0);
        fbo=0;fboTexture=0;backingWidth=backingHeight=0;
    }
    @Override public void close(){
        if(closed)return;closed=true;
        releaseFramebuffer();
        if(layerFilters!=null){layerFilters.close();layerFilters=null;}
        if(backdropTexture!=0)GLES20.glDeleteTextures(1,new int[]{backdropTexture},0);
        if(titleTexture!=0)GLES20.glDeleteTextures(1,new int[]{titleTexture},0);
        for(SpriteTexture sprite:spriteTextures.values())if(sprite.texture!=0)
            GLES20.glDeleteTextures(1,new int[]{sprite.texture},0);
        spriteTextures.clear();
        for(int p:new int[]{bgProgram,artProgram,lineProgram,textProgram,spriteProgram,visualizerProgram})
            if(p!=0)GLES20.glDeleteProgram(p);
        backdropTexture=0;titleTexture=0;loadedBackdrop=null;
    }
}
