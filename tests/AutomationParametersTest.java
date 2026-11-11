package com.ranneki.visualizer;

/** JDK-only regression test for parameter-independent offline keyframe curves. */
public final class AutomationParametersTest {
    private static void eq(int actual,int expected){
        if(actual!=expected)throw new AssertionError(actual+" != "+expected);
    }
    public static void main(String[] args){
        if(AutomationParameters.COUNT!=16)throw new AssertionError("Wrong automation count");
        AutomationTrack size=AutomationParameters.newTrack(AutomationParameters.SIZE);
        AutomationTrack opacity=AutomationParameters.newTrack(AutomationParameters.OPACITY);
        AutomationTrack hertz=AutomationParameters.newTrack(AutomationParameters.MIN_HZ);
        AutomationTrack attack=AutomationParameters.newTrack(AutomationParameters.ATTACK);
        size.put(0,15);size.put(2000,85);
        opacity.put(0,90);opacity.put(2000,20);
        eq(size.valueAt(1000,0),50);
        eq(opacity.valueAt(1000,0),55);
        hertz.put(0,25);hertz.put(1000,10000);
        eq(hertz.valueAt(500,25),5013);
        attack.put(0,100);attack.put(1000,900);
        eq(attack.valueAt(500,0),500);
        // Frequency bounds must reject values outside its valid range on decode.
        hertz.decode("0:0;1000:10001;2000:480");
        eq(hertz.count(),1);
        eq(hertz.valueAt(500,25),480);
        AutomationTrack duplicate=AutomationParameters.newTrack(AutomationParameters.ATTACK);
        duplicate.decode(attack.encode());
        eq(duplicate.valueAt(500,0),500);
        size.removeNear(1950,100);
        eq(size.count(),1);
        System.out.println("PASS: 16 independent parameter types, interpolation, limits and persistence");
    }
}
