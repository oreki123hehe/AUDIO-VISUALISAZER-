package com.ranneki.visualizer;

/** Time easing. X is solved, not assumed equal to the Bezier parameter. */
final class MotionCurve {
    static final String[] NAMES={"Linear","Ease In","Ease Out","Ease In Out","Spring","Bounce","Overshoot","Hold","Bezier Custom"};
    int type;float x1=.25f,y1=.1f,x2=.25f,y2=1;
    MotionCurve(){}
    MotionCurve(int type){this.type=type;}
    float map(float value){
        float t=Math.max(0,Math.min(1,value));if(t==0||t==1)return t;
        switch(type){
            case 1:return t*t*t;
            case 2:return 1-(1-t)*(1-t)*(1-t);
            case 3:return t<.5f?4*t*t*t:1-(float)Math.pow(-2*t+2,3)/2;
            case 4:return 1-(float)(Math.exp(-7*t)*Math.cos(11*t));
            case 5:float n=7.5625f,d=2.75f;if(t<1/d)return n*t*t;if(t<2/d){t-=1.5f/d;return n*t*t+.75f;}
                if(t<2.5f/d){t-=2.25f/d;return n*t*t+.9375f;}t-=2.625f/d;return n*t*t+.984375f;
            case 6:float u=t-1;return 1+2.70158f*u*u*u+1.70158f*u*u;
            case 7:return 0;
            case 8:float lo=0,hi=1;for(int i=0;i<20;i++){float m=(lo+hi)*.5f;if(bezier(m,x1,x2)<t)lo=m;else hi=m;}
                return bezier((lo+hi)*.5f,y1,y2);
            default:return t;
        }
    }
    private static float bezier(float t,float a,float b){float u=1-t;return 3*u*u*t*a+3*u*t*t*b+t*t*t;}
    String encode(){return type+","+x1+","+y1+","+x2+","+y2;}
    static MotionCurve decode(String text){
        String[] p=text.split(",");if(p.length!=5)throw new IllegalArgumentException("Invalid curve");
        MotionCurve c=new MotionCurve(Integer.parseInt(p[0]));c.x1=Float.parseFloat(p[1]);c.y1=Float.parseFloat(p[2]);c.x2=Float.parseFloat(p[3]);c.y2=Float.parseFloat(p[4]);
        if(c.type<0||c.type>=NAMES.length||!Float.isFinite(c.x1)||!Float.isFinite(c.x2)||!Float.isFinite(c.y1)||!Float.isFinite(c.y2)||c.x1<0||c.x1>1||c.x2<0||c.x2>1||Math.abs(c.y1)>3||Math.abs(c.y2)>3)
            throw new IllegalArgumentException("Curve outside supported range");return c;
    }
    MotionCurve copy(){return decode(encode());}
}
