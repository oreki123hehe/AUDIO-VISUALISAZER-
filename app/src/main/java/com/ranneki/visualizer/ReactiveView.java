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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

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
    public static final String[] PROPERTY_NAMES={"Ukuran", "Jumlah", "Kecepatan", "Opacity"};
    public static final String[] BLEND_NAMES={"Normal", "Add", "Screen", "Multiply"};
    public static final String[] MASK_NAMES={"Tanpa mask", "Lingkaran", "Atas", "Bawah"};
    private static final int DESIGN_WIDTH=400;
    private static final int PROPERTY_COUNT=4;
    public static final int MAX_LAYERS=48;
    private final ArrayList<Integer> layerOrder=new ArrayList<>();
    private final boolean[] allocated=new boolean[MAX_LAYERS];
    private final int[] layerType=new int[MAX_LAYERS];
    private final Particle[][] layerParticles=new Particle[MAX_LAYERS][];
    private final Ripple[][] layerRipples=new Ripple[MAX_LAYERS][];
    private final int[] layerRippleCursor=new int[MAX_LAYERS];
    private final float[] prevWaterLevel=new float[MAX_LAYERS],prevSparkLevel=new float[MAX_LAYERS];
    private int activeLayer=0;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blendPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path reusableFlame=new Path();
    private final Path clipPath=new Path();
    private final Random rng=new Random(14350);
    // Effects with particles: dust, fire, rain, smoke, snow, sparks.
    
    private final int[] strength=new int[MAX_LAYERS], size=new int[MAX_LAYERS],
            density=new int[MAX_LAYERS], opacity=new int[MAX_LAYERS],
            velocity=new int[MAX_LAYERS], threshold=new int[MAX_LAYERS],
            attackMs=new int[MAX_LAYERS], releaseMs=new int[MAX_LAYERS],
            minHz=new int[MAX_LAYERS], maxHz=new int[MAX_LAYERS],
            source=new int[MAX_LAYERS],
            blend=new int[MAX_LAYERS], mask=new int[MAX_LAYERS],
            maskRadius=new int[MAX_LAYERS], direction=new int[MAX_LAYERS];
    private final int[][] propertySource=new int[MAX_LAYERS][PROPERTY_COUNT];
    private final float[][] propertyEnvelope=new float[MAX_LAYERS][PROPERTY_COUNT];
    private final AutomationTrack[] automation=new AutomationTrack[MAX_LAYERS];
    private long framePositionMs=0L;
    private final boolean[] enabled=new boolean[MAX_LAYERS];
    private final float[] envelope=new float[MAX_LAYERS];
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
    private float phase=0;
    private AudioAnalyzer.Features live=AudioAnalyzer.Features.zero();
    private long exportPreviousMs=-1;

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
        for(int i=0;i<EFFECT_COUNT;i++) addLayer(i);
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
        track=value;previousPosition=-1;lastKickEvent=-999;
        for(int id:layerOrder) {prevWaterLevel[id]=prevSparkLevel[id]=0f;}
        for(int i=0;i<envelope.length;i++) {
            envelope[i]=0f;
            java.util.Arrays.fill(propertyEnvelope[i],0f);
        }
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
    /** Independent per-property audio routes: -1 = no additional modulation. */
    public int getPropertySource(int fx,int property) {return propertySource[fx][property];}
    public void setPropertySource(int fx,int property,int value) {
        propertySource[fx][property]=value < 0 ? -1 : value % SOURCES.length;
        propertyEnvelope[fx][property]=0;
        invalidate();
    }
    public int getBlend(int i) {return blend[i];}
    public void setBlend(int i,int value) {blend[i]=Math.floorMod(value,BLEND_NAMES.length);invalidate();}
    public int getMask(int i) {return mask[i];}
    public void setMask(int i,int value) {mask[i]=Math.floorMod(value,MASK_NAMES.length);invalidate();}
    public int getMaskRadius(int i) {return maskRadius[i];}
    public void setMaskRadius(int i,int value) {maskRadius[i]=limit(value,0,100);invalidate();}
    public int getDirection(int i) {return direction[i];}
    public void setDirection(int i,int value) {direction[i]=limit(value,0,100);invalidate();}
    public int keyframeCount(int i) {return automation[i].count();}
    public void addStrengthKeyframe(int i,long positionMs) {
        automation[i].put(positionMs,strength[i]);invalidate();
    }
    public void removeStrengthKeyframe(int i,long positionMs) {
        automation[i].removeNear(positionMs,180);invalidate();
    }
    public void clearStrengthKeyframes(int i) {automation[i].clear();invalidate();}
    public String encodeStrengthKeyframes(int i) {return automation[i].encode();}
    public void decodeStrengthKeyframes(int i,String text) {automation[i].decode(text);invalidate();}
    public int[] getLayerOrder() {
        int[] result=new int[layerOrder.size()];
        for(int i=0;i<result.length;i++)result[i]=layerOrder.get(i);
        return result;
    }
    public int getLayerType(int index) {return layerType[index];}
    public int getLayerCount() {return layerOrder.size();}
    public boolean isAllocated(int id) {return id>=0 && id<MAX_LAYERS && allocated[id];}
    public int addLayer(int type) {
        if(type<0 || type>=EFFECT_COUNT)return -1;
        for(int id=0;id<MAX_LAYERS;id++) if(!allocated[id]) {
            allocated[id]=true;layerType[id]=type;layerOrder.add(id);
            strength[id]=80;size[id]=55;density[id]=60;opacity[id]=75;
            velocity[id]=50;threshold[id]=15;attackMs[id]=18;releaseMs[id]=165;
            minHz[id]=35;maxHz[id]=150;
            int[] defaultSources={2,1,4,0,1,3,0,4,2,0};
            source[id]=defaultSources[type];enabled[id]=id<4;
            maskRadius[id]=65;direction[id]=0;blend[id]=0;mask[id]=0;
            automation[id]=new AutomationTrack();
            java.util.Arrays.fill(propertySource[id],-1);
            java.util.Arrays.fill(propertyEnvelope[id],0f);
            envelope[id]=0f;prevWaterLevel[id]=prevSparkLevel[id]=0f;
            initParticles(id);
            invalidate();return id;
        }
        return -1;
    }
    private void initParticles(int id) {
        int type=layerType[id];
        int[] amounts={150,110,175,95,125,110};
        int group=type==DUST?0:type==FIRE?1:type==RAIN?2:type==SMOKE?3:
                type==SNOW?4:type==SPARKS?5:-1;
        if(group>=0) {
            layerParticles[id]=new Particle[amounts[group]];
            for(int i=0;i<layerParticles[id].length;i++) {
                Particle q=new Particle();reset(q,group,true);layerParticles[id][i]=q;
            }
        } else layerParticles[id]=new Particle[0];
        if(type==WATER){
            layerRipples[id]=new Ripple[24];
            for(int i=0;i<24;i++)layerRipples[id][i]=new Ripple();
        }else layerRipples[id]=new Ripple[0];
    }
    public int duplicateLayer(int sourceId) {
        if(!isAllocated(sourceId)) return -1;
        int id=addLayer(layerType[sourceId]);if(id<0)return -1;
        copyParameters(sourceId,id);
        // Place duplicate immediately above its source, not at the end.
        layerOrder.remove(Integer.valueOf(id));
        layerOrder.add(layerOrder.indexOf(sourceId)+1,id);
        invalidate();return id;
    }
    private void copyParameters(int src,int dst) {
        enabled[dst]=enabled[src];source[dst]=source[src];
        strength[dst]=strength[src];density[dst]=density[src];size[dst]=size[src];
        opacity[dst]=opacity[src];velocity[dst]=velocity[src];threshold[dst]=threshold[src];
        attackMs[dst]=attackMs[src];releaseMs[dst]=releaseMs[src];
        minHz[dst]=minHz[src];maxHz[dst]=maxHz[src];
        blend[dst]=blend[src];mask[dst]=mask[src];maskRadius[dst]=maskRadius[src];
        direction[dst]=direction[src];
        System.arraycopy(propertySource[src],0,propertySource[dst],0,PROPERTY_COUNT);
        automation[dst].decode(automation[src].encode());
    }
    public boolean removeLayer(int id) {
        if(!isAllocated(id))return false;
        allocated[id]=false;layerOrder.remove(Integer.valueOf(id));
        layerParticles[id]=null;layerRipples[id]=null;automation[id]=null;
        invalidate();return true;
    }
    public boolean moveLayer(int id,int delta) {
        int idx=layerOrder.indexOf(id),target=idx+delta;
        if(idx<0 || target<0 || target>=layerOrder.size())return false;
        java.util.Collections.swap(layerOrder,idx,target);invalidate();return true;
    }
    public void setLayerOrder(int[] ids) {
        if(ids==null || ids.length!=layerOrder.size())return;
        java.util.HashSet<Integer> unique=new java.util.HashSet<>();
        for(int id:ids)if(!isAllocated(id)||!unique.add(id))return;
        layerOrder.clear();for(int id:ids)layerOrder.add(id);invalidate();
    }
    /** JSON project preset: ordered independent layer instances. */
    public String exportProjectJson() {
        try {
            JSONObject root=new JSONObject();root.put("version",2);
            root.put("portrait",portrait);root.put("eco",economyMode);
            root.put("spectrum",spectrumEnabled);root.put("spectrumScale",spectrumScale);
            root.put("cameraShake",cameraShake);
            JSONArray layers=new JSONArray();
            for(int id:layerOrder) {
                JSONObject o=new JSONObject();o.put("type",layerType[id]);o.put("enabled",enabled[id]);
                o.put("source",source[id]);o.put("strength",strength[id]);o.put("density",density[id]);
                o.put("size",size[id]);o.put("opacity",opacity[id]);o.put("velocity",velocity[id]);
                o.put("threshold",threshold[id]);o.put("attack",attackMs[id]);o.put("release",releaseMs[id]);
                o.put("minHz",minHz[id]);o.put("maxHz",maxHz[id]);o.put("blend",blend[id]);
                o.put("mask",mask[id]);o.put("maskRadius",maskRadius[id]);o.put("direction",direction[id]);
                JSONArray routes=new JSONArray();for(int j:propertySource[id])routes.put(j);
                o.put("routes",routes);o.put("automation",automation[id].encode());layers.put(o);
            }
            root.put("layers",layers);return root.toString();
        }catch(Exception e){throw new IllegalStateException("Preset JSON gagal",e);}
    }
    public boolean importProjectJson(String json) {
        if(json==null||json.isEmpty())return false;
        try {
            JSONObject root=new JSONObject(json);JSONArray arr=root.getJSONArray("layers");
            if(arr.length()>MAX_LAYERS)return false;
            for(int id:getLayerOrder())removeLayer(id);
            for(int j=0;j<arr.length();j++) {
                JSONObject o=arr.getJSONObject(j);
                int id=addLayer(o.getInt("type"));
                if(id<0)throw new IllegalStateException("Invalid layer");
                enabled[id]=o.optBoolean("enabled",true);
                setSource(id,o.optInt("source",0));setStrength(id,o.optInt("strength",80));
                setDensity(id,o.optInt("density",60));setSize(id,o.optInt("size",55));
                setOpacity(id,o.optInt("opacity",75));setVelocity(id,o.optInt("velocity",50));
                setThreshold(id,o.optInt("threshold",15));setAttack(id,o.optInt("attack",18));
                setRelease(id,o.optInt("release",165));
                setFrequency(id,o.optInt("minHz",35),o.optInt("maxHz",150));
                setBlend(id,o.optInt("blend",0));setMask(id,o.optInt("mask",0));
                setMaskRadius(id,o.optInt("maskRadius",65));setDirection(id,o.optInt("direction",0));
                JSONArray routes=o.optJSONArray("routes");
                if(routes!=null)for(int k=0;k<Math.min(PROPERTY_COUNT,routes.length());k++)
                    setPropertySource(id,k,routes.optInt(k,-1));
                automation[id].decode(o.optString("automation",""));
            }
            setPortrait(root.optBoolean("portrait",true));setEconomyMode(root.optBoolean("eco",true));
            setSpectrumEnabled(root.optBoolean("spectrum",true));
            setSpectrumScale(root.optInt("spectrumScale",55));setCameraShake(root.optInt("cameraShake",0));
            invalidate();return true;
        }catch(Exception e){return false;}
    }
    public AudioAnalyzer.Track getTrack() {return track;}
    public Bitmap getBackdrop() {return backdrop;}
    /** On background thread, render consecutive frames in order, from t=0. */
    public void renderExportFrame(Canvas canvas,int width,int height,long positionMs) {
        float dt=exportPreviousMs<0 ? 0f : Math.max(0f,(positionMs-exportPreviousMs)/1000f);
        exportPreviousMs=positionMs;
        advance(positionMs,track!=null,dt);
        render(canvas,width,height,dt);
    }
    public void resetExport() {
        exportPreviousMs=-1;phase=0f;lastKickEvent=-999;previousPosition=-1;
        for(int id:layerOrder) {
            envelope[id]=0;java.util.Arrays.fill(propertyEnvelope[id],0f);
            prevWaterLevel[id]=prevSparkLevel[id]=0;
            for(Ripple ring:layerRipples[id])ring.active=false;
            initParticles(id);
        }
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
        return readSourceId(source[i],i,position);
    }
    private float readSourceId(int src,int i,long position) {
        switch(src) {
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
        advance(position,playing,dt);
        render(canvas,getWidth(),getHeight(),playing?dt:0f);
        if(playing) postInvalidateDelayed(economyMode?33:16);
    }
    private void advance(long position,boolean playing,float dt) {
        live=track==null?AudioAnalyzer.Features.zero():track.at(position);
        if(track!=null) track.spectrumAt(position,spectralBins);
        if(position<previousPosition-100) {
            lastKickEvent=-999;
            for(int i:layerOrder) {prevWaterLevel[i]=prevSparkLevel[i]=0f;for(Ripple ring:layerRipples[i])ring.active=false;}
            for(int i:layerOrder) {envelope[i]=0f;java.util.Arrays.fill(propertyEnvelope[i],0f);}
        }
        previousPosition=position;
        framePositionMs=position;
        if(playing) {
            for(int i:layerOrder) {
                envelope[i]=ReactiveEnvelope.step(envelope[i],readSource(i,position),
                        threshold[i]/100f,attackMs[i],releaseMs[i],dt*1000);
                for(int prop=0;prop<PROPERTY_COUNT;prop++) {
                    int src=propertySource[i][prop];
                    if(src>=0) propertyEnvelope[i][prop]=ReactiveEnvelope.step(
                            propertyEnvelope[i][prop],readSourceId(src,i,position),
                            threshold[i]/100f,attackMs[i],releaseMs[i],dt*1000);
                }
            }
            phase+=dt;
            boolean kickEvent=live.kick>.40f && position-lastKickEvent>110;
            if(kickEvent) lastKickEvent=position;
            for(int i:layerOrder) {
                if(!enabled[i])continue;
                if(layerType[i]==WATER) {
                    float level=envelope[i];
                    boolean event=level>.34f && prevWaterLevel[i]<=.34f;
                    if(event || (kickEvent && source[i]<=2)) {
                        Ripple[] rings=layerRipples[i];
                        Ripple ring=rings[layerRippleCursor[i]++%rings.length];
                        ring.active=true;ring.age=0;ring.amp=clamp(level+.3f);
                        ring.x=.5f+(rng.nextFloat()-.5f)*.22f;ring.y=.72f;
                    }
                    prevWaterLevel[i]=level;
                } else if(layerType[i]==SPARKS) {
                    float level=envelope[i];
                    boolean event=level>.42f && prevSparkLevel[i]<=.42f;
                    if(event || (kickEvent && source[i]<=2))
                        for(Particle q:layerParticles[i])reset(q,5,false);
                    prevSparkLevel[i]=level;
                }
            }

        } else if(track!=null) {
            // Scrubbing or paused preview should reflect the selected timestamp,
            // not stale effect values from the last playback frame.
            for(int i:layerOrder) {
                envelope[i]=ReactiveEnvelope.step(0,readSource(i,position),
                        threshold[i]/100f,0,0,0);
                for(int prop=0;prop<PROPERTY_COUNT;prop++) {
                    int src=propertySource[i][prop];
                    propertyEnvelope[i][prop]=src<0?0:ReactiveEnvelope.step(0,
                            readSourceId(src,i,position),threshold[i]/100f,0,0,0);
                }
            }
        }
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
            activeLayer=fx;
            int save=canvas.save();
            applyMask(canvas,fx,h);
            if(blend[fx]!=0) {
                blendPaint.setXfermode(new PorterDuffXfermode(blendMode(fx)));
                canvas.saveLayer(0,0,400,h,blendPaint);
            }
            switch(layerType[fx]) {
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
            if(blend[fx]!=0) canvas.restore();
            canvas.restoreToCount(save);
        }
        if(spectrumEnabled) drawSpectrumRing(canvas,h);
        canvas.restore();
    }
    private PorterDuff.Mode blendMode(int fx) {
        switch(blend[fx]) {
            case 1: return PorterDuff.Mode.ADD;
            case 2: return PorterDuff.Mode.SCREEN;
            case 3: return PorterDuff.Mode.MULTIPLY;
            default: return PorterDuff.Mode.SRC_OVER;
        }
    }
    private void applyMask(Canvas canvas,int fx,int h) {
        switch(mask[fx]) {
            case 1:
                clipPath.reset();
                clipPath.addCircle(200,h*.5f,Math.max(1,maskRadius[fx]/100f*240),
                        Path.Direction.CW);
                canvas.clipPath(clipPath);
                break;
            case 2: canvas.clipRect(0,0,400,h*maskRadius[fx]/100f);break;
            case 3: canvas.clipRect(0,h*(1-maskRadius[fx]/100f),400,h);break;
            default:break;
        }
    }
    private float parameter(int fx,int property) {
        return propertySource[fx][property]<0?0:propertyEnvelope[fx][property];
    }
    private float intensity(int i) {
        return envelope[i]*automation[i].valueAt(framePositionMs,strength[i])/100f;
    }
    private float volume(int i) {
        return (.2f+size[i]/100f*1.8f)*(1+parameter(i,0)*1.1f);
    }
    private float amount(int i) {
        float modulation=propertySource[i][1]<0?1f:(.45f+parameter(i,1)*1.55f);
        return Math.min(1f,density[i]/100f*(economyMode?.62f:1f)*modulation);
    }
    private float opacity(int i) {
        return opacity[i]/100f*(propertySource[i][3]<0?1f:(.20f+parameter(i,3)*.8f));
    }
    private float speed(int i) {
        return (.15f+velocity[i]/100f*2.7f)*(1+parameter(i,2)*2.2f);
    }
    private void applyWind(Particle q,int fx,float dt) {
        if(direction[fx] == 0 || dt<=0) return;
        double angle=(direction[fx]-1)*2*Math.PI/99;
        float movement=dt*speed(fx)*(.06f+intensity(fx)*.12f);
        q.x+=(float)Math.cos(angle)*movement;
        q.y+=(float)Math.sin(angle)*movement;
    }
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
        float power=intensity(activeLayer);
        int count=(int)(layerParticles[activeLayer].length*amount(activeLayer));
        for(int i=0;i<count;i++) {
            Particle q=layerParticles[activeLayer][i];
            applyWind(q,activeLayer,dt);
            q.x+=(q.vx+power*.08f)*dt*speed(activeLayer);
            q.y+=q.vy*dt*speed(activeLayer)*(1+power*1.7f);
            if(q.y<-.02f||q.x>1.05f||q.x<-.05f) reset(q,0,false);
            p.setColor(rgba(220,204,255,q.alpha*(.25f+power*.7f)*opacity(activeLayer)));
            c.drawCircle(q.x*400,q.y*h,q.r*volume(activeLayer)*(1+power*2.7f),p);
        }
    }
    private void drawFire(Canvas c,int h,float dt) {
        float power=intensity(activeLayer),vol=volume(activeLayer);
        for(int i=0;i<(int)(14*amount(activeLayer));i++) {
            float x=(i+.5f)*400/14f;
            float flame=(10f+28f*power)*(0.7f+((i*17)%9)/18f)*vol;
            float sway=(float)Math.sin(phase*7*speed(activeLayer)+i)*7;
            Path path=reusableFlame;path.reset();path.moveTo(x-13*vol,h);
            path.quadTo(x+sway-13,h-flame*.55f,x+sway,h-flame);
            path.quadTo(x+sway+10,h-flame*.35f,x+15*vol,h);path.close();
            p.setColor(rgba(255,100+(i%4)*13,39,(.32f+power*.4f)*opacity(activeLayer)));
            c.drawPath(path,p);
        }
        int count=(int)(layerParticles[activeLayer].length*amount(activeLayer));
        for(int i=0;i<count;i++) {
            Particle q=layerParticles[activeLayer][i];
            applyWind(q,activeLayer,dt);
            q.x+=(q.vx+.03f*(float)Math.sin(phase*3+i))*dt*speed(activeLayer)*(1+power);
            q.y+=q.vy*dt*speed(activeLayer)*(.6f+power*2.4f);
            if(q.y<0f||q.x<0||q.x>1) reset(q,1,false);
            p.setColor(rgba(255,120+(i%3)*43,39,q.alpha*(.35f+power*.6f)*opacity(activeLayer)));
            c.drawCircle(q.x*400,q.y*h,(q.r+power*2.3f)*vol,p);
        }
    }
    private void drawRain(Canvas c,int h,float dt) {
        float power=intensity(activeLayer),vol=volume(activeLayer);
        int count=(int)(layerParticles[activeLayer].length*amount(activeLayer));
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        for(int i=0;i<count;i++) {
            Particle q=layerParticles[activeLayer][i];
            applyWind(q,activeLayer,dt);
            q.x+=q.vx*dt*speed(activeLayer)*(.5f+power);
            q.y+=q.vy*dt*speed(activeLayer)*(.65f+power*2f);
            if(q.y>1.1f||q.x>1.1f) reset(q,2,false);
            p.setColor(rgba(159,198,255,(.10f+q.alpha*.34f+power*.24f)*opacity(activeLayer)));
            p.setStrokeWidth(.6f+vol*.4f);
            float length=(4f+power*20f)*vol;
            c.drawLine(q.x*400,q.y*h,q.x*400-length*.22f,q.y*h-length,p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void drawWater(Canvas c,int h,float dt) {
        float power=intensity(activeLayer);
        p.setStyle(Paint.Style.STROKE);
        for(Ripple ripple:layerRipples[activeLayer]) {
            if(!ripple.active) continue;
            ripple.age+=dt*(.52f+power*.5f)*speed(activeLayer);
            if(ripple.age>1.05f) {ripple.active=false;continue;}
            p.setColor(rgba(88,204,255,(1-ripple.age)*(.4f+power*.4f)*opacity(activeLayer)));
            p.setStrokeWidth(1.5f+(1-ripple.age)*2f);
            c.drawCircle(ripple.x*400,ripple.y*h,
                    ripple.age*(85+ripple.amp*80)*volume(activeLayer),p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(rgba(32,148,226,(.12f+power*.14f)*amount(activeLayer)*opacity(activeLayer)));
        c.drawRect(0,h-16f-power*12,400,h,p);
    }
    private void drawSmoke(Canvas c,int h,float dt) {
        float power=intensity(activeLayer);
        int count=(int)(layerParticles[activeLayer].length*amount(activeLayer));
        for(int i=0;i<count;i++) {
            Particle q=layerParticles[activeLayer][i];
            applyWind(q,activeLayer,dt);
            q.x+=(q.vx+(float)Math.sin(phase*2+i)*.07f)*dt*speed(activeLayer);
            q.y+=q.vy*dt*speed(activeLayer)*(1+power);
            if(q.y<-.08f||q.x<-.05f||q.x>1.05f) reset(q,3,false);
            p.setColor(rgba(168,161,197,q.alpha*(.10f+power*.18f)*opacity(activeLayer)));
            c.drawCircle(q.x*400,q.y*h,q.r*(2.2f+power*4f)*volume(activeLayer),p);
        }
    }
    private void drawSnow(Canvas c,int h,float dt) {
        float power=intensity(activeLayer);
        int count=(int)(layerParticles[activeLayer].length*amount(activeLayer));
        for(int i=0;i<count;i++) {
            Particle q=layerParticles[activeLayer][i];
            applyWind(q,activeLayer,dt);
            q.x+=(q.vx*.1f+(float)Math.sin(phase*.9f+i)*.025f)*dt*speed(activeLayer);
            q.y+=q.vy*dt*speed(activeLayer)*(1+power*1.2f);
            if(q.y>1.05f||q.x<-.05f||q.x>1.05f) reset(q,4,false);
            p.setColor(rgba(235,242,255,q.alpha*(.35f+power*.55f)*opacity(activeLayer)));
            c.drawCircle(q.x*400,q.y*h,q.r*volume(activeLayer)*(1+power*.8f),p);
        }
    }
    private void drawSparks(Canvas c,int h,float dt) {
        float power=intensity(activeLayer);
        int count=(int)(layerParticles[activeLayer].length*amount(activeLayer));
        p.setStyle(Paint.Style.STROKE);
        for(int i=0;i<count;i++) {
            Particle q=layerParticles[activeLayer][i];
            applyWind(q,activeLayer,dt);
            q.x+=q.vx*dt*speed(activeLayer)*(1+power*2f);
            q.y+=q.vy*dt*speed(activeLayer)*(1+power*2f);
            if(q.y<-.08f||q.x<0||q.x>1) reset(q,5,false);
            float px=q.x*400,py=q.y*h;
            p.setColor(rgba(255,189+(i%4)*14,80,q.alpha*(.35f+power*.65f)*opacity(activeLayer)));
            p.setStrokeWidth(.4f+volume(activeLayer));
            c.drawLine(px,py,px-q.vx*12*volume(activeLayer),py-q.vy*18*volume(activeLayer),p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void drawLightning(Canvas c,int h) {
        float power=intensity(activeLayer);
        if(power<.1f) return;
        p.setStyle(Paint.Style.STROKE);
        int count=Math.max(1,Math.round(4*amount(activeLayer)));
        for(int j=0;j<count;j++) {
            Path path=new Path();float x=50+j*300f/Math.max(1,count);
            path.moveTo(x,0);
            for(int k=1;k<=9;k++) {
                float bend=(float)Math.sin(k*13.7+j*8.3+phase*speed(activeLayer)*33)*18*volume(activeLayer);
                path.lineTo(x+bend,h*k/9f);
            }
            p.setStrokeWidth((1.2f+power*3f)*volume(activeLayer));
            p.setColor(rgba(165,214,255,power*.9f*opacity(activeLayer)));
            c.drawPath(path,p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void drawGlow(Canvas c,int h) {
        float power=intensity(activeLayer);
        p.setStyle(Paint.Style.STROKE);
        int count=Math.max(1,Math.round(10*amount(activeLayer)));
        for(int i=count;i>=0;i--) {
            float width=2+i*2.5f*volume(activeLayer);
            p.setStrokeWidth(width);
            p.setColor(rgba(171,125,255,power*.10f*opacity(activeLayer)));
            c.drawCircle(200,h*.5f,49+power*28,p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void drawGlitch(Canvas c,int h) {
        float power=intensity(activeLayer);
        int count=(int)(15*amount(activeLayer));
        for(int i=0;i<count;i++) {
            float y=((i*131+(int)(phase*speed(activeLayer)*300))%Math.max(1,h));
            float width=(20+power*300)*volume(activeLayer);
            p.setColor(rgba(i%2==0?145:255,85,255,power*.24f*opacity(activeLayer)));
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
