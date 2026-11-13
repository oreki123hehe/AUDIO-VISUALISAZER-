package com.ranneki.visualizer;
import org.json.JSONObject;

/** Optional non-destructive layer controls; old projects retain their original defaults. */
final class LayerAdvanced {
    float pivotX,pivotY,depth,feather,morph;
    int morphSource=-1,pathDuration=4000;
    boolean followPath,fillCanvas,autoArrange;
    DrawnPath motionPath,spectrumPath,morphPath;
    ClipTiming clip=new ClipTiming();
    JSONObject json() throws Exception {JSONObject o=new JSONObject();o.put("pivotX",pivotX);o.put("pivotY",pivotY);o.put("depth",depth);o.put("feather",feather);o.put("morph",morph);o.put("morphSource",morphSource);o.put("pathDuration",pathDuration);o.put("followPath",followPath);o.put("fillCanvas",fillCanvas);o.put("autoArrange",autoArrange);o.put("motionPath",motionPath==null?"":motionPath.encode());o.put("spectrumPath",spectrumPath==null?"":spectrumPath.encode());o.put("morphPath",morphPath==null?"":morphPath.encode());o.put("clip",clip.encode());return o;}
    static LayerAdvanced from(JSONObject o)throws Exception{LayerAdvanced a=new LayerAdvanced();if(o==null)return a;
        a.pivotX=number(o,"pivotX",0,-200,200);a.pivotY=number(o,"pivotY",0,-355,355);a.depth=number(o,"depth",0,-400,1200);a.feather=number(o,"feather",0,0,40);a.morph=number(o,"morph",0,0,1);a.morphSource=o.optInt("morphSource",-1);if(a.morphSource< -1||a.morphSource>10)throw new IllegalArgumentException("Morph source invalid");
        a.pathDuration=o.optInt("pathDuration",4000);if(a.pathDuration<100||a.pathDuration>600000)throw new IllegalArgumentException("Path duration invalid");a.followPath=o.optBoolean("followPath");a.fillCanvas=o.optBoolean("fillCanvas");a.autoArrange=o.optBoolean("autoArrange");
        a.motionPath=DrawnPath.decode(o.optString("motionPath"));a.spectrumPath=DrawnPath.decode(o.optString("spectrumPath"));a.morphPath=DrawnPath.decode(o.optString("morphPath"));a.clip=ClipTiming.decode(o.optString("clip"));return a;}
    private static float number(JSONObject o,String key,float d,float lo,float hi){float f=(float)o.optDouble(key,d);if(!Float.isFinite(f)||f<lo||f>hi)throw new IllegalArgumentException(key+" invalid");return f;}
    LayerAdvanced copy(){try{return from(json());}catch(Exception e){throw new IllegalStateException(e);}}
}
