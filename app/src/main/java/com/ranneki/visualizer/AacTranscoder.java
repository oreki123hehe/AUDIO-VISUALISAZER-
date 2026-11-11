package com.ranneki.visualizer;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/** MP3 / WAV / non-AAC -> PCM16 spool -> AAC-LC in M4A; no external tools. */
final class AacTranscoder {
    private AacTranscoder() {}
    static void transcode(Context context,Uri source,File output,AtomicBoolean cancelled) throws Exception {
        File pcm=File.createTempFile("ranneki-pcm-",".raw",context.getCacheDir());
        try {
            PcmMeta meta=decode(context,source,pcm,cancelled);
            encode(pcm,meta,output,cancelled);
        } finally {pcm.delete();}
    }
    private static final class PcmMeta {
        int sampleRate,channels;
        PcmMeta(int rate,int count) {sampleRate=rate;channels=count;}
    }
    private static PcmMeta decode(Context context,Uri src,File pcm,AtomicBoolean cancelled) throws Exception {
        Mp4Exporter.AudioExtractor ex=new Mp4Exporter.AudioExtractor(context,src);
        MediaCodec decoder=null;PcmMeta meta=new PcmMeta(ex.format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                ex.format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        int encoding=AudioFormat.ENCODING_PCM_16BIT;
        try(BufferedOutputStream target=new BufferedOutputStream(new FileOutputStream(pcm))) {
            if("audio/raw".equals(ex.mime)) {
                if(ex.format.containsKey(MediaFormat.KEY_PCM_ENCODING))encoding=ex.format.getInteger(MediaFormat.KEY_PCM_ENCODING);
                ByteBuffer buffer=ByteBuffer.allocateDirect(1024*1024);
                while(true) {
                    Mp4Exporter.check(cancelled);buffer.clear();
                    int n=ex.ex.readSampleData(buffer,0);if(n<0)break;
                    buffer.position(0);buffer.limit(n);
                    write16(buffer,encoding,target);
                    ex.ex.advance();
                }
            } else {
                decoder=MediaCodec.createDecoderByType(ex.mime);
                decoder.configure(ex.format,null,null,0);
                decoder.start();
                MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                boolean inputDone=false, outputDone=false;
                int empty=0;
                while(!outputDone) {
                    Mp4Exporter.check(cancelled);
                    if(!inputDone) {
                        int input=decoder.dequeueInputBuffer(10000);
                        if(input>=0) {
                            ByteBuffer dst=decoder.getInputBuffer(input);if(dst==null)throw new IllegalStateException("Decoder input null");
                            dst.clear();int count=ex.ex.readSampleData(dst,0);
                            if(count<0) {
                                decoder.queueInputBuffer(input,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone=true;
                            }else {
                                decoder.queueInputBuffer(input,0,count,Math.max(0,ex.ex.getSampleTime()),0);
                                ex.ex.advance();
                            }
                        }
                    }
                    int idx=decoder.dequeueOutputBuffer(info,10000);
                    if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat format=decoder.getOutputFormat();
                        meta.sampleRate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        meta.channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        encoding=format.containsKey(MediaFormat.KEY_PCM_ENCODING)?
                                format.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
                    } else if(idx>=0) {
                        if(info.size>0) {
                            ByteBuffer output=decoder.getOutputBuffer(idx);
                            if(output==null)throw new IllegalStateException("Decoder output null");
                            output.position(info.offset);output.limit(info.offset+info.size);
                            write16(output,encoding,target);
                        }
                        outputDone=(info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                        decoder.releaseOutputBuffer(idx,false);
                        empty=0;
                    } else if(++empty>2000)throw new IllegalStateException("Audio decoder timeout");
                }
            }
        }finally {
            ex.close();
            if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();}
        }
        if(meta.channels<1||meta.channels>2)throw new IllegalArgumentException("Audio >2 channel belum didukung");
        if(meta.sampleRate<8000||meta.sampleRate>96000)throw new IllegalArgumentException("Sample rate tidak didukung");
        return meta;
    }
    private static void write16(ByteBuffer b,int encoding,OutputStream out) throws Exception {
        b.order(ByteOrder.LITTLE_ENDIAN);
        byte[] raw=new byte[32*1024];
        if(encoding==AudioFormat.ENCODING_PCM_16BIT) {
            while(b.hasRemaining()) {
                int n=Math.min(raw.length,b.remaining());b.get(raw,0,n);out.write(raw,0,n);
            }
            return;
        }
        int count=0;
        while(b.hasRemaining()) {
            int sample;
            if(encoding==AudioFormat.ENCODING_PCM_FLOAT) {
                if(b.remaining()<4)break;
                float f=b.getFloat();sample=(int)(Math.max(-1f,Math.min(1f,f))*32767);
            } else if(encoding==AudioFormat.ENCODING_PCM_8BIT) {
                sample=((b.get()&0xff)-128)<<8;
            } else if(encoding==AudioFormat.ENCODING_PCM_24BIT_PACKED) {
                if(b.remaining()<3)break;
                int bits=(b.get()&0xff)|((b.get()&0xff)<<8)|((b.get())<<16);
                sample=bits>>8;
            } else if(encoding==AudioFormat.ENCODING_PCM_32BIT) {
                if(b.remaining()<4)break;
                sample=b.getInt()>>16;
            } else throw new IllegalArgumentException("PCM encoding belum didukung: "+encoding);
            raw[count++]=(byte)sample;raw[count++]=(byte)(sample>>8);
            if(count>=raw.length){out.write(raw,0,count);count=0;}
        }
        if(count>0)out.write(raw,0,count);
    }
    private static void encode(File raw,PcmMeta meta,File output,AtomicBoolean cancelled) throws Exception {
        MediaCodec encoder=null;MediaMuxer muxer=null;boolean started=false;
        int bytesPerSample=2*meta.channels;
        try(InputStream in=new FileInputStream(raw)) {
            MediaFormat format=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,meta.sampleRate,meta.channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE,192000);
            encoder=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            boolean inputDone=false,outputDone=false;
            long frames=0,lastPts=-1;
            byte[] temporary=new byte[64*1024];
            int attempts=0;
            while(!outputDone) {
                Mp4Exporter.check(cancelled);
                if(!inputDone) {
                    int input=encoder.dequeueInputBuffer(10000);
                    if(input>=0) {
                        ByteBuffer buffer=encoder.getInputBuffer(input);
                        if(buffer==null)throw new IllegalStateException("AAC encoder input null");
                        buffer.clear();
                        int max=Math.min(temporary.length,buffer.remaining());
                        max-=max%bytesPerSample;
                        if(max<=0)throw new IllegalStateException("AAC encoder buffer too small");
                        int n=PcmFrameReader.readAligned(in,temporary,max,bytesPerSample);
                        long pts=FrameTimebase.audioSampleUs(frames,meta.sampleRate);
                        if(n<0) {
                            encoder.queueInputBuffer(input,0,0,pts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone=true;
                        }else {
                            buffer.put(temporary,0,n);
                            encoder.queueInputBuffer(input,0,n,pts,0);
                            frames+=n/bytesPerSample;
                        }
                    }
                }
                int idx=encoder.dequeueOutputBuffer(info,10000);
                if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if(started)throw new IllegalStateException("AAC format changed twice");
                    muxer.addTrack(encoder.getOutputFormat());muxer.start();started=true;
                } else if(idx>=0) {
                    if((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
                    if(info.size>0) {
                        if(!started)throw new IllegalStateException("AAC output before format");
                        ByteBuffer data=encoder.getOutputBuffer(idx);
                        if(data==null)throw new IllegalStateException("AAC output null");
                        data.position(info.offset);data.limit(info.offset+info.size);
                        info.presentationTimeUs=Math.max(lastPts+1,info.presentationTimeUs);
                        muxer.writeSampleData(0,data,info);lastPts=info.presentationTimeUs;
                    }
                    outputDone=(info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    encoder.releaseOutputBuffer(idx,false);attempts=0;
                } else if(++attempts>2000)throw new IllegalStateException("AAC encoder timeout");
            }
            if(!started)throw new IllegalStateException("AAC track missing");
        }finally {
            if(encoder!=null){try{encoder.stop();}catch(Exception ignored){}encoder.release();}
            if(muxer!=null){if(started)try{muxer.stop();}catch(Exception ignored){}muxer.release();}
        }
    }
}
