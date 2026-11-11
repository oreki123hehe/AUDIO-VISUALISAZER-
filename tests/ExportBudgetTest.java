package com.ranneki.visualizer;

public final class ExportBudgetTest {
    public static void main(String[] args) {
        long small=ExportBudget.requiredBytes(2_000_000,8_000);
        long large=ExportBudget.requiredBytes(16_000_000,600_000);
        assert small>=64L*1024*1024;
        assert large>small;
        ExportBudget.check(large+1,16_000_000,600_000);
        try { ExportBudget.check(large-1,16_000_000,600_000);throw new AssertionError(); }
        catch(IllegalStateException expected) {}
        System.out.println("ExportBudgetTest PASS");
    }
}
