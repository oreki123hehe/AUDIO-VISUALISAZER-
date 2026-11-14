package com.ranneki.visualizer;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class OfflineStemTest {
    public static void main(String[] args)throws Exception{
        File folder=new File(args[1]);folder.mkdirs();File source=new File(folder,"input.wav");
        try(PcmAudio.Writer w=new PcmAudio.Writer(source)){for(int i=0;i<160000;i++)w.frame((float)Math.sin(i*.017)*.4f,(float)Math.sin(i*.076)*.3f);}
        File[] models=new File[4];for(int i=0;i<4;i++)models[i]=new File(args[0],StemSeparator.NAMES[i]+".onnx");
        long started=System.nanoTime();File[] outputs=StemSeparator.separateDecoded(source,models,new File(folder,"stems"),new AtomicBoolean(),(n,m)->System.out.println(n+"% "+m));
        try(PcmAudio.Reader input=new PcmAudio.Reader(source)){
            PcmAudio.Reader[] tracks=new PcmAudio.Reader[4];double max=0;
            try{for(int i=0;i<4;i++){tracks[i]=new PcmAudio.Reader(outputs[i]);if(tracks[i].frames!=input.frames)throw new AssertionError("Frame count changed: "+tracks[i].frames+" vs "+input.frames);}
                for(int i=0;i<160000;i++)for(int channel=0;channel<2;channel++){float sum=0;for(PcmAudio.Reader t:tracks)sum+=t.sample(i,channel);max=Math.max(max,Math.abs(sum-input.sample(i,channel)));}
            }finally{for(PcmAudio.Reader r:tracks)if(r!=null)r.close();}
            if(max>.003)throw new AssertionError("Reconstruction/alignment failed: "+max);
            System.out.println("PASS: real Java ORT inference, chunk boundary reconstruction, stereo alignment, max error="+max+", host seconds="+(System.nanoTime()-started)/1e9);
        }
    }
}
