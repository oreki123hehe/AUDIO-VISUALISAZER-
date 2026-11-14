package com.ranneki.visualizer;
import android.content.Context;
import android.net.Uri;
import android.graphics.Bitmap;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Real Android codecs, ORT CPU, media persistence and GLES; no mocked model output. */
final class StudioFeatureSmoke {
    private static void step(String message){android.util.Log.i("RANNEKI_STUDIO",message);}
    static String run(Context context)throws Exception{
        step("Generate stereo fixture");
        File dir=new File(context.getFilesDir(),"studio-smoke");dir.mkdirs();File source=new File(dir,"stereo.wav");
        try(PcmAudio.Writer out=new PcmAudio.Writer(source)){for(int i=0;i<88200;i++)out.frame((float)Math.sin(i*2*Math.PI*220/44100)*.4f,(float)Math.sin(i*2*Math.PI*1760/44100)*.3f);}
        step("Analyze stereo fixture");
        AudioAnalyzer.Track track=AudioAnalyzer.analyze(context,Uri.fromFile(source));float[] left=new float[64],right=new float[64];track.stereoAt(500,false,left);track.stereoAt(500,true,right);
        if(peak(left)>=peak(right))throw new AssertionError("Left/right spectrum collapsed");track.pcmAt(500,false,left);boolean pos=false,neg=false;for(float x:left){pos|=x>.1;neg|=x<-.1;}if(!pos||!neg)throw new AssertionError("PCM waveform not signed");
        step("Start offline separation");
        File[] separated=StemSeparator.separate(context,Uri.fromFile(source),new File(dir,"stems"),new AtomicBoolean(),(n,m)->step(n+"% "+m));
        double error=0;try(PcmAudio.Reader original=new PcmAudio.Reader(source)){
            PcmAudio.Reader[] stems=new PcmAudio.Reader[4];try{for(int i=0;i<4;i++){stems[i]=new PcmAudio.Reader(separated[i]);if(stems[i].frames!=original.frames)throw new AssertionError("Stem length/alignment changed");}
                for(int i=0;i<original.frames;i+=31)for(int ch=0;ch<2;ch++){float sum=0;for(PcmAudio.Reader stem:stems)sum+=stem.sample(i,ch);error=Math.max(error,Math.abs(sum-original.sample(i,ch)));}
            }finally{for(PcmAudio.Reader r:stems)if(r!=null)r.close();}
        }if(error>.003)throw new AssertionError("Stem reconstruction error "+error);
        step("Verify stems and mix clips");
        AudioMixProject mix=new AudioMixProject();AudioMixProject.Clip clip=new AudioMixProject.Clip();clip.uri=Uri.fromFile(source).toString();clip.timing.outMs=2000;clip.timing.loop=false;clip.gain=.7f;mix.clips.add(clip);
        if(!mix.split(0,800)||!mix.join(0)||mix.clips.size()!=1)throw new AssertionError("Split/join audio failed");AudioMixProject.Clip second=clip.copy();second.start=500;second.timing.inMs=500;second.timing.outMs=1500;second.pan=1;second.timing.fadeIn=100;mix.clips.add(second);
        File mixed=AudioMixer.render(context,mix,new File(dir,"mix"),new AtomicBoolean(),(n,m)->step(n+"% "+m));
        try(PcmAudio.Reader audio=new PcmAudio.Reader(mixed)){if(audio.frames!=88200)throw new AssertionError("Mix duration wrong");if(Math.abs(audio.sample(1000,0))<.001)throw new AssertionError("Mix silent");}
        step("Verify scene persistence and GPU");
        ReactiveView scene=new ReactiveView(context);for(int id:scene.getLayerOrder())scene.removeLayer(id);scene.setTrack(track);scene.setPostFx(0,0);
        int control=scene.addLayer(ReactiveView.CONTROL),element=scene.addLayer(ReactiveView.VISUALIZER);scene.setParent(element,control);scene.setVisualizerStyle(element,28);
        LayerAdvanced advanced=scene.getAdvanced(element);advanced.pivotX=20;advanced.depth=80;advanced.feather=8;advanced.spectrumPath=new DrawnPath(new float[]{-.8f,0,0,-.6f,.8f,0});advanced.morphPath=new DrawnPath(new float[]{-.8f,-.3f,0,.5f,.8f,-.3f});advanced.motionPath=new DrawnPath(new float[]{0,0,.15f,.1f});advanced.morph=.5f;advanced.clip.fadeIn=100;scene.setAdvanced(element,advanced);scene.setSections(track.sections());scene.arrangeAll(true);
        MotionStyle motion=scene.getMotion(element);motion.blur=50;scene.setMotion(element,motion);CameraRig camera=scene.getCamera();camera.yaw=10;scene.setCamera(camera);
        step("Serialize advanced scene");
        JSONObject metadata=new JSONObject(scene.exportProjectJson());metadata.put("audioClips",mix.json());ReactiveView restored=new ReactiveView(context);if(!restored.importProjectJson(metadata.toString())||restored.getAdvanced(1).depth!=80||restored.getCamera().yaw!=10)throw new AssertionError("Advanced project roundtrip failed");
        step("Bundle media");
        File bundle=new File(dir,"studio.zip");ProjectBundle.save(context,Uri.fromFile(bundle),metadata.toString(),Uri.fromFile(mixed),null);ProjectBundle.Loaded loaded=ProjectBundle.open(context,Uri.fromFile(bundle));AudioMixProject portable=AudioMixProject.from(new JSONObject(loaded.json).getJSONArray("audioClips"));if(portable.clips.size()!=2||!portable.clips.get(0).uri.startsWith("file:"))throw new AssertionError("Multitrack media not bundled");
        step("Check visual split and join");
        MotionStyle.Pose beforeCut=scene.poseAt(element,900);
        int split=scene.splitLayer(element,700);if(split<0||Math.abs(scene.poseAt(split,900).x-beforeCut.x)>.01||Math.abs(scene.poseAt(split,900).y-beforeCut.y)>.01||!scene.joinLayer(element))throw new AssertionError("Visual clip split/join failed");
        for(int type=0;type<LayerFilter.NAMES.length;type++){step("Render filter "+type);LayerFilter filter=new LayerFilter(type);scene.setFilters(element,new LayerFilter[]{filter});Bitmap image=GpuImageRenderer.render(scene,160,284,600);if(image.getWidth()!=160)throw new AssertionError("Filter output failed");image.recycle();step("Filter "+type+" complete");}
        scene.closeVideoLayers();restored.closeVideoLayers();return "ORT four stems (max reconstruction error "+error+"); PCM stereo; multitrack mix + portable sources; paths/pivot/camera; 22 GPU filters + temporal shutter";
    }
    private static int peak(float[] f){int k=0;for(int i=1;i<f.length;i++)if(f[i]>f[k])k=i;return k;}
}
