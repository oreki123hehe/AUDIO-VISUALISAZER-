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

/** Offline, on-device audio analysis. No microphone, Internet, or server required. */
public final class AudioAnalyzer {
    private static final int N = 2048;
    private static final int HOP = 512;
    private static final int DECIMATE = 2;

    public static final class Features {
        public final float bass, mids, highs, kick, snare;
        Features(float bass, float mids, float highs, float kick, float snare) {
            this.bass = bass; this.mids = mids; this.highs = highs;
            this.kick = kick; this.snare = snare;
        }
        public static Features zero() { return new Features(0,0,0,0,0); }
    }
    private static final class Raw {
        final long ms;
        final float low, mid, high;
        final float[] bands;
        float[] left,right; byte[] waveLeft,waveRight;
        Raw(long ms, float low, float mid, float high, float[] bands) {
            this.ms=ms; this.low=low; this.mid=mid; this.high=high;
            this.bands=bands;
        }
    }
    public static final class Track {
        private final long[] timestamps;
        private final Features[] features;
        private final float[][] spectrum;
        private final BeatGrid beatGrid;
        private float[][] left,right; private byte[][] waveLeft,waveRight;
        private SongSections sections;
        public SongSections sections(){return SongSections.decode(sections.encode());}
        void pcmAt(long ms,boolean rightChannel,float[] out){
            java.util.Arrays.fill(out,0);if(features.length==0||ms>timestamps[timestamps.length-1]+80)return;
            int a=before(ms);byte[] wave=(rightChannel?waveRight:waveLeft)[a];
            for(int i=0;i<out.length;i++){float x=i*(wave.length-1f)/Math.max(1,out.length-1);int j=(int)x,k=Math.min(j+1,wave.length-1);out[i]=(wave[j]+(wave[k]-wave[j])*(x-j))/127f;}
        }
        void stereoAt(long ms,boolean rightChannel,float[] out){
            java.util.Arrays.fill(out,0);if(features.length==0||ms>timestamps[timestamps.length-1]+80)return;
            int a=before(ms),b=Math.min(a+1,features.length-1);float t=a==b?0:clamp((ms-timestamps[a])/(float)(timestamps[b]-timestamps[a]));float[][] data=rightChannel?right:left;
            for(int i=0;i<out.length;i++){float x=i*(BandSpectrum.COUNT-1f)/Math.max(1,out.length-1);int j=(int)x,k=Math.min(j+1,BandSpectrum.COUNT-1);out[i]=lerp(lerp(data[a][j],data[a][k],x-j),lerp(data[b][j],data[b][k],x-j),t);}
        }
        private final java.util.LinkedHashMap<String,float[]> envelopes=
                new java.util.LinkedHashMap<String,float[]>(32,.75f,true){
                    @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,float[]> e){return size()>96;}
                };
        private Track(long[] timestamps, Features[] features, float[][] spectrum) {
            this.timestamps=timestamps; this.features=features; this.spectrum=spectrum;
            float[] kick=new float[features.length],snare=new float[features.length];
            for(int i=0;i<features.length;i++){kick[i]=features[i].kick;snare[i]=features[i].snare;}
            beatGrid=BeatGrid.detect(timestamps,kick,snare);
            float[] energy=new float[features.length],bass=new float[features.length];for(int i=0;i<features.length;i++){Features f=features[i];energy[i]=f.bass*.4f+f.mids*.4f+f.highs*.2f;bass[i]=f.bass;}sections=SongSections.detect(timestamps,energy,bass,beatGrid.bpm());
        }
        public int estimatedBpm(){return beatGrid.bpm();}
        public long[] beatPositions(){return beatGrid.positions();}
        /** Precomputed in audio time. Seeking and 30/60 FPS evaluate the same envelope. */
        synchronized float envelopeAt(long ms,int source,int minHz,int maxHz,int threshold,int attack,int release){
            if(features.length==0||ms>timestamps[timestamps.length-1]+80)return 0;
            String key=source+":"+minHz+":"+maxHz+":"+threshold+":"+attack+":"+release;
            float[] curve=envelopes.get(key);
            if(curve==null){
                curve=new float[features.length];float previous=0;
                for(int i=0;i<curve.length;i++){
                    Features f=features[i];float value;
                    switch(source){
                        case 0:value=f.kick;break;
                        case 1:value=f.bass;break;
                        case 2:value=clamp(f.bass*.58f+f.kick*.88f);break;
                        case 3:value=f.highs;break;
                        case 4:value=f.snare;break;
                        case 5:value=BandSpectrum.range(spectrum[i],minHz,maxHz);break;
                        case 6:value=f.mids;break;
                        default:value=Math.max(f.bass,Math.max(f.mids,f.highs));break;
                    }
                    float dt=i==0?Math.max(1,timestamps[0]):Math.max(1,timestamps[i]-timestamps[i-1]);
                    previous=ReactiveEnvelope.step(previous,value,threshold/100f,attack,release,dt);
                    curve[i]=previous;
                }
                envelopes.put(key,curve);
            }
            int a=before(ms),b=Math.min(a+1,curve.length-1);
            float t=a==b?0:clamp((ms-timestamps[a])/(float)Math.max(1,timestamps[b]-timestamps[a]));
            return lerp(curve[a],curve[b],t);
        }
        synchronized float motionAt(long ms,int source,int mode,int minHz,int maxHz,int speed){
            if(mode==0)return envelopeAt(ms,source,minHz,maxHz,0,10,130);
            if(features.length==0||ms>timestamps[timestamps.length-1]+80)return 0;
            String key="motion:"+source+":"+mode+":"+minHz+":"+maxHz+":"+speed;float[] curve=envelopes.get(key);
            if(curve==null){
                envelopeAt(0,source,minHz,maxHz,0,10,130);
                float[] input=envelopes.get(source+":"+minHz+":"+maxHz+":0:10:130");
                curve=MotionStyle.spring(input,timestamps,mode==1?.65f:.38f,16+speed*.5f);envelopes.put(key,curve);
            }
            int a=before(ms),b=Math.min(a+1,curve.length-1);float t=a==b?0:clamp((ms-timestamps[a])/(float)Math.max(1,timestamps[b]-timestamps[a]));
            return lerp(curve[a],curve[b],t);
        }
        void spectrumRangeAt(long ms,int minHz,int maxHz,float[] output){
            if(spectrum.length==0){java.util.Arrays.fill(output,0);return;}
            int a=before(ms),b=Math.min(a+1,spectrum.length-1);
            float t=a==b?0:clamp((ms-timestamps[a])/(float)Math.max(1,timestamps[b]-timestamps[a]));
            float lo=(float)(Math.log(Math.max(25,minHz)/25.0)/Math.log(10000.0/25)*(BandSpectrum.COUNT-1));
            float hi=(float)(Math.log(Math.max(minHz,maxHz)/25.0)/Math.log(10000.0/25)*(BandSpectrum.COUNT-1));
            for(int i=0;i<output.length;i++){
                float index=Math.max(0,Math.min(BandSpectrum.COUNT-1,lo+(hi-lo)*i/Math.max(1f,output.length-1)));
                int x=(int)index,y=Math.min(x+1,BandSpectrum.COUNT-1);float k=index-x;
                output[i]=lerp(lerp(spectrum[a][x],spectrum[a][y],k),lerp(spectrum[b][x],spectrum[b][y],k),t);
            }
        }
        /** Timeline overview from normalized analysis frames (not decoded PCM).
         * Peak pooling preserves short kick/transient spikes without extra decoding. */
        public float[] waveformPeaks(int columns) {
            int n=Math.max(1,Math.min(2048,columns));
            float[] peaks=new float[n];
            if(features.length==0) return peaks;
            for(int i=0;i<features.length;i++) {
                int bin=(int)Math.min(n-1,(long)i*n/features.length);
                Features f=features[i];
                float level=Math.max(f.bass,Math.max(f.mids,f.highs));
                level=Math.max(level,Math.max(f.kick,f.snare)*0.85f);
                if(level>peaks[bin]) peaks[bin]=Math.min(1f,level);
            }
            return peaks;
        }
        /** 25–10,000 Hz effective range, limited by each file's sampling frequency. */
        public float bandAt(long ms, float minHz, float maxHz) {
            if (spectrum.length == 0) return 0f;
            int a=before(ms);
            int b=Math.min(a+1,spectrum.length-1);
            if (a==b) return BandSpectrum.range(spectrum[a],minHz,maxHz);
            float blend=clamp((float)(ms-timestamps[a])/Math.max(1,timestamps[b]-timestamps[a]));
            return lerp(BandSpectrum.range(spectrum[a],minHz,maxHz),
                    BandSpectrum.range(spectrum[b],minHz,maxHz),blend);
        }
        /** Fills a caller-owned buffer with interpolated 32-band levels, zero allocation. */
        public void spectrumAt(long ms,float[] output) {
            if(output==null || output.length<BandSpectrum.COUNT) return;
            if(spectrum.length==0) {
                java.util.Arrays.fill(output,0f);return;
            }
            int a=before(ms),b=Math.min(a+1,spectrum.length-1);
            float t=a==b?0f:clamp((float)(ms-timestamps[a])/
                    Math.max(1,timestamps[b]-timestamps[a]));
            for(int i=0;i<BandSpectrum.COUNT;i++)
                output[i]=lerp(spectrum[a][i],spectrum[b][i],t);
        }
        private int before(long ms) {
            int lo=0,hi=timestamps.length-1;
            if (ms<=timestamps[0]) return 0;
            if (ms>=timestamps[hi]) return hi;
            while (hi-lo>1) {
                int mid=(lo+hi)>>>1;
                if(timestamps[mid]<=ms) lo=mid; else hi=mid;
            }
            return lo;
        }
        public Features at(long ms) {
            if (features.length == 0) return Features.zero();
            int lo=before(ms), hi=Math.min(lo+1,features.length-1);
            if (lo==hi) return features[lo];
            float t=clamp((float)(ms-timestamps[lo])/Math.max(1,timestamps[hi]-timestamps[lo]));
            Features a=features[lo], b=features[hi];
            return new Features(lerp(a.bass,b.bass,t), lerp(a.mids,b.mids,t),
                    lerp(a.highs,b.highs,t), lerp(a.kick,b.kick,t), lerp(a.snare,b.snare,t));
        }
    }
    private static float lerp(float a,float b,float t) { return a+(b-a)*t; }
    private static float clamp(float f) { return Math.max(0f,Math.min(1f,f)); }

    public static Track analyze(Context context, Uri uri) throws Exception {
        MediaExtractor extractor=new MediaExtractor();
        MediaCodec codec=null;
        try {
            extractor.setDataSource(context, uri, null);
            MediaFormat input=null;
            int track=-1;
            for (int i=0; i<extractor.getTrackCount(); i++) {
                MediaFormat format=extractor.getTrackFormat(i);
                String mime=format.getString(MediaFormat.KEY_MIME);
                if (mime!=null && mime.startsWith("audio/")) { track=i; input=format; break; }
            }
            if (input==null) throw new IllegalArgumentException("File tidak berisi trek audio.");
            extractor.selectTrack(track);
            if ("audio/raw".equals(input.getString(MediaFormat.KEY_MIME))) {
                return analyzeRaw(extractor,input);
            }
            codec=MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME));
            codec.configure(input,null,null,0);
            codec.start();
            int sampleRate=input.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels=input.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int encoding=AudioFormat.ENCODING_PCM_16BIT;
            StreamAnalysis streaming=new StreamAnalysis(sampleRate);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            boolean inputDone=false, outputDone=false;
            int idle=0;
            while (!outputDone) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Analisis dibatalkan");
                if (!inputDone) {
                    int index=codec.dequeueInputBuffer(10000);
                    if (index>=0) {
                        ByteBuffer buffer=codec.getInputBuffer(index);
                        if (buffer==null) throw new IllegalStateException("Audio input buffer tidak tersedia");
                        buffer.clear();
                        int size=extractor.readSampleData(buffer,0);
                        if (size<0) {
                            codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone=true;
                        } else {
                            codec.queueInputBuffer(index,0,size,extractor.getSampleTime(),0);
                            extractor.advance();
                        }
                    }
                }
                int index=codec.dequeueOutputBuffer(info,10000);
                if (index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat out=codec.getOutputFormat();
                    if (out.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate=out.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (out.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding=out.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    streaming.setSampleRate(sampleRate);
                } else if (index>=0) {
                    idle=0;
                    if (info.size>0) {
                        ByteBuffer data=codec.getOutputBuffer(index);
                        if (data!=null) {
                            data.order(ByteOrder.LITTLE_ENDIAN);
                            data.position(info.offset);
                            data.limit(info.offset+info.size);
                            consumePcm(data,encoding,channels,streaming);
                        }
                    }
                    outputDone=(info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(index,false);
                } else if (inputDone && ++idle>100) {
                    throw new IllegalStateException("Decoder audio tidak memberi hasil.");
                }
            }
            return streaming.complete();
        } finally {
            if (codec!=null) {
                try { codec.stop(); } catch (Exception ignored) { }
                codec.release();
            }
            extractor.release();
        }
    }

    private static Track analyzeRaw(MediaExtractor extractor, MediaFormat fmt) throws Exception {
        int rate=fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channels=fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int encoding=fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)
                ?fmt.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
        StreamAnalysis analysis=new StreamAnalysis(rate);
        ByteBuffer chunk=ByteBuffer.allocateDirect(65536).order(ByteOrder.LITTLE_ENDIAN);
        while (true) {
            if(Thread.currentThread().isInterrupted()) throw new InterruptedException("Analisis dibatalkan");
            chunk.clear();
            int bytes=extractor.readSampleData(chunk,0);
            if(bytes<0) break;
            chunk.position(0); chunk.limit(bytes);
            consumePcm(chunk,encoding,channels,analysis);
            extractor.advance();
        }
        return analysis.complete();
    }

    /** Supports 8/16/24/32-bit integer PCM and IEEE float PCM. */
    private static void consumePcm(ByteBuffer data,int encoding,int channels,StreamAnalysis analysis) {
        int bytesPerSample;
        switch(encoding) {
            case AudioFormat.ENCODING_PCM_8BIT: bytesPerSample=1;break;
            case AudioFormat.ENCODING_PCM_16BIT: bytesPerSample=2;break;
            case 21: bytesPerSample=3;break; // Android PCM_24BIT_PACKED
            case 22: bytesPerSample=4;break; // Android PCM_32BIT
            case AudioFormat.ENCODING_PCM_FLOAT: bytesPerSample=4;break;
            default: throw new IllegalArgumentException("Encoding PCM tidak didukung: "+encoding);
        }
        data.order(ByteOrder.LITTLE_ENDIAN);
        while(data.remaining()>=bytesPerSample*channels) {
            float mono=0f,left=0f,right=0f;
            for(int ch=0;ch<channels;ch++) {
                float value;
                switch(encoding) {
                    case AudioFormat.ENCODING_PCM_8BIT:
                        value=((data.get()&0xff)-128)/128f;break;
                    case AudioFormat.ENCODING_PCM_16BIT:
                        value=data.getShort()/32768f;break;
                    case 21:
                        int signed24=(data.get()&0xff)|((data.get()&0xff)<<8)|(data.get()<<16);
                        value=signed24/8388608f;break;
                    case 22:
                        value=data.getInt()/2147483648f;break;
                    case AudioFormat.ENCODING_PCM_FLOAT:
                        value=data.getFloat();break;
                    default: value=0f;
                }
                if(ch==0)left=value;if(ch==1)right=value;mono+=value;
            }
            analysis.addSample(mono/channels,left,channels==1?left:right);
        }
    }

    private static final class StreamAnalysis {
        final float[] ring=new float[N],ringLeft=new float[N],ringRight=new float[N];
        final float[] re=new float[N];
        final float[] im=new float[N];
        final List<Raw> raw=new ArrayList<>();
        long decimatedCount=0, sourceCount=0;
        int ringPos=0, sampleRate;
        StreamAnalysis(int sampleRate) { this.sampleRate=sampleRate; }
        void setSampleRate(int sampleRate) { this.sampleRate=sampleRate; }
        void addSample(float sample,float left,float right) {
            if (sourceCount++ % DECIMATE != 0) return;
            ring[ringPos]=sample;ringLeft[ringPos]=left;ringRight[ringPos]=right;
            ringPos=(ringPos+1)%N;
            decimatedCount++;
            if (decimatedCount>=N && (decimatedCount-N)%HOP==0) frame();
        }
        void frame() {
            for (int i=0;i<N;i++) {
                re[i]=(float)(ring[(ringPos+i)%N] * (0.5-0.5*Math.cos(2*Math.PI*i/(N-1))));
                im[i]=0f;
            }
            Fft.forward(re,im);
            float lo=0f, mid=0f, hi=0f;
            int nlo=0,nmid=0,nhi=0;
            float[] bandPower=new float[BandSpectrum.COUNT];
            int[] bandCount=new int[BandSpectrum.COUNT];
            float effectiveRate=(float)sampleRate/DECIMATE;
            for (int k=2;k<N/2;k++) {
                float hz=k*effectiveRate/N;
                float p=re[k]*re[k]+im[k]*im[k];
                if (hz>=BandSpectrum.MIN_HZ && hz<BandSpectrum.MAX_HZ) {
                    int idx=BandSpectrum.index(hz);
                    bandPower[idx]+=p;
                    bandCount[idx]++;
                }
                if (hz>=25 && hz<180) { lo+=p; nlo++; }
                else if (hz>=180 && hz<2000) { mid+=p; nmid++; }
                else if (hz>=2000 && hz<8000) { hi+=p; nhi++; }
            }
            float a=(float)Math.sqrt(lo/Math.max(1,nlo));
            float b=(float)Math.sqrt(mid/Math.max(1,nmid));
            float c=(float)Math.sqrt(hi/Math.max(1,nhi));
            float[] spectrum=new float[BandSpectrum.COUNT];
            for (int i=0;i<spectrum.length;i++)
                spectrum[i]=(float)Math.sqrt(bandPower[i]/Math.max(1,bandCount[i]));
            long ms=(long)((decimatedCount-N/2.0)*DECIMATE*1000/sampleRate);
            Raw f=new Raw(ms,a,b,c,spectrum);f.waveLeft=wave(ringLeft);f.waveRight=wave(ringRight);f.left=channel(ringLeft,effectiveRate);f.right=channel(ringRight,effectiveRate);raw.add(f);
        }
        byte[] wave(float[] ring){byte[] out=new byte[128];for(int i=0;i<128;i++)out[i]=(byte)Math.round(Math.max(-1,Math.min(1,ring[(ringPos+i*4+N/2-256)%N]))*127);return out;}
        float[] channel(float[] ring,float rate){
            for(int i=0;i<N;i++){re[i]=(float)(ring[(ringPos+i)%N]*(.5-.5*Math.cos(2*Math.PI*i/(N-1))));im[i]=0;}
            Fft.forward(re,im);float[] out=new float[BandSpectrum.COUNT];int[] count=new int[out.length];
            for(int k=2;k<N/2;k++){float hz=k*rate/N;if(hz<25||hz>=10000)continue;int i=BandSpectrum.index(hz);out[i]+=re[k]*re[k]+im[k]*im[k];count[i]++;}
            for(int i=0;i<out.length;i++)out[i]=(float)Math.sqrt(out[i]/Math.max(1,count[i]));return out;
        }
        Track complete() {
            if (raw.isEmpty()) throw new IllegalArgumentException("Audio terlalu singkat atau tidak terbaca.");
            List<Float> a=new ArrayList<>(), b=new ArrayList<>(), c=new ArrayList<>();
            for (Raw r:raw) { a.add(r.low); b.add(r.mid); c.add(r.high); }
            float normA=Math.max(0.00001f,percentile(a,.85f));
            float normB=Math.max(0.00001f,percentile(b,.85f));
            float normC=Math.max(0.00001f,percentile(c,.85f));
            Features[] frames=new Features[raw.size()];
            long[] time=new long[raw.size()];
            float[][] spectrum=new float[raw.size()][BandSpectrum.COUNT];
            float[] normBands=new float[BandSpectrum.COUNT];
            float maximum=0f;
            // Robust per-band reference, with shared floor to avoid amplifying noise-only bands.
            for (int k=0;k<BandSpectrum.COUNT;k++) {
                List<Float> bandValues=new ArrayList<>(raw.size());
                for (Raw r:raw) bandValues.add(r.bands[k]);
                normBands[k]=Math.max(.00001f,percentile(bandValues,.85f));
                maximum=Math.max(maximum,normBands[k]);
            }
            for (int k=0;k<normBands.length;k++)
                normBands[k]=Math.max(normBands[k],maximum*.06f);
            float averageBass=0, previousBass=0, averageMid=0, previousMid=0;
            SpectralOnset onset=new SpectralOnset();
            long lastKick=-1000,lastSnare=-1000;
            for(int i=0;i<raw.size();i++) {
                Raw r=raw.get(i);
                float bass=clamp((r.low/normA)*.68f);
                float mids=clamp((r.mid/normB)*.68f);
                float highs=clamp((r.high/normC)*.68f);
                averageBass=averageBass*.94f + bass*.06f;
                averageMid=averageMid*.94f + mids*.06f;
                float bassRise=Math.max(0,bass-previousBass);
                float midRise=Math.max(0,mids-previousMid);
                float kick=0,snare=0;
                if(bass>.19f && bassRise>.105f && bass>averageBass*1.10f && r.ms-lastKick>125) {
                    kick=clamp(bassRise*3.8f+0.25f); lastKick=r.ms;
                }
                if(mids>.22f && midRise>.14f && mids>averageMid*1.12f && r.ms-lastSnare>100) {
                    snare=clamp(midRise*2.5f+.2f); lastSnare=r.ms;
                }
                previousBass=bass;
                previousMid=mids;
                for(int k=0;k<BandSpectrum.COUNT;k++)
                    spectrum[i][k]=clamp(r.bands[k]/normBands[k]*.68f);
                SpectralOnset.Event transientEvent=onset.next(spectrum[i],r.ms);
                // Use both broadband envelope rises and narrower spectral flux. These
                // remain estimates from the mix, not separated instrument tracks.
                kick=Math.max(kick,transientEvent.kick);
                snare=Math.max(snare,transientEvent.snare);
                frames[i]=new Features(bass,mids,highs,kick,snare);
                time[i]=r.ms;
            }
            // Keep transient events visible across adjacent frames for reliable drawing at 60 fps.
            for(int i=1;i<frames.length;i++) {
                Features prev=frames[i-1],cur=frames[i];
                frames[i]=new Features(cur.bass,cur.mids,cur.highs,
                        Math.max(cur.kick,prev.kick*.72f), Math.max(cur.snare,prev.snare*.65f));
            }
            Track result=new Track(time,frames,spectrum);result.left=new float[raw.size()][];result.right=new float[raw.size()][];result.waveLeft=new byte[raw.size()][];result.waveRight=new byte[raw.size()][];
            float peak=0;for(Raw r:raw)for(int k=0;k<BandSpectrum.COUNT;k++)peak=Math.max(peak,Math.max(r.left[k],r.right[k]));
            peak=Math.max(.00001f,peak);
            for(int i=0;i<raw.size();i++){Raw r=raw.get(i);result.left[i]=r.left;result.right[i]=r.right;result.waveLeft[i]=r.waveLeft;result.waveRight[i]=r.waveRight;
                for(int k=0;k<BandSpectrum.COUNT;k++){r.left[k]=clamp(r.left[k]/peak*1.6f);r.right[k]=clamp(r.right[k]/peak*1.6f);}}
            return result;
        }
        private float percentile(List<Float> values,float p) {
            Collections.sort(values);
            return values.get(Math.min(values.size()-1,Math.max(0,(int)(values.size()*p))));
        }
    }

}
