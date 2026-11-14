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

/** Audio-time scene model shared by GLES composition and the Canvas compatibility path. */
public final class ReactiveView extends View {
    public interface AudioClock { long positionMs(); boolean playing(); }
    public static final int DUST=0, FIRE=1, RAIN=2, WATER=3, SMOKE=4,
            SNOW=5, SPARKS=6, LIGHTNING=7, GLOW=8, GLITCH=9;
    public static final int EFFECT_COUNT=10;
    public static final int TEXT=10, IMAGE=11, VIDEO=12, VISUALIZER=13, CONTROL=14;
    public static final int LAYER_TYPE_COUNT=15;
    public static final String[] EFFECT_NAMES={
            "Debu • Dust", "Api • Fire", "Hujan • Rain", "Air • Ripple",
            "Asap • Smoke", "Salju • Snow", "Percikan • Sparks", "Petir • Lightning",
            "Cahaya • Glow", "Distorsi • Glitch"};
    public static final String[] LAYER_NAMES={
            "Debu • Dust", "Api • Fire", "Hujan • Rain", "Air • Ripple",
            "Asap • Smoke", "Salju • Snow", "Percikan • Sparks", "Petir • Lightning",
            "Cahaya • Glow", "Distorsi • Glitch", "Judul • Text", "Gambar • Image", "Video • Clip (tanpa audio)", "Visualizer • Spectrum", "Kontrol • Null"};
    public static final String[] SOURCES={"Kick", "Bass", "Kick + Bass", "Treble",
            "Snare", "Frekuensi custom", "Mid", "Vokal • stem", "Drum • stem", "Bass • stem", "Instrumen lain • stem"};
    public static final String[] STEM_NAMES={"Vokal", "Drum", "Bass", "Instrumen lain"};
    public static final String[] PROPERTY_NAMES={"Ukuran", "Jumlah", "Kecepatan", "Opacity"};
    public static final String[] BLEND_NAMES={"Normal", "Add", "Screen", "Multiply"};
    public static final String[] MASK_NAMES={"Tanpa mask", "Lingkaran", "Atas", "Bawah", "Polygon custom"};
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
    private final String[] layerName=new String[MAX_LAYERS];
    private final String[] layerText=new String[MAX_LAYERS],imageUri=new String[MAX_LAYERS],videoUri=new String[MAX_LAYERS];
    private final String[] polygonEncoded=new String[MAX_LAYERS];
    private final PolygonMask[] polygons=new PolygonMask[MAX_LAYERS];
    private final VideoFrameLayer[] videoSources=new VideoFrameLayer[MAX_LAYERS];
    private final Bitmap[] imageLayers=new Bitmap[MAX_LAYERS];
    private final int[] textColor=new int[MAX_LAYERS];
    private final int[] offsetX=new int[MAX_LAYERS],offsetY=new int[MAX_LAYERS],
            layerScale=new int[MAX_LAYERS],layerRotation=new int[MAX_LAYERS];
    private final long[] startTimeMs=new long[MAX_LAYERS],endTimeMs=new long[MAX_LAYERS];
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
    public static final String[] KEYFRAME_NAMES=AutomationParameters.NAMES;
    private final AudioRoute[][] audioRoutes=new AudioRoute[MAX_LAYERS][AutomationParameters.COUNT];
    private final int[] visualizerStyle=new int[MAX_LAYERS],accentColor=new int[MAX_LAYERS],
            visualizerGlow=new int[MAX_LAYERS],logoSize=new int[MAX_LAYERS],logoShape=new int[MAX_LAYERS];
    private final AudioAnalyzer.Track[] stemTracks=new AudioAnalyzer.Track[4];
    private final String[] stemUris={"","","",""};
    private final VisualizerMesh canvasMesh=new VisualizerMesh();
    private int chromatic=35,vignette=22;
    public int getChromatic(){return chromatic;}
    public int getVignette(){return vignette;}
    public void setPostFx(int rgb,int shade){chromatic=limit(rgb,0,100);vignette=limit(shade,0,100);projectChanged();}
    private final AutomationTrack[][] automation=new AutomationTrack[MAX_LAYERS][AutomationParameters.COUNT];
    // Evaluated once per render frame rather than once per particle.
    private final float[][] animatedValues=new float[MAX_LAYERS][AutomationParameters.COUNT];
    private final MotionStyle[] motion=new MotionStyle[MAX_LAYERS];
    private final int[] parentLayer=new int[MAX_LAYERS];
    private final LayerAdvanced[] advanced=new LayerAdvanced[MAX_LAYERS];
    private CameraRig camera=new CameraRig();
    private SongSections sections=new SongSections();
    public LayerAdvanced getAdvanced(int id){return advanced[id].copy();}
    public void setAdvanced(int id,LayerAdvanced a){advanced[id]=a.copy();if(videoSources[id]!=null)videoSources[id].setTiming(a.clip);projectChanged();}
    public CameraRig getCamera(){return CameraRig.decode(camera.encode());}
    public void setCamera(CameraRig c){camera=CameraRig.decode(c.encode());projectChanged();}
    public SongSections getSections(){return SongSections.decode(sections.encode());}
    public void setSections(SongSections s){sections=SongSections.decode(s.encode());projectChanged();}
    public void arrangeAll(boolean on){for(int id:layerOrder)advanced[id].autoArrange=on;projectChanged();}
    public void editTransform(int id,int param,int value,long ms){
        if(keyframeCount(id,param)>0)setKeyframe(id,param,ms,value);
        else if(param==AutomationParameters.X)setLayerX(id,value);else if(param==AutomationParameters.Y)setLayerY(id,value);
        else if(param==AutomationParameters.SCALE)setLayerScale(id,value);else if(param==AutomationParameters.ROTATION)setLayerRotation(id,value);
    }
    public int splitLayer(int id,long ms){
        if(ms<=startTimeMs[id]||ms>=endTimeMs[id])return -1;long end=endTimeMs[id];int next=duplicateLayer(id);if(next<0)return -1;
        advanced[next].motionOffsetMs=advanced[id].motionOffsetMs+ms-startTimeMs[id];
        advanced[next].clip.offsetMs=advanced[id].clip.offsetMs+(long)((ms-startTimeMs[id])*advanced[id].clip.speed);
        advanced[id].clip.fadeOut=0;advanced[next].clip.fadeIn=0;
        setLayerEnd(id,ms);setLayerStart(next,ms);setLayerEnd(next,end);
        if(videoSources[id]!=null)videoSources[id].setTiming(advanced[id].clip);if(videoSources[next]!=null)videoSources[next].setTiming(advanced[next].clip);projectChanged();return next;
    }
    public boolean joinLayer(int id){int at=layerOrder.indexOf(id);if(at<0||at+1>=layerOrder.size())return false;int next=layerOrder.get(at+1);
        if(startTimeMs[next]!=endTimeMs[id])return false;
        ClipTiming a=advanced[id].clip,b=advanced[next].clip;
        long elapsed=endTimeMs[id]-startTimeMs[id];
        if(a.speed!=b.speed||a.inMs!=b.inMs||a.outMs!=b.outMs||a.loop!=b.loop||a.fadeOut!=0||b.fadeIn!=0)return false;
        if(Math.abs(b.offsetMs-a.offsetMs-(long)(elapsed*a.speed))>1||advanced[next].motionOffsetMs!=advanced[id].motionOffsetMs+elapsed)return false;
        try{JSONObject left=layerJson(id),right=layerJson(next);
            for(JSONObject o:new JSONObject[]{left,right}){o.remove("name");o.remove("startMs");o.remove("endMs");o.getJSONObject("advanced").remove("clip");o.getJSONObject("advanced").remove("motionOffsetMs");}
            if(!left.toString().equals(right.toString()))return false;
        }catch(Exception invalid){return false;}
        for(int child:layerOrder)if(parentLayer[child]==next)parentLayer[child]=id;
        endTimeMs[id]=endTimeMs[next];advanced[id].clip.outMs=advanced[next].clip.outMs;advanced[id].clip.fadeOut=advanced[next].clip.fadeOut;removeLayer(next);if(videoSources[id]!=null)videoSources[id].setTiming(advanced[id].clip);projectChanged();return true;
    }
    @SuppressWarnings("unchecked") private final ArrayList<LayerFilter>[] filters=(ArrayList<LayerFilter>[])new ArrayList<?>[MAX_LAYERS];
    public MotionStyle getMotion(int id){return motion[id].copy();}
    public void setMotion(int id,MotionStyle value){motion[id]=MotionStyle.decode(value.encode());projectChanged();}
    public int getParent(int id){return parentLayer[id];}
    public boolean setParent(int id,int parent){
        if(parent!= -1&&!isAllocated(parent))return false;
        for(int p=parent,n=0;p>=0&&n<=MAX_LAYERS;p=parentLayer[p],n++)if(p==id||n==MAX_LAYERS)return false;
        parentLayer[id]=parent;projectChanged();return true;
    }
    public LayerFilter[] getFilters(int id){LayerFilter[] a=new LayerFilter[filters[id].size()];for(int i=0;i<a.length;i++)a[i]=filters[id].get(i).copy();return a;}
    public void setFilters(int id,LayerFilter[] value){if(value.length>LayerFilter.MAX)throw new IllegalArgumentException("Maximum 12 filters");
        ArrayList<LayerFilter> copy=new ArrayList<>();for(LayerFilter f:value)copy.add(f.copy());filters[id]=copy;projectChanged();}
    public MotionCurve getCurve(int id,int parameter,long ms){return automation[id][parameter].curveAt(ms);}
    public boolean setCurve(int id,int parameter,long ms,MotionCurve curve,boolean all){
        if(all){automation[id][parameter].setAllCurves(curve);projectChanged();return true;}
        boolean changed=automation[id][parameter].setCurve(ms,curve);if(changed)projectChanged();return changed;
    }
    private float valueAt(int id,int param,long ms){
        float base=automation[id][param].sample(ms,baseValue(id,param));AudioRoute route=audioRoutes[id][param];
        AudioAnalyzer.Track t=signalTrack(route.source);
        if(route.source>=0&&t!=null)base=route.applyFloat(param,base,t.envelopeAt(ms,route.source>=7?7:route.source,route.minHz,route.maxHz,0,route.attack,route.release));
        return Math.max(AutomationParameters.min(param),Math.min(AutomationParameters.max(param),base));
    }
    private final java.util.LinkedHashMap<Long,MotionStyle.Pose[]> poseCache=new java.util.LinkedHashMap<Long,MotionStyle.Pose[]>(4,.75f,true){
        protected boolean removeEldestEntry(java.util.Map.Entry<Long,MotionStyle.Pose[]> entry){return size()>2;}
    };
    float depthAt(int id){float depth=0;for(int p=id,n=0;p>=0&&n<MAX_LAYERS;p=parentLayer[p],n++)depth+=advanced[p].depth;return depth;}
    private CameraRig cameraAt(long ms){AudioAnalyzer.Track t=signalTrack(camera.source);return camera.at(ms,t==null?0:t.envelopeAt(ms,camera.source>=7?7:camera.source,25,10000,0,20,180));}
    MotionStyle.Pose poseAt(int id,long ms){return cameraAt(ms).project(poseAt(id,ms,0),depthAt(id));}
    MotionStyle.Pose pivotAt(int id,long ms){MotionStyle.Pose p=worldPoseAt(id,ms);LayerAdvanced a=advanced[id];double r=Math.toRadians(p.rotation);return cameraAt(ms).project(new MotionStyle.Pose(p.x+(float)(a.pivotX*Math.cos(r)-a.pivotY*Math.sin(r))*p.scale,p.y+(float)(a.pivotX*Math.sin(r)+a.pivotY*Math.cos(r))*p.scale,1,0),depthAt(id));}
    void moveOnCanvas(int id,long ms,float initialX,float initialY,float dx,float dy,int baseX,int baseY){CameraRig c=cameraAt(ms);float h=portrait?711:225;float[] a=c.unproject(initialX,initialY,depthAt(id),h),b=c.unproject(initialX+dx,initialY+dy,depthAt(id),h);float x=b[0]-a[0],y=b[1]-a[1];
        if(parentLayer[id]>=0){MotionStyle.Pose p=worldPoseAt(parentLayer[id],ms);double r=Math.toRadians(-p.rotation);float xx=(float)(x*Math.cos(r)-y*Math.sin(r))/p.scale;y=(float)(x*Math.sin(r)+y*Math.cos(r))/p.scale;x=xx;}
        editTransform(id,AutomationParameters.X,baseX+Math.round(x/3.6f),ms);editTransform(id,AutomationParameters.Y,baseY+Math.round(y/h*100),ms);
    }
    void pivotOnCanvas(int id,long ms,float x,float y){MotionStyle.Pose world=worldPoseAt(id,ms);float h=portrait?711:225;float[] point=cameraAt(ms).unproject(x,y,depthAt(id),h);double r=Math.toRadians(-world.rotation);float dx=point[0]-world.x,dy=point[1]-world.y;LayerAdvanced a=getAdvanced(id);
        float ax=Math.max(-200,Math.min(200,(float)(dx*Math.cos(r)-dy*Math.sin(r))/world.scale)),ay=Math.max(-355,Math.min(355,(float)(dx*Math.sin(r)+dy*Math.cos(r))/world.scale));
        MotionStyle.Pose parent=parentLayer[id]<0?new MotionStyle.Pose(0,0,1,0):worldPoseAt(parentLayer[id],ms);r=Math.toRadians(world.rotation-parent.rotation);float scale=world.scale/parent.scale,dax=ax-a.pivotX,day=ay-a.pivotY;
        float compensateX=(float)(dax*Math.cos(r)-day*Math.sin(r))*scale-dax,compensateY=(float)(dax*Math.sin(r)+day*Math.cos(r))*scale-day;
        a.pivotX=ax;a.pivotY=ay;setAdvanced(id,a);editTransform(id,AutomationParameters.X,keyframeValueAt(id,AutomationParameters.X,ms)+Math.round(compensateX/3.6f),ms);editTransform(id,AutomationParameters.Y,keyframeValueAt(id,AutomationParameters.Y,ms)+Math.round(compensateY/h*100),ms);
    }
    private MotionStyle.Pose worldPoseAt(int id,long ms){return poseAt(id,ms,0);}
    private MotionStyle.Pose poseAt(int id,long ms,int depth){
        MotionStyle.Pose[] cached=poseCache.get(ms);if(cached==null){cached=new MotionStyle.Pose[MAX_LAYERS];poseCache.put(ms,cached);}
        if(cached[id]!=null)return cached[id];
        MotionStyle m=motion[id];AudioAnalyzer.Track t=signalTrack(m.source);float signal=t==null||m.type==0||m.type==4||m.type==5||m.type==6||m.type==7||m.type==9?0:t.motionAt(ms,m.source>=7?7:m.source,m.type==2?1:m.type==3?2:0,minHz[id],maxHz[id],m.speed);
        MotionStyle.Pose p=new MotionStyle.Pose((valueAt(id,AutomationParameters.X,ms)-50)*3.6f,
            (valueAt(id,AutomationParameters.Y,ms)-50)*(portrait?711f:225f)/100f,
            .25f+valueAt(id,AutomationParameters.SCALE,ms)*.015f,(valueAt(id,AutomationParameters.ROTATION,ms)-50)*3.6f);
        p=m.apply(p,ms-startTimeMs[id]+advanced[id].motionOffsetMs,signal);
        LayerAdvanced a=advanced[id];
        if(a.motionPath!=null){float progress=Math.max(0,ms-startTimeMs[id]+a.motionOffsetMs)/(float)a.pathDuration;boolean reverse=a.pathMode==1&&((int)progress%2==1);float f=a.pathMode==2?Math.min(1,progress):progress%1;if(reverse)f=1-f;f=a.pathCurve.map(f);float[] point=a.motionPath.at(f),origin=a.motionPath.at(0);
            p=new MotionStyle.Pose(p.x+(point[0]-origin[0])*180,p.y+(point[1]-origin[1])*(portrait?320:100),p.scale,p.rotation+(a.followPath?a.motionPath.tangent(f)+(reverse?180:0):0));}
        if(a.autoArrange)p=new MotionStyle.Pose(p.x,p.y,p.scale*(.9f+.1f*sections.intensity(ms)),p.rotation);
        p=CameraRig.pivot(p,a.pivotX,a.pivotY);
        cached[id]=parentLayer[id]>=0&&depth<MAX_LAYERS?poseAt(parentLayer[id],ms,depth+1).child(p):p;return cached[id];
    }
    private MotionStyle.Pose cameraPose(MotionStyle.Pose p,long ms){
        if(cameraShake==0||track==null)return p;float power=track.at(ms).kick*cameraShake/100f*8f;
        return new MotionStyle.Pose(p.x+(float)Math.sin(ms*.061)*power,p.y+(float)Math.cos(ms*.073)*power,p.scale,p.rotation);
    }
    private GpuSceneFrame.Item decorate(GpuSceneFrame.Item item,int id){
        item.feather=advanced[id].feather;
        item.pose=cameraPose(poseAt(id,framePositionMs),framePositionMs);
        long previous=Math.max(startTimeMs[id],framePositionMs-17);item.previousPose=cameraPose(poseAt(id,previous),previous);item.motionBlur=motion[id].blur/100f;
        item.filters=new float[filters[id].size()*2];int n=0;
        for(LayerFilter f:filters[id]){AudioRoute r=f.route;AudioAnalyzer.Track t=signalTrack(r.source);
            float v=t==null||r.source<0?f.amount:r.applyFloat(AutomationParameters.OPACITY,f.amount,t.envelopeAt(framePositionMs,r.source>=7?7:r.source,r.minHz,r.maxHz,0,r.attack,r.release));
            item.filters[n++]=f.type;item.filters[n++]=v/100f;
        }return item;
    }
    private long framePositionMs=0L;
    private final boolean[] enabled=new boolean[MAX_LAYERS];
    private final float[] envelope=new float[MAX_LAYERS];
    private final float[] spectralBins=new float[BandSpectrum.COUNT];
    private boolean economyMode=true;
    private String shaderSource=ShaderSource.CLASSIC;
    public String getShaderSource(){return shaderSource;}
    public void setShaderSource(String source) {shaderSource=ShaderSource.validate(source);projectChanged();}
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
    private boolean inExportFrame=false;
    private boolean gpuPreviewActive=false;
    private GpuFrameListener gpuFrameListener;
    /** Published scene snapshots are drawn in their configured order by the GLES compositor. */
    public interface GpuFrameListener {void onGpuFrame(GpuSceneFrame frame);}
    public void setGpuFrameListener(GpuFrameListener listener) {gpuFrameListener=listener;}
    public void setGpuPreviewActive(boolean active) {gpuPreviewActive=active;projectChanged();}
    public boolean isGpuPreviewActive(){return gpuPreviewActive;}
    private Runnable editListener;
    public void setEditListener(Runnable listener){editListener=listener;}
    private void projectChanged(){poseCache.clear();invalidate();if(editListener!=null)editListener.run();}

