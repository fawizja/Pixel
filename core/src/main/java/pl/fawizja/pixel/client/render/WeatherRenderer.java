package pl.fawizja.pixel.client.render;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import pl.fawizja.pixel.world.Weather;

/**
 * Opady rysowane w jednostkach swiata nad widokiem kamery. Bez stanu: pozycja kazdej czasteczki wynika z jej
 * numeru i czasu, wiec nie ma alokacji ani aktualizacji w petli gry.
 */
public final class WeatherRenderer {

    private static final int MAX_PARTICLES = 220;

    public void render(SpriteBatch batch, TextureRegion white, Weather.Kind kind, float intensity, float quality,
                       float left, float bottom, float width, float height, long timeMs) {
        if (kind == Weather.Kind.CLEAR || intensity <= 0f) return;
        int n = (int) (MAX_PARTICLES * intensity * Math.max(0.2f, quality));
        float sec = timeMs / 1000f;
        if (kind == Weather.Kind.SANDSTORM) {
            batch.setColor(0.8f, 0.62f, 0.35f, 0.24f * intensity); // piaskowa mgla
            batch.draw(white, left, bottom, width, height);
        }
        for (int i = 0; i < n; i++) {
            float u = hash(i * 3 + 1);
            float v = hash(i * 3 + 2);
            float s = 0.75f + 0.5f * hash(i * 3 + 3);
            float x;
            float y;
            switch (kind) {
                case RAIN:
                    x = wrap(u * width - sec * 2.5f * s, width);
                    y = wrap(v * height - sec * 14f * s, height);
                    batch.setColor(0.65f, 0.75f, 1f, 0.5f);
                    batch.draw(white, left + x, bottom + y, 0.05f, 0.5f * s);
                    break;
                case SNOW:
                    x = wrap(u * width + (float) Math.sin(sec * 1.3f + i) * 0.4f, width);
                    y = wrap(v * height - sec * 1.6f * s, height);
                    batch.setColor(1f, 1f, 1f, 0.85f);
                    batch.draw(white, left + x, bottom + y, 0.1f * s, 0.1f * s);
                    break;
                default:
                    x = wrap(u * width + sec * 9f * s, width);
                    y = wrap(v * height - sec * 0.8f, height);
                    batch.setColor(0.6f, 0.45f, 0.25f, 0.75f);
                    batch.draw(white, left + x, bottom + y, 0.7f * s, 0.06f);
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private static float wrap(float a, float m) {
        float r = a % m;
        return r < 0 ? r + m : r;
    }

    /** Powtarzalny los 0..1 dla numeru czasteczki. */
    private static float hash(int i) {
        int x = i * 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA77;
        x ^= x >>> 13;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }
}
