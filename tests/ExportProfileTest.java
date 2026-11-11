package com.ranneki.visualizer;
public final class ExportProfileTest {
    public static void main(String[] args) {
        ExportProfile p=new ExportProfile(1080,1920,30,75,45,25);
        if(p.rasterWidth()!=810||p.rasterHeight()!=1440||p.fps!=30)
            throw new AssertionError("raster scale");
        ExportProfile q=new ExportProfile(1920,1080,60,65,0,0);
        if((q.rasterHeight()&1)!=0||(q.rasterWidth()&1)!=0)throw new AssertionError("even sizes");
        if(q.bitrate()<1800000)throw new AssertionError("bitrate");
        try {new ExportProfile(1080,1920,15,75,0,0);throw new AssertionError("invalid fps");}
        catch(IllegalArgumentException expected){}
        System.out.println("PASS: independent output/render resolution and bitrate profiles");
    }
}
