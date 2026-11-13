package com.ranneki.visualizer;

import android.graphics.Bitmap;

/** Ready-to-play arrangements. Each layer has its own frequency window and audio connections. */
final class StudioTemplates {
    static final String[] NAMES={"Neon Pulse", "Montagem Impact", "Dual Frequency", "Cinematic Orbit", "Clean Bars", "Flow Waves", "Heart Bloom", "Star Punch", "Neon Hexagon", "Infinity Drift", "Spiral Orbit", "LED Stage"};
    static void apply(ReactiveView s,int template){
        Bitmap logo=null;String uri="";
        for(int id:s.getLayerOrder())if((s.getLayerType(id)==ReactiveView.VISUALIZER||s.getLayerType(id)==ReactiveView.IMAGE)
                &&s.getLayerImage(id)!=null&&!s.getLayerImage(id).isRecycled()){
            logo=s.getLayerImage(id).copy(Bitmap.Config.ARGB_8888,false);uri=s.getImageUri(id);break;
        }
        for(int id:s.getLayerOrder())s.removeLayer(id);
        s.setSpectrumEnabled(true);s.setCameraShake(0);s.setPostFx(template==1?40:12,20);
        int dust=s.addLayer(ReactiveView.DUST);s.setEnabled(dust,true);s.setDensity(dust,18);s.setOpacity(dust,30);s.setSource(dust,3);
        connect(s,dust,AutomationParameters.VELOCITY,3,28,25,10000);
        int main=s.addLayer(ReactiveView.VISUALIZER);s.setLayerName(main,NAMES[template]);
        s.setDensity(main,65);s.setSize(main,s.isPortrait()?55:35);s.setOpacity(main,100);s.setStrength(main,70);
        s.setFrequency(main,25,10000);s.setSource(main,1);s.setVisualizerGlow(main,45);
        AudioRoute noScale=new AudioRoute();s.setAudioRoute(main,AutomationParameters.SCALE,noScale);
        MotionStyle motion=new MotionStyle();motion.type=template==1?3:2;motion.amount=template==1?48:28;motion.blur=30;s.setMotion(main,motion);
        if(template==1){s.setLayerColor(main,0xffff537d);s.setAccentColor(main,0xffffc13d);
            int sparks=s.addLayer(ReactiveView.SPARKS);s.setEnabled(sparks,true);s.setOpacity(sparks,55);s.setDensity(sparks,28);s.setSource(sparks,0);
            connect(s,sparks,AutomationParameters.OPACITY,0,55,25,180);s.setOpacity(sparks,15);
        }else if(template==2){
            s.setVisualizerStyle(main,1);s.setFrequency(main,25,350);s.setLayerName(main,"Low • 25–350 Hz");
            int high=s.addLayer(ReactiveView.VISUALIZER);s.setVisualizerStyle(high,0);s.setSize(high,s.isPortrait()?79:48);
            s.setDensity(high,70);s.setSource(high,3);s.setFrequency(high,1800,10000);s.setOpacity(high,85);
            s.setLayerColor(high,0xff37deed);s.setAccentColor(high,0xffb7fbff);s.setLayerName(high,"High • 1.8–10 kHz");
            connect(s,high,AutomationParameters.SCALE,3,5,1800,10000);
        }else if(template==3){s.setVisualizerStyle(main,5);s.setLayerColor(main,0xffe7c36d);s.setAccentColor(main,0xfffff2cc);
            s.setStrength(main,35);s.setVisualizerGlow(main,25);s.setDensity(main,95);
            connect(s,main,AutomationParameters.ROTATION,6,2,180,2000);
        }else if(template==4){s.setVisualizerStyle(main,2);s.setLayerY(main,68);s.setSize(main,50);s.setDensity(main,42);s.setVisualizerGlow(main,12);
            s.setLayerColor(main,0xffffffff);s.setAccentColor(main,0xff79dbe8);
        }else if(template==5){s.setVisualizerStyle(main,4);s.setSize(main,62);s.setDensity(main,90);s.setVisualizerGlow(main,50);
            s.setLayerColor(main,0xff83e9e0);s.setAccentColor(main,0xffd5a3ff);
            int lower=s.addLayer(ReactiveView.VISUALIZER);s.setVisualizerStyle(lower,3);s.setLayerY(lower,76);
            s.setSize(lower,38);s.setStrength(lower,35);s.setOpacity(lower,60);s.setSource(lower,3);s.setFrequency(lower,2500,10000);
        }
        if(template>=6){
            int[] styles={16,15,14,17,8,20};s.setVisualizerStyle(main,styles[template-6]);
            int[] colors={0xffff7daf,0xffffc35a,0xff49e7df,0xff8e9bff,0xffda8aff,0xff5aff94};s.setLayerColor(main,colors[template-6]);
            motion.type=template==9?9:template==10?6:template==7?3:2;motion.amount=template==10?15:30;s.setMotion(main,motion);
            if(template==11)s.setLayerY(main,65);
            LayerFilter bloom=new LayerFilter(1);bloom.amount=8;bloom.route.source=1;bloom.route.depth=18;s.setFilters(main,new LayerFilter[]{bloom});
        }
        if(logo!=null)s.setLayerImage(main,logo,uri);
    }
    private static void connect(ReactiveView scene,int id,int param,int source,int depth,int min,int max){
        AudioRoute route=new AudioRoute();route.source=source;route.depth=depth;route.minHz=min;route.maxHz=max;
        scene.setAudioRoute(id,param,route);
    }
}
