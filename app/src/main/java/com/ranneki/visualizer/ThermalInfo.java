package com.ranneki.visualizer;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

/** Android thermal severity status; not a battery/CPU temperature sensor. */
final class ThermalInfo {
    private final PowerManager manager;
    private int peak = -1;
    ThermalInfo(Context context) {
        manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }
    int sample() {
        if (Build.VERSION.SDK_INT >= 29 && manager != null) {
            peak = Math.max(peak, manager.getCurrentThermalStatus());
        }
        return peak;
    }
    int peak() { return peak; }
    static boolean shouldStop(int status) { return status >= 3; }
    static String label(int status) {
        switch(status) {
            case 0: return "NONE";
            case 1: return "LIGHT";
            case 2: return "MODERATE";
            case 3: return "SEVERE";
            case 4: return "CRITICAL";
            case 5: return "EMERGENCY";
            case 6: return "SHUTDOWN";
            default: return "UNAVAILABLE";
        }
    }
}
