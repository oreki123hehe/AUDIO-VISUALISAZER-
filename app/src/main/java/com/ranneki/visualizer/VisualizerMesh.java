package com.ranneki.visualizer;

/** Shared bounded triangle geometry. Existing style IDs remain stable for old projects. */
final class VisualizerMesh {
    static final String[] STYLES={"Ring Spectrum","Ring Wave","Spectrum Bars","Mirror Bars","Wave Spectrum","Orbit Flower",
        "Dotted Ring","Twin Rings","Spiral","Sunburst","Petal Loop","Square","Diamond","Triangle","Hexagon","Star","Heart","Infinity","Arc Spectrum","Dot Matrix","LED Columns","Ribbon","Radial Ticks","Lissajous"};
    static final String[] GROUPS={"Ring & orbit","Bars & waves","Geometry & symbols"};
    static final int MAX_FLOATS=128*16*6*4;
    final float[] vertices=new float[MAX_FLOATS];int floats;
    static boolean circular(int style){return style==0||style==1||style==5||style==6||style==7||style==9||style==10||(style>=11&&style<=16)||style==22;}
    static int logoShape(int style){switch(style){case 11:return 3;case 12:return 6;case 13:return 4;case 14:return 5;case 15:return 8;case 16:return 7;default:return circular(style)?1:2;}}
    static int group(int s){return s>=11&&s<=17||s==23?2:s==2||s==3||s==4||s==19||s==20||s==21?1:0;}
    static float sample(float[] bands,float t){
        if(bands.length==0)return 0;float x=Math.max(0,Math.min(1,t))*(bands.length-1);int a=(int)x,b=Math.min(a+1,bands.length-1);
        return Math.max(0,Math.min(1,bands[a]+(bands[b]-bands[a])*(x-a)));
    }
    private static float[] path(int style,float t,float radius){
        double a=t*Math.PI*2-Math.PI/2;
        if(style==16){double h=t*Math.PI*2;return new float[]{(float)(Math.pow(Math.sin(h),3)*radius),(float)(-(13*Math.cos(h)-5*Math.cos(2*h)-2*Math.cos(3*h)-Math.cos(4*h))*radius/17)};}
        if(style==17){double h=t*Math.PI*2;return new float[]{(float)(Math.cos(h)*radius*1.55),(float)(Math.sin(2*h)*radius*.65)};}
        if(style==23){double h=t*Math.PI*2;return new float[]{(float)(Math.sin(3*h+.6)*radius*1.25),(float)(Math.sin(2*h)*radius*.75)};}
        int n=style==11||style==12?4:style==13?3:style==14?6:10;
        double start=-Math.PI/2+(style==11?Math.PI/4:0);float v=t*n;int i=Math.min(n-1,(int)v);float k=v-i;
        double a0=start+i*Math.PI*2/n,a1=start+(i+1)*Math.PI*2/n;
        float r0=radius*(style==15&&(i%2==1)?.48f:1),r1=radius*(style==15&&((i+1)%2==1)?.48f:1);
        return new float[]{(float)(Math.cos(a0)*r0*(1-k)+Math.cos(a1)*r1*k),(float)(Math.sin(a0)*r0*(1-k)+Math.sin(a1)*r1*k)};
    }
    void build(int style,float[] bands,int bars,float radius,float height,float thickness){
        floats=0;bars=Math.max(16,Math.min(128,bars));radius=Math.max(1,radius);height=Math.max(0,height);thickness=Math.max(.1f,thickness);
        if(style==19)bars=Math.min(32,bars);if(style==20)bars=Math.min(48,bars);
        for(int i=0;i<bars;i++){
            float t=i/(float)bars,next=(i+1)/(float)bars;
            float q=circular(style)?1-Math.abs(2*t-1):t,qn=circular(style)?1-Math.abs(2*next-1):next;
            float v=sample(bands,q),vn=sample(bands,qn);double a=t*Math.PI*2-Math.PI/2,b=next*Math.PI*2-Math.PI/2;
            float x=(float)Math.cos(a),y=(float)Math.sin(a),xn=(float)Math.cos(b),yn=(float)Math.sin(b);
            float px=(t-.5f)*radius*3.5f,nx=(next-.5f)*radius*3.5f;
            switch(style){
                case 0:segment(x*radius,y*radius,x*(radius+2+v*height),y*(radius+2+v*height),thickness,t);break;
                case 1:case 5:case 10:{
                    float r=radius+v*height*(style==5?1:.48f),rn=radius+vn*height*(style==5?1:.48f);
                    if(style==5){r+=height*.12f*Math.sin(a*6);rn+=height*.12f*Math.sin(b*6);}
                    if(style==10){r=radius*(.78f+.22f*(float)Math.cos(a*5))+v*height*.5f;rn=radius*(.78f+.22f*(float)Math.cos(b*5))+vn*height*.5f;}
                    segment(x*r,y*r,xn*rn,yn*rn,thickness,t);break;}
                case 2:segment(px,0,px,-(2+v*height),thickness,t);break;
                case 3:segment(px,-(2+v*height),px,2+v*height,thickness,t);break;
                case 4:segment(px,(v-.35f)*height,nx,(vn-.35f)*height,thickness,t);segment(px,-(v-.35f)*height,nx,-(vn-.35f)*height,thickness*.55f,t);break;
                case 6:dot(x*(radius+v*height*.35f),y*(radius+v*height*.35f),thickness*(.8f+v*1.7f),t);break;
                case 7:for(int side=-1;side<=1;side+=2){float r=radius+side*(5+v*height*.42f),rn=radius+side*(5+vn*height*.42f);segment(x*r,y*r,xn*rn,yn*rn,thickness,t);}break;
                case 8:{double aa=t*Math.PI*6,bb=next*Math.PI*6;float r=radius*(.15f+t)+v*height*.25f,rn=radius*(.15f+next)+vn*height*.25f;segment((float)Math.cos(aa)*r,(float)Math.sin(aa)*r,(float)Math.cos(bb)*rn,(float)Math.sin(bb)*rn,thickness,t);break;}
                case 9:segment(x*radius*.28f,y*radius*.28f,x*(radius+v*height),y*(radius+v*height),thickness*(.5f+v),t);break;
                case 11:case 12:case 13:case 14:case 15:case 16:case 17:case 23:{
                    float[] c=path(style,t,radius),d=path(style,next,radius);
                    float lc=(float)Math.hypot(c[0],c[1]),ld=(float)Math.hypot(d[0],d[1]);
                    float f=1+v*height*.3f/Math.max(10,lc),g=1+vn*height*.3f/Math.max(10,ld);
                    segment(c[0]*f,c[1]*f,d[0]*g,d[1]*g,thickness,t);break;}
                case 18:{double aa=Math.PI+t*Math.PI;float cx=(float)Math.cos(aa),cy=(float)Math.sin(aa);segment(cx*radius,cy*radius,cx*(radius+v*height+2),cy*(radius+v*height+2),thickness,t);break;}
                case 19:case 20:{int rows=Math.max(1,Math.round(v*12));float step=(height+24)/12;
                    for(int row=0;row<rows;row++){float yy=-row*step;
                        if(style==19)dot(px,yy,Math.min(step*.35f,thickness*1.3f),t);
                        else segment(px,yy,px,yy-step*.65f,Math.min(radius*3.5f/bars*.7f,thickness*2.5f),t);
                    }break;}
                case 21:{float h=2+v*height*.65f,hn=2+vn*height*.65f;
                    vertex(px,-h,t,1);vertex(px,h,t,-1);vertex(nx,-hn,next,1);
                    vertex(nx,-hn,next,1);vertex(px,h,t,-1);vertex(nx,hn,next,-1);break;}
                case 22:for(int j=0;j<1+Math.round(v*6);j++){float r=radius+j*(height/7+2);segment(x*r,y*r,x*(r+height/10+1),y*(r+height/10+1),thickness,t);}break;
                default:throw new IllegalArgumentException("Unknown visualizer style");
            }
        }
    }
    private void dot(float x,float y,float r,float t){for(int i=0;i<8;i++){double a=i*Math.PI/4,b=(i+1)*Math.PI/4;vertex(x,y,t,0);vertex(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r,t,1);vertex(x+(float)Math.cos(b)*r,y+(float)Math.sin(b)*r,t,1);}}
    private void vertex(float x,float y,float t,float edge){if(floats+4>vertices.length)throw new IllegalStateException("Visualizer mesh capacity");vertices[floats++]=x;vertices[floats++]=y;vertices[floats++]=t;vertices[floats++]=edge;}
    private void segment(float x,float y,float bx,float by,float width,float t){
        float dx=bx-x,dy=by-y,len=(float)Math.sqrt(dx*dx+dy*dy);if(len<.0001f)return;
        float nx=-dy/len*width*.5f,ny=dx/len*width*.5f;
        vertex(x+nx,y+ny,t,1);vertex(x-nx,y-ny,t,-1);vertex(bx+nx,by+ny,t,1);
        vertex(bx+nx,by+ny,t,1);vertex(x-nx,y-ny,t,-1);vertex(bx-nx,by-ny,t,-1);
    }
}
