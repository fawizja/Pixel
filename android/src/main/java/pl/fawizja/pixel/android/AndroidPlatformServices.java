package pl.fawizja.pixel.android;

import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import java.io.File;
import java.util.Map;
import pl.fawizja.pixel.client.platform.PlatformServices;
import pl.fawizja.pixel.perf.Resource;

/**
 * Pomiary zasobow na Androidzie. Uzywane sa tylko dane, ktore system faktycznie udostepnia
 * aplikacji: sterta Javy, pamiec systemu (ActivityManager) i stan termiczny (API 29+).
 * Globalnego uzycia CPU Android nie udostepnia zwyklym aplikacjom - wartosc jest NaN.
 */
public final class AndroidPlatformServices implements PlatformServices {

    private final Context context;
    private final ActivityManager activityManager;
    private final PowerManager powerManager;
    private final BatteryManager batteryManager;
    private final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

    public AndroidPlatformServices(Context context) {
        this.context = context.getApplicationContext();
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    }

    @Override
    public void sampleResources(Map<Resource, Float> out) {
        Runtime rt = Runtime.getRuntime();
        out.put(Resource.APP_MEMORY, (float) (rt.totalMemory() - rt.freeMemory()) / (float) rt.maxMemory());
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
            float used = memoryInfo.totalMem > 0 ? 1f - (float) memoryInfo.availMem / (float) memoryInfo.totalMem : Float.NaN;
            out.put(Resource.SYSTEM_MEMORY, memoryInfo.lowMemory ? Math.max(0.95f, used) : used);
        } else {
            out.put(Resource.SYSTEM_MEMORY, Float.NaN);
        }
        out.put(Resource.CPU, Float.NaN);
        out.put(Resource.THERMAL, thermalLevel());
    }

    /** Stan termiczny jako przyblizony poziom 0..1 (SEVERE i wyzej przekracza prog 90%). */
    private float thermalLevel() {
        if (powerManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Float.NaN;
        switch (powerManager.getCurrentThermalStatus()) {
            case PowerManager.THERMAL_STATUS_NONE: return 0f;
            case PowerManager.THERMAL_STATUS_LIGHT: return 0.5f;
            case PowerManager.THERMAL_STATUS_MODERATE: return 0.8f;
            case PowerManager.THERMAL_STATUS_SEVERE: return 0.92f;
            default: return 1f;
        }
    }

    @Override
    public String batteryInfo() {
        if (batteryManager == null) return null;
        int pct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (pct < 0 || pct > 100) return null;
        return pct + "%" + (batteryManager.isCharging() ? " (ladowanie)" : "");
    }

    @Override
    public String thermalInfo() {
        if (powerManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        switch (powerManager.getCurrentThermalStatus()) {
            case PowerManager.THERMAL_STATUS_NONE: return "normalny";
            case PowerManager.THERMAL_STATUS_LIGHT: return "lekko podwyzszony";
            case PowerManager.THERMAL_STATUS_MODERATE: return "umiarkowany";
            case PowerManager.THERMAL_STATUS_SEVERE: return "wysoki";
            default: return "krytyczny";
        }
    }

    @Override
    public File saveDirectory() {
        return new File(context.getFilesDir(), "saves");
    }

    @Override
    public boolean needsSoftwareFrameLimit() {
        return true;
    }
}
