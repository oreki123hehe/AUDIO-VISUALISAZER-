package com.ranneki.visualizer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class PcmFrameReaderTest {
    static class FragmentedStream extends ByteArrayInputStream {
        FragmentedStream(byte[] content) {super(content);}
        @Override public synchronized int read(byte[] buffer,int offset,int length){
            return super.read(buffer,offset,Math.min(3,length));
        }
    }
    static void check(boolean condition) {if(!condition)throw new AssertionError();}
    public static void main(String[] args) throws Exception {
        byte[] audio=new byte[212];
        for(int i=0;i<audio.length;i++)audio[i]=(byte)i;
        InputStream input=new FragmentedStream(audio);
        byte[] buffer=new byte[65];
        byte[] output=new byte[212];int offset=0;
        while(true){
            int n=PcmFrameReader.readAligned(input,buffer,buffer.length,4);
            if(n==-1)break;
            check(n%4==0);System.arraycopy(buffer,0,output,offset,n);offset+=n;
        }
        check(offset==audio.length);
        check(Arrays.equals(audio,output));
        try {
            PcmFrameReader.readAligned(new FragmentedStream(new byte[]{1,2,3}),new byte[64],64,4);
            throw new AssertionError("Truncated frame should fail");
        }catch(IOException expected) { }
        System.out.println("PASS: whole PCM frames survive fragmented InputStream reads");
    }
}