    private float cameraShakeX(){
        float power=live.kick*cameraShake/100f*8f;
        return (float)Math.sin(phase*61f)*power;
    }
    private float cameraShakeY(){
        float power=live.kick*cameraShake/100f*8f;
        return (float)Math.cos(phase*73f)*power;
    }
    private GpuEffectFrame snapshotGpuFrame(){
        java.util.ArrayList<GpuEffectFrame.Layer> batches=new java.util.ArrayList<>();
        for(int id:layerOrder){
            int type=layerType[id];
            if(type>=EFFECT_COUNT||!enabled[id]||framePositionMs<startTimeMs[id]
                    ||framePositionMs>endTimeMs[id])continue;
            PolygonMask p=polygons[id];
            float[] polygon=null;
            if(mask[id]==4 && p!=null){
                polygon=new float[p.count()*2];
                for(int i=0;i<p.count();i++){
                    polygon[i*2]=p.x(i);polygon[i*2+1]=p.y(i);
                }
            }
            int count=Math.round(512f*amount(id));
            MotionStyle.Pose pose=worldPoseAt(id,framePositionMs);
            int dir=animated(id,AutomationParameters.DIRECTION);
            batches.add(new GpuEffectFrame.Layer(id,type,blend[id],mask[id],count,
                    intensity(id),opacity(id),volume(id),speed(id),
                    (pose.x+cameraShakeX())/400f,(pose.y+cameraShakeY())/(portrait?711f:225f),
                    pose.scale,pose.rotation,
                    dir==0?-1f:(dir-1)/99f,
                    smooth(id,AutomationParameters.MASK_RADIUS)/100f,polygon));
            batches.get(batches.size()-1).depth=depthAt(id);
        }
        return new GpuEffectFrame(Math.max(0L,framePositionMs),portrait,
                batches.toArray(new GpuEffectFrame.Layer[0]));
    }
    /** The GL compositor handles all standard scene types, including camera shake. */
    private boolean gpuOnlyAt(long positionMs) {return true;}
    private GpuSceneFrame snapshotGpuSceneFrame(){
        GpuSceneFrame current=buildGpuSceneFrame();boolean needed=false;
        for(GpuSceneFrame.Item i:current.drawOrder)if(i.motionBlur>0&&(i.sprite==null||i.sprite.video==null)){needed=true;break;}
        if(needed){long at=framePositionMs;current.shutter=new GpuSceneFrame[2];
            for(int i=0;i<2;i++){sampleGpuState(Math.max(0,at-(i+1)*8));current.shutter[i]=buildGpuSceneFrame();}sampleGpuState(at);}
        return current;
    }
    private void sampleGpuState(long ms){framePositionMs=ms;phase=ms/1000f;live=track==null?AudioAnalyzer.Features.zero():track.at(ms);if(track!=null)track.spectrumAt(ms,spectralBins);sampleAutomation(ms);evaluateAudioConnections();}
    private GpuSceneFrame buildGpuSceneFrame() {
        GpuEffectFrame fx=snapshotGpuFrame();
        java.util.ArrayList<GpuSceneFrame.Item> drawOrder=new java.util.ArrayList<>();
        int h=portrait?711:225;
        for(int id:layerOrder){
            if(!enabled[id] || framePositionMs<startTimeMs[id] || framePositionMs>endTimeMs[id])continue;
            int type=layerType[id];
            if(type==CONTROL)continue;
            if(type==VISUALIZER){if(spectrumEnabled)drawOrder.add(decorate(new GpuSceneFrame.Item(visualizerFrame(id)),id));continue;}
            if(type<EFFECT_COUNT){
                for(GpuEffectFrame.Layer effect:fx.layers)
                    if(effect.id==id){drawOrder.add(decorate(new GpuSceneFrame.Item(effect),id));break;}
                continue;
            }
            Bitmap bitmap=type==IMAGE?imageLayers[id]:null;
            VideoFrameLayer video=type==VIDEO?videoSources[id]:null;
            String text=type==TEXT?layerText[id]:null;
            if(type==IMAGE && (bitmap==null||bitmap.isRecycled()))continue;
            if(type==VIDEO && video==null)continue;
            if(type==TEXT && (text==null||text.isEmpty()))continue;
            float contentWidth=400f,contentHeight=h;
            if(type==IMAGE){
                float strength=volume(id)*(1f+intensity(id)*.32f);
                float fit=Math.min(260f*strength/bitmap.getWidth(),h*.55f*strength/bitmap.getHeight());
                contentWidth=bitmap.getWidth()*fit;contentHeight=bitmap.getHeight()*fit;
                if(advanced[id].fillCanvas){contentWidth=400;contentHeight=h;}
            }else if(type==TEXT){
                float fontSize=(12f+volume(id)*20f)*(1f+.45f*intensity(id));
                contentWidth=Math.min(395f,280f*fontSize/31f);
                contentHeight=Math.min(h*.9f,120f*fontSize/31f);
            }
            float[] poly=null;
            if(mask[id]==4&&polygons[id]!=null){
                PolygonMask p=polygons[id];poly=new float[p.count()*2];
                for(int j=0;j<p.count();j++){poly[2*j]=p.x(j);poly[2*j+1]=p.y(j);}
            }
            MotionStyle.Pose pose=worldPoseAt(id,framePositionMs);
            GpuSceneFrame.Sprite sprite=new GpuSceneFrame.Sprite(
                    id,type,bitmap,video,text,textColor[id],framePositionMs,startTimeMs[id],
                    contentWidth,contentHeight,opacity(id),
                    pose.x,pose.y,pose.scale,pose.rotation,
                    blend[id],mask[id],smooth(id,AutomationParameters.MASK_RADIUS)/100f,poly);
            sprite.depth=depthAt(id);
            if(type==IMAGE&&advanced[id].fillCanvas)sprite.crop=GpuSceneMath.centerCrop(bitmap.getWidth(),bitmap.getHeight(),400f/h);
            if(type==VIDEO)sprite.crop=GpuSceneMath.centerCrop(video.width(),video.height(),400f/h);
            drawOrder.add(decorate(new GpuSceneFrame.Item(sprite),id));
        }
        GpuSceneFrame scene=new GpuSceneFrame(fx,backdrop,spectralBins,gpuOnlyAt(framePositionMs),
                false,spectrumScale,live.kick,live.bass,live.snare,live.highs,
                cameraShakeX(),cameraShakeY(),shaderSource,
                drawOrder.toArray(new GpuSceneFrame.Item[0]));
        scene.camera=cameraAt(framePositionMs);scene.chromatic=chromatic/100f;scene.vignette=vignette/100f;return scene;
    }
    /** Advances audio/automation without allocating a full-screen CPU canvas. */
    public GpuSceneFrame renderGpuOnlyExportFrame(long positionMs) {
        float dt=exportPreviousMs<0 ? 0f : Math.max(0f,(positionMs-exportPreviousMs)/1000f);
        exportPreviousMs=positionMs;
        advance(positionMs,track!=null,dt);
        return snapshotGpuSceneFrame();
    }
    public boolean supportsGpuOnlyAt(long ms) {return gpuOnlyAt(ms);}

