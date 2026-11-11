package com.ranneki.visualizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Offline multi-lane timeline overview. Taps scrub; focus chooses which FX lanes are visible. */
public final class TimelineView extends View {
    public interface SeekListener { void seekTo(long ms); }
    private static final int MAX_VISIBLE_LANES=4;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar=new RectF();
    private ReactiveView scene;
    private float[] peaks=new float[0];
    private long durationMs=0,positionMs=0;
    private int focusLayer=-1;
    private SeekListener seekListener;
    private final float dp;
    public TimelineView(Context ctx) {
        super(ctx);
        dp=getResources().getDisplayMetrics().density;
        setBackgroundColor(0xff10172a);
    }
    public void setScene(ReactiveView view){scene=view;invalidate();}
    public void setSeekListener(SeekListener listener){seekListener=listener;}
    public void setDuration(long ms){durationMs=Math.max(0,ms);invalidate();}
    public void setPosition(long ms){positionMs=Math.max(0,ms);invalidate();}
    public void setTrack(AudioAnalyzer.Track track){
        peaks=track==null?new float[0]:track.waveformPeaks(360);
        invalidate();
    }
    public void setFocusLayer(int id){focusLayer=id;invalidate();}
    public void refresh(){invalidate();}
    private float x(long ms,float left,float width) {
        return left+TimelineMath.fraction(ms,durationMs)*width;
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        float left=9*dp,right=w-9*dp,span=right-left;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffb8c6e9);paint.setTextSize(11*dp);
        c.drawText("WAVEFORM • sentuh untuk pindah posisi",left,14*dp,paint);
        float mid=44*dp, amp=23*dp;
        if(peaks.length>0) {
            paint.setStrokeWidth(Math.max(1f,span/Math.max(1,peaks.length)*.83f));
            paint.setColor(0xff9788fa);
            for(int i=0;i<peaks.length;i++) {
                float px=left+(i+.5f)*span/peaks.length;
                float level=Math.max(.04f,peaks[i]);
                c.drawLine(px,mid-amp*level,px,mid+amp*level,paint);
            }
        } else {
            paint.setColor(0xff354260);
            c.drawLine(left,mid,right,mid,paint);
        }
        if(scene!=null && durationMs>0) {
            int[] ids=scene.getLayerOrder();
            int start=0;
            if(focusLayer>=0) {
                for(int i=0;i<ids.length;i++)if(ids[i]==focusLayer){start=Math.max(0,i-1);break;}
            }
            start=Math.min(start,Math.max(0,ids.length-MAX_VISIBLE_LANES));
            int show=Math.min(MAX_VISIBLE_LANES,ids.length-start);
            for(int i=0;i<show;i++) {
                int id=ids[i+start];float top=75*dp+i*25*dp;
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
                    bar.set(bx,top-10*dp,Math.max(bx+1,ex),top+4*dp);
                    paint.setColor(scene.isEnabled(id)?(id==focusLayer?0xffc6a7ff:0xff6477ca):0xff48506a);
                    c.drawRoundRect(bar,4*dp,4*dp,paint);
                }
                for(long key:scene.getAllKeyframePositions(id)) {
                    float kx=x(key,barStart,barWidth);
                    if(kx<barStart||kx>right)continue;
                    paint.setColor(Color.WHITE);
                    c.drawCircle(kx,top-3*dp,2.4f*dp,paint);
                }
            }
        }
        float playX=x(positionMs,left,span);
        paint.setColor(0xffffce87);paint.setStrokeWidth(1.5f*dp);
        c.drawLine(playX,17*dp,playX,h-3*dp,paint);
        paint.setColor(0xffffce87);c.drawCircle(playX,16*dp,3.5f*dp,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE){
            if(durationMs<=0||seekListener==null)return true;
            float left=9*dp,span=Math.max(1,getWidth()-18*dp);
            long target=TimelineMath.positionAt((event.getX()-left)/span,durationMs);
            setPosition(target);seekListener.seekTo(target);return true;
        }
        return event.getActionMasked()==MotionEvent.ACTION_UP||super.onTouchEvent(event);
    }
}
