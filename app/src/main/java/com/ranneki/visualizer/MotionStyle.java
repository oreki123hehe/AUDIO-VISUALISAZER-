package com.ranneki.visualizer;

/** Deterministic automatic motion; settings use source-audio time, never frame counters. */
final class MotionStyle {
    static final String[] NAMES={"Diam","Kick Punch","Spring Zoom","Bounce Zoom","Float","Orbit","Spin","Sway","Beat Tilt","Cinematic Drift"};
    int type=0,amount=40,speed=35,blur=0,source=0;
    String encode(){return type+","+amount+","+speed+","+blur+","+source;}
    static MotionStyle decode(String text){
        String[] p=text.split(",");if(p.length!=5)throw new IllegalArgumentException("Invalid motion");
        MotionStyle m=new MotionStyle();m.type=Integer.parseInt(p[0]);m.amount=Integer.parseInt(p[1]);m.speed=Integer.parseInt(p[2]);m.blur=Integer.parseInt(p[3]);m.source=Integer.parseInt(p[4]);
        if(m.type<0||m.type>=NAMES.length||m.source<0||m.source>10||m.amount<0||m.amount>100||m.speed<0||m.speed>100||m.blur<0||m.blur>100)throw new IllegalArgumentException("Invalid motion range");return m;
    }
    MotionStyle copy(){return decode(encode());}
    Pose apply(Pose p,long ms,float signal){
        double t=ms/1000.0*(.2+speed*.028);float a=amount*.01f;
        float x=p.x,y=p.y,s=p.scale,r=p.rotation;
        switch(type){
            case 1:case 2:case 3:s*=1+a*.55f*signal;break;
            case 4:x+=Math.sin(t)*8*a;y+=Math.sin(t*.73)*22*a;break;
            case 5:x+=Math.cos(t)*42*a;y+=Math.sin(t)*32*a;break;
            case 6:r+=(float)(t*45*a);break;
            case 7:r+=Math.sin(t)*14*a;break;
            case 8:r+=Math.sin(t*.8)*signal*28*a;break;
            case 9:x+=Math.sin(t*.37)*38*a;y+=Math.cos(t*.23)*16*a;s*=1+.09f*a*(float)Math.sin(t*.4);break;
        }
        return new Pose(x,y,Math.max(.02f,s),r);
    }
    static float[] spring(float[] input,long[] ms,float damping){
        return spring(input,ms,damping,34);
    }
    static float[] spring(float[] input,long[] ms,float damping,float w){
        float[] out=new float[input.length];float y=0,v=0;
        for(int i=0;i<input.length;i++){
            float dt=i==0?.001f:Math.max(.001f,(ms[i]-ms[i-1])/1000f);
            int steps=Math.max(1,(int)Math.ceil(dt/.004f));float h=dt/steps;
            for(int j=0;j<steps;j++){v+=(w*w*(input[i]-y)-2*damping*w*v)*h;y+=v*h;}
            out[i]=Math.max(-.35f,Math.min(1.5f,y));
        }return out;
    }
    static final class Pose {
        final float x,y,scale,rotation;
        Pose(float x,float y,float s,float r){this.x=x;this.y=y;scale=s;rotation=r;}
        Pose child(Pose child){double r=Math.toRadians(rotation);return new Pose(x+scale*(float)(child.x*Math.cos(r)-child.y*Math.sin(r)),y+scale*(float)(child.x*Math.sin(r)+child.y*Math.cos(r)),scale*child.scale,rotation+child.rotation);}
        float[] array(){return new float[]{x,y,scale,(float)Math.toRadians(rotation)};}
    }
}