    /** Single-pass CPU base and GLES particle uniforms for the export GL context. */
    public GpuEffectFrame renderGpuExportFrame(Canvas canvas,int width,int height,long positionMs){
        float dt=exportPreviousMs<0?0f:Math.max(0f,(positionMs-exportPreviousMs)/1000f);
        exportPreviousMs=positionMs;
        inExportFrame=true;
        try{
            advance(positionMs,track!=null,dt);
            render(canvas,width,height,dt,true);
            return snapshotGpuFrame();
        } finally {inExportFrame=false;}
    }
    private FrameMetrics previewMetrics=new FrameMetrics();
    private PreviewPacing previewPacing=new PreviewPacing();
    private long previewMetricsStartNs=0L;
    /** Measured on the actual Android device, not a synthetic FPS estimate. */
    public void resetPreviewStats() {
        previewMetrics=new FrameMetrics();
        previewPacing.reset();
        previewMetricsStartNs=0L;
    }
    public String previewReport(){
        long elapsed=previewMetricsStartNs==0L?0L:System.nanoTime()-previewMetricsStartNs;
        return previewMetrics.summary(elapsed)+"\n"+previewPacing.summary();
    }

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
        addLayer(VISUALIZER);
    }
    public void setEconomyMode(boolean v) {economyMode=v;projectChanged();}
    public boolean isEconomyMode() {return economyMode;}
    public void setSpectrumEnabled(boolean v) {spectrumEnabled=v;projectChanged();}
    public boolean isSpectrumEnabled() {return spectrumEnabled;}
    public void setSpectrumScale(int v) {spectrumScale=limit(v,0,100);projectChanged();}
    public int getSpectrumScale() {return spectrumScale;}
    public void setCameraShake(int v) {cameraShake=limit(v,0,100);projectChanged();}
    public int getCameraShake() {return cameraShake;}
    public void setClock(AudioClock value) {clock=value;projectChanged();}
    public void setTrack(AudioAnalyzer.Track value) {
        track=value;previousPosition=-1;lastKickEvent=-999;
        for(int id:layerOrder) {prevWaterLevel[id]=prevSparkLevel[id]=0f;}
        for(int i=0;i<envelope.length;i++) {
            envelope[i]=0f;
            java.util.Arrays.fill(propertyEnvelope[i],0f);
        }
        projectChanged();
    }
    public void setBackdrop(Bitmap value) {
        // Published GPU frames may still reference the previous bitmap; let GC release it.
        backdrop=value;projectChanged();
    }
    public void setPortrait(boolean value) {portrait=value;projectChanged();}
    public boolean isPortrait() {return portrait;}
    public void setStrength(int i,int value) {strength[i]=limit(value,0,100);projectChanged();}
    public void setDensity(int i,int value) {density[i]=limit(value,0,100);projectChanged();}
    public void setSize(int i,int value) {size[i]=limit(value,0,100);projectChanged();}
    public void setOpacity(int i,int value) {opacity[i]=limit(value,0,100);projectChanged();}
    public void setVelocity(int i,int value) {velocity[i]=limit(value,0,100);projectChanged();}
    public void setThreshold(int i,int value) {threshold[i]=limit(value,0,95);projectChanged();}
    public void setAttack(int i,int value) {attackMs[i]=limit(value,0,1000);projectChanged();}
    public void setRelease(int i,int value) {releaseMs[i]=limit(value,0,1500);projectChanged();}
    public void setFrequency(int i,int min,int max) {
        minHz[i]=limit(min,25,10000);
        maxHz[i]=limit(max,minHz[i],10000);
        projectChanged();
    }
    public void setEnabled(int i,boolean value) {enabled[i]=value;projectChanged();}
    public boolean isEnabled(int i) {return enabled[i];}
    public void setSource(int i,int value) {source[i]=Math.floorMod(value,SOURCES.length);projectChanged();}
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
        projectChanged();
    }
    public AudioRoute getAudioRoute(int id,int parameter){AutomationParameters.check(parameter);return audioRoutes[id][parameter].copy();}
    public void setAudioRoute(int id,int parameter,AudioRoute route){
        AutomationParameters.check(parameter);AudioRoute copy=new AudioRoute();copy.decode(route.encode());
        audioRoutes[id][parameter]=copy;projectChanged();
    }
    public void setStem(int slot,AudioAnalyzer.Track value,String uri){stemTracks[slot]=value;stemUris[slot]=uri==null?"":uri;projectChanged();}
    public String getStemUri(int slot){return stemUris[slot];}
    public boolean hasStem(int slot){return stemTracks[slot]!=null;}
    public void copyAnalysisTo(ReactiveView target){target.setTrack(track);for(int i=0;i<4;i++)target.setStem(i,stemTracks[i],stemUris[i]);}
    private AudioAnalyzer.Track signalTrack(int src){return src>=7&&src<11?stemTracks[src-7]:track;}
    private float signalAt(int src,int lo,int hi,int threshold,int attack,int release){
        AudioAnalyzer.Track data=signalTrack(src);
        return data==null?0:data.envelopeAt(framePositionMs,src>=7?7:src,lo,hi,threshold,attack,release);
    }
    public int getVisualizerStyle(int id){return visualizerStyle[id];}
    public void setVisualizerStyle(int id,int v){visualizerStyle[id]=Math.floorMod(v,VisualizerMesh.STYLES.length);projectChanged();}
    public int getAccentColor(int id){return accentColor[id];}
    public void setAccentColor(int id,int color){accentColor[id]=color;projectChanged();}
    public int getVisualizerGlow(int id){return visualizerGlow[id];}
    public void setVisualizerGlow(int id,int value){visualizerGlow[id]=limit(value,0,100);projectChanged();}
    public int getLogoSize(int id){return logoSize[id];}
    public void setLogoSize(int id,int value){logoSize[id]=limit(value,0,100);projectChanged();}
    public int getLogoShape(int id){return logoShape[id];}
    public void setLogoShape(int id,int value){logoShape[id]=Math.floorMod(value,9);projectChanged();}
    public int getBlend(int i) {return blend[i];}
    public void setBlend(int i,int value) {blend[i]=Math.floorMod(value,BLEND_NAMES.length);projectChanged();}
    public int getMask(int i) {return mask[i];}
    public void setMask(int i,int value) {mask[i]=Math.floorMod(value,MASK_NAMES.length);projectChanged();}
    public int getMaskRadius(int i) {return maskRadius[i];}
    public void setMaskRadius(int i,int value) {maskRadius[i]=limit(value,0,100);projectChanged();}
    public int getDirection(int i) {return direction[i];}
    public void setDirection(int i,int value) {direction[i]=limit(value,0,100);projectChanged();}
    public int keyframeCount(int i) {return keyframeCount(i,AutomationParameters.STRENGTH);}
    public long[] getStrengthKeyframePositions(int i) {return getKeyframePositions(i,AutomationParameters.STRENGTH);}
    public int keyframeCount(int id,int param) {
        AutomationParameters.check(param);
        return isAllocated(id)?automation[id][param].count():0;
    }
    public long[] getKeyframePositions(int id,int param) {
        AutomationParameters.check(param);
        return isAllocated(id)?automation[id][param].positions():new long[0];
    }
    /** Aggregate keyframe markers for the timeline overview. */
    public long[] getAllKeyframePositions(int id) {
        if(!isAllocated(id))return new long[0];
        java.util.TreeSet<Long> positions=new java.util.TreeSet<>();
        for(AutomationTrack item:automation[id])for(long time:item.positions())positions.add(time);
        long[] out=new long[positions.size()];int i=0;
        for(long time:positions)out[i++]=time;
        return out;
    }
    public int keyframeValueAt(int id,int param,long timeMs) {
        AutomationParameters.check(param);
        return automation[id][param].valueAt(Math.max(0,timeMs),baseValue(id,param));
    }
    public void setKeyframe(int id,int param,long positionMs,int value) {
        AutomationParameters.check(param);
        if(!isAllocated(id))return;
        automation[id][param].put(positionMs,AutomationParameters.clamp(param,value));projectChanged();
    }
    public boolean moveKeyframe(int id,int param,long fromMs,long toMs) {
        AutomationParameters.check(param);
        if(!isAllocated(id))return false;
        boolean moved=automation[id][param].move(fromMs,toMs);
        if(moved)projectChanged();
        return moved;
    }
    public void removeKeyframe(int id,int param,long positionMs) {
        AutomationParameters.check(param);
        if(isAllocated(id)) {automation[id][param].removeNear(positionMs,180);projectChanged();}
    }
    public void clearKeyframes(int id,int param) {
        AutomationParameters.check(param);
        if(isAllocated(id)) {automation[id][param].clear();projectChanged();}
    }
    public void addStrengthKeyframe(int id,long ms) {setKeyframe(id,AutomationParameters.STRENGTH,ms,strength[id]);}
    public void removeStrengthKeyframe(int id,long ms) {removeKeyframe(id,AutomationParameters.STRENGTH,ms);}
    public void clearStrengthKeyframes(int id) {clearKeyframes(id,AutomationParameters.STRENGTH);}
    public String encodeStrengthKeyframes(int id) {return automation[id][AutomationParameters.STRENGTH].encode();}
    public void decodeStrengthKeyframes(int id,String encoded) {
        automation[id][AutomationParameters.STRENGTH].decode(encoded);projectChanged();
    }
    private int baseValue(int id,int param){
        switch(param){
            case AutomationParameters.STRENGTH:return strength[id];
            case AutomationParameters.SIZE:return size[id];
            case AutomationParameters.DENSITY:return density[id];
            case AutomationParameters.VELOCITY:return velocity[id];
            case AutomationParameters.OPACITY:return opacity[id];
            case AutomationParameters.X:return offsetX[id];
            case AutomationParameters.Y:return offsetY[id];
            case AutomationParameters.SCALE:return layerScale[id];
            case AutomationParameters.ROTATION:return layerRotation[id];
            case AutomationParameters.THRESHOLD:return threshold[id];
            case AutomationParameters.ATTACK:return attackMs[id];
            case AutomationParameters.RELEASE:return releaseMs[id];
            case AutomationParameters.MIN_HZ:return minHz[id];
            case AutomationParameters.MAX_HZ:return maxHz[id];
            case AutomationParameters.MASK_RADIUS:return maskRadius[id];
            case AutomationParameters.DIRECTION:return direction[id];
            default:throw new IllegalArgumentException("Unknown parameter");
        }
    }
    private void sampleAutomation(long timeMs){
        for(int id:layerOrder)for(int param=0;param<AutomationParameters.COUNT;param++)
            animatedValues[id][param]=automation[id][param].sample(timeMs,baseValue(id,param));
    }
    private int animated(int id,int param){return Math.round(animatedValues[id][param]);}
    private float smooth(int id,int param){return Math.max(AutomationParameters.min(param),Math.min(AutomationParameters.max(param),animatedValues[id][param]));}
    public int[] getLayerOrder() {
        int[] result=new int[layerOrder.size()];
        for(int i=0;i<result.length;i++)result[i]=layerOrder.get(i);
        return result;
    }
    public int getLayerType(int index) {return layerType[index];}
    public String getLayerName(int index) {return layerName[index];}
    public void setLayerName(int index,String name) {
        if(isAllocated(index)&&name!=null&&!name.trim().isEmpty()) {
            layerName[index]=name.trim().substring(0,Math.min(60,name.trim().length()));projectChanged();
        }
    }
    public int getLayerX(int id){return offsetX[id];}
    public int getLayerY(int id){return offsetY[id];}
    public int getLayerScale(int id){return layerScale[id];}
    public int getLayerRotation(int id){return layerRotation[id];}
    public void setLayerX(int id,int v){offsetX[id]=limit(v,0,100);projectChanged();}
    public void setLayerY(int id,int v){offsetY[id]=limit(v,0,100);projectChanged();}
    public void setLayerScale(int id,int v){layerScale[id]=limit(v,0,100);projectChanged();}
    public void setLayerRotation(int id,int v){layerRotation[id]=limit(v,0,100);projectChanged();}
    public long getLayerStart(int id){return startTimeMs[id];}
    public long getLayerEnd(int id){return endTimeMs[id];}
    public void setLayerStart(int id,long ms){
        startTimeMs[id]=Math.max(0,ms);
        if(endTimeMs[id]!=Long.MAX_VALUE&&endTimeMs[id]<startTimeMs[id])endTimeMs[id]=startTimeMs[id];
        projectChanged();
    }
    public void setLayerEnd(int id,long ms){endTimeMs[id]=Math.max(startTimeMs[id],ms);projectChanged();}
    public void clearLayerRange(int id){startTimeMs[id]=0;endTimeMs[id]=Long.MAX_VALUE;projectChanged();}

    public int getLayerCount() {return layerOrder.size();}
    public String getLayerText(int id){return layerText[id];}
    public void setLayerText(int id,String value){
        if(isAllocated(id)&&value!=null){layerText[id]=value.substring(0,Math.min(120,value.length()));projectChanged();}
    }
    public int getLayerColor(int id){return textColor[id];}
    public void setLayerColor(int id,int color){textColor[id]=color;projectChanged();}
    public String getImageUri(int id){return imageUri[id];}
    public Bitmap getLayerImage(int id){return imageLayers[id];}
    public void setLayerImage(int id,Bitmap bitmap,String uri){
        if(!isAllocated(id)||(layerType[id]!=IMAGE&&layerType[id]!=VISUALIZER))return;
        Bitmap old=imageLayers[id];
        // Do not recycle a bitmap while a published GPU frame may be uploading it.
        imageLayers[id]=bitmap;imageUri[id]=uri==null?"":uri;projectChanged();
    }
    /** Persisted content URI supports reloading clips in previews and independent export scenes. */
    public String getVideoUri(int id){return videoUri[id]==null?"":videoUri[id];}
    public void setLayerVideoUri(int id,String path) throws Exception {
        if(!isAllocated(id)||layerType[id]!=VIDEO)return;
        VideoFrameLayer replacement=null;
        if(path!=null&&!path.isEmpty())replacement=new VideoFrameLayer(getContext(),android.net.Uri.parse(path));
        VideoFrameLayer old=videoSources[id];videoSources[id]=replacement;
        videoUri[id]=path==null?"":path;if(replacement!=null)replacement.setTiming(advanced[id].clip);
        if(old!=null)old.close();
        projectChanged();
    }
    public String getPolygonMask(int id){return polygonEncoded[id];}
    public void setPolygonMask(int id,String points){
        if(!isAllocated(id))return;
        PolygonMask parsed=PolygonMask.parse(points);
        polygons[id]=parsed;polygonEncoded[id]=parsed.encode();projectChanged();
    }
    public void copyVideoLayersTo(ReactiveView destination) throws Exception {
        int[] src=getLayerOrder(),dst=destination.getLayerOrder();
        for(int i=0;i<Math.min(src.length,dst.length);i++){
            if(layerType[src[i]]==VIDEO && videoUri[src[i]]!=null && !videoUri[src[i]].isEmpty())
                destination.setLayerVideoUri(dst[i],videoUri[src[i]]);
        }
    }
    public void closeVideoLayers(){
        for(int id=0;id<MAX_LAYERS;id++){
            if(videoSources[id]!=null){videoSources[id].close();videoSources[id]=null;}
        }
    }
    public void copyImageLayersTo(ReactiveView destination) {
        int[] src=getLayerOrder(),dst=destination.getLayerOrder();
        for(int i=0;i<Math.min(src.length,dst.length);i++){
            Bitmap b=imageLayers[src[i]];
            if((layerType[src[i]]==IMAGE||layerType[src[i]]==VISUALIZER) && b!=null && !b.isRecycled())
                destination.setLayerImage(dst[i],b.copy(Bitmap.Config.ARGB_8888,false),imageUri[src[i]]);
        }
    }
    public void recycleLayerImages() {
        for(int id=0;id<MAX_LAYERS;id++){
            Bitmap b=imageLayers[id];
            if(b!=null&&!b.isRecycled())b.recycle();
            imageLayers[id]=null;
        }
    }

    public boolean isAllocated(int id) {return id>=0 && id<MAX_LAYERS && allocated[id];}
    public int addLayer(int type) {
        if(type<0 || type>=LAYER_TYPE_COUNT)return -1;
        for(int id=0;id<MAX_LAYERS;id++) if(!allocated[id]) {
            allocated[id]=true;layerType[id]=type;layerOrder.add(id);
            layerName[id]=LAYER_NAMES[type];layerText[id]="RANNEKI";imageUri[id]="";videoUri[id]="";
            polygonEncoded[id]=PolygonMask.DEFAULT;polygons[id]=PolygonMask.parse(PolygonMask.DEFAULT);
            textColor[id]=Color.WHITE;offsetX[id]=50;offsetY[id]=50;
            layerScale[id]=50;layerRotation[id]=50;
            startTimeMs[id]=0;endTimeMs[id]=Long.MAX_VALUE;
            strength[id]=80;size[id]=55;density[id]=60;opacity[id]=75;
            velocity[id]=50;threshold[id]=15;attackMs[id]=18;releaseMs[id]=165;
            minHz[id]=35;maxHz[id]=150;
            int[] defaultSources={2,1,4,0,1,3,0,4,2,0};
            source[id]=type<EFFECT_COUNT?defaultSources[type]:2;
            enabled[id]=(type>=EFFECT_COUNT)||type==DUST;
            motion[id]=new MotionStyle();advanced[id]=new LayerAdvanced();parentLayer[id]=-1;filters[id]=new ArrayList<>();
            visualizerStyle[id]=0;accentColor[id]=0xff38d9ef;visualizerGlow[id]=40;logoSize[id]=80;logoShape[id]=0;
            if(type==VISUALIZER){textColor[id]=0xffaa88ff;minHz[id]=25;maxHz[id]=10000;
                source[id]=1;opacity[id]=100;threshold[id]=0;attackMs[id]=30;releaseMs[id]=200;}
            for(int param=0;param<AutomationParameters.COUNT;param++)audioRoutes[id][param]=new AudioRoute();
            if(type==VISUALIZER){audioRoutes[id][AutomationParameters.SCALE].source=0;audioRoutes[id][AutomationParameters.SCALE].depth=12;}
            maskRadius[id]=65;direction[id]=0;blend[id]=0;mask[id]=0;
            for(int param=0;param<AutomationParameters.COUNT;param++)
                automation[id][param]=AutomationParameters.newTrack(param);
            for(int param=0;param<AutomationParameters.COUNT;param++)
                animatedValues[id][param]=baseValue(id,param);
            java.util.Arrays.fill(propertySource[id],-1);
            java.util.Arrays.fill(propertyEnvelope[id],0f);
            envelope[id]=0f;prevWaterLevel[id]=prevSparkLevel[id]=0f;
            initParticles(id);
            projectChanged();return id;
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
        projectChanged();return id;
    }
    private void copyParameters(int src,int dst) {
        enabled[dst]=enabled[src];source[dst]=source[src];
        advanced[dst]=advanced[src].copy();motion[dst]=motion[src].copy();parentLayer[dst]=parentLayer[src];for(LayerFilter f:filters[src])filters[dst].add(f.copy());
        visualizerStyle[dst]=visualizerStyle[src];accentColor[dst]=accentColor[src];
        visualizerGlow[dst]=visualizerGlow[src];logoSize[dst]=logoSize[src];logoShape[dst]=logoShape[src];
        for(int param=0;param<AutomationParameters.COUNT;param++)audioRoutes[dst][param]=audioRoutes[src][param].copy();
        strength[dst]=strength[src];density[dst]=density[src];size[dst]=size[src];
        opacity[dst]=opacity[src];velocity[dst]=velocity[src];threshold[dst]=threshold[src];
        attackMs[dst]=attackMs[src];releaseMs[dst]=releaseMs[src];
        minHz[dst]=minHz[src];maxHz[dst]=maxHz[src];
        blend[dst]=blend[src];mask[dst]=mask[src];maskRadius[dst]=maskRadius[src];
        direction[dst]=direction[src];
        System.arraycopy(propertySource[src],0,propertySource[dst],0,PROPERTY_COUNT);
        for(int param=0;param<AutomationParameters.COUNT;param++)
            automation[dst][param].decode(automation[src][param].encode());
        layerText[dst]=layerText[src];imageUri[dst]=imageUri[src];textColor[dst]=textColor[src];
        polygonEncoded[dst]=polygonEncoded[src];polygons[dst]=polygons[src];
        videoUri[dst]=videoUri[src];
        if(layerType[src]==VIDEO && videoUri[src]!=null&&!videoUri[src].isEmpty()) {
            try{setLayerVideoUri(dst,videoUri[src]);}catch(Exception ignored){}
        }
        Bitmap b=imageLayers[src];
        if(b!=null&&!b.isRecycled())imageLayers[dst]=b.copy(Bitmap.Config.ARGB_8888,false);
        layerName[dst]=layerName[src]+" (copy)";
        offsetX[dst]=offsetX[src];offsetY[dst]=offsetY[src];
        layerScale[dst]=layerScale[src];layerRotation[dst]=layerRotation[src];
        startTimeMs[dst]=startTimeMs[src];endTimeMs[dst]=endTimeMs[src];
    }
    public boolean removeLayer(int id) {
        if(!isAllocated(id))return false;
        allocated[id]=false;layerOrder.remove(Integer.valueOf(id));
        for(int child:layerOrder)if(parentLayer[child]==id)parentLayer[child]=-1;
        // GPU snapshots retain old artwork until the following frame.
        imageLayers[id]=null;imageUri[id]=null;layerText[id]=null;
        if(videoSources[id]!=null){videoSources[id].close();videoSources[id]=null;}
        videoUri[id]=null;polygonEncoded[id]=null;polygons[id]=null;
        layerParticles[id]=null;layerRipples[id]=null;
        java.util.Arrays.fill(automation[id],null);
        projectChanged();return true;
    }
    public boolean moveLayer(int id,int delta) {
        int idx=layerOrder.indexOf(id),target=idx+delta;
        if(idx<0 || target<0 || target>=layerOrder.size())return false;
        java.util.Collections.swap(layerOrder,idx,target);projectChanged();return true;
    }
    public void setLayerOrder(int[] ids) {
        if(ids==null || ids.length!=layerOrder.size())return;
        java.util.HashSet<Integer> unique=new java.util.HashSet<>();
        for(int id:ids)if(!isAllocated(id)||!unique.add(id))return;
        layerOrder.clear();for(int id:ids)layerOrder.add(id);projectChanged();
    }
    /** JSON project preset: ordered independent layer instances. */
    public String exportProjectJson() {
        try {
            JSONObject root=new JSONObject();root.put("version",6);root.put("camera",camera.encode());root.put("sections",sections.encode());
            root.put("chromatic",chromatic);root.put("vignette",vignette);
            JSONArray stems=new JSONArray();for(String uri:stemUris)stems.put(uri);root.put("stems",stems);
            root.put("portrait",portrait);root.put("eco",economyMode);
            root.put("spectrum",spectrumEnabled);root.put("spectrumScale",spectrumScale);
            root.put("cameraShake",cameraShake);root.put("shaderSource",shaderSource);
            JSONArray layers=new JSONArray();
            for(int id:layerOrder) {
                layers.put(layerJson(id));
            }
            root.put("layers",layers);return root.toString();
        }catch(Exception e){throw new IllegalStateException("Preset JSON gagal",e);}
    }
    private JSONObject layerJson(int id)throws Exception {
                JSONObject o=new JSONObject();o.put("type",layerType[id]);o.put("enabled",enabled[id]);
                o.put("name",layerName[id]);o.put("text",layerText[id]);
                o.put("visualizerStyle",visualizerStyle[id]);o.put("accentColor",accentColor[id]);
                o.put("visualizerGlow",visualizerGlow[id]);o.put("logoSize",logoSize[id]);o.put("logoShape",logoShape[id]);
                JSONArray connections=new JSONArray();for(AudioRoute route:audioRoutes[id])connections.put(route.encode());
                o.put("audioConnections",connections);
                o.put("advanced",advanced[id].json());o.put("motion",motion[id].encode());o.put("parent",layerOrder.indexOf(parentLayer[id]));
                JSONArray fs=new JSONArray();for(LayerFilter f:filters[id])fs.put(f.encode());o.put("filters",fs);
                o.put("imageUri",imageUri[id]);o.put("videoUri",videoUri[id]);
                o.put("polygon",polygonEncoded[id]);o.put("textColor",textColor[id]);
                o.put("x",offsetX[id]);o.put("y",offsetY[id]);
                o.put("scale",layerScale[id]);o.put("rotation",layerRotation[id]);
                o.put("startMs",startTimeMs[id]);o.put("endMs",endTimeMs[id]);
                o.put("source",source[id]);o.put("strength",strength[id]);o.put("density",density[id]);
                o.put("size",size[id]);o.put("opacity",opacity[id]);o.put("velocity",velocity[id]);
                o.put("threshold",threshold[id]);o.put("attack",attackMs[id]);o.put("release",releaseMs[id]);
                o.put("minHz",minHz[id]);o.put("maxHz",maxHz[id]);o.put("blend",blend[id]);
                o.put("mask",mask[id]);o.put("maskRadius",maskRadius[id]);o.put("direction",direction[id]);
                JSONArray routes=new JSONArray();for(int j:propertySource[id])routes.put(j);
                o.put("routes",routes);
                // Keep old V1.6 strength field for backward compatibility.
                o.put("automation",automation[id][AutomationParameters.STRENGTH].encode());
                JSONArray curves=new JSONArray();
                for(AutomationTrack curve:automation[id])curves.put(curve.encode());
                o.put("keyframes",curves);return o;
    }
    public boolean importProjectJson(String json) {
        if(json==null||json.isEmpty())return false;
        try {
            JSONObject root=new JSONObject(json);JSONArray arr=root.getJSONArray("layers");
            if(arr.length()>MAX_LAYERS)return false;
            // Validate before mutating scene: malicious or broken preset must not erase layers.
            CameraRig parsedCamera=CameraRig.decode(root.optString("camera"));SongSections parsedSections=SongSections.decode(root.optString("sections"));
            String parsedShader=ShaderSource.validate(root.optString("shaderSource",ShaderSource.CLASSIC));
            for(int j=0;j<arr.length();j++){
                JSONObject o=arr.getJSONObject(j);int type=o.getInt("type");
                if(type<0||type>=LAYER_TYPE_COUNT)return false;
                PolygonMask.parse(o.optString("polygon",PolygonMask.DEFAULT));
                MotionStyle.decode(o.optString("motion",new MotionStyle().encode()));LayerAdvanced.from(o.optJSONObject("advanced"));
                JSONArray fs=o.optJSONArray("filters");if(fs!=null){if(fs.length()>LayerFilter.MAX)return false;for(int k=0;k<fs.length();k++)LayerFilter.decode(fs.getString(k));}
                int parent=o.optInt("parent",-1);if(parent < -1||parent>=arr.length()||parent==j)return false;
                for(int ancestor=parent,count=0;ancestor>=0;count++){
                    if(ancestor>=arr.length()||ancestor==j||count>=MAX_LAYERS)return false;
                    ancestor=arr.getJSONObject(ancestor).optInt("parent",-1);
                }
                JSONArray connections=o.optJSONArray("audioConnections");
                if(connections!=null)for(int k=0;k<Math.min(connections.length(),AutomationParameters.COUNT);k++)
                    new AudioRoute().decode(connections.getString(k));
            }
            for(int id:getLayerOrder())removeLayer(id);
            for(int j=0;j<arr.length();j++) {
                JSONObject o=arr.getJSONObject(j);
                int id=addLayer(o.getInt("type"));
                if(id<0)throw new IllegalStateException("Invalid layer");
                enabled[id]=o.optBoolean("enabled",true);
                motion[id]=MotionStyle.decode(o.optString("motion",new MotionStyle().encode()));LayerAdvanced.from(o.optJSONObject("advanced"));
                JSONArray fs=o.optJSONArray("filters");if(fs!=null)for(int k=0;k<fs.length();k++)filters[id].add(LayerFilter.decode(fs.getString(k)));
                setLayerName(id,o.optString("name",LAYER_NAMES[layerType[id]]));
                setLayerText(id,o.optString("text","RANNEKI"));
                imageUri[id]=o.optString("imageUri","");
                videoUri[id]=o.optString("videoUri","");
                setPolygonMask(id,o.optString("polygon",PolygonMask.DEFAULT));
                setLayerColor(id,o.optInt("textColor",Color.WHITE));
                setVisualizerStyle(id,o.optInt("visualizerStyle",0));setAccentColor(id,o.optInt("accentColor",0xff38d9ef));
                setVisualizerGlow(id,o.optInt("visualizerGlow",40));setLogoSize(id,o.optInt("logoSize",80));setLogoShape(id,o.optInt("logoShape",0));
                JSONArray connections=o.optJSONArray("audioConnections");
                if(connections!=null)for(int k=0;k<Math.min(connections.length(),AutomationParameters.COUNT);k++)
                    audioRoutes[id][k].decode(connections.getString(k));
                setAdvanced(id,LayerAdvanced.from(o.optJSONObject("advanced")));setLayerX(id,o.optInt("x",50));setLayerY(id,o.optInt("y",50));
                setLayerScale(id,o.optInt("scale",50));setLayerRotation(id,o.optInt("rotation",50));
                setLayerStart(id,o.optLong("startMs",0));setLayerEnd(id,o.optLong("endMs",Long.MAX_VALUE));
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
                JSONArray curves=o.optJSONArray("keyframes");
                if(curves!=null) {
                    for(int param=0;param<Math.min(curves.length(),AutomationParameters.COUNT);param++)
                        automation[id][param].decode(curves.optString(param,""));
                }else automation[id][AutomationParameters.STRENGTH].decode(o.optString("automation",""));
            }
            for(int j=0;j<arr.length();j++){int parent=arr.getJSONObject(j).optInt("parent",-1);parentLayer[layerOrder.get(j)]=parent<0?-1:layerOrder.get(parent);}
            setPortrait(root.optBoolean("portrait",true));setEconomyMode(root.optBoolean("eco",true));
            setSpectrumEnabled(root.optBoolean("spectrum",true));
            setSpectrumScale(root.optInt("spectrumScale",55));setCameraShake(root.optInt("cameraShake",0));
            camera=parsedCamera;sections=parsedSections;shaderSource=parsedShader;setPostFx(root.optInt("chromatic",35),root.optInt("vignette",22));
            JSONArray stems=root.optJSONArray("stems");
            for(int i=0;i<4;i++){
                String uri=stems==null?"":stems.optString(i,"");
                if(!uri.equals(stemUris[i]))stemTracks[i]=null;
                stemUris[i]=uri;
            }
            if(root.optInt("version",3)<4&&spectrumEnabled&&getLayerCount()<MAX_LAYERS){
                int id=addLayer(VISUALIZER);setSize(id,spectrumScale);
            }
            projectChanged();return true;
        }catch(Exception e){return false;}
    }
    public AudioAnalyzer.Track getTrack() {return track;}
    public Bitmap getBackdrop() {return backdrop;}
    /** On background thread, render consecutive frames in order, from t=0. */
    public void renderExportFrame(Canvas canvas,int width,int height,long positionMs) {
        float dt=exportPreviousMs<0 ? 0f : Math.max(0f,(positionMs-exportPreviousMs)/1000f);
        exportPreviousMs=positionMs;
        inExportFrame=true;
        try{advance(positionMs,track!=null,dt);render(canvas,width,height,dt);}
        finally{inExportFrame=false;}
    }
    public void resetExport() {
        exportPreviousMs=-1;phase=0f;lastKickEvent=-999;previousPosition=-1;
        rng.setSeed(14350L); // Same project + audio => same initial particles between exports.
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
            case 5:return track==null?0:track.bandAt(position,
                        Math.min(animated(i,AutomationParameters.MIN_HZ),animated(i,AutomationParameters.MAX_HZ)),
                        Math.max(animated(i,AutomationParameters.MIN_HZ),animated(i,AutomationParameters.MAX_HZ)));
            case 6:return live.mids;
            default:
                AudioAnalyzer.Track stem=signalTrack(src);
                if(src>=7&&stem!=null){AudioAnalyzer.Features f=stem.at(position);return Math.max(f.bass,Math.max(f.mids,f.highs));}
                return 0;
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long beginNs=System.nanoTime();
        long now=SystemClock.elapsedRealtime();
        float dt=lastDraw==0?0f:Math.min(.05f,(now-lastDraw)/1000f);
        lastDraw=now;
        long position=clock==null?0:clock.positionMs();
        boolean playing=clock!=null && clock.playing() && track!=null;
        advance(position,playing,dt);
        GpuSceneFrame gpuScene=gpuPreviewActive?snapshotGpuSceneFrame():null;
        if(gpuScene==null || !gpuScene.fastPath)
            render(canvas,getWidth(),getHeight(),playing?dt:0f,gpuPreviewActive);
        if(gpuScene!=null && gpuFrameListener!=null)
            gpuFrameListener.onGpuFrame(gpuScene);
        if(previewMetricsStartNs==0L)previewMetricsStartNs=beginNs;
        if(playing){previewMetrics.record(System.nanoTime()-beginNs,0);previewPacing.record(beginNs);}
        if(playing) postInvalidateDelayed(economyMode?33:16);
    }
    private void advance(long position,boolean playing,float dt) {
        sampleAutomation(position);
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
                        animated(i,AutomationParameters.THRESHOLD)/100f,
                        animated(i,AutomationParameters.ATTACK),animated(i,AutomationParameters.RELEASE),dt*1000);
                for(int prop=0;prop<PROPERTY_COUNT;prop++) {
                    int src=propertySource[i][prop];
                    if(src>=0) propertyEnvelope[i][prop]=ReactiveEnvelope.step(
                            propertyEnvelope[i][prop],readSourceId(src,i,position),
                            animated(i,AutomationParameters.THRESHOLD)/100f,
                        animated(i,AutomationParameters.ATTACK),animated(i,AutomationParameters.RELEASE),dt*1000);
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
                        animated(i,AutomationParameters.THRESHOLD)/100f,0,0,0);
                for(int prop=0;prop<PROPERTY_COUNT;prop++) {
                    int src=propertySource[i][prop];
                    propertyEnvelope[i][prop]=src<0?0:ReactiveEnvelope.step(0,
                            readSourceId(src,i,position),animated(i,AutomationParameters.THRESHOLD)/100f,0,0,0);
                }
            }
        }
        evaluateAudioConnections();
        phase=position/1000f;
    }
    private void evaluateAudioConnections(){
        for(int id:layerOrder){
            for(int param=0;param<AutomationParameters.COUNT;param++){
                AudioRoute route=audioRoutes[id][param];
                if(route.source>=0) animatedValues[id][param]=route.applyFloat(param,animatedValues[id][param],
                        signalAt(route.source,route.minHz,route.maxHz,0,route.attack,route.release));
            }
            int lo=Math.min(animated(id,AutomationParameters.MIN_HZ),animated(id,AutomationParameters.MAX_HZ));
            int hi=Math.max(animated(id,AutomationParameters.MIN_HZ),animated(id,AutomationParameters.MAX_HZ));
            envelope[id]=signalAt(source[id],lo,hi,animated(id,AutomationParameters.THRESHOLD),
                    animated(id,AutomationParameters.ATTACK),animated(id,AutomationParameters.RELEASE));
            for(int prop=0;prop<PROPERTY_COUNT;prop++)propertyEnvelope[id][prop]=propertySource[id][prop]<0?0:
                    signalAt(propertySource[id][prop],lo,hi,animated(id,AutomationParameters.THRESHOLD),
                            animated(id,AutomationParameters.ATTACK),animated(id,AutomationParameters.RELEASE));
        }
    }
    public Bitmap snapshot(int width,int height) {
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        render(new Canvas(bitmap),width,height,0f);
        return bitmap;
    }
    private void render(Canvas canvas,int width,int height,float dt) {
        render(canvas,width,height,dt,false);
    }
    private void render(Canvas canvas,int width,int height,float dt,boolean skipCpuFx) {
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
            if(!enabled[fx] || framePositionMs<startTimeMs[fx] || framePositionMs>endTimeMs[fx]) continue;
            if(skipCpuFx && layerType[fx]<EFFECT_COUNT)continue;
            activeLayer=fx;
            int save=canvas.save();
            float centerX=200f,centerY=h*.5f;
            canvas.translate((smooth(fx,AutomationParameters.X)-50)*3.6f,
                    (smooth(fx,AutomationParameters.Y)-50)*(h/100f));
            canvas.rotate((smooth(fx,AutomationParameters.ROTATION)-50)*3.6f,centerX,centerY);
            float factor=.25f+(smooth(fx,AutomationParameters.SCALE)/100f)*1.5f;
            canvas.scale(factor,factor,centerX,centerY);
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
                case TEXT:drawTextLayer(canvas,h);break;
                case IMAGE:drawImageLayer(canvas,h);break;
                case VIDEO:drawVideoLayer(canvas,h);break;
                case VISUALIZER:if(spectrumEnabled)drawVisualizerLayer(canvas,h,fx);break;
            }
            if(blend[fx]!=0) canvas.restore();
            canvas.restoreToCount(save);
        }
        // Spectrum elements are regular, ordered layers.
        canvas.restore();
    }
    private VisualizerFrame visualizerFrame(int id){
        float radius=25+smooth(id,AutomationParameters.SIZE)*1.05f;
        float height=(8+smooth(id,AutomationParameters.STRENGTH)*.65f)*(.35f+envelope[id]*.65f);
        float[] bands=new float[64];AudioAnalyzer.Track data=signalTrack(source[id]);
        if(data!=null)data.spectrumRangeAt(framePositionMs,animated(id,AutomationParameters.MIN_HZ),animated(id,AutomationParameters.MAX_HZ),bands);
        MotionStyle.Pose pose=worldPoseAt(id,framePositionMs);
        float x=pose.x+cameraShakeX(),y=pose.y+cameraShakeY(),scale=pose.scale,rotation=pose.rotation;
        float[] poly=new float[0];
        if(mask[id]==4&&polygons[id]!=null){PolygonMask pm=polygons[id];poly=new float[pm.count()*2];
            for(int j=0;j<pm.count();j++){poly[2*j]=pm.x(j);poly[2*j+1]=pm.y(j);}}
        GpuSceneFrame.Sprite logo=null;Bitmap image=imageLayers[id];
        if(image!=null&&!image.isRecycled()&&logoSize[id]>0){
            float diameter=radius*2*logoSize[id]/100f;
            if(logoShape[id]==0&&visualizerStyle[id]==11)diameter*=.85f;
            // Logo and spectrum share one transform and audio envelope; only the image is center-cropped.
            float logoOffset=VisualizerMesh.circular(visualizerStyle[id])?0:-radius*1.65f;
            double angle=Math.toRadians(rotation);
            float logoX=pose.x-(float)Math.sin(angle)*logoOffset*scale,logoY=pose.y+(float)Math.cos(angle)*logoOffset*scale;
            logo=new GpuSceneFrame.Sprite(id,IMAGE,image,null,null,Color.WHITE,framePositionMs,0,
                    diameter,diameter,opacity(id),logoX,logoY,scale,rotation,blend[id],mask[id],smooth(id,AutomationParameters.MASK_RADIUS)/100f,poly);
            logo.crop=GpuSceneMath.centerCrop(image.getWidth(),image.getHeight(),1f);
            logo.depth=depthAt(id);logo.localShape=logoShape[id]==0?VisualizerMesh.logoShape(visualizerStyle[id]):logoShape[id];
        }
        VisualizerFrame frame=new VisualizerFrame(id,visualizerStyle[id],16+animated(id,AutomationParameters.DENSITY)*112/100,
                textColor[id],accentColor[id],blend[id],mask[id],radius,height,1+smooth(id,AutomationParameters.VELOCITY)*.045f,
                visualizerGlow[id]/100f,opacity(id),x,y,scale,rotation,smooth(id,AutomationParameters.MASK_RADIUS)/100f,bands,poly,logo);
        frame.depth=depthAt(id);frame.right=new float[bands.length];
        if(data!=null&&visualizerStyle[id]>=24&&visualizerStyle[id]<=26){
            if(visualizerStyle[id]<=25){data.pcmAt(framePositionMs,false,bands);data.pcmAt(framePositionMs,true,frame.right);}
            else{data.stereoAt(framePositionMs,false,bands);data.stereoAt(framePositionMs,true,frame.right);}}
        LayerAdvanced a=advanced[id];if(a.spectrumPath!=null){float morph=a.morph;AudioAnalyzer.Track t=signalTrack(a.morphSource);
            if(a.morphSource>=0&&t!=null)morph=t.envelopeAt(framePositionMs,a.morphSource>=7?7:a.morphSource,25,10000,0,40,180);
            frame.path=DrawnPath.morph(a.spectrumPath,visualizerStyle[id]==28?a.morphPath:null,morph,128);}
        else frame.path=new float[]{-.8f,.3f,-.4f,-.4f,.4f,.4f,.8f,-.3f};
        return frame;
    }
    private void drawVisualizerLayer(Canvas canvas,int h,int id){
        VisualizerFrame frame=visualizerFrame(id);
        canvas.save();canvas.translate(200,h*.5f);
        if(frame.logo!=null){
            float d=frame.logo.width;canvas.save();if(!VisualizerMesh.circular(frame.style))canvas.translate(0,-frame.radius*1.65f);Path shape=new Path();
            if(frame.logo.localShape==1)shape.addCircle(0,0,d*.5f,Path.Direction.CW);
            else shape.addRoundRect(new RectF(-d*.5f,-d*.5f,d*.5f,d*.5f),frame.logo.localShape==2?d*.15f:0,frame.logo.localShape==2?d*.15f:0,Path.Direction.CW);
            canvas.clipPath(shape);Bitmap b=frame.logo.image;float[] crop=frame.logo.crop;
            p.setAlpha(Math.round(frame.opacity*255));
            canvas.drawBitmap(b,new Rect(Math.round(crop[0]*b.getWidth()),Math.round(crop[1]*b.getHeight()),
                    Math.round(crop[2]*b.getWidth()),Math.round(crop[3]*b.getHeight())),new RectF(-d/2,-d/2,d/2,d/2),p);
            p.setAlpha(255);canvas.restore();
        }
        canvasMesh.build(frame.style,frame.bands,frame.right,frame.path,frame.bars,frame.radius,frame.height,frame.thickness);
        p.setStyle(Paint.Style.FILL);p.setColor(frame.color);p.setAlpha(Math.round(frame.opacity*255));
        Path triangle=new Path();
        for(int i=0;i<canvasMesh.floats;i+=12){triangle.reset();triangle.moveTo(canvasMesh.vertices[i],canvasMesh.vertices[i+1]);
            triangle.lineTo(canvasMesh.vertices[i+4],canvasMesh.vertices[i+5]);triangle.lineTo(canvasMesh.vertices[i+8],canvasMesh.vertices[i+9]);
            triangle.close();canvas.drawPath(triangle,p);}
        p.setAlpha(255);canvas.restore();
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
                clipPath.addCircle(200,h*.5f,Math.max(1,smooth(fx,AutomationParameters.MASK_RADIUS)/100f*240),
                        Path.Direction.CW);
                canvas.clipPath(clipPath);
                break;
            case 2: canvas.clipRect(0,0,400,h*smooth(fx,AutomationParameters.MASK_RADIUS)/100f);break;
            case 3: canvas.clipRect(0,h*(1-smooth(fx,AutomationParameters.MASK_RADIUS)/100f),400,h);break;
            case 4:
                PolygonMask shape=polygons[fx];
                if(shape!=null) {
                    clipPath.reset();
                    clipPath.moveTo(shape.x(0)*400f,shape.y(0)*h);
                    for(int point=1;point<shape.count();point++)
                        clipPath.lineTo(shape.x(point)*400f,shape.y(point)*h);
                    clipPath.close();canvas.clipPath(clipPath);
                }
                break;
            default:break;
        }
    }
    private float parameter(int fx,int property) {
        return propertySource[fx][property]<0?0:propertyEnvelope[fx][property];
    }
    private float intensity(int i) {
        return envelope[i]*smooth(i,AutomationParameters.STRENGTH)/100f*(advanced[i].autoArrange?sections.intensity(framePositionMs):1);
    }
    private float volume(int i) {
        return (.2f+smooth(i,AutomationParameters.SIZE)/100f*1.8f)*(1+parameter(i,0)*1.1f);
    }
    private float amount(int i) {
        float modulation=propertySource[i][1]<0?1f:(.45f+parameter(i,1)*1.55f);
        return Math.min(1f,animated(i,AutomationParameters.DENSITY)/100f*(economyMode?.62f:1f)*modulation);
    }
    private float opacity(int i) {
        return advanced[i].clip.alpha(framePositionMs,startTimeMs[i],endTimeMs[i])*(advanced[i].autoArrange?Math.min(1,.6f+sections.intensity(framePositionMs)*.4f):1)*smooth(i,AutomationParameters.OPACITY)/100f*(propertySource[i][3]<0?1f:(.20f+parameter(i,3)*.8f));
    }
    private float speed(int i) {
        return (.15f+smooth(i,AutomationParameters.VELOCITY)/100f*2.7f)*(1+parameter(i,2)*2.2f);
    }
    private void applyWind(Particle q,int fx,float dt) {
        if(animated(fx,AutomationParameters.DIRECTION) == 0 || dt<=0) return;
        double angle=(animated(fx,AutomationParameters.DIRECTION)-1)*2*Math.PI/99;
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
    private void drawVideoLayer(Canvas c,int h) {
        VideoFrameLayer layer=videoSources[activeLayer];
        if(layer==null)return;
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(Math.round(255*opacity(activeLayer)));
        layer.draw(c,new RectF(0,0,400,h),p,framePositionMs,startTimeMs[activeLayer],
                inExportFrame,()->postInvalidate());
        p.setAlpha(255);
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
    private void drawTextLayer(Canvas c,int h) {
        String content=layerText[activeLayer];if(content==null||content.isEmpty())return;
        float energy=intensity(activeLayer);
        p.setColor(textColor[activeLayer]);
        p.setAlpha((int)(255*opacity(activeLayer)));
        p.setTypeface(android.graphics.Typeface.create("sans-serif-black",android.graphics.Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize((12f+volume(activeLayer)*20f)*(1+.45f*energy));
        p.setShadowLayer(5f+energy*5,0,2,Color.BLACK);
        String[] lines=content.split("\\n",-1);
        float spacing=p.getTextSize()*1.18f;
        float first=h*.5f-spacing*(lines.length-1)*.5f;
        for(int i=0;i<lines.length && i<5;i++) c.drawText(lines[i],200,first+i*spacing,p);
        p.clearShadowLayer();p.setTextAlign(Paint.Align.LEFT);p.setAlpha(255);
    }
    private void drawImageLayer(Canvas c,int h) {
        Bitmap b=imageLayers[activeLayer];
        if(b==null||b.isRecycled())return;
        float power=intensity(activeLayer),s=volume(activeLayer)*(1+power*.32f);
        float maxWidth=260*s,maxHeight=h*.55f*s;
        float fit=Math.min(maxWidth/b.getWidth(),maxHeight/b.getHeight());
        float bw=b.getWidth()*fit,bh=b.getHeight()*fit;
        p.setColor(Color.WHITE);p.setAlpha((int)(255*opacity(activeLayer)));
        c.drawBitmap(b,null,new RectF(200-bw*.5f,h*.5f-bh*.5f,
                200+bw*.5f,h*.5f+bh*.5f),p);
        p.setAlpha(255);
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
