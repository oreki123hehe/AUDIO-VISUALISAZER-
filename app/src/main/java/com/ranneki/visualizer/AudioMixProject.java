package com.ranneki.visualizer;

import org.json.*;
import java.util.*;
/** Source clips remain editable after rendering a preview mix. */
final class AudioMixProject {
    static final int MAX=16;
    static final class Clip {
        String uri,name="Audio";long start;float gain=1,pan;boolean mute;ClipTiming timing=new ClipTiming();
        JSONObject json()throws Exception{JSONObject o=new JSONObject();o.put("uri",uri);o.put("name",name);o.put("start",start);o.put("gain",gain);o.put("pan",pan);o.put("mute",mute);o.put("timing",timing.encode());return o;}
        static Clip from(JSONObject o)throws Exception{Clip c=new Clip();c.uri=o.getString("uri");c.name=o.optString("name","Audio");c.start=o.optLong("start",0);c.gain=(float)o.optDouble("gain",1);c.pan=(float)o.optDouble("pan",0);c.mute=o.optBoolean("mute");c.timing=ClipTiming.decode(o.optString("timing"));c.timing.loop=false;
            if(c.start<0||c.start>86_400_000||!Float.isFinite(c.gain)||c.gain<0||c.gain>4||!Float.isFinite(c.pan)||Math.abs(c.pan)>1)throw new IllegalArgumentException("Audio clip invalid");return c;}
        long duration(){return timing.duration(timing.outMs);}
        Clip copy(){try{return from(json());}catch(Exception e){throw new IllegalArgumentException(e);}}
    }
    final ArrayList<Clip> clips=new ArrayList<>();
    JSONArray json()throws Exception{JSONArray a=new JSONArray();for(Clip c:clips)a.put(c.json());return a;}
    static AudioMixProject from(JSONArray a)throws Exception{AudioMixProject p=new AudioMixProject();if(a==null)return p;if(a.length()>MAX)throw new IllegalArgumentException("Maksimum 16 audio clip");for(int i=0;i<a.length();i++)p.clips.add(Clip.from(a.getJSONObject(i)));return p;}
    long duration(){long n=0;for(Clip c:clips)n=Math.max(n,c.start+c.duration());return n;}
    boolean split(int index,long at){if(index<0||index>=clips.size()||clips.size()>=MAX)return false;Clip a=clips.get(index);if(at<=a.start||at>=a.start+a.duration())return false;Clip b=a.copy();long cut=a.timing.inMs+(long)((at-a.start)*a.timing.speed);b.start=at;b.timing.inMs=cut;b.timing.fadeIn=0;a.timing.outMs=cut;a.timing.fadeOut=0;clips.add(index+1,b);return true;}
    boolean join(int index){if(index<0||index+1>=clips.size())return false;Clip a=clips.get(index),b=clips.get(index+1);if(!a.uri.equals(b.uri)||Math.abs(a.start+a.duration()-b.start)>2||a.timing.outMs!=b.timing.inMs||a.timing.speed!=b.timing.speed||a.gain!=b.gain||a.pan!=b.pan||a.mute!=b.mute||a.timing.fadeOut!=0||b.timing.fadeIn!=0)return false;a.timing.outMs=b.timing.outMs;a.timing.fadeOut=b.timing.fadeOut;clips.remove(index+1);return true;}
}
