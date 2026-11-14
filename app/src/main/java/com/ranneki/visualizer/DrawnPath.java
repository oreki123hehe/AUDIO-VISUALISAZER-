package com.ranneki.visualizer;

/** Arc-length parameterized path in normalized coordinates; independent of frame rate. */
final class DrawnPath {
    static final int MAX_POINTS=128;
    final float[] xy, distance;
    final float length;
    DrawnPath(float[] points){
        if(points==null||points.length<4||points.length>MAX_POINTS*2||points.length%2!=0)
            throw new IllegalArgumentException("Path perlu 2–128 titik");
        xy=points.clone();distance=new float[xy.length/2];
        for(int i=0;i<xy.length;i++)if(!Float.isFinite(xy[i])||Math.abs(xy[i])>2)
            throw new IllegalArgumentException("Titik path di luar kanvas");
        for(int i=1;i<distance.length;i++)distance[i]=distance[i-1]+(float)Math.hypot(xy[i*2]-xy[i*2-2],xy[i*2+1]-xy[i*2-1]);
        length=distance[distance.length-1];
    }
    float[] at(float fraction){
        float d=Math.max(0,Math.min(1,fraction))*length;
        int i=1;while(i<distance.length-1&&distance[i]<d)i++;
        float t=(d-distance[i-1])/Math.max(.000001f,distance[i]-distance[i-1]);
        return new float[]{xy[i*2-2]+(xy[i*2]-xy[i*2-2])*t,xy[i*2-1]+(xy[i*2+1]-xy[i*2-1])*t};
    }
    float tangent(float fraction){float[] a=at(Math.max(0,fraction-.005f)),b=at(Math.min(1,fraction+.005f));return (float)Math.toDegrees(Math.atan2(b[1]-a[1],b[0]-a[0]));}
    float[] resample(int count){count=Math.max(2,Math.min(MAX_POINTS,count));float[] out=new float[count*2];for(int i=0;i<count;i++){float[] p=at(i/(float)(count-1));out[i*2]=p[0];out[i*2+1]=p[1];}return out;}
    String encode(){StringBuilder s=new StringBuilder();for(int i=0;i<xy.length;i++){if(i>0)s.append(',');s.append(xy[i]);}return s.toString();}
    static DrawnPath decode(String text){if(text==null||text.isEmpty())return null;String[] p=text.split(",");if(p.length>MAX_POINTS*2)throw new IllegalArgumentException("Path terlalu panjang");float[] xy=new float[p.length];for(int i=0;i<p.length;i++)xy[i]=Float.parseFloat(p[i]);return new DrawnPath(xy);}
    static float[] morph(DrawnPath a,DrawnPath b,float amount,int count){float[] out=a.resample(count);if(b==null)return out;float[] next=b.resample(count);float t=Math.max(0,Math.min(1,amount));for(int i=0;i<out.length;i++)out[i]+=(next[i]-out[i])*t;return out;}
}
