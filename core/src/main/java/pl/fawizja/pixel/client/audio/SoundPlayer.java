package pl.fawizja.pixel.client.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.audio.SoundCues;
import pl.fawizja.pixel.audio.SoundSynth;
import pl.fawizja.pixel.audio.SoundSynth.Sfx;

/**
 * Odtwarzacz efektow: dzwieki syntezuje {@link SoundSynth}, zapisuje raz jako WAV w pamieci lokalnej
 * aplikacji (libGDX laduje Sound tylko z pliku) i wczytuje. Brak urzadzenia audio (np. Xvfb, CI)
 * albo blad zapisu wylacza dzwiek bez przerywania gry.
 */
public final class SoundPlayer {

    /** Zmiana wersji wymusza ponowna synteze po zmianie brzmienia. */
    private static final String DIR = "sfx-v1/";

    private final Map<Sfx, Sound> sounds = new EnumMap<>(Sfx.class);
    private boolean available;

    public SoundPlayer() {
        try {
            if (Gdx.audio == null || Gdx.files == null) return;
            for (Sfx s : Sfx.values()) {
                // desktop: katalog domowy (nie katalog roboczy repozytorium), Android: pamiec aplikacji
                FileHandle f = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop
                        ? Gdx.files.external(".pixel/" + DIR + s.fileName()) : Gdx.files.local(DIR + s.fileName());
                if (!f.exists()) f.writeBytes(SoundSynth.wav(s), false);
                sounds.put(s, Gdx.audio.newSound(f));
            }
            available = true;
        } catch (RuntimeException e) {
            Gdx.app.log("Pixel", "Dźwięk niedostępny: " + e.getMessage());
            dispose();
        }
    }

    public boolean available() { return available; }

    public void play(List<SoundCues.Cue> cues, float master) {
        if (!available || master <= 0f) return;
        for (SoundCues.Cue c : cues) {
            Sound s = sounds.get(c.sfx());
            if (s != null) s.play(Math.min(1f, c.volume() * master));
        }
    }

    public void dispose() {
        for (Sound s : sounds.values()) {
            try {
                s.dispose();
            } catch (RuntimeException ignored) {
                // zamykanie - nic do zrobienia
            }
        }
        sounds.clear();
        available = false;
    }
}
