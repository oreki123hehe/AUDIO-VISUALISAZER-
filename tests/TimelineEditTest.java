package com.ranneki.visualizer;

public final class TimelineEditTest {
    private static void eq(long expected,long actual){if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args) {
        long[] beats={200,700,1200,1700,2200};
        eq(1200,TimelineEdit.snapToBeat(1270,beats,100));
        eq(1270,TimelineEdit.snapToBeat(1270,beats,40));
        eq(0,TimelineEdit.snapToBeat(-123,beats,100));
        eq(700,TimelineEdit.snapToBeat(700,beats,0));
        eq(1500,TimelineEdit.snapToBeat(1500,null,100));
        eq(900,TimelineEdit.shiftRange(700,1200,200,3000)[0]);
        eq(1400,TimelineEdit.shiftRange(700,1200,200,3000)[1]);
        eq(0,TimelineEdit.shiftRange(700,1200,-900,3000)[0]);
        eq(500,TimelineEdit.shiftRange(700,1200,-900,3000)[1]);
        eq(2500,TimelineEdit.shiftRange(700,1200,3000,3000)[0]);
        eq(3000,TimelineEdit.shiftRange(700,1200,3000,3000)[1]);
        eq(0,TimelineEdit.clampFirstLane(-100,48,6));
        eq(42,TimelineEdit.clampFirstLane(100,48,6));
        eq(0,TimelineEdit.clampFirstLane(20,3,6));
        System.out.println("TimelineEditTest OK");
    }
}
