package com.ranneki.visualizer;

/** JDK-only contract test for manual strength automation. */
public final class AutomationTrackTest {
    private static void assertNear(int a,int b) {
        if(Math.abs(a-b)>1) throw new AssertionError(a+" not near "+b);
    }
    public static void main(String[] args) {
        AutomationTrack a=new AutomationTrack();
        assertNear(a.valueAt(1200,42),42);
        a.put(0,10);a.put(2000,90);
        assertNear(a.valueAt(1000,10),50);
        assertNear(a.valueAt(-5,10),10);
        assertNear(a.valueAt(2500,10),90);
        a.put(1000,30);
        if(a.count()!=3) throw new AssertionError("wrong keyframe count");
        String serial=a.encode();
        AutomationTrack b=new AutomationTrack();b.decode(serial);
        assertNear(b.valueAt(500,99),20);
        assertNear(b.valueAt(1500,99),60);
        b.removeNear(1001,100);
        if(b.count()!=2) throw new AssertionError("removal failed");
        if(!b.move(0,120L))throw new AssertionError("exact keyframe move failed");
        if(b.count()!=2 || b.positions()[0]!=120)throw new AssertionError("move must preserve count");
        if(b.move(120,2000))throw new AssertionError("moving into another keyframe must be rejected");
        if(b.count()!=2 || b.positions()[0]!=120)throw new AssertionError("collision damaged existing points");
        b.decode("bad;20:999;5:50;20:80");
        if(b.count()!=2) throw new AssertionError("invalid decoding");
        b.clear();
        if(b.count()!=0) throw new AssertionError("clear failed");
        System.out.println("PASS: AutomationTrack interpolation, persistence, removal, validation");
    }
}
