package com.ranneki.visualizer;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.nio.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Disk-backed stereo PCM. Decodes once, never holds an entire song in the Java heap. */
final class PcmAudio {
    static final int RATE=44100;
    interface Progress {void update(int percent,String message);}
    static void check(AtomicBoolean cancel)throws InterruptedException{if(cancel.get()||Thread.currentThread().isInterrupted())throw new InterruptedException("Dibatalkan");}
    static File decode(Context context,Uri uri,File output,AtomicBoolean cancel)throws Exception{
        MediaExtractor ex=new MediaExtractor();MediaCodec codec=null;
        try(Writer writer=new Writer(output)){
            ex.setDataSource(context,uri,null);MediaFormat format=null;
            for(int i=0;i<ex.getTrackCount();i++){MediaFormat f=ex.getTrackFormat(i);if(f.getString(MediaFormat.KEY_MIME).startsWith("audio/")){format=f;ex.selectTrack(i);break;}}
            if(format==null)throw new IOException("Tidak ada audio dalam file");
            int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),encoding=format.containsKey(MediaFormat.KEY_PCM_ENCODING)?format.getInteger(MediaFormat.KEY_PCM_ENCODING):2;
            Resampler resample=new Resampler(writer,rate);
            if("audio/raw".equals(format.getString(MediaFormat.KEY_MIME))){ByteBuffer b=ByteBuffer.allocateDirect(1024*1024);while(true){check(cancel);b.clear();int n=ex.readSampleData(b,0);if(n<0)break;b.position(0);b.limit(n);consume(b,channels,encoding,resample);ex.advance();}}
            else{
                codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));codec.configure(format,null,null,0);codec.start();boolean inputDone=false,done=false;int idle=0;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                while(!done){check(cancel);boolean worked=false;
                    if(!inputDone){int i=codec.dequeueInputBuffer(10000);if(i>=0){ByteBuffer b=codec.getInputBuffer(i);b.clear();int n=ex.readSampleData(b,0);if(n<0){codec.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}else{codec.queueInputBuffer(i,0,n,ex.getSampleTime(),0);ex.advance();}worked=true;}}
                    int i=codec.dequeueOutputBuffer(info,10000);if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=codec.getOutputFormat();channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);resample.rate=rate;encoding=f.containsKey(MediaFormat.KEY_PCM_ENCODING)?f.getInteger(MediaFormat.KEY_PCM_ENCODING):2;worked=true;}
                    else if(i>=0){try{ByteBuffer b=codec.getOutputBuffer(i);if(b!=null&&info.size>0){b.position(info.offset);b.limit(info.offset+info.size);consume(b,channels,encoding,resample);}done=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;}finally{codec.releaseOutputBuffer(i,false);}worked=true;}
                    idle=worked?0:idle+1;if(idle>1500)throw new IOException("Decoder audio tidak merespons");
                }
            }
            resample.finish();if(writer.frames<4096)throw new IOException("Audio terlalu singkat");return output;
        }catch(Exception e){output.delete();throw e;}finally{if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}ex.release();}
    }
    private static void consume(ByteBuffer b,int channels,int encoding,Resampler r)throws IOException{
        b.order(ByteOrder.LITTLE_ENDIAN);int bytes=encoding==3?1:encoding==2?2:encoding==21?3:encoding==4||encoding==22?4:0;
        if(bytes==0||channels<1)throw new IOException("PCM format tidak didukung");
        while(b.remaining()>=bytes*channels){float left=0,right=0;for(int ch=0;ch<channels;ch++){float v;
            if(encoding==3)v=((b.get()&255)-128)/128f;else if(encoding==2)v=b.getShort()/32768f;else if(encoding==21)v=((b.get()&255)|((b.get()&255)<<8)|(b.get()<<16))/8388608f;else if(encoding==22)v=b.getInt()/2147483648f;else v=b.getFloat();
            if(!Float.isFinite(v))v=0;if(ch==0)left=v;if(ch==1)right=v;}r.add(left,channels==1?left:right);}
    }
    private static final class Resampler {
        final Writer writer;int rate;long source=0;double next=0;float left,right;
        Resampler(Writer w,int r){writer=w;rate=r;}
        void add(float l,float r)throws IOException{if(source==0){left=l;right=r;}while(next<=source){double t=source==0?0:next-(source-1);writer.frame((float)(left+(l-left)*t),(float)(right+(r-right)*t));next+=rate/(double)RATE;}left=l;right=r;source++;}
        void finish()throws IOException{while(next<source){writer.frame(left,right);next+=rate/(double)RATE;}}
    }
    static final class Writer implements AutoCloseable {
        private final RandomAccessFile file;private final byte[] buffer=new byte[65536];private int used;long frames;
        Writer(File path)throws IOException{file=new RandomAccessFile(path,"rw");file.setLength(0);file.write(new byte[44]);}
        void frame(float left,float right)throws IOException{if(frames>=500_000_000L)throw new IOException("Audio melewati batas WAV");sample(left);sample(right);frames++;}
        private void sample(float v)throws IOException{int value=Math.round(Math.max(-1,Math.min(1,v))*32767);buffer[used++]=(byte)value;buffer[used++]=(byte)(value>>8);if(used==buffer.length)flush();}
        private void flush()throws IOException{file.write(buffer,0,used);used=0;}
        public void close()throws IOException{try{flush();int bytes=(int)(frames*4);ByteBuffer h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);h.put(new byte[]{82,73,70,70}).putInt(bytes+36).put(new byte[]{87,65,86,69,102,109,116,32}).putInt(16).putShort((short)1).putShort((short)2).putInt(RATE).putInt(RATE*4).putShort((short)4).putShort((short)16).put(new byte[]{100,97,116,97}).putInt(bytes);file.seek(0);file.write(h.array());}finally{file.close();}}
    }
    static final class Reader implements AutoCloseable {
        final RandomAccessFile file;final long frames;private long cached=-1;private int valid;private final byte[] cache=new byte[65536];
        Reader(File path)throws IOException{file=new RandomAccessFile(path,"r");frames=Math.max(0,(file.length()-44)/4);}
        float sample(long index,int channel)throws IOException{if(index<0||index>=frames)return 0;long block=index/16384*16384;if(block!=cached){file.seek(44+block*4);valid=file.read(cache);cached=block;}int i=(int)((index-cached)*4)+channel*2;if(i+1>=valid)return 0;return (short)((cache[i]&255)|(cache[i+1]<<8))/32768f;}
        float interpolated(double frame,int channel)throws IOException{long i=(long)Math.floor(frame);float f=(float)(frame-i);return sample(i,channel)*(1-f)+sample(i+1,channel)*f;}
        public void close()throws IOException{file.close();}
    }
}
