package com.ranneki.visualizer;
import android.content.Context;
import android.graphics.*;
import android.view.*;

/** One independently draggable lane per clip; edge drags trim in source time. */
final class AudioMixTimelineView extends View {
    final AudioMixProject project;final Runnable changed;final Paint p=new Paint(3);
    int selected=-1,edge;float down;long oldStart,oldIn,oldOut,window;
    AudioMixTimelineView(Context context,AudioMixProject project,Runnable changed){super(context);this.project=project;this.changed=changed;setBackgroundColor(0xff0b1023);setContentDescription("Timeline audio. Geser clip atau ujung kiri dan kanan untuk trim.");}
    protected void onDraw(Canvas c){window=Math.max(10000,project.duration()+2000);float left=44,w=Math.max(1,getWidth()-left-10),lane=(getHeight()-24f)/Math.max(1,project.clips.size());p.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);p.setColor(0xffbbc3d9);c.drawText("0",left,18,p);c.drawText(window/1000+" s",getWidth()-55,18,p);
        for(int i=0;i<project.clips.size();i++){AudioMixProject.Clip clip=project.clips.get(i);float y=26+i*lane,a=left+clip.start/(float)window*w,b=left+(clip.start+clip.duration())/(float)window*w;p.setColor(0xffbbc3d9);c.drawText(""+(i+1),5,y+lane*.65f,p);p.setColor(clip.mute?0xff3c4457:0xff765ab4);c.drawRoundRect(a,y,b,y+lane-5,5,5,p);p.setColor(0xffd7c9ff);c.drawRect(a,y,Math.min(b,a+4),y+lane-5,p);c.drawRect(Math.max(a,b-4),y,b,y+lane-5,p);}}
    public boolean onTouchEvent(MotionEvent e){float left=44,w=Math.max(1,getWidth()-left-10),lane=(getHeight()-24f)/Math.max(1,project.clips.size());
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){selected=(int)((e.getY()-24)/lane);if(selected<0||selected>=project.clips.size())return false;AudioMixProject.Clip c=project.clips.get(selected);float a=left+c.start/(float)window*w,b=left+(c.start+c.duration())/(float)window*w;edge=Math.abs(e.getX()-a)<20?-1:Math.abs(e.getX()-b)<20?1:0;down=e.getX();oldStart=c.start;oldIn=c.timing.inMs;oldOut=c.timing.outMs;getParent().requestDisallowInterceptTouchEvent(true);return true;}
        if(selected>=0&&e.getActionMasked()==MotionEvent.ACTION_MOVE){AudioMixProject.Clip c=project.clips.get(selected);long delta=(long)((e.getX()-down)/w*window);if(edge==0)c.start=Math.max(0,oldStart+delta);else if(edge<0){long sourceDelta=Math.max(-oldIn,Math.min(oldOut-oldIn-100,(long)(delta*c.timing.speed)));c.start=Math.max(0,oldStart+(long)(sourceDelta/c.timing.speed));c.timing.inMs=oldIn+sourceDelta;}else c.timing.outMs=Math.max(oldIn+100,Math.min(oldOut,oldOut+(long)(delta*c.timing.speed)));invalidate();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){selected=-1;changed.run();performClick();return true;}return true;}
    public boolean performClick(){super.performClick();return true;}
}
