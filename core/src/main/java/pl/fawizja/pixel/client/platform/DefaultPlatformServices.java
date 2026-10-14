package pl.fawizja.pixel.client.platform;

import com.badlogic.gdx.Gdx;
import java.io.File;
import java.util.Map;
import pl.fawizja.pixel.perf.Resource;

/** Implementacja domyslna (desktop): tylko pamiec JVM jest mierzona dokladnie. */
public class DefaultPlatformServices implements PlatformServices {

    @Override
    public void sampleResources(Map<Resource, Float> out) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        out.put(Resource.APP_MEMORY, (float) used / (float) rt.maxMemory());
        out.put(Resource.SYSTEM_MEMORY, Float.NaN);
        out.put(Resource.CPU, Float.NaN);
        out.put(Resource.THERMAL, Float.NaN);
    }

    @Override
    public String batteryInfo() { return null; }

    @Override
    public String thermalInfo() { return null; }

    @Override
    public File saveDirectory() {
        String custom = System.getProperty("pixel.saveDir");
        return custom != null ? new File(custom) : Gdx.files.local("saves").file();
    }

    @Override
    public boolean needsSoftwareFrameLimit() { return false; }
}
