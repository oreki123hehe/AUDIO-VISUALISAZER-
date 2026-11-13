package com.ranneki.visualizer;
public final class MotionCurveTest {
    static void near(float actual,float expected){if(Math.abs(actual-expected)>.001f)throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        for(int type=0;type<MotionCurve.NAMES.length;type++){MotionCurve c=new MotionCurve(type);near(c.map(0),0);near(c.map(1),1);for(int i=0;i<=1000;i++)if(!Float.isFinite(c.map(i/1000f)))throw new AssertionError("Non-finite easing");}
        near(new MotionCurve(1).map(.5f),.125f);near(new MotionCurve(2).map(.5f),.875f);near(new MotionCurve(3).map(.5f),.5f);
        if(new MotionCurve(6).map(.7f)<=1||new MotionCurve(4).map(.3f)<=1)throw new AssertionError("Overshoot missing");
        MotionCurve curve=MotionCurve.decode("8,0.9,0,1,1");if(curve.map(.5f)>=.3f)throw new AssertionError("Bezier X must be inverted");
        AutomationTrack a=new AutomationTrack();a.put(0,50);a.put(1000,51);near(a.sample(17,0),50.017f);
        if(!a.setCurve(0,curve))throw new AssertionError("Segment selection");String saved=a.encode();AutomationTrack b=new AutomationTrack();b.decode(saved);near(a.sample(500,0),b.sample(500,0));
        b.move(0,10);if(b.curveAt(10).type!=8)throw new AssertionError("Moved keyframe lost easing");
        b.decode("0:20;1000:80");near(b.sample(500,0),50);b.setAllCurves(new MotionCurve(7));near(b.sample(999,0),20);near(b.sample(1000,0),80);
        try{MotionCurve.decode("8,NaN,0,1,1");throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){}
        System.out.println("PASS: subpixel animation, nine easing modes, Bezier inversion, overshoot and legacy roundtrip");
    }
}
