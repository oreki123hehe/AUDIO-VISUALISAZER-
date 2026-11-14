package com.ranneki.visualizer;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Two-pass disk-backed mix; preserves original clips and avoids clipping by peak attenuation. */
final class AudioMixer {
    static File render(Context context,AudioMixProject project,File folder,AtomicBoolean cancel,PcmAudio.Progress progress)throws Exception{
        if(project.clips.isEmpty())throw new IOException("Tambah audio clip dulu");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Penyimpanan mix tidak tersedia");
        Map<String,PcmAudio.Reader> sources=new HashMap<>();ArrayList<File> temporary=new ArrayList<>();File output=new File(folder,"mix.wav"),floats=new File(folder,"mix.f32");temporary.add(floats);boolean success=false;
        try{
            int n=0;for(AudioMixProject.Clip c:project.clips)if(!sources.containsKey(c.uri)){PcmAudio.check(cancel);progress.update(0,"Membaca clip "+(++n)+"…");File pcm=new File(folder,"decoded-"+n+".wav");temporary.add(pcm);PcmAudio.decode(context,Uri.parse(c.uri),pcm,cancel);sources.put(c.uri,new PcmAudio.Reader(pcm));}
            long duration=0;for(AudioMixProject.Clip c:project.clips){long sourceMs=sources.get(c.uri).frames*1000/PcmAudio.RATE;c.timing.outMs=Math.min(c.timing.outMs,sourceMs);duration=Math.max(duration,c.start+c.timing.duration(sourceMs));}
            if(duration<1||duration>3_600_000)throw new IOException("Mix harus 0–60 menit");long frames=duration*PcmAudio.RATE/1000;
            if(folder.getUsableSpace()<frames*12+32*1024*1024)throw new IOException("Ruang kosong tidak cukup untuk mix");float peak=0;ByteBuffer buffer=ByteBuffer.allocate(8192*8).order(ByteOrder.LITTLE_ENDIAN);
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(floats))){
                for(long first=0;first<frames;first+=8192){PcmAudio.check(cancel);int count=(int)Math.min(8192,frames-first);float[] block=new float[count*2];
                    for(AudioMixProject.Clip c:project.clips){if(c.mute)continue;PcmAudio.Reader r=sources.get(c.uri);double sourceStart=c.timing.inMs*PcmAudio.RATE/1000.0;long end=c.start+c.duration();
                        for(int i=0;i<count;i++){double ms=(first+i)*1000.0/PcmAudio.RATE;if(ms<c.start||ms>=end)continue;double src=sourceStart+(ms-c.start)*c.timing.speed*PcmAudio.RATE/1000;
                            float a=c.gain*c.timing.alpha((long)ms,c.start,end);block[i*2]+=r.interpolated(src,0)*a*Math.min(1,1-c.pan);block[i*2+1]+=r.interpolated(src,1)*a*Math.min(1,1+c.pan);}}
                    buffer.clear();for(float f:block){peak=Math.max(peak,Math.abs(f));buffer.putFloat(f);}out.write(buffer.array(),0,count*8);progress.update((int)(first*80/Math.max(1,frames)),"Mencampur "+project.clips.size()+" clip…");}
            }
            float gain=peak>.98f?.98f/peak:1;try(InputStream in=new BufferedInputStream(new FileInputStream(floats));PcmAudio.Writer writer=new PcmAudio.Writer(output)){
                for(long first=0;first<frames;first+=8192){PcmAudio.check(cancel);int count=(int)Math.min(8192,frames-first),bytes=count*8,read=0;while(read<bytes){int got=in.read(buffer.array(),read,bytes-read);if(got<0)throw new EOFException();read+=got;}buffer.position(0);for(int i=0;i<count;i++)writer.frame(buffer.getFloat()*gain,buffer.getFloat()*gain);progress.update(80+(int)(first*20/Math.max(1,frames)),"Menyiapkan audio preview…");}}
            success=true;return output;
        }finally{for(PcmAudio.Reader r:sources.values())try{r.close();}catch(Exception ignored){}for(File f:temporary)f.delete();if(!success)output.delete();}
    }
}
