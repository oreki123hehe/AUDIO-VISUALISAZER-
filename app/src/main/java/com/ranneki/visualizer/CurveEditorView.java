package com.ranneki.visualizer;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Interactive time curve, with draggable Bezier handles and visible overshoot space. */
final class CurveEditorView extends View {
    private MotionCurve curve;private final Paint paint=new Paint(3);private int dragging;
    CurveEditorView(Context context,MotionCurve curve){super(context);this.curve=curve.copy();setMinimumHeight((int)(220*getResources().getDisplayMetrics().density));setContentDescription("Kurva easing; geser handle Bezier");}
    void setCurve(MotionCurve c){curve=c.copy();invalidate();}MotionCurve getCurve(){return curve.copy();}
    private float x(float v){return 30+v*(getWidth()-60);}
    private float y(float v){return getHeight()*.75f-v*getHeight()*.5f;}
    protected void onDraw(Canvas canvas){
        canvas.drawColor(0xff10182b);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(0xff34415a);
        for(int i=0;i<=4;i++){canvas.drawLine(x(i/4f),y(-.5f),x(i/4f),y(1.5f),paint);canvas.drawLine(x(0),y(i/4f),x(1),y(i/4f),paint);}
        Path path=new Path();path.moveTo(x(0),y(0));for(int i=1;i<=200;i++)path.lineTo(x(i/200f),y(curve.map(i/200f)));
        paint.setStrokeWidth(3);paint.setColor(0xff64e4eb);canvas.drawPath(path,paint);
        if(curve.type==8){paint.setStrokeWidth(2);paint.setColor(0xffa99aff);canvas.drawLine(x(0),y(0),x(curve.x1),y(curve.y1),paint);canvas.drawLine(x(1),y(1),x(curve.x2),y(curve.y2),paint);
            paint.setStyle(Paint.Style.FILL);canvas.drawCircle(x(curve.x1),y(curve.y1),10,paint);canvas.drawCircle(x(curve.x2),y(curve.y2),10,paint);}
    }
    public boolean onTouchEvent(android.view.MotionEvent e){
        if(curve.type!=8)return false;
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){dragging=Math.hypot(e.getX()-x(curve.x1),e.getY()-y(curve.y1))<Math.hypot(e.getX()-x(curve.x2),e.getY()-y(curve.y2))?1:2;getParent().requestDisallowInterceptTouchEvent(true);}
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE){
            float xx=Math.max(0,Math.min(1,(e.getX()-30)/Math.max(1,getWidth()-60))),yy=Math.max(-.5f,Math.min(1.5f,(getHeight()*.75f-e.getY())/(getHeight()*.5f)));
            if(dragging==1){curve.x1=xx;curve.y1=yy;}else{curve.x2=xx;curve.y2=yy;}invalidate();return true;
        }if(e.getActionMasked()==MotionEvent.ACTION_UP){performClick();getParent().requestDisallowInterceptTouchEvent(false);return true;}return true;
    }
    public boolean performClick(){super.performClick();return true;}
}
