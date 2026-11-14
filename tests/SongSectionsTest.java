package com.ranneki.visualizer;
public final class SongSectionsTest {
    public static void main(String[] args){int n=640;long[] t=new long[n];float[] e=new float[n],b=new float[n];for(int i=0;i<n;i++){t[i]=i*100;e[i]=i<160?.2f:i<320?.4f:i<480?1:.15f;b[i]=i>=320&&i<480?.9f:.2f;}
        SongSections s=SongSections.detect(t,e,b,120);if(s.sections.get(0).type!=0)throw new AssertionError("Intro missing");boolean drop=false;long last=0;for(SongSections.Section part:s.sections){if(part.start!=last||part.end<=part.start)throw new AssertionError("Noncontiguous sections");last=part.end;drop|=part.type==3;}if(!drop)throw new AssertionError("Energy drop missing");
        if(s.intensity(34000)<=s.intensity(2000))throw new AssertionError("Arrangement not responding");if(!s.encode().equals(SongSections.decode(s.encode()).encode()))throw new AssertionError("Section persistence");
        try{SongSections.decode("0,1000,0;900,2000,3");throw new AssertionError("Overlap accepted");}catch(IllegalArgumentException good){}
        System.out.println("PASS: estimated song structure, transitions and editable section validation");
    }
}
