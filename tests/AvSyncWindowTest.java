package com.ranneki.visualizer;

public final class AvSyncWindowTest {
    public static void main(String[] args) {
        AvSyncWindow.validate(0,1_966_667L,0,1_980_000L,2_000L);
        try {AvSyncWindow.validate(0,1_966_667L,500_000L,1_980_000L,2_000L);
            throw new AssertionError("expected offset detection");
        } catch(IllegalStateException expected){}
        try {AvSyncWindow.validate(0,1_966_667L,0,800_000L,2_000L);
            throw new AssertionError("expected truncated audio detection");
        }catch(IllegalStateException expected){}
        System.out.println("AvSyncWindowTest PASS");
    }
}
