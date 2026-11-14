package com.ranneki.visualizer;

/** Perspective camera for flat artwork at independent 3D depths. */
final class CameraRig {
    float x,y,z,yaw,pitch,roll,focal=600;
    int motion,amount=30,speed=30,source=0;
    CameraRig at(long ms,float audio){CameraRig c=decode(encode());double phase=ms*.0001*(.2+speed*.025);
        if(motion==1){c.yaw+=(float)Math.sin(phase)*amount*.35f;c.pitch+=(float)Math.cos(phase)*amount*.15f;}
        else if(motion==2){c.z+=(float)Math.sin(phase)*amount*2;c.x+=(float)Math.cos(phase)*amount*.4f;}
        else if(motion==3)c.z+=audio*amount*2;return c;}
    private float[] homogeneous(float px,float py,float depth,float[] rect,float h){
        double a=Math.toRadians(yaw),b=Math.toRadians(pitch),r=Math.toRadians(-roll);float dx=px-x,dy=py-y,dz=depth-z;
        float xx=(float)(dx*Math.cos(a)-dz*Math.sin(a)),zz=(float)(dx*Math.sin(a)+dz*Math.cos(a));
        float yy=(float)(dy*Math.cos(b)-zz*Math.sin(b));zz=(float)(dy*Math.sin(b)+zz*Math.cos(b));
        float xr=(float)(xx*Math.cos(r)-yy*Math.sin(r)),yr=(float)(xx*Math.sin(r)+yy*Math.cos(r)),w=(focal+zz)/focal;
        return new float[]{xr/200*rect[2]+(rect[0]*2+rect[2]-1)*w,-yr*2/h*rect[3]+(1-rect[1]*2-rect[3])*w,w};
    }
    float[] matrix(float depth,float[] rect,float height){float[] origin=homogeneous(0,0,depth,rect,height),right=homogeneous(1,0,depth,rect,height),down=homogeneous(0,1,depth,rect,height);
        return new float[]{right[0]-origin[0],right[1]-origin[1],right[2]-origin[2],down[0]-origin[0],down[1]-origin[1],down[2]-origin[2],origin[0],origin[1],origin[2]};}
    static float[] inverse(float[] m){double a=m[0],b=m[3],c=m[6],d=m[1],e=m[4],f=m[7],g=m[2],h=m[5],i=m[8];double det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);if(Math.abs(det)<1e-12)det=det<0?-1e-12:1e-12;
        return new float[]{(float)((e*i-f*h)/det),(float)((f*g-d*i)/det),(float)((d*h-e*g)/det),(float)((c*h-b*i)/det),(float)((a*i-c*g)/det),(float)((b*g-a*h)/det),(float)((b*f-c*e)/det),(float)((c*d-a*f)/det),(float)((a*e-b*d)/det)};}
    MotionStyle.Pose project(MotionStyle.Pose p,float depth){
        double a=Math.toRadians(yaw),b=Math.toRadians(pitch),r=Math.toRadians(-roll);
        float dx=p.x-x,dy=p.y-y,dz=depth-z;
        float xx=(float)(dx*Math.cos(a)-dz*Math.sin(a)),zz=(float)(dx*Math.sin(a)+dz*Math.cos(a));
        float yy=(float)(dy*Math.cos(b)-zz*Math.sin(b));zz=(float)(dy*Math.sin(b)+zz*Math.cos(b));
        float scale=focal/Math.max(30,focal+zz);
        return new MotionStyle.Pose((float)(xx*Math.cos(r)-yy*Math.sin(r))*scale,(float)(xx*Math.sin(r)+yy*Math.cos(r))*scale,p.scale*scale,p.rotation-roll);
    }
    String encode(){return x+","+y+","+z+","+yaw+","+pitch+","+roll+","+focal+","+motion+","+amount+","+speed+","+source;}
    static CameraRig decode(String s){CameraRig c=new CameraRig();if(s==null||s.isEmpty())return c;String[] p=s.split(",");if(p.length!=7&&p.length!=11)throw new IllegalArgumentException("Camera invalid");float[] f=new float[7];for(int i=0;i<7;i++){f[i]=Float.parseFloat(p[i]);if(!Float.isFinite(f[i])||Math.abs(f[i])>2000)throw new IllegalArgumentException("Camera out of range");}
        c.x=f[0];c.y=f[1];c.z=f[2];c.yaw=f[3];c.pitch=f[4];c.roll=f[5];c.focal=f[6];if(c.focal<100)throw new IllegalArgumentException("Focal too small");
        if(p.length==11){c.motion=Integer.parseInt(p[7]);c.amount=Integer.parseInt(p[8]);c.speed=Integer.parseInt(p[9]);c.source=Integer.parseInt(p[10]);if(c.motion<0||c.motion>3||c.amount<0||c.amount>100||c.speed<0||c.speed>100||c.source<0||c.source>10)throw new IllegalArgumentException("Camera motion invalid");}return c;}
    static MotionStyle.Pose pivot(MotionStyle.Pose p,float x,float y){
        double a=Math.toRadians(p.rotation);return new MotionStyle.Pose(p.x+x-(float)(x*Math.cos(a)-y*Math.sin(a))*p.scale,p.y+y-(float)(x*Math.sin(a)+y*Math.cos(a))*p.scale,p.scale,p.rotation);
    }
}
