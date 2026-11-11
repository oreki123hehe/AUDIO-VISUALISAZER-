package com.ranneki.visualizer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;

/** GPU-accelerated Canvas preview; graphics respond to decoded music features. */
public final class ReactiveView extends View {
    public interface AudioClock { long positionMs(); boolean playing(); }
    public static final int DUST=0, FIRE=1, RAIN=2, WATER=3;
    public static final String[] SOURCES={"Kick", "Bass", "Kick + Bass", "Treble", "Snare"};
    private static final int DESIGN_WIDTH=400;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng=new Random(14350);
    private final Particle[][] particles=new Particle[3][];
    private final Ripple[] ripples=new Ripple[20];
    private int nextRipple=0;
    private final int[] strength={80,80,80,80};
    private final int[] size={55,55,55,55};
    private final int[] density={60,60,60,60};
    private final boolean[] enabled={true,true,true,true};
    private final int[] source={2,1,4,0};
    private AudioAnalyzer.Track track;
    private Bitmap backdrop;
    private AudioClock clock;
    private boolean portrait=true;
    private long lastDraw=0, previousPosition=-1, lastKickEvent=-999;
    private float kickEnvelope=0f, snareEnvelope=0f, phase=0;
    private AudioAnalyzer.Features live=AudioAnalyzer.Features.zero();

