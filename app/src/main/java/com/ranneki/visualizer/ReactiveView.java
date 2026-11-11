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

/** On-device, audio-driven Canvas compositor with user-reorderable effect layers. */
public final class ReactiveView extends View {
    public interface AudioClock { long positionMs(); boolean playing(); }
    public static final int DUST=0, FIRE=1, RAIN=2, WATER=3, SMOKE=4,
            SNOW=5, SPARKS=6, LIGHTNING=7, GLOW=8, GLITCH=9;
    public static final int EFFECT_COUNT=10;
    public static final String[] EFFECT_NAMES={
            "Debu • Dust", "Api • Fire", "Hujan • Rain", "Air • Ripple",
            "Asap • Smoke", "Salju • Snow", "Percikan • Sparks", "Petir • Lightning",
            "Cahaya • Glow", "Distorsi • Glitch"};
    public static final String[] SOURCES={"Kick", "Bass", "Kick + Bass", "Treble",
            "Snare", "Frekuensi custom", "Mid"};
    private static final int DESIGN_WIDTH=400;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng=new Random(14350);
    // Effects with particles: dust, fire, rain, smoke, snow, sparks.
    private final Particle[][] particles=new Particle[6][];
    private final Ripple[] ripples=new Ripple[24];
    private final int[] strength=new int[EFFECT_COUNT], size=new int[EFFECT_COUNT],
            density=new int[EFFECT_COUNT], opacity=new int[EFFECT_COUNT],
            velocity=new int[EFFECT_COUNT], threshold=new int[EFFECT_COUNT],
            attackMs=new int[EFFECT_COUNT], releaseMs=new int[EFFECT_COUNT],
            minHz=new int[EFFECT_COUNT], maxHz=new int[EFFECT_COUNT],
            source=new int[EFFECT_COUNT], layerOrder=new int[EFFECT_COUNT];
    private final boolean[] enabled=new boolean[EFFECT_COUNT];
    private final float[] envelope=new float[EFFECT_COUNT];
    private final float[] spectralBins=new float[BandSpectrum.COUNT];
    private boolean economyMode=true;
    private boolean spectrumEnabled=true;
    private int spectrumScale=55;
    private int cameraShake=0;
    private AudioAnalyzer.Track track;
    private Bitmap backdrop;
    private AudioClock clock;
    private boolean portrait=true;
    private long lastDraw=0, previousPosition=-1, lastKickEvent=-999;
    private float phase=0, previousWater=0f, previousSparks=0f;
    private AudioAnalyzer.Features live=AudioAnalyzer.Features.zero();
    private int nextRipple=0;

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
        int[] amounts={150,110,175,95,125,110};
        for(int group=0;group<particles.length;group++) {
            particles[group]=new Particle[amounts[group]];
            for(int j=0;j<amounts[group];j++) {
                Particle q=new Particle();reset(q,group,true);particles[group][j]=q;
            }
        }
        for(int j=0;j<ripples.length;j++) ripples[j]=new Ripple();
        int[] defaultSources={2,1,4,0,1,3,0,4,2,0};
        for(int i=0;i<EFFECT_COUNT;i++) {
            enabled[i]=i<4;
            strength[i]=80;size[i]=55;density[i]=60;opacity[i]=75;
            velocity[i]=50;threshold[i]=15;attackMs[i]=18;releaseMs[i]=165;
            minHz[i]=35;maxHz[i]=150;source[i]=defaultSources[i];layerOrder[i]=i;
        }
    }
    public void setEconomyMode(boolean v) {economyMode=v;invalidate();}
    public boolean isEconomyMode() {return economyMode;}
    public void setSpectrumEnabled(boolean v) {spectrumEnabled=v;invalidate();}
    public boolean isSpectrumEnabled() {return spectrumEnabled;}
    public void setSpectrumScale(int v) {spectrumScale=limit(v,0,100);invalidate();}
    public int getSpectrumScale() {return spectrumScale;}
    public void setCameraShake(int v) {cameraShake=limit(v,0,100);invalidate();}
    public int getCameraShake() {return cameraShake;}
    public void setClock(AudioClock value) {clock=value;invalidate();}
    public void setTrack(AudioAnalyzer.Track value) {
        track=value;previousPosition=-1;previousWater=0f;previousSparks=0f;
        for(int i=0;i<envelope.length;i++) envelope[i]=0f;
        invalidate();
    }
    public void setBackdrop(Bitmap value) {
        if(backdrop!=null && backdrop!=value && !backdrop.isRecycled()) backdrop.recycle();
        backdrop=value;invalidate();
    }
    public void setPortrait(boolean value) {portrait=value;invalidate();}
    public boolean isPortrait() {return portrait;}
    public void setStrength(int i,int value) {strength[i]=limit(value,0,100);invalidate();}
    public void setDensity(int i,int value) {density[i]=limit(value,0,100);invalidate();}
    public void setSize(int i,int value) {size[i]=limit(value,0,100);invalidate();}
    public void setOpacity(int i,int value) {opacity[i]=limit(value,0,100);invalidate();}
    public void setVelocity(int i,int value) {velocity[i]=limit(value,0,100);invalidate();}
    public void setThreshold(int i,int value) {threshold[i]=limit(value,0,95);invalidate();}
    public void setAttack(int i,int value) {attackMs[i]=limit(value,0,1000);invalidate();}
    public void setRelease(int i,int value) {releaseMs[i]=limit(value,0,1500);invalidate();}
    public void setFrequency(int i,int min,int max) {
        minHz[i]=limit(min,25,10000);
        maxHz[i]=limit(max,minHz[i],10000);
        invalidate();
    }
    public void setEnabled(int i,boolean value) {enabled[i]=value;invalidate();}
    public boolean isEnabled(int i) {return enabled[i];}
    public void setSource(int i,int value) {source[i]=Math.floorMod(value,SOURCES.length);invalidate();}
    public int getSource(int i) {return source[i];}
    public int getStrength(int i) {return strength[i];}
    public int getDensity(int i) {return density[i];}
    public int getSize(int i) {return size[i];}
    public int getOpacity(int i) {return opacity[i];}
    public int getVelocity(int i) {return velocity[i];}
    public int getThreshold(int i) {return threshold[i];}
    public int getAttack(int i) {return attackMs[i];}
    public int getRelease(int i) {return releaseMs[i];}
    public int getMinHz(int i) {return minHz[i];}
    public int getMaxHz(int i) {return maxHz[i];}
    public int[] getLayerOrder() {return layerOrder.clone();}
    public void setLayerOrder(int[] value) {
        if(value==null || value.length!=EFFECT_COUNT) return;
        boolean[] seen=new boolean[EFFECT_COUNT];
        for(int index:value) {
            if(index<0 || index>=EFFECT_COUNT || seen[index]) return;
            seen[index]=true;
        }
        System.arraycopy(value,0,layerOrder,0,EFFECT_COUNT);
        invalidate();
    }
    public boolean moveLayer(int effect,int delta) {
        for(int i=0;i<layerOrder.length;i++) if(layerOrder[i]==effect) {
            int next=i+delta;
            if(next<0 || next>=layerOrder.length) return false;
            int swap=layerOrder[next];layerOrder[next]=effect;layerOrder[i]=swap;
            invalidate();return true;
        }
        return false;
    }
    private static int limit(int v,int lo,int hi) {return Math.max(lo,Math.min(hi,v));}
    private static float clamp(float v) {return BandSpectrum.clamp(v);}
    private static int rgba(int r,int g,int b,float a) {
        return Color.argb((int)(clamp(a)*255),r,g,b);
    }
    private void reset(Particle q,int group,boolean initial) {
        q.x=rng.nextFloat();q.y=initial?rng.nextFloat():(group==1||group==3||group==5?1.05f:-.05f);
        q.vx=(rng.nextFloat()-.5f)*.32f;
        q.vy=(.15f+rng.nextFloat()*.32f);
        q.r=.5f+rng.nextFloat()*1.65f;
        q.alpha=.3f+rng.nextFloat()*.68f;
        if(group==0) q.vy=-.02f-rng.nextFloat()*.09f;
        if(group==1) q.vy=-.22f-rng.nextFloat()*.42f;
        if(group==2) {q.vx=.08f+rng.nextFloat()*.12f;q.vy=.60f+rng.nextFloat()*.55f;}
        if(group==3) {q.vy=-.03f-rng.nextFloat()*.10f;q.r+=1.3f;}
        if(group==4) {q.vy=.04f+rng.nextFloat()*.13f;q.r+=.7f;}
        if(group==5) {q.vy=-.5f-rng.nextFloat()*.5f;q.vx=(rng.nextFloat()-.5f)*.55f;}
    }
    private float readSource(int i,long position) {
        switch(source[i]) {
            case 0:return live.kick;
            case 1:return live.bass;
            case 2:return clamp(live.bass*.58f+live.kick*.88f);
            case 3:return live.highs;
            case 4:return live.snare;
            case 5:return track==null?0:track.bandAt(position,minHz[i],maxHz[i]);
            case 6:return live.mids;
            default:return 0;
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now=SystemClock.elapsedRealtime();
        float dt=lastDraw==0?0f:Math.min(.05f,(now-lastDraw)/1000f);
        lastDraw=now;
        long position=clock==null?0:clock.positionMs();
        boolean playing=clock!=null && clock.playing() && track!=null;
        live=track==null?AudioAnalyzer.Features.zero():track.at(position);
        if(track!=null) track.spectrumAt(position,spectralBins);
        if(position<previousPosition-100) {
            previousWater=0;previousSparks=0;lastKickEvent=-999;
            for(int i=0;i<envelope.length;i++) envelope[i]=0f;
            for(Ripple ring:ripples) ring.active=false;
        }
        previousPosition=position;
        if(playing) {
            for(int i=0;i<EFFECT_COUNT;i++) {
                envelope[i]=ReactiveEnvelope.step(envelope[i],readSource(i,position),
                        threshold[i]/100f,attackMs[i],releaseMs[i],dt*1000);
            }
            phase+=dt;
            float water=envelope[WATER];
            float sparks=envelope[SPARKS];
            boolean waterEvent=water>.34f && previousWater<=.34f;
            boolean sparkEvent=sparks>.42f && previousSparks<=.42f;
            boolean kickEvent=live.kick>.40f && position-lastKickEvent>110;
            if(kickEvent) lastKickEvent=position;
            if((waterEvent || (kickEvent && source[WATER]<=2)) && enabled[WATER]) {
                Ripple ring=ripples[nextRipple++%ripples.length];
                ring.active=true;ring.age=0;ring.amp=clamp(water+.3f);
                ring.x=.5f+(rng.nextFloat()-.5f)*.22f;ring.y=.72f;
            }
            if((sparkEvent || (kickEvent && source[SPARKS]<=2)) && enabled[SPARKS]) {
                for(Particle q:particles[5]) reset(q,5,false);
            }
            previousWater=water;previousSparks=sparks;
        }
        render(canvas,getWidth(),getHeight(),playing?dt:0f);
        if(playing) postInvalidateDelayed(economyMode?33:16);
    }
    public Bitmap snapshot(int width,int height) {
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        render(new Canvas(bitmap),width,height,0f);
        return bitmap;
    }
    private void render(Canvas canvas,int width,int height,float dt) {
        if(width<=0 || height<=0) return;
        canvas.drawColor(Color.rgb(7,9,21));
        float aspect=portrait?9f/16f:16f/9f;
        float fittedWidth=Math.min(width,height*aspect),fittedHeight=fittedWidth/aspect;
        float left=(width-fittedWidth)*.5f,top=(height-fittedHeight)*.5f;
        int h=portrait?711:225;
        canvas.save();canvas.translate(left,top);
        canvas.clipRect(0,0,fittedWidth,fittedHeight);
        canvas.scale(fittedWidth/DESIGN_WIDTH,fittedHeight/h);
        if(cameraShake>0 && track!=null) {
            float shake=live.kick*cameraShake/100f*8f;
            canvas.translate((float)Math.sin(phase*61)*shake,(float)Math.cos(phase*73)*shake);
        }
        drawBackground(canvas,h);
        for(int fx:layerOrder) {
            if(!enabled[fx]) continue;
            switch(fx) {
                case DUST:drawDust(canvas,h,dt);break;
                case FIRE:drawFire(canvas,h,dt);break;
                case RAIN:drawRain(canvas,h,dt);break;
                case WATER:drawWater(canvas,h,dt);break;
                case SMOKE:drawSmoke(canvas,h,dt);break;
                case SNOW:drawSnow(canvas,h,dt);break;
                case SPARKS:drawSparks(canvas,h,dt);break;
                case LIGHTNING:drawLightning(canvas,h);break;
                case GLOW:drawGlow(canvas,h);break;
                case GLITCH:drawGlitch(canvas,h);break;
            }
        }
        if(spectrumEnabled) drawSpectrumRing(canvas,h);
        canvas.restore();
    }
    private float intensity(int i) {return envelope[i]*strength[i]/100f;}
    private float volume(int i) {return .2f+size[i]/100f*1.8f;}
    private float amount(int i) {
        return density[i]/100f*(economyMode?.62f:1f);
    }
    private float opacity(int i) {return opacity[i]/100f;}
    private float speed(int i) {return .15f+velocity[i]/100f*2.7f;}
    private void drawBackground(Canvas c,int h) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0,0,360,h,
                new int[]{0xff080e27,0xff1c163a,0xff090b1a},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,400,h,p);p.setShader(null);
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
            p.setColor(Color.WHITE);p.setAlpha(220);
            c.drawBitmap(backdrop,crop,new RectF(0,0,400,h),p);
            p.setAlpha(255);p.setColor(0x77060b20);c.drawRect(0,0,400,h,p);
        }
    }
    private void drawDust(Canvas c,int h,float dt) {
        float power=intensity(DUST);
        int count=(int)(particles[0].length*amount(DUST));
        for(int i=0;i<count;i++) {
            Particle q=particles[0][i];
            q.x+=(q.vx+power*.08f)*dt*speed(DUST);
            q.y+=q.vy*dt*speed(DUST)*(1+power*1.7f);
            if(q.y<-.02f||q.x>1.05f||q.x<-.05f) reset(q,0,false);
            p.setColor(rgba(220,204,255,q.alpha*(.25f+power*.7f)*opacity(DUST)));
            c.drawCircle(q.x*400,q.y*h,q.r*volume(DUST)*(1+power*2.7f),p);
        }
    }
    private void drawFire(Canvas c,int h,float dt) {
        float power=intensity(FIRE),vol=volume(FIRE);
        for(int i=0;i<(int)(14*amount(FIRE));i++) {
            float x=(i+.5f)*400/14f;
            float flame=(10f+28f*power)*(0.7f+((i*17)%9)/18f)*vol;
            float sway=(float)Math.sin(phase*7*speed(FIRE)+i)*7;
            Path path=new Path();path.moveTo(x-13*vol,h);
            path.quadTo(x+sway-13,h-flame*.55f,x+sway,h-flame);
            path.quadTo(x+sway+10,h-flame*.35f,x+15*vol,h);path.close();
            p.setColor(rgba(255,100+(i%4)*13,39,(.32f+power*.4f)*opacity(FIRE)));
            c.drawPath(path,p);
        }
        int count=(int)(particles[1].length*amount(FIRE));
        for(int i=0;i<count;i++) {
            Particle q=particles[1][i];
            q.x+=(q.vx+.03f*(float)Math.sin(phase*3+i))*dt*speed(FIRE)*(1+power);
            q.y+=q.vy*dt*speed(FIRE)*(.6f+power*2.4f);
            if(q.y<0f||q.x<0||q.x>1) reset(q,1,false);
            p.setColor(rgba(255,120+(i%3)*43,39,q.alpha*(.35f+power*.6f)*opacity(FIRE)));
            c.drawCircle(q.x*400,q.y*h,(q.r+power*2.3f)*vol,p);
        }
    }
    private void drawRain(Canvas c,int h,float dt) {
        float power=intensity(RAIN),vol=volume(RAIN);
        int count=(int)(particles[2].length*amount(RAIN));
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        for(int i=0;i<count;i++) {
            Particle q=particles[2][i];
            q.x+=q.vx*dt*speed(RAIN)*(.5f+power);
            q.y+=q.vy*dt*speed(RAIN)*(.65f+power*2f);
            if(q.y>1.1f||q.x>1.1f) reset(q,2,false);
            p.setColor(rgba(159,198,255,(.10f+q.alpha*.34f+power*.24f)*opacity(RAIN)));
            p.setStrokeWidth(.6f+vol*.4f);
            float length=(4f+power*20f)*vol;
            c.drawLine(q.x*400,q.y*h,q.x*400-length*.22f,q.y*h-length,p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void drawWater(Canvas c,int h,float dt) {
        float power=intensity(WATER);
        p.setStyle(Paint.Style.STROKE);
        for(Ripple ripple:ripples) {
            if(!ripple.active) continue;
            ripple.age+=dt*(.52f+power*.5f)*speed(WATER);
            if(ripple.age>1.05f) {ripple.active=false;continue;}
            p.setColor(rgba(88,204,255,(1-ripple.age)*(.4f+power*.4f)*opacity(WATER)));
            p.setStrokeWidth(1.5f+(1-ripple.age)*2f);
            c.drawCircle(ripple.x*400,ripple.y*h,
                    ripple.age*(85+ripple.amp*80)*volume(WATER),p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(rgba(32,148,226,(.12f+power*.14f)*amount(WATER)*opacity(WATER)));
        c.drawRect(0,h-16f-power*12,400,h,p);
    }
    private void drawSmoke(Canvas c,int h,float dt) {
        float power=intensity(SMOKE);
        int count=(int)(particles[3].length*amount(SMOKE));
        for(int i=0;i<count;i++) {
            Particle q=particles[3][i];
            q.x+=(q.vx+(float)Math.sin(phase*2+i)*.07f)*dt*speed(SMOKE);
            q.y+=q.vy*dt*speed(SMOKE)*(1+power);
            if(q.y<-.08f||q.x<-.05f||q.x>1.05f) reset(q,3,false);
            p.setColor(rgba(168,161,197,q.alpha*(.10f+power*.18f)*opacity(SMOKE)));
            c.drawCircle(q.x*400,q.y*h,q.r*(2.2f+power*4f)*volume(SMOKE),p);
        }
    }
    private void drawSnow(Canvas c,int h,float dt) {
        float power=intensity(SNOW);
        int count=(int)(particles[4].length*amount(SNOW));
        for(int i=0;i<count;i++) {
            Particle q=particles[4][i];
            q.x+=(q.vx*.1f+(float)Math.sin(phase*.9f+i)*.025f)*dt*speed(SNOW);
            q.y+=q.vy*dt*speed(SNOW)*(1+power*1.2f);
            if(q.y>1.05f||q.x<-.05f||q.x>1.05f) reset(q,4,false);
            p.setColor(rgba(235,242,255,q.alpha*(.35f+power*.55f)*opacity(SNOW)));
            c.drawCircle(q.x*400,q.y*h,q.r*volume(SNOW)*(1+power*.8f),p);
        }
    }
    private void drawSparks(Canvas c,int h,float dt) {
        float power=intensity(SPARKS);
        int count=(int)(particles[5].length*amount(SPARKS));
        p.setStyle(Paint.Style.STROKE);
        for(int i=0;i<count;i++) {
            Particle q=particles[5][i];
            q.x+=q.vx*dt*speed(SPARKS)*(1+power*2f);
            q.y+=q.vy*dt*speed(SPARKS)*(1+power*2f);
            if(q.y<-.08f||q.x<0||q.x>1) reset(q,5,false);
            float px=q.x*400,py=q.y*h;
            p.setColor(rgba(255,189+(i%4)*14,80,q.alpha*(.35f+power*.65f)*opacity(SPARKS)));
            p.setStrokeWidth(.4f+volume(SPARKS));
            c.drawLine(px,py,px-q.vx*12*volume(SPARKS),py-q.vy*18*volume(SPARKS),p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void drawLightning(Canvas c,int h) {
        float power=intensity(LIGHTNING);
        if(power<.1f) return;
        p.setStyle(Paint.Style.STROKE);
        int count=Math.max(1,Math.round(4*amount(LIGHTNING)));
        for(int j=0;j<count;j++) {
            Path path=new Path();float x=50+j*300f/Math.max(1,count);
            path.moveTo(x,0);
            for(int k=1;k<=9;k++) {
                float bend=(float)Math.sin(k*13.7+j*8.3+phase*speed(LIGHTNING)*33)*18*volume(LIGHTNING);
                path.lineTo(x+bend,h*k/9f);
            }
            p.setStrokeWidth((1.2f+power*3f)*volume(LIGHTNING));
            p.setColor(rgba(165,214,255,power*.9f*opacity(LIGHTNING)));
            c.drawPath(path,p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void drawGlow(Canvas c,int h) {
        float power=intensity(GLOW);
        p.setStyle(Paint.Style.STROKE);
        int count=Math.max(1,Math.round(10*amount(GLOW)));
        for(int i=count;i>=0;i--) {
            float width=2+i*2.5f*volume(GLOW);
            p.setStrokeWidth(width);
            p.setColor(rgba(171,125,255,power*.10f*opacity(GLOW)));
            c.drawCircle(200,h*.5f,49+power*28,p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void drawGlitch(Canvas c,int h) {
        float power=intensity(GLITCH);
        int count=(int)(15*amount(GLITCH));
        for(int i=0;i<count;i++) {
            float y=((i*131+(int)(phase*speed(GLITCH)*300))%Math.max(1,h));
            float width=(20+power*300)*volume(GLITCH);
            p.setColor(rgba(i%2==0?145:255,85,255,power*.24f*opacity(GLITCH)));
            c.drawRect((float)Math.sin(i*27+phase)*120+120,y,
                    Math.min(400,(float)Math.sin(i*27+phase)*120+120+width),y+1+power*3,p);
        }
    }
    private void drawSpectrumRing(Canvas c,int h) {
        float bass=live.bass,kick=live.kick;
        float radius=(45+bass*10+kick*24)*(.55f+spectrumScale/100f),cy=h*.5f;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4.5f+kick*5);
        p.setColor(rgba(168,132,255,.55f+kick*.42f));c.drawCircle(200,cy,radius,p);
        p.setStrokeWidth(1.7f);p.setColor(rgba(213,194,255,.7f));
        c.drawCircle(200,cy,radius+8,p);
        int bars=64;
        for(int i=0;i<bars;i++) {
            float angle=(float)(i*Math.PI*2/bars);
            int band=i<32?i:63-i;
            float note=spectralBins[band];
            float len=5+note*22+kick*12;
            float x=(float)Math.cos(angle),y=(float)Math.sin(angle);
            p.setColor(rgba(154+(i%3)*16,135+(i%4)*13,250,.35f+note*.5f));
            p.setStrokeWidth(1.5f);
            c.drawLine(200+x*(radius+13),cy+y*(radius+13),
                    200+x*(radius+13+len),cy+y*(radius+13+len),p);
        }
        p.setStyle(Paint.Style.FILL);p.setColor(0xdd080d20);
        c.drawCircle(200,cy,radius-10,p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD));
        p.setTextSize(19f);p.setColor(Color.WHITE);c.drawText("RANNEKI",200,cy+4,p);
        p.setTextSize(8f);p.setColor(0xffaa9bff);c.drawText("AUDIO REACTIVE",200,cy+19,p);
    }
}
