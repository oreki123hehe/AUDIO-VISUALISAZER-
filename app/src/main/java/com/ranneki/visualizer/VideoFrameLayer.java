package com.ranneki.visualizer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import java.io.Closeable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** A silent video clip composited into the scene; soundtrack remains the imported song.
 * Preview decodes asynchronously so a slow file cannot block touch or screen rendering.
 * Export decodes exact-time frames on the export worker for stable timestamps. */
final class VideoFrameLayer implements Closeable {
    private final Object guard=new Object(),decodeGuard=new Object();
    private final ExecutorService previewWorker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final MediaMetadataRetriever retriever=new MediaMetadataRetriever();
    private volatile boolean closed=false;
    private Bitmap cached;
    private volatile long cachedMs=-1;
    private final long durationMs;
    private final int sourceWidth,sourceHeight;
    VideoFrameLayer(Context context,Uri uri) {
        retriever.setDataSource(context,uri);
        long duration=0;
        try{duration=Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));}
        catch(Exception ignored){}
        durationMs=Math.max(1,duration);
        int width=640,height=360;
        try{
            width=Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            height=Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        }catch(Exception ignored){}
        sourceWidth=Math.max(2,width);sourceHeight=Math.max(2,height);
    }
    private long wrapped(long projectMs,long layerStartMs) {
        long offset=Math.max(0L,projectMs-layerStartMs);
        return durationMs<=0?0:offset%durationMs;
    }
    int width(){return sourceWidth;}
    int height(){return sourceHeight;}
    private Bitmap decode(long timeMs){return decode(timeMs,1280);}
    private Bitmap decode(long timeMs,int maxSide) {
        if(closed)return null;
        long micros=Math.max(0L,timeMs)*1000L;
        synchronized(decodeGuard){
            if(closed)return null;
            float scale=Math.min(1f,Math.min(4096,Math.max(128,maxSide))/(float)Math.max(sourceWidth,sourceHeight));
            int width=Math.max(2,Math.round(sourceWidth*scale)),height=Math.max(2,Math.round(sourceHeight*scale));
            // getScaledFrameAtTime prevents full-resolution 4K input frames entering Java heap.
            if(Build.VERSION.SDK_INT>=27)
                return retriever.getScaledFrameAtTime(micros,MediaMetadataRetriever.OPTION_CLOSEST,width,height);
            return retriever.getFrameAtTime(micros,MediaMetadataRetriever.OPTION_CLOSEST);
        }
    }
    void draw(Canvas canvas,RectF box,Paint paint,long projectMs,long startMs,boolean export,Runnable refresh) {
        long time=wrapped(projectMs,startMs);
        if(export) {
            Bitmap frame=null;
            try{frame=decode(time);if(frame!=null)canvas.drawBitmap(frame,null,box,paint);}
            catch(Exception ignored){}
            finally{if(frame!=null&&!frame.isRecycled())frame.recycle();}
            return;
        }
        synchronized(guard) {
            if(cached!=null&&!cached.isRecycled())canvas.drawBitmap(cached,null,box,paint);
        }
        if(closed||(cachedMs>=0&&Math.abs(cachedMs-time)<16)||!busy.compareAndSet(false,true))return;
        previewWorker.execute(()->{
            try{
                Bitmap frame=decode(time);
                synchronized(guard){
                    if(!closed){
                        Bitmap old=cached;cached=frame;cachedMs=time;
                        if(old!=null&&!old.isRecycled())old.recycle();
                    }else if(frame!=null)frame.recycle();
                }
                if(!closed)refresh.run();
            }catch(Exception ignored){}finally{busy.set(false);}
        });
    }
    /** Request a preview frame without rendering a CPU Canvas. The worker owns decoding. */
    void requestGpuPreview(long projectMs,long startMs,Runnable refresh) {
        final long time=wrapped(projectMs,startMs);
        if(closed || (cachedMs>=0 && Math.abs(cachedMs-time)<16) ||
                !busy.compareAndSet(false,true))return;
        previewWorker.execute(()->{
            try {
                Bitmap frame=decode(time);
                synchronized(guard){
                    if(!closed){
                        Bitmap old=cached;cached=frame;cachedMs=time;
                        if(old!=null&&!old.isRecycled())old.recycle();
                    }else if(frame!=null)frame.recycle();
                }
                if(!closed)refresh.run();
            }catch(Exception ignored){}finally{busy.set(false);}
        });
    }
    long cachedGpuVersion(){synchronized(guard){return cachedMs;}}
    /** Called on the GLES thread; hold the guard while uploading so the preview worker
     * cannot recycle its cached bitmap during GLUtils.texImage2D. */
    boolean uploadGpuPreview(int texture) {
        synchronized(guard){
            if(closed||cached==null||cached.isRecycled())return false;
            android.opengl.GLES20.glBindTexture(android.opengl.GLES20.GL_TEXTURE_2D,texture);
            android.opengl.GLUtils.texImage2D(android.opengl.GLES20.GL_TEXTURE_2D,0,cached,0);
            return true;
        }
    }
    /** Uses scaled MediaMetadataRetriever frames. Decode still uses Android media codecs,
     * but the clip is transformed, masked and blended on the GPU. */
    boolean uploadGpuExport(int texture,long projectMs,long startMs,int maxSide) {
        Bitmap frame=null;
        try{
            frame=decode(wrapped(projectMs,startMs),maxSide);
            if(frame==null)return false;
            android.opengl.GLES20.glBindTexture(android.opengl.GLES20.GL_TEXTURE_2D,texture);
            android.opengl.GLUtils.texImage2D(android.opengl.GLES20.GL_TEXTURE_2D,0,frame,0);
            return true;
        }finally{if(frame!=null&&!frame.isRecycled())frame.recycle();}
    }
    @Override public void close() {
        closed=true;
        previewWorker.shutdownNow();
        synchronized(guard) {
            if(cached!=null&&!cached.isRecycled())cached.recycle();
            cached=null;
            synchronized(decodeGuard){try{retriever.release();}catch(Exception ignored){}}
        }
    }
}
