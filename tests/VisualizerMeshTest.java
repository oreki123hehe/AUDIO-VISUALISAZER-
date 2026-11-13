package com.ranneki.visualizer;

public final class VisualizerMeshTest {
    public static void main(String[] args){
        VisualizerMesh mesh=new VisualizerMesh();float[] signal=new float[32];
        for(int i=0;i<signal.length;i++)signal[i]=i/31f;
        for(int style=0;style<VisualizerMesh.STYLES.length;style++){
            mesh.build(style,signal,128,60,30,2);
            if(mesh.floats==0||mesh.floats%12!=0)throw new AssertionError("Incomplete triangles");
            for(int i=0;i<mesh.floats;i++)if(!Float.isFinite(mesh.vertices[i]))throw new AssertionError("Invalid vertex");
            float[] first=java.util.Arrays.copyOf(mesh.vertices,mesh.floats);
            mesh.build(style,signal,128,60,30,2);
            if(!java.util.Arrays.equals(first,java.util.Arrays.copyOf(mesh.vertices,mesh.floats)))
                throw new AssertionError("Geometry must not depend on playback history");
        }
        java.util.Arrays.fill(signal,1);for(int style=0;style<VisualizerMesh.STYLES.length;style++){
            mesh.build(style,signal,128,60,80,3);
            if(mesh.floats>VisualizerMesh.MAX_FLOATS)throw new AssertionError("Peak-density overflow");
        }
        if(VisualizerMesh.sample(new float[]{0,1},.5f)!=.5f)throw new AssertionError("Band interpolation");
        System.out.println("PASS: 24 deterministic spectrum styles, bounded triangle mesh and interpolation");
    }
}
