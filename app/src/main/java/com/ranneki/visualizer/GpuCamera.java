package com.ranneki.visualizer;
import android.opengl.GLES20;
final class GpuCamera {
    static void apply(int program,CameraRig camera,float depth,float[] rect,float height){
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uCameraEnabled"),camera==null?0:1);
        if(camera==null)return;float[] matrix=camera.matrix(depth,rect,height);
        GLES20.glUniformMatrix3fv(GLES20.glGetUniformLocation(program,"uProjection"),1,false,matrix,0);
        GLES20.glUniformMatrix3fv(GLES20.glGetUniformLocation(program,"uProjectionInv"),1,false,CameraRig.inverse(matrix),0);
    }
}
