package com.ranneki.visualizer;

import android.content.Context;
import android.net.Uri;
import ai.onnxruntime.*;
import java.io.*;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Four trained UMX-HQ networks; bounded chunks with contextual overlap, on-device CPU. */
final class StemSeparator {
    static final String[] NAMES={"vocals","drums","bass","other"};
    static final int FFT=4096,HOP=1024,BINS=2049,CORE=96,CONTEXT=32;
    static File[] separate(Context context,Uri input,File directory,AtomicBoolean cancel,PcmAudio.Progress progress)throws Exception{
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Penyimpanan stem tidak tersedia");
        File decoded=new File(directory,"source.wav");
        try{progress.update(0,"Membaca audio stereo…");PcmAudio.decode(context,input,decoded,cancel);File[] models=new File[4];for(int i=0;i<4;i++){PcmAudio.check(cancel);models[i]=prepareModel(context,NAMES[i]);}return separateDecoded(decoded,models,directory,cancel,progress);}finally{decoded.delete();}
    }
    static File[] separateDecoded(File decoded,File[] models,File directory,AtomicBoolean cancel,PcmAudio.Progress progress)throws Exception{
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Penyimpanan stem tidak tersedia");
        File[] output=new File[4];PcmAudio.Writer[] writers=new PcmAudio.Writer[4];boolean complete=false;
        try{
            long required=decoded.length()*4+64L*1024*1024;if(directory.getUsableSpace()<required)throw new IOException("Ruang kosong tidak cukup untuk empat stem");
            OrtEnvironment env=OrtEnvironment.getEnvironment();
            for(int s=0;s<4;s++){PcmAudio.check(cancel);
                output[s]=new File(directory,NAMES[s]+".wav");writers[s]=new PcmAudio.Writer(output[s]);}
            float[] window=new float[FFT];for(int i=0;i<FFT;i++)window[i]=(float)(.5-.5*Math.cos(2*Math.PI*i/FFT));
            float[][][] carry=new float[4][2][FFT];float[] weightCarry=new float[FFT];
            try(PcmAudio.Reader audio=new PcmAudio.Reader(decoded)){
                long total=(audio.frames+FFT/2+HOP-1)/HOP;
                for(long first=0;first<total;first+=CORE){PcmAudio.check(cancel);int core=(int)Math.min(CORE,total-first),frames=core+2*CONTEXT,block=core*HOP;
                    float[] magnitude=new float[2*BINS*frames];float[][][] real=new float[2][core][FFT],imag=new float[2][core][FFT];
                    for(int ch=0;ch<2;ch++)for(int f=0;f<frames;f++){float[] re=new float[FFT],im=new float[FFT];long center=(first+f-CONTEXT)*HOP;
                        for(int i=0;i<FFT;i++)re[i]=audio.sample(reflect(center+i-FFT/2,audio.frames),ch)*window[i];Fft.forward(re,im);
                        for(int k=0;k<BINS;k++)magnitude[(ch*BINS+k)*frames+f]=(float)Math.hypot(re[k],im[k]);
                        if(f>=CONTEXT&&f<CONTEXT+core){real[ch][f-CONTEXT]=re;imag[ch][f-CONTEXT]=im;}}
                    float[][] estimates=new float[4][];
                    try(OnnxTensor tensor=OnnxTensor.createTensor(env,FloatBuffer.wrap(magnitude),new long[]{1,2,BINS,frames})){
                        for(int s=0;s<4;s++){PcmAudio.check(cancel);try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
                            options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);options.setCPUArenaAllocator(false);options.setMemoryPatternOptimization(false);
                            try(OrtSession session=env.createSession(models[s].getAbsolutePath(),options);OrtSession.Result result=session.run(Collections.singletonMap("magnitude",tensor))){FloatBuffer data=((OnnxTensor)result.get(0)).getFloatBuffer();estimates[s]=new float[data.remaining()];data.get(estimates[s]);}}}
                    }
                    float[] norm=new float[block+FFT];System.arraycopy(weightCarry,0,norm,0,FFT);
                    float[][][] mix=new float[4][2][block+FFT];for(int s=0;s<4;s++)for(int ch=0;ch<2;ch++)System.arraycopy(carry[s][ch],0,mix[s][ch],0,FFT);
                    for(int f=0;f<core;f++){PcmAudio.check(cancel);for(int i=0;i<FFT;i++)norm[f*HOP+i]+=window[i]*window[i];
                        for(int ch=0;ch<2;ch++)for(int s=0;s<4;s++){float[] re=new float[FFT],im=new float[FFT];for(int k=0;k<BINS;k++){
                            int index=(ch*BINS+k)*frames+f+CONTEXT;double sum=0;for(int t=0;t<4;t++){float v=estimates[t][index];if(!Float.isFinite(v))throw new IOException("Model menghasilkan nilai tidak valid");sum+=(double)v*v;}
                            float v=estimates[s][index],mask=sum<1e-16?.25f:(float)(v*(double)v/sum);re[k]=real[ch][f][k]*mask;im[k]=imag[ch][f][k]*mask;
                            if(k>0&&k<FFT/2){re[FFT-k]=re[k];im[FFT-k]=-im[k];}}
                            Fft.inverse(re,im);for(int i=0;i<FFT;i++)mix[s][ch][f*HOP+i]+=re[i]*window[i];}
                    }
                    long start=first*HOP-FFT/2;
                    for(int i=0;i<block;i++)if(start+i>=0&&start+i<audio.frames){float w=Math.max(1e-8f,norm[i]);for(int s=0;s<4;s++)writers[s].frame(mix[s][0][i]/w,mix[s][1][i]/w);}
                    System.arraycopy(norm,block,weightCarry,0,FFT);for(int s=0;s<4;s++)for(int ch=0;ch<2;ch++)System.arraycopy(mix[s][ch],block,carry[s][ch],0,FFT);
                    progress.update((int)Math.min(99,(first+core)*100/total),"Memisahkan vokal, drum, bass, instrumen lain…");
                }
            }
            for(int s=0;s<4;s++){writers[s].close();writers[s]=null;}
            complete=true;progress.update(100,"Empat stem selesai");return output;
        }finally{
            for(PcmAudio.Writer w:writers)if(w!=null)try{w.close();}catch(Exception ignored){}
            if(!complete)for(File f:output)if(f!=null)f.delete();
        }
    }
    private static long reflect(long i,long n){if(n<=1)return 0;long period=2*n-2;i=((i%period)+period)%period;return i<n?i:period-i;}
    private static File prepareModel(Context context,String name)throws Exception{
        File folder=new File(context.getFilesDir(),"umxhq-models");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Model folder unavailable");
        JSONObject manifest;try(InputStream in=context.getAssets().open("separation/manifest.json")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);manifest=new JSONObject(bytes.toString("UTF-8"));}
        JSONArray stems=manifest.getJSONArray("stems");String sha=null;long size=0;for(int i=0;i<stems.length();i++)if(stems.getJSONObject(i).getString("name").equals(name)){sha=stems.getJSONObject(i).getString("sha256");size=stems.getJSONObject(i).getLong("bytes");}
        if(sha==null)throw new IOException("Model stem tidak ditemukan");File target=new File(folder,name+".onnx");
        if(target.isFile()&&target.length()==size&&sha.equals(hash(target)))return target;
        File temp=new File(folder,name+".tmp");try(InputStream in=context.getAssets().open("separation/"+name+".onnx");OutputStream out=new FileOutputStream(temp)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        if(temp.length()!=size||!sha.equals(hash(temp))){temp.delete();throw new IOException("Model checksum gagal");}
        if(target.exists()&&!target.delete())throw new IOException("Tidak dapat memperbarui model");if(!temp.renameTo(target))throw new IOException("Tidak dapat menyimpan model");return target;
    }
    private static String hash(File file)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder h=new StringBuilder();for(byte b:d.digest())h.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return h.toString();}
}
