package com.ranneki.visualizer;

import android.app.Activity;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Debug-only automated Android integration test: no AndroidX/JUnit dependency. */
public final class SmokeTestActivity extends Activity {
    private static final String TAG="RANNEKI_SMOKE";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text=new TextView(this);
        text.setPadding(24,24,24,24);
        text.setText("Ranneki: testing H.264 + AAC offline export ...");
        setContentView(text);
        File result=new File(getFilesDir(),"smoke-result.txt");
        if(result.exists())result.delete();
        new Thread(()->{
            String message;
            try { message="PASS: "+runPipeline(); }
            catch(Throwable error){
                Log.e(TAG,"Integration failure",error);
                message="FAIL: "+Log.getStackTraceString(error);
            }
            try(FileOutputStream out=new FileOutputStream(result)) {
                out.write(message.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }catch(IOException e){Log.e(TAG,"Cannot write smoke test result",e);}
            String finalMessage=message;
            runOnUiThread(()->text.setText(finalMessage));
        },"ranneki-smoke-test").start();
    }
    private String runPipeline() throws Exception {
        File wav=new File(getFilesDir(),"smoke-2sec.wav");
        File mp4=new File(getFilesDir(),"smoke-2sec.mp4");
        try {
            writeWav(wav,2,44100);
            AudioAnalyzer.Track track=AudioAnalyzer.analyze(this,Uri.fromFile(wav));
            if(track==null || track.waveformPeaks(48).length!=48)
                throw new IllegalStateException("Audio analysis did not return valid peaks");
            ReactiveView scene=new ReactiveView(this);
            scene.setTrack(track);
            scene.setPortrait(true);
            scene.setEconomyMode(true);
            scene.setShaderSource(ShaderSource.NEON);
            if(!ShaderSource.NEON.equals(scene.getShaderSource()))
                throw new IllegalStateException("GPU custom shader not configured");
            // Force all ten GPU particle families, masks, and blend paths through real GLES.
            for(int layer:scene.getLayerOrder()) {
                scene.setEnabled(layer,true);
                scene.setDensity(layer,35);
                if(scene.getLayerType(layer)==ReactiveView.FIRE)scene.setBlend(layer,1);
                if(scene.getLayerType(layer)==ReactiveView.SNOW)scene.setMask(layer,1);
                if(scene.getLayerType(layer)==ReactiveView.DUST){
                    scene.setMask(layer,4);
                    scene.setPolygonMask(layer,"0.10,0.10;0.90,0.10;0.85,0.90;0.15,0.90");
                }
            }
            String json=scene.exportProjectJson();
            ReactiveView verifyShader=new ReactiveView(this);
            if(!verifyShader.importProjectJson(json) ||
                    !ShaderSource.NEON.equals(verifyShader.getShaderSource()))
                throw new IllegalStateException("GLSL shader project serialization failed");
            // Prove the compositor can upload static artwork only once, then render
            // the entire background + effects + spectrum in the GLES framebuffer.
            android.graphics.Bitmap artwork=android.graphics.Bitmap.createBitmap(
                    96,96,android.graphics.Bitmap.Config.ARGB_8888);
            artwork.eraseColor(0xff284471);
            scene.setBackdrop(artwork);
            ExportProfile profile=new ExportProfile(320,568,30,75,15,10);
            String stats=Mp4Exporter.export(this,Uri.fromFile(wav),Uri.fromFile(mp4),
                    scene,profile,2000,new AtomicBoolean(false),(percent,stage)->{});
            if(!stats.contains("GPU particle points:") || stats.contains("GPU particle points: 0"))
                throw new IllegalStateException("GPU effects were not submitted: "+stats);
            if(!stats.contains("GPU-only export frames: 60") ||
                    !stats.contains("cached background texture uploads: 1"))
                throw new IllegalStateException("GPU compositor did not eliminate frame-by-frame CPU uploads: "+stats);
            if(!mp4.exists() || mp4.length()<4096)
                throw new IllegalStateException("MP4 not written");
            MediaExtractor extractor=new MediaExtractor();
            try {
                extractor.setDataSource(mp4.getAbsolutePath());
                boolean video=false, audio=false;
                for(int i=0;i<extractor.getTrackCount();i++) {
                    MediaFormat fmt=extractor.getTrackFormat(i);
                    String mime=fmt.getString(MediaFormat.KEY_MIME);
                    if(mime.startsWith("video/"))video=true;
                    if(mime.startsWith("audio/"))audio=true;
                }
                if(!video||!audio)throw new IllegalStateException("Missing MP4 video or audio track");
            }finally{extractor.release();}
            // Validate actual clip decoding in the same layer compositor used for preview/export.
            int videoLayer=scene.addLayer(ReactiveView.VIDEO);
            scene.setLayerVideoUri(videoLayer,Uri.fromFile(mp4).toString());
            scene.setMask(videoLayer,4);
            scene.setPolygonMask(videoLayer,"0.10,0.10;0.90,0.10;0.80,0.90;0.20,0.90");
            scene.setLayerStart(videoLayer,0);
            android.graphics.Bitmap preview=android.graphics.Bitmap.createBitmap(
                    320,568,android.graphics.Bitmap.Config.ARGB_8888);
            try{
                scene.renderExportFrame(new android.graphics.Canvas(preview),320,568,500);
            }finally{preview.recycle();scene.closeVideoLayers();}
            File archive=new File(getFilesDir(),"ranneki-smoke-project.zip");
            try {
                ProjectBundle.save(this,Uri.fromFile(archive),scene.exportProjectJson(),
                        Uri.fromFile(wav),null);
                ProjectBundle.Loaded loaded=ProjectBundle.open(this,Uri.fromFile(archive));
                if(loaded.music==null)throw new IllegalStateException("Portable project has no audio");
                ReactiveView imported=new ReactiveView(this);
                if(!imported.importProjectJson(loaded.json))throw new IllegalStateException("Project JSON lost");
                int[] ids=imported.getLayerOrder();int last=ids[ids.length-1];
                if(imported.getLayerType(last)!=ReactiveView.VIDEO || imported.getMask(last)!=4)
                    throw new IllegalStateException("Video layer or polygon mask was lost");
                imported.setLayerVideoUri(last,imported.getVideoUri(last));
                imported.closeVideoLayers();
            }finally {archive.delete();}
            // The real GLES compositor must now also handle image, text and decoded
            // video layers in order, without a full-frame CPU Canvas upload.
            scene.setLayerVideoUri(videoLayer,Uri.fromFile(mp4).toString());
            int imageLayer=scene.addLayer(ReactiveView.IMAGE);
            android.graphics.Bitmap badge=android.graphics.Bitmap.createBitmap(
                    72,72,android.graphics.Bitmap.Config.ARGB_8888);
            badge.eraseColor(0xff1dbfd8);
            scene.setLayerImage(imageLayer,badge,"test-image");
            scene.setMask(imageLayer,1);
            int textLayer=scene.addLayer(ReactiveView.TEXT);
            scene.setLayerText(textLayer,"GPU TEST");
            scene.setBlend(textLayer,1);
            File layered=new File(getFilesDir(),"smoke-layered.mp4");
            try{
                String layeredReport=Mp4Exporter.export(this,Uri.fromFile(wav),Uri.fromFile(layered),
                        scene,profile,1000,new AtomicBoolean(false),(p,stage)->{});
                if(!layeredReport.contains("GPU-only export frames: 30") ||
                        !layeredReport.contains("media compatibility frames: 0") ||
                        !layeredReport.contains("GPU sprite/video texture uploads:"))
                    throw new IllegalStateException("Mixed GPU compositor failed: "+layeredReport);
                if(!layered.exists()||layered.length()<4096)
                    throw new IllegalStateException("GPU media compositor output is empty");
            }finally{layered.delete();scene.closeVideoLayers();}
            return "H264/AAC + cached GLES compositor + GPU image/text/video layers + portable ZIP: "+
                    mp4.length()+" bytes; "+stats;
        }finally {wav.delete();mp4.delete();}
    }
    private static void write16(FileOutputStream out,int value) throws IOException {
        out.write(value&255);out.write((value>>>8)&255);
    }
    private static void write32(FileOutputStream out,int value) throws IOException {
        write16(out,value);write16(out,value>>>16);
    }
    private static void writeWav(File file,int seconds,int rate) throws IOException {
        int count=seconds*rate;
        try(FileOutputStream out=new FileOutputStream(file)) {
            out.write(new byte[]{'R','I','F','F'});write32(out,36+count*2);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});write32(out,16);
            write16(out,1);write16(out,1);write32(out,rate);
            write32(out,rate*2);write16(out,2);write16(out,16);
            out.write(new byte[]{'d','a','t','a'});write32(out,count*2);
            for(int i=0;i<count;i++) {
                float time=i/(float)rate;
                double thump=(i%(rate/2)<rate/10)?0.9:0.25;
                short sample=(short)(Math.sin(time*2*Math.PI*110)*thump*27000);
                write16(out,sample);
            }
        }
    }
}
