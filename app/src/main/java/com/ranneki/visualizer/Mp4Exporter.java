package com.ranneki.visualizer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.view.Surface;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline MP4 pipeline: Canvas -> H.264 Surface encoder -> MP4 + source AAC audio. */
public final class Mp4Exporter {
    public interface Progress { void update(int percent,String stage); }
    private static final int FPS=30;
    private Mp4Exporter() {}
    public static void export(Context context, Uri audio, Uri target, ReactiveView scene,
            int width,int height,int durationMs,AtomicBoolean cancelled,Progress progress) throws Exception {
        if(width<=0||height<=0||durationMs<=0||scene.getTrack()==null)
            throw new IllegalArgumentException("Scene / durasi / analisis audio belum siap");
        if(width%2!=0||height%2!=0)throw new IllegalArgumentException("Ukuran video harus genap");
        File video=File.createTempFile("ranneki-video-",".mp4",context.getCacheDir());
        File rendered=File.createTempFile("ranneki-final-",".mp4",context.getCacheDir());
        File convertedAudio=null;
        try {
            check(cancelled);
            progress.update(0,"Render frame");
            encodeVideo(video,scene,width,height,durationMs,cancelled,progress);
            check(cancelled);
            progress.update(91,"Menyiapkan audio");
            AudioExtractor audioProbe=new AudioExtractor(context,audio);
            Uri audioUri=audio;
            try {
                if(!"audio/mp4a-latm".equals(audioProbe.mime)) {
                    convertedAudio=File.createTempFile("ranneki-aac-",".m4a",context.getCacheDir());
                    AacTranscoder.transcode(context,audio,convertedAudio,cancelled);
                }
            }finally {audioProbe.close();}
            progress.update(96,"Menggabungkan audio & video");
            mux(rendered,video,context,audioUri,convertedAudio,durationMs,cancelled);
            check(cancelled);
            // Export document is written only when the MP4 is complete.
            try(InputStream in=new FileInputStream(rendered);
                    OutputStream out=context.getContentResolver().openOutputStream(target,"w")) {
                if(out==null)throw new IllegalStateException("File tujuan tidak bisa ditulis");
                byte[] buffer=new byte[256*1024];int n;
                while((n=in.read(buffer))!=-1){check(cancelled);out.write(buffer,0,n);}
                out.flush();
            }
            progress.update(100,"Selesai");
        }finally {
            video.delete();rendered.delete();
            if(convertedAudio!=null)convertedAudio.delete();
        }
    }
    static void check(AtomicBoolean stop) throws InterruptedException {
        if(stop.get() || Thread.currentThread().isInterrupted())
            throw new InterruptedException("Ekspor dibatalkan");
    }
    private static void encodeVideo(File dest,ReactiveView scene,int w,int h,long durationMs,
            AtomicBoolean stop,Progress progress) throws Exception {
        MediaCodec encoder=null;MediaMuxer muxer=null;CanvasEncoderSurface gl=null;
        Bitmap frame=null;boolean muxerStarted=false;int track=-1;
        try {
            MediaFormat format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,w,h);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE,Math.max(1800000,w*h*5));
            format.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2);
            encoder=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface surface=encoder.createInputSurface();
            gl=new CanvasEncoderSurface(surface,w,h);
            encoder.start();
            muxer=new MediaMuxer(dest.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            frame=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(frame);
            scene.resetExport();
            long totalFrames=FrameTimebase.frameCount(durationMs,FPS);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            for(long index=0;index<totalFrames;index++) {
                check(stop);
                long ms=FrameTimebase.frameMillis(index,FPS);
                canvas.drawColor(android.graphics.Color.BLACK);
                scene.renderExportFrame(canvas,w,h,ms);
                gl.submit(frame,FrameTimebase.presentationNs(index,FPS));
                muxerStarted=drain(encoder,muxer,info,false,muxerStarted);
                if(index%FPS==0)progress.update((int)(90*index/Math.max(1,totalFrames)),"Render frame");
            }
            encoder.signalEndOfInputStream();
            muxerStarted=drain(encoder,muxer,info,true,muxerStarted);
            if(!muxerStarted)throw new IllegalStateException("Video encoder tidak mengeluarkan track");
        }finally {
            if(gl!=null)gl.close();
            if(frame!=null && !frame.isRecycled())frame.recycle();
            if(encoder!=null){try{encoder.stop();}catch(Exception ignored){}encoder.release();}
            if(muxer!=null){if(muxerStarted)try{muxer.stop();}catch(Exception ignored){}muxer.release();}
        }
    }
    private static boolean drain(MediaCodec encoder,MediaMuxer muxer,MediaCodec.BufferInfo info,
            boolean eos,boolean started) throws Exception {
        int idle=0;
        boolean sawEos=false;
        while(true) {
            int index=encoder.dequeueOutputBuffer(info,eos?10000:0);
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER) {
                if(!eos)break;
                if(++idle>500)throw new IllegalStateException("Video encoder tidak mengirim akhir stream (EOS)");
            } else if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if(started)throw new IllegalStateException("Video format changed twice");
                int track=muxer.addTrack(encoder.getOutputFormat());muxer.start();started=true;
            } else if(index>=0) {
                if((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
                if(info.size>0) {
                    if(!started)throw new IllegalStateException("Encoder produced data before track format");
                    ByteBuffer bytes=encoder.getOutputBuffer(index);
                    if(bytes==null)throw new IllegalStateException("Encoder output unavailable");
                    bytes.position(info.offset);bytes.limit(info.offset+info.size);
                    muxer.writeSampleData(0,bytes,info);
                }
                boolean finished=(info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                encoder.releaseOutputBuffer(index,false);
                if(finished){sawEos=true;break;}
            }
        }
        if(eos&&!sawEos)throw new IllegalStateException("Video encoder selesai tanpa EOS");
        return started;
    }
    static final class AudioExtractor implements AutoCloseable {
        final MediaExtractor ex=new MediaExtractor();
        final int index;
        final String mime;
        final MediaFormat format;
        AudioExtractor(Context ctx,Uri uri) throws Exception {
            ex.setDataSource(ctx,uri,null);
            int found=-1;MediaFormat f=null;
            for(int i=0;i<ex.getTrackCount();i++) {
                MediaFormat candidate=ex.getTrackFormat(i);
                String m=candidate.getString(MediaFormat.KEY_MIME);
                if(m!=null && m.startsWith("audio/")){found=i;f=candidate;break;}
            }
            if(found<0)throw new IllegalArgumentException("Track audio tidak ditemukan");
            index=found;format=f;mime=f.getString(MediaFormat.KEY_MIME);ex.selectTrack(index);
        }
        @Override public void close(){ex.release();}
    }
    private static void mux(File output,File video,Context context,Uri audioUri,File audioAac,
            long durationMs,AtomicBoolean cancelled) throws Exception {
        MediaExtractor visual=new MediaExtractor();MediaExtractor sound=new MediaExtractor();
        MediaMuxer muxer=null;boolean started=false;
        try {
            visual.setDataSource(video.getAbsolutePath());
            if(audioAac!=null)sound.setDataSource(audioAac.getAbsolutePath());
            else sound.setDataSource(context,audioUri,null);
            int vi=findTrack(visual,"video/"),ai=findTrack(sound,"audio/");
            visual.selectTrack(vi);sound.selectTrack(ai);
            muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int vout=muxer.addTrack(visual.getTrackFormat(vi));
            int aout=muxer.addTrack(sound.getTrackFormat(ai));
            muxer.start();started=true;
            copyTrack(visual,muxer,vout,durationMs,cancelled,false);
            copyTrack(sound,muxer,aout,durationMs,cancelled,true);
        }finally {
            visual.release();sound.release();
            if(muxer!=null){if(started)try{muxer.stop();}catch(Exception ignored){}muxer.release();}
        }
    }
    private static int findTrack(MediaExtractor extractor,String prefix) {
        for(int i=0;i<extractor.getTrackCount();i++) {
            String m=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if(m!=null&&m.startsWith(prefix))return i;
        }
        throw new IllegalArgumentException("Media track not found: "+prefix);
    }
    private static void copyTrack(MediaExtractor extractor,MediaMuxer muxer,int out,long durationMs,
            AtomicBoolean cancelled,boolean normalizeAudio) throws Exception {
        int capacity=2*1024*1024;
        int sourceTrack=extractor.getSampleTrackIndex();
        if(sourceTrack<0)throw new IllegalStateException("Track media tidak memiliki sample");
        MediaFormat f=extractor.getTrackFormat(sourceTrack);
        if(f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)){
            int requested=f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            if(requested>8*1024*1024)throw new IllegalArgumentException("Media sample terlalu besar");
            capacity=Math.max(capacity,requested);
        }
        ByteBuffer data=ByteBuffer.allocateDirect(capacity);
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        long base=-1,previous=-1;
        int copied=0;
        while(true) {
            check(cancelled);data.clear();
            int length=extractor.readSampleData(data,0);
            if(length<0)break;
            long pts=extractor.getSampleTime();
            if(base<0)base=normalizeAudio?Math.max(0,pts):0;
            pts=Math.max(0,pts-base);
            if(pts>=durationMs*1000L)break;
            if(previous>=0 && pts<=previous)pts=previous+1;
            info.set(0,length,pts,extractor.getSampleFlags());
            data.position(0);data.limit(length);
            muxer.writeSampleData(out,data,info);
            copied++;
            previous=pts;
            extractor.advance();
        }
        if(copied==0)throw new IllegalStateException("Track media kosong atau lebih pendek dari durasi ekspor");
    }
}
