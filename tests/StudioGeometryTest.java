package com.ranneki.visualizer;
public final class StudioGeometryTest {
    static void near(float a,float b){if(Math.abs(a-b)>.001f)throw new AssertionError(a+" != "+b);}
    static void close(float a,float b){if(!Float.isFinite(a)||Math.abs(a-b)>.04f)throw new AssertionError(a+" != "+b);}
    public static void main(String[] args){
        DrawnPath path=new DrawnPath(new float[]{0,0,1,0,1,1});float[] p=path.at(.75f);near(p[0],1);near(p[1],.5f);
        DrawnPath next=new DrawnPath(new float[]{0,0,0,1});float[] morph=DrawnPath.morph(path,next,1,9);near(morph[16],0);near(morph[17],1);
        try{DrawnPath.decode("NaN,0,1,1");throw new AssertionError("NaN accepted");}catch(IllegalArgumentException good){}
        CameraRig camera=new CameraRig();MotionStyle.Pose a=new MotionStyle.Pose(100,50,1,0);
        near(camera.project(a,600).x,50);near(camera.project(a,600).scale,.5f);
        camera.x=27;camera.y=-18;camera.z=45;camera.yaw=29;camera.pitch=-17;camera.roll=23;
        float[] matrix=camera.matrix(250,new float[]{0,0,1,1},711);
        for(float x:new float[]{-150,0,130})for(float y:new float[]{-230,0,290}){
            MotionStyle.Pose projected=camera.project(new MotionStyle.Pose(x,y,1,0),250);
            float w=matrix[2]*x+matrix[5]*y+matrix[8];
            close((matrix[0]*x+matrix[3]*y+matrix[6])/w*200,projected.x);
            close(-(matrix[1]*x+matrix[4]*y+matrix[7])/w*711/2,projected.y);
            float[] back=camera.unproject(projected.x,projected.y,250,711);close(back[0],x);close(back[1],y);
        }
        a=CameraRig.pivot(new MotionStyle.Pose(0,0,1,90),10,0);near(a.x,10);near(a.y,-10);
        VisualizerMesh mesh=new VisualizerMesh();mesh.build(24,new float[]{-.8f,.8f},new float[0],new float[0],16,50,30,2);boolean positive=false,negative=false;for(int i=1;i<mesh.floats;i+=4){positive|=mesh.vertices[i]>10;negative|=mesh.vertices[i]<-10;}if(!positive||!negative)throw new AssertionError("PCM waveform discarded polarity");
        mesh.build(26,new float[]{1,1},new float[]{0,0},new float[0],32,50,30,2);float top=0,bottom=0;for(int i=1;i<mesh.floats;i+=4){top=Math.min(top,mesh.vertices[i]);bottom=Math.max(bottom,mesh.vertices[i]);}if(top> -30||bottom>5)throw new AssertionError("Stereo channels collapsed");
        System.out.println("PASS: path arc length, morph endpoints, pivot, perspective and true signed/stereo geometry");
    }
}
