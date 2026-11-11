package com.ranneki.visualizer;

public final class ShaderSourceTest {
    public static void main(String[] args) {
        assert ShaderSource.validate(null).equals(ShaderSource.CLASSIC);
        assert ShaderSource.validate(ShaderSource.CLASSIC).equals(ShaderSource.CLASSIC);
        assert ShaderSource.validate(ShaderSource.NEON).equals(ShaderSource.NEON);
        assert ShaderSource.validate(ShaderSource.MONOCHROME).equals(ShaderSource.MONOCHROME);
        rejects("void main() { while(true) {} gl_FragColor=vec4(vTex,0.,1.); }");
        rejects("void main() { for(;;) {} gl_FragColor=vec4(vTex,0.,1.); }");
        rejects("not a shader");
        rejects("x".repeat(ShaderSource.MAX_CHARS+1));
        System.out.println("ShaderSourceTest PASS");
    }
    private static void rejects(String shader) {
        try { ShaderSource.validate(shader);throw new AssertionError("Expected rejection"); }
        catch(IllegalArgumentException expected) { }
    }
}
