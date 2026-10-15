package pl.fawizja.pixel.android;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import pl.fawizja.pixel.client.PixelGame;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Punkt wejscia aplikacji na Androidzie.
 * W wersji debug dodatkowy parametr {@code autostart} ("Imie:PROFESJA") pomija menu i od razu uruchamia gre
 * lokalna - uzywa go test na emulatorze w CI ({@code adb shell am start ... --es autostart Test:MONK}).
 */
public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useGyroscope = false;
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        String autostart = debuggable && getIntent() != null ? getIntent().getStringExtra("autostart") : null;
        initialize(autostart == null ? new PixelGame(new AndroidPlatformServices(this))
                : new AutostartGame(new AndroidPlatformServices(this), autostart), config);
    }

    private static final class AutostartGame extends PixelGame {
        private final String autostart;

        AutostartGame(AndroidPlatformServices platform, String autostart) {
            super(platform);
            this.autostart = autostart;
        }

        @Override
        public void create() {
            super.create();
            String[] p = autostart.split(":");
            Vocation v;
            try {
                v = Vocation.valueOf(p.length > 1 ? p[1].trim() : "MONK");
            } catch (IllegalArgumentException e) {
                v = Vocation.MONK;
            }
            if (localSaveSummary() != null) playLocal();
            else createCharacter(p[0].trim().isEmpty() ? "Test" : p[0].trim(), v);
        }
    }
}
