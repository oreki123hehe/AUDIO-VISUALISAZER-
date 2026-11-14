package com.ranneki.visualizer;

/** One independent audio connection. Depth is a signed percentage of the target range. */
final class AudioRoute {
    int source=-1, minHz=25, maxHz=10000, depth=25, attack=20, release=180;
    AudioRoute copy(){AudioRoute r=new AudioRoute();r.decode(encode());return r;}
    String encode(){return source+","+minHz+","+maxHz+","+depth+","+attack+","+release;}
    void decode(String value){
        if(value==null||value.isEmpty())return;
        String[] p=value.split(",");if(p.length!=6)throw new IllegalArgumentException("Invalid audio route");
        int s=Integer.parseInt(p[0]),lo=Integer.parseInt(p[1]),hi=Integer.parseInt(p[2]);
        int d=Integer.parseInt(p[3]),a=Integer.parseInt(p[4]),r=Integer.parseInt(p[5]);
        if(s < -1||s>10||lo<25||hi>10000||lo>hi||d < -100||d>100||a<0||a>1000||r<0||r>1500)
            throw new IllegalArgumentException("Audio route outside supported limits");
        source=s;minHz=lo;maxHz=hi;depth=d;attack=a;release=r;
    }
    int apply(int parameter,int base,float envelope){return Math.round(applyFloat(parameter,base,envelope));}
    float applyFloat(int parameter,float base,float envelope){
        if(source<0)return base;
        float range=AutomationParameters.max(parameter)-AutomationParameters.min(parameter);
        return Math.max(AutomationParameters.min(parameter),Math.min(AutomationParameters.max(parameter),base+range*depth*.01f*Math.max(-.35f,Math.min(1.5f,envelope))));
    }
}
