package com.ranneki.visualizer;

public final class AudioRouteTest {
    public static void main(String[] args){
        AudioRoute kick=new AudioRoute();kick.source=0;kick.depth=25;
        AudioRoute bass=kick.copy();bass.source=1;bass.depth=-50;
        if(kick.apply(AutomationParameters.SCALE,50,1)!=75||bass.apply(AutomationParameters.SCALE,50,1)!=0)
            throw new AssertionError("Independent signed modulation");
        if(kick.apply(AutomationParameters.OPACITY,90,1)!=100)throw new AssertionError("Target bounds");
        AudioRoute restored=new AudioRoute();restored.decode(kick.encode());
        if(!restored.encode().equals(kick.encode()))throw new AssertionError("Route persistence");
        try{restored.decode("0,300,25,10,20,100");throw new AssertionError("Invalid frequency range accepted");}
        catch(IllegalArgumentException expected){}
        System.out.println("PASS: independent positive/negative audio routing, bounds and persistence");
    }
}
