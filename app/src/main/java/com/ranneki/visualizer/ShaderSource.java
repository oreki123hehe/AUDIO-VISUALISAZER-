package com.ranneki.visualizer;

import java.util.Locale;
import java.util.regex.Pattern;

/** User-editable GLES 2.0 fragment shader for offline video export.
 * A conservative input check prevents common infinite GPU loops; the Android GLSL compiler
 * remains authoritative for syntax and supported driver features. */
public final class ShaderSource {
    private ShaderSource() {}
    public static final int MAX_CHARS = 8000;
    private static final Pattern LOOP = Pattern.compile("\\b(for|while|do)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(");
    public static final String CLASSIC =
            "precision mediump float;\n"+
            "varying vec2 vTex;\n"+
            "uniform sampler2D uTexture;\n"+
            "uniform float uPulse, uChromatic, uVignette;\n"+
            "uniform float uTime, uKick, uBass, uSnare, uTreble;\n"+
            "uniform vec2 uResolution;\n"+
            "void main(){\n"+
            " vec2 uv=clamp(vTex,vec2(0.0),vec2(1.0));\n"+
            " vec2 dir=uv-vec2(0.5);\n"+
            " vec2 shift=dir*(0.012*uChromatic)*(0.4+uPulse);\n"+
            " float r=texture2D(uTexture,clamp(uv+shift,vec2(0.0),vec2(1.0))).r;\n"+
            " float g=texture2D(uTexture,uv).g;\n"+
            " float b=texture2D(uTexture,clamp(uv-shift,vec2(0.0),vec2(1.0))).b;\n"+
            " float fade=1.0-uVignette*clamp(dot(dir,dir)*1.65,0.0,1.0)*0.60;\n"+
            " gl_FragColor=vec4(vec3(r,g,b)*fade*(1.0+uPulse*0.055),1.0);\n"+
            "}\n";
    public static final String NEON =
            "precision mediump float;\n"+
            "varying vec2 vTex; uniform sampler2D uTexture;\n"+
            "uniform float uPulse,uChromatic,uVignette,uTime,uKick,uBass,uSnare,uTreble;\n"+
            "uniform vec2 uResolution;\n"+
            "void main(){\n"+
            " vec2 uv=clamp(vTex,vec2(0.0),vec2(1.0));\n"+
            " float scan=0.965+0.035*sin(uv.y*uResolution.y*0.8+uTime*13.0);\n"+
            " float shift=0.006*(0.2+uKick+uChromatic);\n"+
            " float r=texture2D(uTexture,clamp(uv+vec2(shift,0.0),vec2(0.0),vec2(1.0))).r;\n"+
            " float g=texture2D(uTexture,uv).g;\n"+
            " float b=texture2D(uTexture,clamp(uv-vec2(shift,0.0),vec2(0.0),vec2(1.0))).b;\n"+
            " vec3 color=vec3(r,g,b)*scan;\n"+
            " color+=vec3(0.08*uBass,0.02*uTreble,0.11*uSnare);\n"+
            " gl_FragColor=vec4(clamp(color,0.0,1.0),1.0);\n"+
            "}\n";
    public static final String MONOCHROME =
            "precision mediump float; varying vec2 vTex; uniform sampler2D uTexture;\n"+
            "uniform float uPulse,uChromatic,uVignette,uTime,uKick,uBass,uSnare,uTreble;\n"+
            "uniform vec2 uResolution;\n"+
            "void main(){ vec3 c=texture2D(uTexture,vTex).rgb;\n"+
            " float y=dot(c,vec3(0.2126,0.7152,0.0722));\n"+
            " float v=clamp(y*(1.0+uKick*0.22),0.0,1.0);\n"+
            " gl_FragColor=vec4(vec3(v),1.0); }\n";
    public static String validate(String source) {
        if(source==null || source.trim().isEmpty()) return CLASSIC;
        if(source.length()>MAX_CHARS) throw new IllegalArgumentException("Shader melebihi "+MAX_CHARS+" karakter");
        if(source.indexOf('\u0000')>=0) throw new IllegalArgumentException("Shader mengandung karakter NUL");
        String lower=source.toLowerCase(Locale.ROOT);
        if(lower.contains("#extension") || lower.contains("#version") ||
                LOOP.matcher(source).find() || lower.matches("(?s).*\\bdo\\s*\\{.*"))
            throw new IllegalArgumentException("Versi/extension/loop GLSL dibatasi agar aman untuk GPU HP");
        if(!MAIN.matcher(source).find() || !source.contains("gl_FragColor"))
            throw new IllegalArgumentException("Shader perlu void main() dan gl_FragColor");
        if(!source.contains("vTex"))
            throw new IllegalArgumentException("Shader perlu varying vec2 vTex");
        return source;
    }
}
