import ctypes as C
import ctypes.util
import json
import re
from pathlib import Path

E=C.CDLL(ctypes.util.find_library('EGL'))
E.eglGetProcAddress.argtypes=[C.c_char_p]
E.eglGetProcAddress.restype=C.c_void_p
def efn(name, result, *args):
    f=getattr(E,name); f.restype=result; f.argtypes=list(args); return f
def glfn(name, result, *args):
    p=E.eglGetProcAddress(name.encode()); assert p,name
    return C.CFUNCTYPE(result,*args)(p)
I=C.c_int; U=C.c_uint; F=C.c_float; P=C.c_void_p; IP=C.POINTER(I)
platform=C.CFUNCTYPE(P,U,P,IP)(E.eglGetProcAddress(b'eglGetPlatformDisplayEXT'))
d=platform(0x31DD,None,None)
major,minor=I(),I()
assert efn('eglInitialize',U,P,IP,IP)(d,C.byref(major),C.byref(minor))
assert efn('eglBindAPI',U,U)(0x30A0)
attrs=(I*15)(0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3033,1,0x3040,4,0x3038,0,0)
cfg=P(); n=I()
assert efn('eglChooseConfig',U,P,IP,C.POINTER(P),I,IP)(d,attrs,C.byref(cfg),1,C.byref(n)) and n.value
surface=efn('eglCreatePbufferSurface',P,P,P,IP)(d,cfg,(I*5)(0x3057,64,0x3056,64,0x3038))
ctx=efn('eglCreateContext',P,P,P,P,IP)(d,cfg,None,(I*3)(0x3098,2,0x3038))
assert ctx and surface
assert efn('eglMakeCurrent',U,P,P,P,P)(d,surface,surface,ctx)
print('Renderer:',glfn('glGetString',C.c_char_p,U)(0x1F01).decode())
source=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/ranneki/visualizer/GpuSceneRenderer.java').read_text()
def java_string(name):
    expr=re.search(r'private static final String '+name+r'=(.*?);\n',source,re.S).group(1)
    return ''.join(json.loads(t) for t in re.findall(r'"(?:\\.|[^"\\])*"',expr))
create_shader=glfn('glCreateShader',U,U)
shader_source=glfn('glShaderSource',None,U,I,C.POINTER(C.c_char_p),IP)
compile_shader=glfn('glCompileShader',None,U)
get_shader=glfn('glGetShaderiv',None,U,U,IP)
shader_log=glfn('glGetShaderInfoLog',None,U,I,IP,C.c_char_p)
def shader(kind,text):
    s=create_shader(kind); data=C.c_char_p(text.encode());shader_source(s,1,C.byref(data),None);compile_shader(s)
    ok=I();get_shader(s,0x8B81,C.byref(ok))
    if not ok.value:
        b=C.create_string_buffer(8192);shader_log(s,8192,None,b);raise RuntimeError(b.value)
    return s
v=shader(0x8B31,java_string('SPRITE_VERTEX'));f=shader(0x8B30,java_string('SPRITE_FRAGMENT'))
p=glfn('glCreateProgram',U)()
attach=glfn('glAttachShader',None,U,U);attach(p,v);attach(p,f)
glfn('glLinkProgram',None,U)(p)
linked=I();glfn('glGetProgramiv',None,U,U,IP)(p,0x8B82,C.byref(linked));assert linked.value
glfn('glUseProgram',None,U)(p)
location=glfn('glGetUniformLocation',I,U,C.c_char_p)
def uni(name,*values):
    loc=location(p,name.encode())
    glfn('glUniform'+str(len(values))+'f',None,I,*([F]*len(values)))(loc,*values)
