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
            String studioChecks=StudioFeatureSmoke.run(this);
            writeWav(wav,2,44100);
            AudioAnalyzer.Track track=AudioAnalyzer.analyze(this,Uri.fromFile(wav));
            verifyReactiveStudio(track,Uri.fromFile(wav));
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
            // Distinctive top/bottom halves also verify that the GPU FBO is not
            // black or vertically inverted after H.264 encode and decode.
            artwork.eraseColor(0xffd43b29);
            for(int y=48;y<96;y++)for(int x=0;x<96;x++)artwork.setPixel(x,y,0xff2a46db);
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
            android.media.MediaMetadataRetriever pixels=new android.media.MediaMetadataRetriever();
            try{
                pixels.setDataSource(mp4.getAbsolutePath());
                android.graphics.Bitmap decoded=pixels.getFrameAtTime(200000,
                        android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if(decoded==null)throw new IllegalStateException("Cannot decode GPU FBO video frame");
                try{
                    long tr=0,tb=0,br=0,bb=0;int n=0;
                    for(int x=40;x<decoded.getWidth()-40;x+=40){
                        int top=decoded.getPixel(x,Math.max(1,decoded.getHeight()/6));
                        int bottom=decoded.getPixel(x,Math.min(decoded.getHeight()-2,decoded.getHeight()*4/5));
                        tr+=android.graphics.Color.red(top);tb+=android.graphics.Color.blue(top);
                        br+=android.graphics.Color.red(bottom);bb+=android.graphics.Color.blue(bottom);n++;
                    }
                    if(n==0 || tr<=tb+12L*n || bb<=br+12L*n)
                        throw new IllegalStateException("GPU FBO pixels black/inverted; decoded="+decoded.getWidth()+"x"+decoded.getHeight()+
                                " samples="+n+" top red "+tr+"/"+tb+" bottom blue "+bb+"/"+br+
                                " center top "+Integer.toHexString(decoded.getPixel(decoded.getWidth()/2,decoded.getHeight()/6))+
                                " center bottom "+Integer.toHexString(decoded.getPixel(decoded.getWidth()/2,decoded.getHeight()*4/5)));
                }finally{decoded.recycle();}
            }finally{pixels.release();}

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
            return studioChecks+"; H264/AAC + 29 visualizers + easing/motion/filters/parenting + logo/stem ZIP + mixed GPU layers: "+
                    mp4.length()+" bytes; "+stats;
        }finally {wav.delete();mp4.delete();}
    }
    private void verifyReactiveStudio(AudioAnalyzer.Track track,Uri audio) throws Exception {
        ReactiveView scene=new ReactiveView(this);for(int id:scene.getLayerOrder())scene.removeLayer(id);
        scene.setTrack(track);scene.setPostFx(0,0);scene.setStem(0,track,audio.toString());
        int element=scene.addLayer(ReactiveView.VISUALIZER);scene.setOpacity(element,100);scene.setLogoSize(element,90);
        AudioRoute route=new AudioRoute();route.source=7;route.depth=15;
        scene.setAudioRoute(element,AutomationParameters.SCALE,route);
        android.graphics.Bitmap logo=android.graphics.Bitmap.createBitmap(192,96,android.graphics.Bitmap.Config.ARGB_8888);
        for(int y=0;y<96;y++)for(int x=0;x<192;x++)logo.setPixel(x,y,x<48||x>=144?0xff00ff00:y<48?0xffff0000:0xff0000ff);
        File logoFile=new File(getFilesDir(),"test-logo.png");
        try(FileOutputStream out=new FileOutputStream(logoFile)){logo.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
        scene.setLayerImage(element,logo,Uri.fromFile(logoFile).toString());
        android.graphics.Bitmap output=GpuImageRenderer.render(scene,320,568,350);
        try{
            int top=output.getPixel(160,264),bottom=output.getPixel(160,304);
            if(android.graphics.Color.red(top)<180||android.graphics.Color.blue(bottom)<180)
                throw new IllegalStateException("Attached logo inverted or missing: "+Integer.toHexString(top)+" / "+Integer.toHexString(bottom));
            // Green side strips must disappear after square center crop; the circular mask
            // must expose the background outside the logo rather than a rectangular image.
            int corner=output.getPixel(115,239);
            if(android.graphics.Color.green(corner)>android.graphics.Color.red(corner)+80&&android.graphics.Color.green(corner)>android.graphics.Color.blue(corner)+80)
                throw new IllegalStateException("Logo center crop failed");
        }finally{output.recycle();}
        // Exercise transparent layer FBOs and filters with a known colored logo.
        LayerFilter gray=new LayerFilter(4);gray.amount=0;scene.setFilters(element,new LayerFilter[]{gray});
        MotionStyle motion=new MotionStyle();motion.blur=60;scene.setMotion(element,motion);
        output=GpuImageRenderer.render(scene,320,568,350);
        try{int pixel=output.getPixel(160,264);if(Math.abs(android.graphics.Color.red(pixel)-android.graphics.Color.green(pixel))>3||Math.abs(android.graphics.Color.green(pixel)-android.graphics.Color.blue(pixel))>3)
            throw new IllegalStateException("Per-layer saturation or transparent composition failed");}finally{output.recycle();}
        scene.setKeyframe(element,AutomationParameters.X,0,50);scene.setKeyframe(element,AutomationParameters.X,1000,51);
        float subpixel=scene.poseAt(element,17).x;if(subpixel<=0||subpixel>=1)throw new IllegalStateException("Transform rounded to integer");
        scene.setCurve(element,AutomationParameters.X,0,new MotionCurve(2),false);
        motion.type=2;motion.amount=35;scene.setMotion(element,motion);
        LayerFilter bloom=new LayerFilter(1);bloom.amount=12;bloom.route.source=0;bloom.route.depth=20;
        scene.setFilters(element,new LayerFilter[]{bloom,gray});
        for(int style=1;style<VisualizerMesh.STYLES.length;style++){
            int id=scene.addLayer(ReactiveView.VISUALIZER);scene.setVisualizerStyle(id,style);scene.setLayerY(id,12+(style%6)*14);scene.setLayerX(id,20+(style/6)*20);scene.setSize(id,12);scene.setVisualizerGlow(id,0);
        }
        int child=scene.getLayerOrder()[1];if(!scene.setParent(child,element)||scene.setParent(element,child))throw new IllegalStateException("Invalid parent cycle accepted");
        String json=scene.exportProjectJson();ReactiveView restored=new ReactiveView(this);
        if(!restored.importProjectJson(json)||restored.getLayerCount()!=VisualizerMesh.STYLES.length)
            throw new IllegalStateException("Multiple visualizers not preserved");
        if(restored.getAudioRoute(restored.getLayerOrder()[0],AutomationParameters.SCALE).source!=7)
            throw new IllegalStateException("Independent stem route not preserved");
        if(restored.getCurve(0,AutomationParameters.X,0).type!=2||restored.getMotion(0).type!=2||restored.getFilters(0).length!=2||restored.getParent(1)!=0)
            throw new IllegalStateException("Motion, easing, filters or parenting lost in project");
        org.json.JSONObject malformed=new org.json.JSONObject(json);malformed.getJSONArray("layers").getJSONObject(0).put("parent",0);
        if(restored.importProjectJson(malformed.toString())||restored.getLayerCount()!=VisualizerMesh.STYLES.length)
            throw new IllegalStateException("Invalid parent import damaged scene");
        File bundle=new File(getFilesDir(),"studio-test.zip");
        try{
            ProjectBundle.save(this,Uri.fromFile(bundle),json,audio,null);
            ProjectBundle.Loaded loaded=ProjectBundle.open(this,Uri.fromFile(bundle));
            org.json.JSONObject metadata=new org.json.JSONObject(loaded.json);
            if(!metadata.getJSONArray("stems").getString(0).startsWith("file:"))throw new IllegalStateException("Stem not bundled");
            if(!metadata.getJSONArray("layers").getJSONObject(0).getString("imageUri").startsWith("file:"))
                throw new IllegalStateException("Visualizer logo not bundled");
        }finally{bundle.delete();}
        output=GpuImageRenderer.render(scene,320,568,600);output.recycle();
        File mp4=new File(getFilesDir(),"studio-elements.mp4");
        try{Mp4Exporter.export(this,audio,Uri.fromFile(mp4),scene,new ExportProfile(320,568,30,65,0,0),1000,new AtomicBoolean(false),(progress,stage)->{});}
        finally{mp4.delete();scene.recycleLayerImages();logoFile.delete();}
        // Sampling after a seek must give exactly the same audio envelope.
        float before=track.envelopeAt(400,0,25,180,0,20,180);
        track.envelopeAt(1200,0,25,180,0,20,180);
        if(before!=track.envelopeAt(400,0,25,180,0,20,180))throw new IllegalStateException("Audio route depends on seek history");
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