    private static final class Particle {
        float x,y,vx,vy,r,alpha;
    }
    private static final class Ripple {
        float x,y,age,amp;
        boolean active;
    }
    public ReactiveView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        for(int group=0;group<particles.length;group++) {
            int amount=group==0?160:group==1?110:175;
            particles[group]=new Particle[amount];
            for(int j=0;j<amount;j++) {
                Particle particle=new Particle();
                reset(particle,group,true);
                particles[group][j]=particle;
            }
        }
        for(int j=0;j<ripples.length;j++) ripples[j]=new Ripple();
    }
    public void setClock(AudioClock value) { clock=value; invalidate(); }
    public void setTrack(AudioAnalyzer.Track value) { track=value; kickEnvelope=0; invalidate(); }
    public void setBackdrop(Bitmap value) {
        if(backdrop!=null && backdrop!=value && !backdrop.isRecycled()) backdrop.recycle();
        backdrop=value; invalidate();
    }
    public void setPortrait(boolean value) { portrait=value; invalidate(); }
    public boolean isPortrait() { return portrait; }
    public void setStrength(int effect,int value) { strength[effect]=value; invalidate(); }
    public void setDensity(int effect,int value) { density[effect]=value; invalidate(); }
    public void setSize(int effect,int value) { size[effect]=value; invalidate(); }
    public void setEnabled(int which,boolean value) { enabled[which]=value; invalidate(); }
    public boolean isEnabled(int which) { return enabled[which]; }
    public void setSource(int which,int value) { source[which]=value%SOURCES.length; invalidate(); }
    public int getSource(int which) { return source[which]; }
    public int getStrength(int effect) { return strength[effect]; }
    public int getDensity(int effect) { return density[effect]; }
    public int getSize(int effect) { return size[effect]; }

    private void reset(Particle particle,int group,boolean initial) {
        particle.x=rng.nextFloat();
        particle.y=initial?rng.nextFloat():(group==1?1f:group==0?1.1f:-.1f);
        particle.vx=(rng.nextFloat()-.5f)*.35f;
        particle.vy=(.13f+rng.nextFloat()*.35f)*(group==1?-1:1);
        particle.r=.45f+rng.nextFloat()*1.5f;
        particle.alpha=.35f+rng.nextFloat()*.55f;
        if(group==0) particle.vy=-.02f-rng.nextFloat()*.07f;
        if(group==2) { particle.vx=.16f+rng.nextFloat()*.12f;particle.vy=.65f+rng.nextFloat()*.45f; }
    }
    private float level(int effect) {
        switch(source[effect]) {
            case 0:return kickEnvelope;
            case 1:return live.bass;
            case 2:return Math.min(1f,live.bass*.6f+kickEnvelope*.85f);
            case 3:return live.highs;
            case 4:return snareEnvelope;
            default:return 0;
        }
    }
    private static float clamp(float v) { return Math.min(1f,Math.max(0f,v)); }
    private static int rgba(int r,int g,int b,float a) {
        return Color.argb((int)(clamp(a)*255),r,g,b);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now=SystemClock.elapsedRealtime();
        float dt=lastDraw==0?0f:Math.min(.05f,(now-lastDraw)/1000f);
        lastDraw=now;
        long position=clock==null?0:clock.positionMs();
        boolean playing=clock!=null && clock.playing() && track!=null;
        live=track==null?AudioAnalyzer.Features.zero():track.at(position);
        if(position<previousPosition-100) { lastKickEvent=-999;kickEnvelope=0; }
        previousPosition=position;
        if(playing && live.kick>.32f && position-lastKickEvent>105) {
            kickEnvelope=Math.max(kickEnvelope,live.kick);
            lastKickEvent=position;
            Ripple ring=ripples[nextRipple++%ripples.length];
            ring.active=true;
            ring.age=0;
            ring.x=.5f+(rng.nextFloat()-.5f)*.22f;
            ring.y=.72f;
            ring.amp=clamp(level(WATER)+.35f);
        }
        if(playing) snareEnvelope=Math.max(snareEnvelope,live.snare);
        kickEnvelope*=Math.pow(.80f,dt*60f);
        snareEnvelope*=Math.pow(.80f,dt*60f);
        if(playing) phase+=dt;
        render(canvas,getWidth(),getHeight(),playing?dt:0f);
        if(playing) postInvalidateDelayed(16);
    }
    public Bitmap snapshot(int w,int h) {
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);
        render(c,w,h,0f);
        return bitmap;
    }
    private void render(Canvas canvas,int width,int height,float dt) {
        if(width<=0 || height<=0) return;
        canvas.drawColor(Color.rgb(7,9,21));
        float aspect=portrait?9f/16f:16f/9f;
        float fittedWidth=Math.min(width,height*aspect);
        float fittedHeight=fittedWidth/aspect;
        float left=(width-fittedWidth)*.5f, top=(height-fittedHeight)*.5f;
        int logicalHeight=portrait?711:225;
        canvas.save();
        canvas.translate(left,top);
        canvas.clipRect(0,0,fittedWidth,fittedHeight);
        canvas.scale(fittedWidth/DESIGN_WIDTH,fittedHeight/logicalHeight);
        drawBackground(canvas,logicalHeight);
        if(enabled[WATER]) drawWater(canvas,logicalHeight,dt,power(WATER));
        if(enabled[FIRE]) drawFire(canvas,logicalHeight,dt,power(FIRE),volume(FIRE),count(FIRE));
        if(enabled[RAIN]) drawRain(canvas,logicalHeight,dt,power(RAIN),volume(RAIN),count(RAIN));
        if(enabled[DUST]) drawDust(canvas,logicalHeight,dt,power(DUST),volume(DUST),count(DUST));
        drawRing(canvas,logicalHeight,1f);
        canvas.restore();
    }
    private float power(int i) {return strength[i]/100f;}
    private float volume(int i) {return .2f+size[i]/100f*1.8f;}
    private float count(int i) {return density[i]/100f;}
    private void drawBackground(Canvas c,int h) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0,0,360,h,
                new int[]{0xff080e27,0xff1c163a,0xff090b1a},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,400,h,p);
        p.setShader(null);
        if(backdrop!=null && !backdrop.isRecycled()) {
            float imageRatio=(float)backdrop.getWidth()/backdrop.getHeight();
            float targetRatio=400f/h;
            Rect crop;
            if(imageRatio>targetRatio) {
                int sw=Math.round(backdrop.getHeight()*targetRatio);
                int x=(backdrop.getWidth()-sw)/2;
                crop=new Rect(x,0,x+sw,backdrop.getHeight());
            } else {
                int sh=Math.round(backdrop.getWidth()/targetRatio);
                int y=(backdrop.getHeight()-sh)/2;
                crop=new Rect(0,y,backdrop.getWidth(),y+sh);
            }
            p.setColor(Color.WHITE);p.setAlpha(205);
            c.drawBitmap(backdrop,crop,new RectF(0,0,400,h),p);
            p.setAlpha(255);
            p.setColor(0x79060b20);
            c.drawRect(0,0,400,h,p);
        }
        float bass=live.bass*.6f+kickEnvelope*.8f;
        p.setStyle(Paint.Style.STROKE);
        for(int i=0;i<4;i++) {
            p.setColor(rgba(142,113,255,.10f + bass*.07f));
            p.setStrokeWidth(1.3f);
            c.drawCircle(200,h*.52f,55+i*35+bass*8,p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void drawDust(Canvas c,int h,float dt,float power,float volume,float density) {
        float intensity=level(DUST)*power;
        int count=Math.round(particles[0].length*density);
        for(int i=0;i<count;i++) {
            Particle q=particles[0][i];
            q.x+=(q.vx+intensity*.08f)*dt;
            q.y+=q.vy*dt*(1f+intensity*1.7f);
            if(q.y<-.02f || q.x>1.05f || q.x<-.05f) reset(q,0,false);
            float rad=q.r*volume*(1+intensity*2.7f);
            p.setColor(rgba(220,204,255,q.alpha*(.25f+intensity*.7f)));
            c.drawCircle(q.x*400,q.y*h,rad,p);
        }
    }
    private void drawFire(Canvas c,int h,float dt,float power,float volume,float density) {
        float intensity=level(FIRE)*power;
        int count=Math.round(particles[1].length*density);
        // Procedural flame tongues, height directly controlled by the chosen audio source.
        for(int i=0;i<12;i++) {
            float x=(i+.5f)*400/12f;
            float flame=(10f+28f*intensity)*(0.7f+((i*17)%9)/18f)*volume;
            float sway=(float)Math.sin(phase*7+i)*7;
            Path path=new Path();
            path.moveTo(x-13*volume,h);
            path.quadTo(x+sway-13,h-flame*.55f,x+sway,h-flame);
            path.quadTo(x+sway+10,h-flame*.35f,x+15*volume,h);
            path.close();
            p.setColor(rgba(255,100+(i%4)*13,39,.32f+intensity*.4f));
            c.drawPath(path,p);
        }
        for(int i=0;i<count;i++) {
            Particle q=particles[1][i];
            q.x+=(q.vx+.03f*(float)Math.sin(phase*3+i))*dt*(1+intensity);
            q.y+=q.vy*dt*(.6f+intensity*2.4f);
            if(q.y<0f || q.x<0 || q.x>1) reset(q,1,false);
            float a=q.alpha*(.35f+intensity*.6f)*(1-q.y*.3f);
            p.setColor(rgba(255,120+(i%3)*43,39,a));
            c.drawCircle(q.x*400,q.y*h,(q.r+intensity*2.3f)*volume,p);
        }
    }
    private void drawRain(Canvas c,int h,float dt,float power,float volume,float density) {
        float intensity=level(RAIN)*power;
        int count=Math.round(particles[2].length*density);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for(int i=0;i<count;i++) {
            Particle q=particles[2][i];
            q.x+=q.vx*dt*(.5f+intensity);
            q.y+=q.vy*dt*(.65f+intensity*2f);
            if(q.y>1.1f || q.x>1.1f) reset(q,2,false);
            p.setColor(rgba(159,198,255,.10f+q.alpha*.34f+intensity*.24f));
            p.setStrokeWidth((.6f+volume*.4f));
            float len=(4+intensity*20)*volume;
            c.drawLine(q.x*400,q.y*h,q.x*400-len*.22f,q.y*h-len,p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void drawWater(Canvas c,int h,float dt,float power) {
        float intensity=level(WATER)*power;
        p.setStyle(Paint.Style.STROKE);
        for(Ripple ripple:ripples) {
            if(!ripple.active) continue;
            ripple.age+=dt*(.52f+intensity*.5f);
            if(ripple.age>1.05f) { ripple.active=false;continue; }
            p.setColor(rgba(88,204,255,(1-ripple.age)*(.4f+intensity*.4f)));
            p.setStrokeWidth(1.5f+(1-ripple.age)*2f);
            c.drawCircle(ripple.x*400,ripple.y*h,ripple.age*(85+intensity*80)*volume(WATER),p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(rgba(32,148,226,(.12f+intensity*.14f)*count(WATER)));
        c.drawRect(0,h-16f-intensity*12,400,h,p);
    }
    private void drawRing(Canvas c,int h,float power) {
        float bass=live.bass*power;
        float kick=kickEnvelope*power;
        float radius=45+bass*10+kick*24;
        float cy=h*.50f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4.5f+kick*5);
        p.setColor(rgba(168,132,255,.55f+kick*.42f));
        c.drawCircle(200,cy,radius,p);
        p.setStrokeWidth(1.7f);
        p.setColor(rgba(213,194,255,.7f));
        c.drawCircle(200,cy,radius+8,p);
        int bars=64;
        for(int i=0;i<bars;i++) {
            float angle=(float)(i*Math.PI*2/bars);
            float note=(i%4==0?live.bass:(i%4==1?live.mids:live.highs));
            float length=5+note*22+kick*12;
            float x=(float)Math.cos(angle),y=(float)Math.sin(angle);
            p.setColor(rgba(154+(i%3)*16,135+(i%4)*13,250,.35f+note*.5f));
            p.setStrokeWidth(1.5f);
            c.drawLine(200+x*(radius+13),cy+y*(radius+13),
                    200+x*(radius+13+length),cy+y*(radius+13+length),p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xdd080d20);
        c.drawCircle(200,cy,radius-10,p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD));
        p.setTextSize(19f);
        p.setColor(Color.WHITE);
        c.drawText("RANNEKI",200,cy+4,p);
        p.setTextSize(8f);
        p.setColor(0xffaa9bff);
        c.drawText("AUDIO REACTIVE",200,cy+19,p);
    }
}
