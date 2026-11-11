package com.ranneki.visualizer;
public final class PolygonMaskTest {
    public static void main(String[] args){
        PolygonMask p=PolygonMask.parse(PolygonMask.DEFAULT);
        if(p.count()!=4||Math.abs(p.x(0)-.5f)>.00001)throw new AssertionError();
        if(PolygonMask.parse(p.encode()).count()!=4)throw new AssertionError();
        invalid("0,0;0,0;0,0");invalid("0,0;1,0");invalid("NaN,0;1,0;0,1");
        invalid("-1,0;1,0;0,1");invalid("0,0;1,0;2,1");
        System.out.println("PolygonMaskTest OK");
    }
    static void invalid(String text){try{PolygonMask.parse(text);throw new AssertionError(text);}catch(IllegalArgumentException expected){}}
}
