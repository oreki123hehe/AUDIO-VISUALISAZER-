package com.ranneki.visualizer;

/** Source time and transition envelope shared by video and audio clips. */
final class ClipTiming {
    long inMs=0,outMs=Long.MAX_VALUE,fadeIn=0,fadeOut=0,offsetMs=0;
    float speed=1;
    boolean loop=true;
    long sourceAt(long timelineMs,long startMs,long sourceDuration){
        long begin=Math.max(0,Math.min(inMs,Math.max(0,sourceDuration-1)));
        long end=Math.max(begin+1,Math.min(outMs,sourceDuration));
        long offset=offsetMs+(long)(Math.max(0,timelineMs-startMs)*(double)speed);
        return begin+(loop?offset%(end-begin):Math.min(end-begin-1,offset));
    }
    float alpha(long time,long start,long end){
        if(time<start||time>=end)return 0;
        float a=fadeIn==0?1:Math.min(1,(time-start)/(float)fadeIn);
        return fadeOut==0?a:Math.min(a,Math.max(0,(end-time)/(float)fadeOut));
    }
    long duration(long sourceDuration){return (long)(Math.max(0,Math.min(outMs,sourceDuration)-inMs)/(double)speed);}
    String encode(){return inMs+","+outMs+","+speed+","+fadeIn+","+fadeOut+","+loop+","+offsetMs;}
    static ClipTiming decode(String text){ClipTiming t=new ClipTiming();if(text==null||text.isEmpty())return t;String[] p=text.split(",");if(p.length!=6&&p.length!=7)throw new IllegalArgumentException("Clip timing invalid");
        t.inMs=Long.parseLong(p[0]);t.outMs=Long.parseLong(p[1]);t.speed=Float.parseFloat(p[2]);t.fadeIn=Long.parseLong(p[3]);t.fadeOut=Long.parseLong(p[4]);t.loop=Boolean.parseBoolean(p[5]);t.offsetMs=p.length==7?Long.parseLong(p[6]):0;
        if(t.offsetMs<0||t.offsetMs>86_400_000||t.inMs<0||t.outMs<=t.inMs||!Float.isFinite(t.speed)||t.speed<.25f||t.speed>4||t.fadeIn<0||t.fadeOut<0)throw new IllegalArgumentException("Clip timing out of range");return t;}
}
