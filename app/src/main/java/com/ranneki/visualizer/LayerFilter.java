package com.ranneki.visualizer;

/** Ordered GPU filter with an optional independent audio connection. */
final class LayerFilter {
    static final String[] NAMES={"Blur","Bloom","RGB Split","Wave Warp","Saturation","Exposure","Directional Blur","Zoom Blur","Radial Blur","Twirl","Pixelate","Mirror","Kaleidoscope","Posterize","Invert","Sepia","Contrast","Hue Shift","Sharpen","Scanlines","Lens Distortion","Displacement"};
    static final int MAX=12;
    int type,amount=30;AudioRoute route=new AudioRoute();
    LayerFilter(int type){this.type=type;if(type==4||type==5||type==16||type==17)amount=50;}
    String encode(){return type+":"+amount+"|"+route.encode();}
    static LayerFilter decode(String text){String[] a=text.split("\\|");if(a.length!=2)throw new IllegalArgumentException("Invalid filter");String[] b=a[0].split(":");if(b.length!=2)throw new IllegalArgumentException("Invalid filter");
        LayerFilter f=new LayerFilter(Integer.parseInt(b[0]));f.amount=Integer.parseInt(b[1]);f.route.decode(a[1]);
        if(f.type<0||f.type>=NAMES.length||f.amount<0||f.amount>100)throw new IllegalArgumentException("Invalid filter range");return f;}
    LayerFilter copy(){return decode(encode());}
}
