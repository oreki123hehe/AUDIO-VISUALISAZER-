package com.ranneki.visualizer;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** RNS project archive: portable JSON, audio, background and independent layer media.
 * Entries are fixed names, never used as filesystem paths supplied by ZIP authors. */
public final class ProjectBundle {
    private static final long MAX_ARCHIVE=768L*1024*1024;
    private static final long MAX_ENTRY=512L*1024*1024;
    private ProjectBundle(){}
    public static final class Loaded {
        public final String json;
        public final Uri music,background;
        private Loaded(String json,Uri music,Uri background){
            this.json=json;this.music=music;this.background=background;
        }
    }
    public static void save(Context context,Uri output,String scene,Uri music,Uri background) throws Exception {
        JSONObject project=new JSONObject(scene);
        JSONArray layers=project.getJSONArray("layers");
        long[] total={0};
        try(OutputStream stream=context.getContentResolver().openOutputStream(output,"w")) {
            if(stream==null)throw new java.io.IOException("Cannot open destination");
            try(ZipOutputStream zip=new ZipOutputStream(stream)) {
                if(music!=null){zipAsset(context,zip,"assets/music.bin",music,total);project.put("bundleAudio","assets/music.bin");}
                if(background!=null){zipAsset(context,zip,"assets/background.bin",background,total);project.put("bundleBackground","assets/background.bin");}
                JSONArray clips=project.optJSONArray("audioClips");
                if(clips!=null){if(clips.length()>AudioMixProject.MAX)throw new IllegalArgumentException("Too many audio clips");for(int i=0;i<clips.length();i++){JSONObject clip=clips.getJSONObject(i);String entry="assets/audio_"+i+".bin";zipAsset(context,zip,entry,Uri.parse(clip.getString("uri")),total);clip.put("uri",entry);}}
                JSONArray stems=project.optJSONArray("stems");
                if(stems!=null)for(int i=0;i<Math.min(4,stems.length());i++){
                    String uri=stems.optString(i,"");
                    if(!uri.isEmpty()){String entry="assets/stem_"+i+".bin";zipAsset(context,zip,entry,Uri.parse(uri),total);stems.put(i,entry);}
                }
                for(int i=0;i<layers.length();i++){
                    JSONObject layer=layers.getJSONObject(i);
                    if(layer.optInt("type")==ReactiveView.IMAGE||layer.optInt("type")==ReactiveView.VISUALIZER){
                        String str=layer.optString("imageUri","");
                        if(!str.isEmpty()){
                            String entry="assets/image_"+i+".bin";
                            zipAsset(context,zip,entry,Uri.parse(str),total);
                            layer.put("imageUri",entry);
                        }
                    }else if(layer.optInt("type")==ReactiveView.VIDEO){
                        String str=layer.optString("videoUri","");
                        if(!str.isEmpty()){
                            String entry="assets/video_"+i+".bin";
                            zipAsset(context,zip,entry,Uri.parse(str),total);
                            layer.put("videoUri",entry);
                        }
                    }
                }
                project.put("bundleVersion",1);
                byte[] json=project.toString().getBytes(StandardCharsets.UTF_8);
                if(json.length>2*1024*1024)throw new IllegalArgumentException("Project metadata too large");
                zip.putNextEntry(new ZipEntry("project.json"));
                zip.write(json);zip.closeEntry();
            }
        }
    }
    private static void zipAsset(Context ctx,ZipOutputStream zip,String name,Uri uri,long[] total) throws Exception {
        try(InputStream in=ctx.getContentResolver().openInputStream(uri)) {
            if(in==null)throw new java.io.IOException("Asset inaccessible: "+name);
            zip.putNextEntry(new ZipEntry(name));
            byte[] buffer=new byte[65536];int n;long per=0;
            while((n=in.read(buffer))!=-1){
                per+=n;total[0]+=n;
                if(per>MAX_ENTRY||total[0]>MAX_ARCHIVE)
                    throw new IllegalArgumentException("Bundle media exceeds safety size limit");
                zip.write(buffer,0,n);
            }
            zip.closeEntry();
        }
    }
    public static Loaded open(Context context,Uri archive) throws Exception {
        File folder=new File(context.getFilesDir(),"ranneki-project-"+UUID.randomUUID());
        if(!folder.mkdirs())throw new java.io.IOException("Cannot allocate project directory");
        boolean success=false;
        try {
            byte[] jsonBytes=null;
            java.util.HashMap<String,Uri> media=new java.util.HashMap<>();
            long total=0;
            try(InputStream stream=context.getContentResolver().openInputStream(archive)) {
                if(stream==null)throw new java.io.IOException("Archive is unavailable");
                try(ZipInputStream zip=new ZipInputStream(stream)) {
                    ZipEntry entry;
                    while((entry=zip.getNextEntry())!=null){
                        String name=entry.getName();
                        if(!name.equals("project.json")&&!name.matches("assets/(music|background|stem_[0-3]|audio_[0-9]{1,2}|image_[0-9]{1,3}|video_[0-9]{1,3})\\.bin"))
                            throw new IllegalArgumentException("Unknown archive entry");
                        if(media.containsKey(name)||name.equals("project.json")&&jsonBytes!=null)
                            throw new IllegalArgumentException("Duplicate entry");
                        java.io.ByteArrayOutputStream json=new java.io.ByteArrayOutputStream();
                        File file=name.equals("project.json")?null:new File(folder,name.replace('/','_'));
                        long entryCount=0;
                        try(OutputStream out=file==null?json:new FileOutputStream(file)) {
                            byte[] buf=new byte[65536];int n;
                            while((n=zip.read(buf))!=-1){
                                entryCount+=n;total+=n;
                                if(entryCount>(file==null?2*1024*1024:MAX_ENTRY)||total>MAX_ARCHIVE)
                                    throw new IllegalArgumentException("Oversized ZIP media");
                                out.write(buf,0,n);
                            }
                        }
                        if(file==null)jsonBytes=json.toByteArray();
                        else media.put(name,Uri.fromFile(file));
                        zip.closeEntry();
                    }
                }
            }
            if(jsonBytes==null)throw new IllegalArgumentException("Archive has no project.json");
            JSONObject project=new JSONObject(new String(jsonBytes,StandardCharsets.UTF_8));
            if(project.optInt("bundleVersion")!=1)throw new IllegalArgumentException("Unsupported project archive version");
            JSONArray layers=project.getJSONArray("layers");
            if(layers.length()>ReactiveView.MAX_LAYERS)throw new IllegalArgumentException("Too many layers");
            for(int i=0;i<layers.length();i++){
                JSONObject layer=layers.getJSONObject(i);
                for(String key:new String[]{"imageUri","videoUri"}){
                    String asset=layer.optString(key,"");
                    if(asset.startsWith("assets/")){
                        Uri restored=media.get(asset);
                        if(restored==null)throw new IllegalArgumentException("Missing ZIP media: "+asset);
                        layer.put(key,restored.toString());
                    }else if(!asset.isEmpty()){
                        // Archives must not trust stale content:// URIs from another phone.
                        throw new IllegalArgumentException("Unbundled layer media: "+key);
                    }
                }
            }
            JSONArray stems=project.optJSONArray("stems");
            if(stems!=null)for(int i=0;i<Math.min(4,stems.length());i++){
                String asset=stems.optString(i,"");
                if(!asset.isEmpty()){
                    if(!asset.matches("assets/stem_[0-3]\\.bin")||!media.containsKey(asset))
                        throw new IllegalArgumentException("Missing or unbundled stem");
                    stems.put(i,media.get(asset).toString());
                }
            }
            JSONArray clips=project.optJSONArray("audioClips");if(clips!=null){if(clips.length()>AudioMixProject.MAX)throw new IllegalArgumentException("Too many audio clips");for(int i=0;i<clips.length();i++){JSONObject clip=clips.getJSONObject(i);String asset=clip.getString("uri");if(!asset.matches("assets/audio_[0-9]{1,2}\\.bin")||!media.containsKey(asset))throw new IllegalArgumentException("Missing audio clip");clip.put("uri",media.get(asset).toString());}AudioMixProject.from(clips);}
            Uri music=media.get(project.optString("bundleAudio",""));
            Uri background=media.get(project.optString("bundleBackground",""));
            success=true;
            return new Loaded(project.toString(),music,background);
        }finally{
            if(!success)delete(folder);
        }
    }
    private static void delete(File folder){
        File[] files=folder.listFiles();
        if(files!=null)for(File f:files)if(f.isDirectory())delete(f);else f.delete();
        folder.delete();
    }
}
