package com.ranneki.visualizer;
import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Explicit edit mode leaves normal playback touch-free. Handles edit at the current playhead. */
final class CanvasGizmoView extends View {
    private final ReactiveView scene;private final ReactiveView.AudioClock clock;private final Paint paint=new Paint(3);
    private int selected=-1,mode;private float lastX,lastY,originAngle,originDistance;private int baseScale,baseRotation,baseX,baseY;
    CanvasGizmoView(Context c,ReactiveView s,ReactiveView.AudioClock clock){super(c);scene=s;this.clock=clock;setVisibility(GONE);setContentDescription("Geser tengah, ukuran di sudut, rotasi di atas, pivot pada tanda silang");}
    void select(int id){selected=id;setVisibility(scene.isAllocated(id)?VISIBLE:GONE);invalidate();}
    private float[] fit(){return GpuSceneMath.fittedRect(scene.isPortrait(),getWidth(),getHeight());}
    private float[] center(){MotionStyle.Pose p=scene.poseAt(selected,clock.positionMs());float[] r=fit();return new float[]{(r[0]+r[2]*(.5f+p.x/400))*getWidth(),(r[1]+r[3]*(.5f+p.y/(scene.isPortrait()?711:225)))*getHeight()};}
    protected void onDraw(Canvas canvas){if(!scene.isAllocated(selected)){setVisibility(GONE);return;}float[] c=center();float radius=48*getResources().getDisplayMetrics().density;paint.setColor(0xffb7a1ff);paint.setStrokeWidth(2);paint.setStyle(Paint.Style.STROKE);canvas.drawRect(c[0]-radius,c[1]-radius,c[0]+radius,c[1]+radius,paint);canvas.drawLine(c[0],c[1]-radius,c[0],c[1]-radius-26,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(c[0]+radius,c[1]+radius,10,paint);canvas.drawCircle(c[0],c[1]-radius-26,10,paint);canvas.drawCircle(c[0],c[1],7,paint);paint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);canvas.drawText("Geser • sudut: ukuran • atas: rotasi",12,getHeight()-14,paint);postInvalidateDelayed(33);}
    public boolean onTouchEvent(MotionEvent e){if(!scene.isAllocated(selected))return false;long at=clock.positionMs();float[] c=center(),r=fit();float dx=e.getX()-c[0],dy=e.getY()-c[1],radius=48*getResources().getDisplayMetrics().density;
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();mode=Math.hypot(dx-radius,dy-radius)<32?1:Math.hypot(dx,dy+radius+26)<32?2:0;originDistance=Math.max(1,(float)Math.hypot(dx,dy));originAngle=(float)Math.atan2(dy,dx);baseX=scene.keyframeValueAt(selected,AutomationParameters.X,at);baseY=scene.keyframeValueAt(selected,AutomationParameters.Y,at);baseScale=scene.keyframeValueAt(selected,AutomationParameters.SCALE,at);baseRotation=scene.keyframeValueAt(selected,AutomationParameters.ROTATION,at);getParent().requestDisallowInterceptTouchEvent(true);return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
            if(mode==1){float scale=(.25f+baseScale*.015f)*(float)Math.hypot(dx,dy)/originDistance;scene.editTransform(selected,AutomationParameters.SCALE,Math.round((scale-.25f)/.015f),at);}
            else if(mode==2)scene.editTransform(selected,AutomationParameters.ROTATION,baseRotation+Math.round((float)Math.toDegrees(Math.atan2(dy,dx)-originAngle)/3.6f),at);
            else {float x=(e.getX()-lastX)/(r[2]*getWidth())*400,y=(e.getY()-lastY)/(r[3]*getHeight())*(scene.isPortrait()?711:225);int parent=scene.getParent(selected);if(parent>=0){MotionStyle.Pose p=scene.poseAt(parent,at);double angle=Math.toRadians(-p.rotation);float nx=(float)(x*Math.cos(angle)-y*Math.sin(angle))/p.scale;y=(float)(x*Math.sin(angle)+y*Math.cos(angle))/p.scale;x=nx;}
                scene.editTransform(selected,AutomationParameters.X,baseX+Math.round(x/3.6f),at);scene.editTransform(selected,AutomationParameters.Y,baseY+Math.round(y/(scene.isPortrait()?7.11f:2.25f)),at);}
            invalidate();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP){performClick();return true;}return true;
    }
    public boolean performClick(){super.performClick();return true;}
}