def int_uni(name,value):glfn('glUniform1i',None,I,I)(location(p,name.encode()),value)
uni('uSceneRect',0,0,1,1);uni('uSize',400,225);uni('uShift',0,0);uni('uRot',1,0);uni('uCamera',0,0)
uni('uScale',1);uni('uDesignH',225);uni('uAlpha',1);uni('uMaskRadius',1)
int_uni('uMask',0);int_uni('uPolyCount',0);int_uni('uImage',0)
uni('uCrop',0,0,1,1);int_uni('uLocalShape',0)
vertices=(F*8)(-.5,.5,.5,.5,-.5,-.5,.5,-.5)
a=glfn('glGetAttribLocation',I,U,C.c_char_p)(p,b'aCorner')
glfn('glEnableVertexAttribArray',None,U)(a)
glfn('glVertexAttribPointer',None,U,I,U,U,I,P)(a,2,0x1406,0,0,C.cast(vertices,P))
texture=U();glfn('glGenTextures',None,I,C.POINTER(U))(1,C.byref(texture))
glfn('glBindTexture',None,U,U)(0x0DE1,texture.value)
param=glfn('glTexParameteri',None,U,U,I)
for key in [0x2801,0x2800]:param(0x0DE1,key,0x2600)
for key in [0x2802,0x2803]:param(0x0DE1,key,0x812F)
upload=glfn('glTexImage2D',None,U,I,I,I,I,I,U,U,P)
def tex(pixels):
    data=(C.c_ubyte*len(pixels))(*pixels)
    upload(0x0DE1,0,0x1908,4,4,0,0x1908,0x1401,C.cast(data,P))
clear_color=glfn('glClearColor',None,F,F,F,F)
clear=glfn('glClear',None,U)
draw=glfn('glDrawArrays',None,U,I,I)
read=glfn('glReadPixels',None,I,I,I,I,U,U,P)
def pixel(x,y):
    out=(C.c_ubyte*4)();read(x,y,1,1,0x1908,0x1401,C.cast(out,P));return list(out)
glfn('glViewport',None,I,I,I,I)(0,0,64,64)
tex([255,0,0,255]*8+[0,0,255,255]*8)
clear_color(0,0,0,1);clear(0x4000);draw(5,0,4)
top,bottom=pixel(32,48),pixel(32,16)
print('Sprite orientation, input top=red/bottom=blue; actual top=',top,'bottom=',bottom)
assert top[0]>top[2] and bottom[2]>bottom[0], 'Sprite image/text/video vertically inverted'
glfn('glEnable',None,U)(0x0BE2)
blend=glfn('glBlendFunc',None,U,U)
tex([128,64,32,255]*16)
for name,src,dst in [('Normal',1,0x0303),('Screen',1,0x0301),('Multiply',0x0306,0x0303)]:
    blend(src,dst)
    values=[]
    for alpha in [1,.1]:
        uni('uAlpha',alpha);clear_color(.4,.4,.4,1);clear(0x4000);draw(5,0,4)
        values.append(pixel(32,32))
    print(name, 'opacity 100%:',values[0], 'opacity 10%:',values[1])
    assert values[0][:3]!=values[1][:3],name+' ignores opacity'
    color=[128/255,64/255,32/255]
    for index,alpha in enumerate([1,.1]):
        expected=[]
        for c in color:
            if name=='Normal':value=c*alpha+.4*(1-alpha)
            elif name=='Screen':value=c*alpha+.4*(1-c*alpha)
            else:value=c*alpha*.4+.4*(1-alpha)
            expected.append(round(value*255))
        assert all(abs(a-b)<=2 for a,b in zip(expected,values[index][:3])),(name,expected,values[index])
# Center-cropped circular logo must discard its rectangular corners and retain orientation.
glfn('glDisable',None,U)(0x0BE2);uni('uAlpha',1);uni('uCrop',.25,0,.75,1);int_uni('uLocalShape',1)
tex(([0,255,0,255]+[255,0,0,255]*2+[0,255,0,255])*2+
    ([0,255,0,255]+[0,0,255,255]*2+[0,255,0,255])*2)
