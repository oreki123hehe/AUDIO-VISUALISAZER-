package com.ranneki.visualizer;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.ArrayList;

/** Finger-drawn paths, with bounded arc-length resampling on save. */
final class PathEditorView extends View {
    private final Paint p=new Paint(3);private final ArrayList<Float> points=new ArrayList<>();
    PathEditorView(Context c,DrawnPath initial){super(c);setBackgroundColor(0xff0e1429);setContentDescription("Gambar jalur dengan jari");if(initial!=null)for(float f:initial.xy)points.add(f);}
    DrawnPath value(){if(points.size()<4)throw new IllegalArgumentException("Gambar jalur minimal dua titik");int n=Math.min(128,points.size()/2);float[] xy=new float[n*2];for(int i=0;i<n;i++){int k=(int)((long)i*(points.size()/2-1)/(n-1));xy[i*2]=points.get(k*2);xy[i*2+1]=points.get(k*2+1);}return new DrawnPath(xy);}
    protected void onDraw(Canvas c){p.setColor(0xff28334e);p.setStrokeWidth(1);for(int i=1;i<8;i++){c.drawLine(i*getWidth()/8f,0,i*getWidth()/8f,getHeight(),p);c.drawLine(0,i*getHeight()/8f,getWidth(),i*getHeight()/8f,p);}p.setColor(0xffb4a0ff);p.setStrokeWidth(4);for(int i=2;i<points.size();i+=2)c.drawLine((points.get(i-2)+1)*getWidth()/2,(points.get(i-1)+1)*getHeight()/2,(points.get(i)+1)*getWidth()/2,(points.get(i+1)+1)*getHeight()/2,p);}
    public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){points.clear();getParent().requestDisallowInterceptTouchEvent(true);}if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP){if(points.size()<8192){points.add(Math.max(-1,Math.min(1,e.getX()/getWidth()*2-1)));points.add(Math.max(-1,Math.min(1,e.getY()/getHeight()*2-1)));}invalidate();return true;}return true;}
}
