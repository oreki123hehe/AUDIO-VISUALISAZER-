package com.ranneki.visualizer;

/** Shared triangle geometry for GLES preview/export and the Canvas compatibility renderer.
 * Coordinates are local design pixels; each vertex contains x, y, gradient position, edge. */
final class VisualizerMesh {
    static final String[] STYLES={"Ring Spectrum", "Ring Wave", "Spectrum Bars",
            "Mirror Bars", "Waveform", "Orbit Flower"};
    final float[] vertices=new float[128*18*4];
    int floats;
    static boolean circular(int style){return style==0||style==1||style==5;}
    static float sample(float[] bands,float t){
        if(bands.length==0)return 0;
        float x=Math.max(0,Math.min(1,t))*(bands.length-1);
        int a=(int)x,b=Math.min(a+1,bands.length-1);
        return Math.max(0,Math.min(1,bands[a]+(bands[b]-bands[a])*(x-a)));
    }
    void build(int style,float[] bands,int bars,float radius,float height,float thickness){
        floats=0;bars=Math.max(16,Math.min(128,bars));
        for(int i=0;i<bars;i++){
            float t=i/(float)bars, next=(i+1)/(float)bars;
            // Mirror frequency order around the ring seam, avoiding a bass/treble discontinuity.
            float q=circular(style)?1-Math.abs(2*t-1):t;
            float qn=circular(style)?1-Math.abs(2*next-1):next;
            float value=sample(bands,q), valueNext=sample(bands,qn);
            double a=t*Math.PI*2-Math.PI/2,b=next*Math.PI*2-Math.PI/2;
            float x=(float)Math.cos(a),y=(float)Math.sin(a),xn=(float)Math.cos(b),yn=(float)Math.sin(b);
            if(style==0){
                segment(x*radius,y*radius,x*(radius+2+value*height),y*(radius+2+value*height),thickness,t);
            }else if(style==1||style==5){
                float r=radius+value*height*(style==5?1:.48f),rn=radius+valueNext*height*(style==5?1:.48f);
                if(style==5){r+=height*.12f*(float)Math.sin(a*6);rn+=height*.12f*(float)Math.sin(b*6);}
                segment(x*r,y*r,xn*rn,yn*rn,thickness,t);
            }else{
                float px=(t-.5f)*radius*3.5f, nx=(next-.5f)*radius*3.5f;
                if(style==2)segment(px,0,px,-(2+value*height),thickness,t);
                else if(style==3)segment(px,-(2+value*height),px,2+value*height,thickness,t);
                else{
                    // A smooth frequency contour, not a claim of sample-accurate PCM waveform.
                    float sy=(value-.35f)*height,ey=(valueNext-.35f)*height;
                    segment(px,sy,nx,ey,thickness,t);
                    segment(px,-sy,nx,-ey,thickness*.55f,t);
                }
            }
        }
    }
    private void vertex(float x,float y,float t,float edge){
        if(floats+4>vertices.length)throw new IllegalStateException("Visualizer mesh capacity");
        vertices[floats++]=x;vertices[floats++]=y;vertices[floats++]=t;vertices[floats++]=edge;
    }
    private void segment(float x,float y,float bx,float by,float width,float t){
        float dx=bx-x,dy=by-y,len=(float)Math.sqrt(dx*dx+dy*dy);
        if(len<.0001f)return;
        float nx=-dy/len*width*.5f,ny=dx/len*width*.5f;
        vertex(x+nx,y+ny,t,1);vertex(x-nx,y-ny,t,-1);vertex(bx+nx,by+ny,t,1);
        vertex(bx+nx,by+ny,t,1);vertex(x-nx,y-ny,t,-1);vertex(bx-nx,by-ny,t,-1);
    }
}