clear_color(0,0,0,1);clear(0x4000);draw(5,0,4)
assert pixel(3,3)[:3]==[0,0,0], 'Logo corners were not cropped'
assert pixel(32,48)[0]>200 and pixel(32,16)[2]>200, 'Logo crop/orientation changed'
# Semitransparent source pixels are already premultiplied by Android Bitmap.
int_uni('uLocalShape',0);uni('uCrop',0,0,1,1);tex([64,32,16,128]*16)
glfn('glEnable',None,U)(0x0BE2);blend(1,0x0303);clear_color(0,0,0,1);clear(0x4000);draw(5,0,4)
assert pixel(32,32)[:3]==[64,32,16], 'Premultiplied alpha was applied twice'
shader(0x8B31,java_string('VISUALIZER_VERTEX'));shader(0x8B30,java_string('VISUALIZER_FRAGMENT'))
# Geometric logo masks must retain center and discard square corners.
glfn('glDisable',None,U)(0x0BE2);tex([255,255,255,255]*16);uni('uAlpha',1)
for shape in [1,2,4,5,6,7,8]:
    int_uni('uLocalShape',shape);clear_color(0,0,0,0);clear(0x4000);draw(5,0,4)
    assert pixel(32,32)[0]>200, ('Missing logo center',shape)
    assert pixel(2,2)[3]<20, ('Uncropped logo corner',shape)
# Execute the actual ordered-filter / motion-blur shader.
source=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/ranneki/visualizer/GpuLayerFilters.java').read_text()
v=shader(0x8B31,java_string('VERTEX'));f=shader(0x8B30,java_string('FRAGMENT'))
p=glfn('glCreateProgram',U)();attach(p,v);attach(p,f);glfn('glLinkProgram',None,U)(p)
glfn('glGetProgramiv',None,U,U,IP)(p,0x8B82,C.byref(linked));assert linked.value
glfn('glUseProgram',None,U)(p)
a=glfn('glGetAttribLocation',I,U,C.c_char_p)(p,b'aPos');vertices=(F*8)(-1,-1,1,-1,-1,1,1,1)
glfn('glEnableVertexAttribArray',None,U)(a);glfn('glVertexAttribPointer',None,U,I,U,U,I,P)(a,2,0x1406,0,0,C.cast(vertices,P))
int_uni('uImage',0);uni('uPixel',1/400,1/225);uni('uTime',.3);uni('uDesignH',225);uni('uRect',0,0,1,1)
uni('uCurrent',0,0,1,0);uni('uPrevious',0,0,1,0)
tex([64,32,16,128]*16)
for kind,amount in [(0,0),(2,0),(3,0),(4,.5),(5,.5),(6,1)]:
    int_uni('uType',kind);uni('uAmount',amount);draw(5,0,4)
    assert all(abs(a-b)<2 for a,b in zip(pixel(32,32),[64,32,16,128])), ('Neutral filter changed pixels',kind,pixel(32,32))
int_uni('uType',4);uni('uAmount',0);draw(5,0,4);gray=pixel(32,32)
assert abs(gray[0]-gray[1])<=1 and abs(gray[1]-gray[2])<=1, gray
# A thin white bar moving right must leave a left-side exposure trail.
data=(C.c_ubyte*(64*64*4))()
for yy in range(64):
    for xx in range(31,34):
        for ch in range(4):data[(yy*64+xx)*4+ch]=255
upload(0x0DE1,0,0x1908,64,64,0,0x1908,0x1401,C.cast(data,P))
int_uni('uType',100);uni('uAmount',1);uni('uPrevious',-40,0,1,0);draw(5,0,4)
assert pixel(27,32)[3]>10 and pixel(45,32)[3]==0, ('Motion blur direction',pixel(27,32),pixel(45,32))
print('PASS: geometric logo masks, neutral filters, saturation and transform-based motion-blur trail')
print('PASS: real GLES shader orientation, premultiplied blending, opacity, logo crop and visualizer shaders')
assert glfn('glGetError',U)()==0
efn('eglMakeCurrent',U,P,P,P,P)(d,None,None,None)
efn('eglDestroyContext',U,P,P)(d,ctx);efn('eglDestroySurface',U,P,P)(d,surface);efn('eglTerminate',U,P)(d)
