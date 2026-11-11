package com.ranneki.visualizer;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Emulator smoke tests: not a Poco F6 hardware benchmark. */
public final class AndroidPipelineTest {
    private static final String TAG="RANNEKI_PIPELINE";
    @Test public void mainActivityLaunches() {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();
        Intent intent=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity=instrumentation.startActivitySync(intent);
        assertNotNull(activity);
        instrumentation.runOnMainSync(activity::finish);
    }

    @Test public void wavToH264AacMuxIsReadable() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File wav=new File(context.getCacheDir(),"test-reactive-3s.wav");
        File mp4=new File(context.getCacheDir(),"test-reactive-3s.mp4");
        try {
            writeWav(wav,3,44100);
            AudioAnalyzer.Track track=AudioAnalyzer.analyze(context,Uri.fromFile(wav));
            assertTrue(track.waveformPeaks(60).length==60);
            ReactiveView scene=new ReactiveView(context);
            scene.setTrack(track);
            scene.setPortrait(true);
            scene.setEconomyMode(true);
            for(int id:scene.getLayerOrder())
                if(scene.getLayerType(id)!=ReactiveView.DUST&&scene.getLayerType(id)!=ReactiveView.GLOW)
                    scene.setEnabled(id,false);
            ExportProfile profile=new ExportProfile(540,960,30,75,18,12);
            String report=Mp4Exporter.export(context,Uri.fromFile(wav),Uri.fromFile(mp4),scene,
                    profile,3000,new AtomicBoolean(false),(p,stage)->{});
            Log.i(TAG,report);
            assertTrue("Empty MP4",mp4.exists() && mp4.length()>32_000);
            MediaExtractor extractor=new MediaExtractor();
            try {
                extractor.setDataSource(mp4.getAbsolutePath());
                boolean video=false,audio=false;
                for(int i=0;i<extractor.getTrackCount();i++) {
                    MediaFormat format=extractor.getTrackFormat(i);
                    String mime=format.getString(MediaFormat.KEY_MIME);
                    if(mime.startsWith("video/"))video=true;
                    if(mime.startsWith("audio/"))audio=true;
                }
                assertTrue("MP4 missing video",video);
                assertTrue("MP4 missing audio",audio);
            }finally{extractor.release();}
        }finally{
            wav.delete();mp4.delete();
        }
    }

    private static void little16(FileOutputStream out,int v) throws IOException {
        out.write(v&0xff);out.write((v>>>8)&0xff);
    }
    private static void little32(FileOutputStream out,int v) throws IOException {
        little16(out,v);little16(out,v>>>16);
    }
    private static void writeWav(File f,int seconds,int rate) throws IOException {
        int frames=seconds*rate;int bytes=frames*2;
        try(FileOutputStream out=new FileOutputStream(f)) {
            out.write(new byte[]{'R','I','F','F'});little32(out,36+bytes);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});little32(out,16);
            little16(out,1);little16(out,1);little32(out,rate);
            little32(out,rate*2);little16(out,2);little16(out,16);
            out.write(new byte[]{'d','a','t','a'});little32(out,bytes);
            for(int i=0;i<frames;i++) {
                double time=i/(double)rate;
                double attack=(i%(rate/2)<rate/12)?0.88:0.32;
                short value=(short)(Math.sin(time*2*Math.PI*110)*attack*28000);
                little16(out,value);
            }
        }
    }
}
