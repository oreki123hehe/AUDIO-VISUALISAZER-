package com.ranneki.visualizer;

import java.util.Locale;

/** Validated normalized polygon mask. Coordinates are within [0,1]. */
public final class PolygonMask {
    public static final String DEFAULT = "0.50,0.05;0.95,0.50;0.50,0.95;0.05,0.50";
    private final float[] points;
    private PolygonMask(float[] points) {this.points=points;}
    public int count() {return points.length/2;}
    public float x(int index) {return points[index*2];}
    public float y(int index) {return points[index*2+1];}
    public static PolygonMask parse(String input) {
        if(input==null)throw new IllegalArgumentException("Mask polygon kosong");
        String[] vertices=input.trim().split(";",-1);
        if(vertices.length<3||vertices.length>32)
            throw new IllegalArgumentException("Mask perlu 3–32 titik");
        float[] values=new float[vertices.length*2];
        for(int i=0;i<vertices.length;i++) {
            String[] xy=vertices[i].trim().split(",",-1);
            if(xy.length!=2)throw new IllegalArgumentException("Titik harus berbentuk x,y");
            for(int j=0;j<2;j++){
                float number;
                try {number=Float.parseFloat(xy[j].trim());}
                catch(NumberFormatException e){throw new IllegalArgumentException("Koordinat tidak valid",e);}
                if(!Float.isFinite(number)||number<0||number>1)
                    throw new IllegalArgumentException("Koordinat harus 0–1");
                values[i*2+j]=number;
            }
        }
        double twiceArea=0;
        for(int i=0;i<vertices.length;i++) {
            int next=(i+1)%vertices.length;
            twiceArea+=values[i*2]*values[next*2+1]-values[next*2]*values[i*2+1];
        }
        if(Math.abs(twiceArea)<0.0002)
            throw new IllegalArgumentException("Luas polygon terlalu kecil");
        return new PolygonMask(values);
    }
    public String encode(){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<count();i++) {
            if(i>0)out.append(';');
            out.append(String.format(Locale.US,"%.5f,%.5f",x(i),y(i)));
        }
        return out.toString();
    }
}
