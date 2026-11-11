package com.ranneki.visualizer;

import java.io.IOException;
import java.io.InputStream;

/** Read a buffer of whole PCM frames, without dropping partial InputStream reads. */
final class PcmFrameReader {
    private PcmFrameReader() { }

    static int readAligned(InputStream source,byte[] output,int available,int frameBytes) throws IOException {
        if(frameBytes<=0||output==null||available>output.length||available<frameBytes)
            throw new IllegalArgumentException("Invalid PCM buffer/frame size");
        int limit=available-available%frameBytes;
        int count=0;
        while(count<limit){
            int read=source.read(output,count,limit-count);
            if(read<0)break;
            if(read==0){
                int single=source.read();
                if(single<0)break;
                output[count++]=(byte)single;
            } else count+=read;
        }
        if(count%frameBytes!=0)
            throw new IOException("Truncated PCM sample frame: "+count+" bytes, frame "+frameBytes);
        return count==0?-1:count;
    }
}
