package com.ranneki.visualizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** 48-layer editable timeline: waveform, beat snapping, trims and parameter-keyframe drag. */
public final class TimelineView extends View {
    public interface SeekListener { void seekTo(long ms); }
    public interface ParameterProvider { int parameterFor(int layerId); }
    private static final int MAX_VISIBLE_LANES=6;
    private static final float LANE_TOP_DP=76f, LANE_HEIGHT_DP=25f;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar=new RectF();
    private ReactiveView scene;
    private float[] peaks=new float[0];
    private long[] beats=new long[0];
    private long durationMs=0,positionMs=0;
    private float zoom=1f;
    private long windowStartMs=0;
    private int focusLayer=-1;
    private SeekListener seekListener;
    private ParameterProvider parameterProvider;
    private Runnable editListener;
    private int firstLane=0;
    private boolean snapEnabled=false;
    private final float dp;
    private static final int DRAG_NONE=0, DRAG_START=1, DRAG_END=2, DRAG_MOVE=3, DRAG_SCRUB=4,
            DRAG_KEYFRAME=5, DRAG_LANE_SCROLL=6;
    private int dragMode=DRAG_NONE, dragLayer=-1;
    private long dragOriginMs, dragLayerStart, dragLayerEnd;
    private float dragBarLeft, dragBarWidth;
    private float initialY;
    private int initialFirstLane, touchedLane=-1;
    private long movingKeyframeMs;
    private int movingParameter;
    private boolean edited=false;
    public TimelineView(Context ctx) {
        super(ctx);
        dp=getResources().getDisplayMetrics().density;
        setBackgroundColor(0xff10172a);
    }
    public void setScene(ReactiveView view){scene=view;firstLane=0;invalidate();}
    public void setSeekListener(SeekListener listener){seekListener=listener;}
    public void setParameterProvider(ParameterProvider provider){parameterProvider=provider;invalidate();}
    public void setOnEditListener(Runnable listener){editListener=listener;}
    public void setSnapEnabled(boolean enabled){snapEnabled=enabled;invalidate();}
    public boolean isSnapEnabled(){return snapEnabled;}
    public void scrollLanes(int direction){
        int count=scene==null?0:scene.getLayerCount();
        firstLane=TimelineEdit.clampFirstLane(firstLane+direction,count,MAX_VISIBLE_LANES);
        focusLayer=-1;invalidate();
    }
    public void setDuration(long ms){durationMs=Math.max(0,ms);windowStartMs=0;invalidate();}
    public float getZoom(){return zoom;}
    public void setZoom(float value){
        float next=Math.max(1f,Math.min(16f,value));
        long centered=windowStartMs+windowDuration()/2;
        zoom=next;
        windowStartMs=Math.max(0,Math.min(Math.max(0,durationMs-windowDuration()),centered-windowDuration()/2));
        invalidate();
    }
    public void scrollWindow(int direction){
        long step=Math.max(1,windowDuration()/2);
        windowStartMs=Math.max(0,Math.min(Math.max(0,durationMs-windowDuration()),windowStartMs+step*direction));
        invalidate();
    }
    private long windowDuration(){return Math.max(1,(long)(durationMs/zoom));}
    private float windowFraction(long ms){
        return Math.max(0f,Math.min(1f,(ms-windowStartMs)/(float)windowDuration()));
    }
    private long windowAt(float fraction){
        return Math.max(0,Math.min(durationMs,windowStartMs+(long)(Math.max(0f,Math.min(1f,fraction))*windowDuration())));
    }
    public void setPosition(long ms){positionMs=Math.max(0,ms);invalidate();}
    public void setTrack(AudioAnalyzer.Track track){
        peaks=track==null?new float[0]:track.waveformPeaks(360);
        beats=track==null?new long[0]:track.beatPositions();
        invalidate();
    }
    public void setFocusLayer(int id){
        focusLayer=id;
        if(scene!=null){
            int[] ids=scene.getLayerOrder();
            for(int i=0;i<ids.length;i++)if(ids[i]==id){
                if(i<firstLane)firstLane=i;
                else if(i>=firstLane+MAX_VISIBLE_LANES)firstLane=i-MAX_VISIBLE_LANES+1;
                break;
            }
            firstLane=TimelineEdit.clampFirstLane(firstLane,ids.length,MAX_VISIBLE_LANES);
        }
        invalidate();
    }
    public void refresh(){invalidate();}
    private int laneStart(int[] ids){
        firstLane=TimelineEdit.clampFirstLane(firstLane,ids.length,MAX_VISIBLE_LANES);
        return firstLane;
    }
    private int selectedParam(int layer){
        int value=parameterProvider==null?AutomationParameters.STRENGTH:parameterProvider.parameterFor(layer);
        return Math.max(0,Math.min(AutomationParameters.COUNT-1,value));
    }
    private long beatSnap(long ms){
        // Don't snap to an estimated onset unless reasonably close to a beat marker.
        return snapEnabled?TimelineEdit.snapToBeat(ms,beats,140L):ms;
    }
    private float x(long ms,float left,float width) {
        return left+windowFraction(ms)*width;
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        float left=9*dp,right=w-9*dp,span=right-left;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffb8c6e9);paint.setTextSize(11*dp);
        c.drawText("WAVEFORM • x"+((int)zoom)+" • lane "+(firstLane+1)+"/"+
                (scene==null?0:scene.getLayerCount())+(snapEnabled?" • SNAP ON":""),left,14*dp,paint);
        float mid=44*dp, amp=23*dp;
        paint.setColor(0xff394666);paint.setStrokeWidth(.65f*dp);
        int markers=0;
        for(long beat:beats){
            if(beat<windowStartMs)continue;
            if(beat>windowStartMs+windowDuration()||markers++>220)break;
            float px=x(beat,left,span);
            c.drawLine(px,22*dp,px,68*dp,paint);
        }
        if(peaks.length>0) {
            paint.setStrokeWidth(Math.max(1f,span/Math.max(1,peaks.length)*.83f));
            paint.setColor(0xff9788fa);
            int start=(int)(peaks.length*windowStartMs/(double)Math.max(1,durationMs));
            int end=(int)Math.ceil(peaks.length*(windowStartMs+windowDuration())/(double)Math.max(1,durationMs));
            start=Math.max(0,Math.min(peaks.length-1,start));
            end=Math.max(start+1,Math.min(peaks.length,end));
            for(int i=start;i<end;i++){
                float px=x((long)((i+.5)*durationMs/peaks.length),left,span);
                if(px<left || px>right)continue;
                float level=Math.max(.04f,peaks[i]);
                c.drawLine(px,mid-amp*level,px,mid+amp*level,paint);
            }
        } else {
            paint.setColor(0xff354260);
            c.drawLine(left,mid,right,mid,paint);
        }
        if(scene!=null && durationMs>0) {
            int[] ids=scene.getLayerOrder();
            int start=laneStart(ids);
            int show=Math.min(MAX_VISIBLE_LANES,ids.length-start);
            for(int i=0;i<show;i++) {
                int id=ids[i+start];float top=(LANE_TOP_DP+i*LANE_HEIGHT_DP)*dp;
                paint.setColor(0xff65708d);paint.setTextSize(10*dp);
                String name=scene.getLayerName(id);
                c.drawText(name.substring(0,Math.min(11,name.length())),left,top,paint);
                float barStart=left+Math.max(65*dp,span*.29f);
                float barWidth=right-barStart;
                long a=scene.getLayerStart(id),b=scene.getLayerEnd(id);
                bar.set(barStart,top-10*dp,right,top+4*dp);
                paint.setColor(0xff24344d);c.drawRoundRect(bar,4*dp,4*dp,paint);
                if(TimelineMath.intersects(a,b,durationMs)) {
                    float bx=x(a,barStart,barWidth),ex=x(b==Long.MAX_VALUE?durationMs:b,barStart,barWidth);
                    bar.set(Math.max(barStart,bx),top-10*dp,Math.max(Math.max(barStart,bx)+1,Math.min(right,ex)),top+4*dp);
                    paint.setColor(scene.isEnabled(id)?(id==focusLayer?0xffc6a7ff:0xff6477ca):0xff48506a);
                    c.drawRoundRect(bar,4*dp,4*dp,paint);
                }
                for(long key:scene.getAllKeyframePositions(id)) {
                    if(key<windowStartMs||key>windowStartMs+windowDuration())continue;
                    float kx=x(key,barStart,barWidth);
                    if(kx<barStart||kx>right)continue;
                    paint.setColor(0xff7d879b);
                    c.drawCircle(kx,top-3*dp,1.6f*dp,paint);
                }
                // Selected-parameter points are larger so they can be touched and dragged.
                for(long key:scene.getKeyframePositions(id,selectedParam(id))) {
                    if(key<windowStartMs||key>windowStartMs+windowDuration())continue;
                    float kx=x(key,barStart,barWidth);
                    if(kx<barStart||kx>right)continue;
                    paint.setColor(0xffffd795);
                    c.drawCircle(kx,top-3*dp,3.6f*dp,paint);
                }
            }
        }
        if(positionMs>=windowStartMs && positionMs<=windowStartMs+windowDuration()){
            float playX=x(positionMs,left,span);
            paint.setColor(0xffffce87);paint.setStrokeWidth(1.5f*dp);
            c.drawLine(playX,17*dp,playX,h-3*dp,paint);
            c.drawCircle(playX,16*dp,3.5f*dp,paint);
        }
    }
    /** Touch label and scroll vertically through 48 lanes; drag clips or selected keyframes. */
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            if(durationMs<=0)return true;
            dragMode=DRAG_SCRUB;dragLayer=-1;touchedLane=-1;edited=false;
            float px=event.getX(),py=event.getY();
            initialY=py;initialFirstLane=firstLane;
            if(scene!=null && py>=60*dp) {
                int[] ids=scene.getLayerOrder();
                int first=laneStart(ids);
                for(int row=0;row<Math.min(MAX_VISIBLE_LANES,ids.length-first);row++) {
                    float top=(LANE_TOP_DP+row*LANE_HEIGHT_DP)*dp;
                    if(py<top-15*dp||py>top+11*dp)continue;
                    int layer=ids[first+row];touchedLane=layer;
                    float left=9*dp,right=getWidth()-9*dp;
                    float startX=left+Math.max(65*dp,(right-left)*.29f), width=right-startX;
                    if(px<startX){
                        dragMode=DRAG_LANE_SCROLL;
                        break;
                    }
                    long a=scene.getLayerStart(layer), b=scene.getLayerEnd(layer);
                    float x1=x(a,startX,width),x2=x(b==Long.MAX_VALUE?durationMs:b,startX,width);
                    int mode=DRAG_NONE;
                    int param=selectedParam(layer);
                    for(long key:scene.getKeyframePositions(layer,param)){
                        if(key<windowStartMs||key>windowStartMs+windowDuration())continue;
                        if(Math.abs(px-x(key,startX,width))<=11*dp){
                            mode=DRAG_KEYFRAME;movingKeyframeMs=key;
                            movingParameter=param;break;
                        }
                    }
                    if(mode==DRAG_NONE){
                        if(a>=windowStartMs&&a<=windowStartMs+windowDuration()&&Math.abs(px-x1)<=12*dp)
                            mode=DRAG_START;
                        else if(b>=windowStartMs&&b<=windowStartMs+windowDuration()&&Math.abs(px-x2)<=12*dp)
                            mode=DRAG_END;
                        else if(px>x1 && px<x2)mode=DRAG_MOVE;
                    }
                    if(mode!=DRAG_NONE){
                        dragMode=mode;dragLayer=layer;
                        dragBarLeft=startX;dragBarWidth=width;
                        dragOriginMs=windowAt((px-startX)/width);
                        dragLayerStart=a;dragLayerEnd=b==Long.MAX_VALUE?durationMs:b;
                        setFocusLayer(layer);
                    }
                    break;
                }
            }
            if(dragMode==DRAG_SCRUB)seekToX(px);
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(dragMode==DRAG_SCRUB)seekToX(event.getX());
            else if(dragMode==DRAG_LANE_SCROLL && scene!=null){
                int move=Math.round((initialY-event.getY())/(LANE_HEIGHT_DP*dp));
                firstLane=TimelineEdit.clampFirstLane(initialFirstLane+move,scene.getLayerCount(),MAX_VISIBLE_LANES);
                invalidate();
            }else if(scene!=null&&dragLayer>=0){
                long ms=windowAt((event.getX()-dragBarLeft)/Math.max(1f,dragBarWidth));
                ms=beatSnap(ms);
                long min=100;
                if(dragMode==DRAG_KEYFRAME){
                    if(ms!=movingKeyframeMs &&
                            scene.moveKeyframe(dragLayer,movingParameter,movingKeyframeMs,ms)){
                        movingKeyframeMs=ms;edited=true;
                    }
                }else if(dragMode==DRAG_START){
                    scene.setLayerStart(dragLayer,Math.min(ms,Math.max(0,dragLayerEnd-min)));
                    edited=true;
                }else if(dragMode==DRAG_END){
                    scene.setLayerEnd(dragLayer,Math.max(dragLayerStart+min,ms));
                    edited=true;
                }else if(dragMode==DRAG_MOVE){
                    // Snapping shifts the clip's start; its duration never changes.
                    long[] moved=TimelineEdit.shiftRange(dragLayerStart,dragLayerEnd,
                            ms-dragOriginMs,durationMs);
                    if(snapEnabled){
                        long snapped=beatSnap(moved[0]);
                        moved=TimelineEdit.shiftRange(moved[0],moved[1],snapped-moved[0],durationMs);
                    }
                    scene.setLayerStart(dragLayer,moved[0]);
                    scene.setLayerEnd(dragLayer,moved[1]);
                    edited=true;
                }
                refresh();
            }
            return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            if(dragMode==DRAG_LANE_SCROLL && touchedLane>=0 &&
                    Math.abs(event.getY()-initialY)<10*dp)setFocusLayer(touchedLane);
            if(edited && editListener!=null)editListener.run();
            dragMode=DRAG_NONE;dragLayer=-1;touchedLane=-1;return true;
        }
        return super.onTouchEvent(event);
    }
    private void seekToX(float screenX){
        float left=9*dp, span=Math.max(1,getWidth()-18*dp);
        long time=beatSnap(windowAt((screenX-left)/span));
        setPosition(time);
        if(seekListener!=null)seekListener.seekTo(time);
    }
}
