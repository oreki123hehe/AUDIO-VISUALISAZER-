package com.ranneki.visualizer;

/** Perspective camera for planar layers at independent depths (2.5D composition). */
final class CameraRig {
    float x,y,z,yaw,pitch,roll,focal=600;
    MotionStyle.Pose project(MotionStyle.Pose p,float depth){
        double a=Math.toRadians(yaw),b=Math.toRadians(pitch),r=Math.toRadians(-roll);
        float dx=p.x-x,dy=p.y-y,dz=depth-z;
        float xx=(float)(dx*Math.cos(a)-dz*Math.sin(a)),zz=(float)(dx*Math.sin(a)+dz*Math.cos(a));
        float yy=(float)(dy*Math.cos(b)-zz*Math.sin(b));zz=(float)(dy*Math.sin(b)+zz*Math.cos(b));
        float scale=focal/Math.max(30,focal+zz);
        return new MotionStyle.Pose((float)(xx*Math.cos(r)-yy*Math.sin(r))*scale,(float)(xx*Math.sin(r)+yy*Math.cos(r))*scale,p.scale*scale,p.rotation-roll);
    }
    String encode(){return x+","+y+","+z+","+yaw+","+pitch+","+roll+","+focal;}
    static CameraRig decode(String s){CameraRig c=new CameraRig();if(s==null||s.isEmpty())return c;String[] p=s.split(",");if(p.length!=7)throw new IllegalArgumentException("Camera invalid");float[] f=new float[7];for(int i=0;i<7;i++){f[i]=Float.parseFloat(p[i]);if(!Float.isFinite(f[i])||Math.abs(f[i])>2000)throw new IllegalArgumentException("Camera out of range");}
        c.x=f[0];c.y=f[1];c.z=f[2];c.yaw=f[3];c.pitch=f[4];c.roll=f[5];c.focal=f[6];if(c.focal<100)throw new IllegalArgumentException("Focal too small");return c;}
    static MotionStyle.Pose pivot(MotionStyle.Pose p,float x,float y){
        double a=Math.toRadians(p.rotation);return new MotionStyle.Pose(p.x+x-(float)(x*Math.cos(a)-y*Math.sin(a))*p.scale,p.y+y-(float)(x*Math.sin(a)+y*Math.cos(a))*p.scale,p.scale,p.rotation);
    }
}
