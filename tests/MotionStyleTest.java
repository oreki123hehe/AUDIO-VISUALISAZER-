package com.ranneki.visualizer;
public final class MotionStyleTest {
    public static void main(String[] args){
        MotionStyle.Pose parent=new MotionStyle.Pose(10,20,2,90),child=new MotionStyle.Pose(5,0,.5f,15),world=parent.child(child);
        if(Math.abs(world.x-10)>.001||Math.abs(world.y-30)>.001||world.scale!=1||world.rotation!=105)throw new AssertionError("Parent composition");
        float[] signal=new float[400];long[] times=new long[400];for(int i=0;i<400;i++){times[i]=i*10;signal[i]=i<100?1:0;}
        float[] curve=MotionStyle.spring(signal,times,.38f);float max=0,min=0;
        for(int i=0;i<curve.length;i++){if(!Float.isFinite(curve[i]))throw new AssertionError("Unstable spring");max=Math.max(max,curve[i]);if(i>100)min=Math.min(min,curve[i]);}
        if(max<=1.1||min>=-.05||Math.abs(curve[399])>.001)throw new AssertionError("Spring must overshoot and settle");
        for(int type=0;type<MotionStyle.NAMES.length;type++){MotionStyle m=new MotionStyle();m.type=type;MotionStyle n=MotionStyle.decode(m.encode());MotionStyle.Pose a=m.apply(child,1700,.8f),b=n.apply(child,1700,.8f);if(a.x!=b.x||a.rotation!=b.rotation||a.scale!=b.scale)throw new AssertionError("Motion persistence/time independence");}
        LayerFilter f=new LayerFilter(3);f.route.source=4;f.route.depth=-20;LayerFilter copy=LayerFilter.decode(f.encode());if(copy.type!=3||copy.route.source!=4||copy.route.depth!=-20)throw new AssertionError("Filter route roundtrip");
        AudioRoute route=new AudioRoute();route.source=0;route.depth=1;if(route.applyFloat(7,50,.1f)<=50||route.applyFloat(7,50,.1f)>=51)throw new AssertionError("Audio quantized");
        System.out.println("PASS: spring settling, deterministic motion, parent transforms and independent filter routes");
    }
}
