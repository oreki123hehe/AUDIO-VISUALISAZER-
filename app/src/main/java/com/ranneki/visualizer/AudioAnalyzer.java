package com.ranneki.visualizer;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Offline music analysis. No microphone and no server required. */
public final class AudioAnalyzer {
    private static final int N=1024, HOP=256, DECIMATE=4;
    public static final class Features {
        public final float bass,mids,highs,kick,snare;
        Features(float bass,float mids,float highs,float kick,float snare) {
            this.bass=bass;this.mids=mids;this.highs=highs;this.kick=kick;this.snare=snare;
        }
        public static Features zero(){return new Features(0,0,0,0,0);}
    }
    private static final class Raw {
        final long ms;final float low,mid,high;
        Raw(long ms,float low,float mid,float high){this.ms=ms;this.low=low;this.mid=mid;this.high=high;}
    }
    public static final class Track {
        private final long[] times;
        private final Features[] frames;
        Track(long[] times,Features[] frames){this.times=times;this.frames=frames;}
        public Features at(long timeMs){
            if(frames.length==0)return Features.zero();
            if(timeMs<=times[0])return frames[0];
            int lo=0,hi=times.length-1;
            if(timeMs>=times[hi])return frames[hi];
            while(hi-lo>1){int m=(lo+hi)>>>1;if(times[m]<=timeMs)lo=m;else hi=m;}
            float t=(float)(timeMs-times[lo])/Math.max(1,times[hi]-times[lo]);
            Features a=frames[lo],b=frames[hi];
            return new Features(mix(a.bass,b.bass,t),mix(a.mids,b.mids,t),
                mix(a.highs,b.highs,t),mix(a.kick,b.kick,t),mix(a.snare,b.snare,t));
        }
    }
    private static float mix(float a,float b,float t){return a+(b-a)*t;}
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}

    public static Track analyze(Context context,Uri uri) throws Exception {
        MediaExtractor ex=new MediaExtractor(); MediaCodec decoder=null;
        try {
            ex.setDataSource(context,uri,null);
            MediaFormat format=null;int index=-1;
            for(int n=0;n<ex.getTrackCount();n++){
                MediaFormat f=ex.getTrackFormat(n);
                String mime=f.getString(MediaFormat.KEY_MIME);
                if(mime!=null && mime.startsWith("audio/")){index=n;format=f;break;}
            }
            if(format==null)throw new IllegalArgumentException("Tidak ada audio");
            ex.selectTrack(index);
            Stream stream=new Stream(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
            int channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int encoding=AudioFormat.ENCODING_PCM_16BIT;
            String mime=format.getString(MediaFormat.KEY_MIME);
            if("audio/raw".equals(mime)) {
                if(format.containsKey(MediaFormat.KEY_PCM_ENCODING))
                    encoding=format.getInteger(MediaFormat.KEY_PCM_ENCODING);
                ByteBuffer buf=ByteBuffer.allocateDirect(65536);
                while(true){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                    buf.clear();int length=ex.readSampleData(buf,0);if(length<0)break;
                    buf.position(0);buf.limit(length);consume(buf,channels,encoding,stream);
                    ex.advance();
                }
                return stream.finish();
            }
            decoder=MediaCodec.createDecoderByType(mime);
            decoder.configure(format,null,null,0);
            decoder.start();
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            boolean endInput=false,endOutput=false;int idle=0;
            while(!endOutput){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                if(!endInput){
                    int in=decoder.dequeueInputBuffer(10000);
                    if(in>=0){
                        ByteBuffer buffer=decoder.getInputBuffer(in);
                        if(buffer==null)throw new IllegalStateException("Decoder input unavailable");
                        buffer.clear();int length=ex.readSampleData(buffer,0);
                        if(length<0){
                            decoder.queueInputBuffer(in,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            endInput=true;
                        } else {
                            decoder.queueInputBuffer(in,0,length,ex.getSampleTime(),0);
                            ex.advance();
                        }
                    }
                }
                int out=decoder.dequeueOutputBuffer(info,10000);
                if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat output=decoder.getOutputFormat();
                    if(output.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                        stream.setRate(output.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                    if(output.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                        channels=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(output.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        encoding=output.getInteger(MediaFormat.KEY_PCM_ENCODING);
                } else if(out>=0){
                    idle=0;
                    if(info.size>0){
                        ByteBuffer buf=decoder.getOutputBuffer(out);
                        if(buf!=null){
                            buf.position(info.offset);buf.limit(info.offset+info.size);
                            consume(buf,channels,encoding,stream);
                        }
                    }
                    endOutput=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    decoder.releaseOutputBuffer(out,false);
                } else if(endInput && ++idle>100){
                    throw new IllegalStateException("Decoder timeout");
                }
            }
            return stream.finish();
        } finally {
            if(decoder!=null){try{decoder.stop();}catch(Exception ignored){} decoder.release();}
            ex.release();
        }
    }
    private static void consume(ByteBuffer bytes,int channels,int encoding,Stream stream){
        int width;
        switch(encoding){
            case AudioFormat.ENCODING_PCM_8BIT:width=1;break;
            case AudioFormat.ENCODING_PCM_16BIT:width=2;break;
            case 21:width=3;break; // 24-bit packed PCM
            case 22:width=4;break; // 32-bit integer PCM
            case AudioFormat.ENCODING_PCM_FLOAT:width=4;break;
            default:throw new IllegalArgumentException("PCM format unsupported: "+encoding);
        }
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        while(bytes.remaining()>=width*channels){
            float sum=0;
            for(int ch=0;ch<channels;ch++){
                float sample;
                switch(encoding){
                    case AudioFormat.ENCODING_PCM_8BIT:sample=((bytes.get()&255)-128)/128f;break;
                    case AudioFormat.ENCODING_PCM_16BIT:sample=bytes.getShort()/32768f;break;
                    case 21:
                        int s24=(bytes.get()&255)|((bytes.get()&255)<<8)|(bytes.get()<<16);
                        sample=s24/8388608f;break;
                    case 22:sample=bytes.getInt()/2147483648f;break;
                    case AudioFormat.ENCODING_PCM_FLOAT:sample=bytes.getFloat();break;
                    default:sample=0;
                }
                sum+=sample;
            }
            stream.add(sum/channels);
        }
    }
    private static final class Stream{
        final float[] ring=new float[N],re=new float[N],im=new float[N];
        final List<Raw> raws=new ArrayList<>();
        int rate,pos;long samples,frames;
        Stream(int rate){this.rate=rate;}
        void setRate(int rate){this.rate=rate;}
        void add(float sample){
            if(samples++%DECIMATE!=0)return;
            ring[pos]=sample;pos=(pos+1)%N;frames++;
            if(frames>=N && (frames-N)%HOP==0)process();
        }
        void process(){
            for(int i=0;i<N;i++){
                re[i]=ring[(pos+i)%N]*(float)(.5-.5*Math.cos(2*Math.PI*i/(N-1)));
                im[i]=0;
            }
            Fft.forward(re,im);
            float low=0,mid=0,high=0;int nl=0,nm=0,nh=0;
            float effRate=rate/(float)DECIMATE;
            for(int k=2;k<N/2;k++){
                float hz=k*effRate/N;
                float energy=re[k]*re[k]+im[k]*im[k];
                if(hz>=25 && hz<180){low+=energy;nl++;}
                else if(hz>=180 && hz<2000){mid+=energy;nm++;}
                else if(hz>=2000 && hz<8000){high+=energy;nh++;}
            }
            long time=(long)((frames-N/2f)*DECIMATE*1000/rate);
            raws.add(new Raw(time,(float)Math.sqrt(low/Math.max(1,nl)),
                (float)Math.sqrt(mid/Math.max(1,nm)),
                (float)Math.sqrt(high/Math.max(1,nh))));
        }
        Track finish(){
            if(raws.size()==0)throw new IllegalArgumentException("Audio terlalu singkat");
            List<Float> low=new ArrayList<>(),mid=new ArrayList<>(),hi=new ArrayList<>();
            for(Raw r:raws){low.add(r.low);mid.add(r.mid);hi.add(r.high);}
            float nLow=Math.max(.00001f,percentile(low)),nMid=Math.max(.00001f,percentile(mid)),
                  nHi=Math.max(.00001f,percentile(hi));
            Features[] features=new Features[raws.size()];
            long[] times=new long[raws.size()];
            float avgLow=0,avgMid=0,prevLow=0,prevMid=0;long lastKick=-1000,lastSnare=-1000;
            for(int i=0;i<raws.size();i++){
                Raw r=raws.get(i);
                float bass=clamp(r.low/nLow*.68f),mids=clamp(r.mid/nMid*.68f),
                      highs=clamp(r.high/nHi*.68f);
                avgLow=avgLow*.94f+bass*.06f;avgMid=avgMid*.94f+mids*.06f;
                float riseLow=Math.max(0,bass-prevLow),riseMid=Math.max(0,mids-prevMid);
                float kick=0,snare=0;
                if(bass>.19f && riseLow>.105f && bass>avgLow*1.1f && r.ms-lastKick>125){
                    kick=clamp(riseLow*3.8f+.25f);lastKick=r.ms;
                }
                if(mids>.22f && riseMid>.14f && mids>avgMid*1.12f && r.ms-lastSnare>100){
                    snare=clamp(riseMid*2.5f+.2f);lastSnare=r.ms;
                }
                prevLow=bass;prevMid=mids;
                features[i]=new Features(bass,mids,highs,kick,snare);
                times[i]=r.ms;
            }
            for(int i=1;i<features.length;i++){
                Features a=features[i-1],b=features[i];
                features[i]=new Features(b.bass,b.mids,b.highs,
                    Math.max(b.kick,a.kick*.72f),Math.max(b.snare,a.snare*.65f));
            }
            return new Track(times,features);
        }
        private float percentile(List<Float> a){
            Collections.sort(a);return a.get(Math.min(a.size()-1,(int)(a.size()*.85f)));
        }
    }
}
