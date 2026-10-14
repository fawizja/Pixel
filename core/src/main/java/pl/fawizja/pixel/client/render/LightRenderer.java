package pl.fawizja.pixel.client.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.world.FloorMap;

/**
 * Mapa swiatla w buforze FBO: wypelnienie kolorem otoczenia, addytywne swiatla,
 * a potem mnozenie przez obraz sceny. Bufor ma obnizona rozdzielczosc (swiatlo jest
 * rozmyte, wiec to niewidoczne), a przy trybie LIMITED jest jeszcze mniejszy.
 * Lista zrodel swiatla na pietrze jest buforowana - nie skanujemy mapy w kazdej klatce.
 */
public final class LightRenderer implements Disposable {

    private final Texture lightTexture;
    private FrameBuffer fbo;
    private int fboW;
    private int fboH;
    private final Matrix4 screenMatrix = new Matrix4();
    private final Color warm = new Color(1f, 0.82f, 0.55f, 1f);
    private FloorMap cachedFloor;
    private List<int[]> cachedLights = new ArrayList<>();

    public LightRenderer(Texture lightTexture) {
        this.lightTexture = lightTexture;
    }

    private void ensureFbo(int w, int h) {
        if (fbo != null && fboW == w && fboH == h) return;
        if (fbo != null) fbo.dispose();
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        fboW = w;
        fboH = h;
    }

    /**
     * @param ambient     jasnosc otoczenia 0..1 (1 = brak efektu, nic nie jest rysowane)
     * @param quality     jakosc efektow 0..1 z ochrony zasobow
     * @param playerX     srodek swiatla postaci (wspolrzedne swiata)
     * @param playerLight promien swiatla postaci w kafelkach
     */
    public void render(SpriteBatch batch, OrthographicCamera cam, FloorMap floor, float mapHeight,
                       float ambient, float quality, float playerX, float playerY, float playerLight) {
        if (ambient >= 0.999f) return;
        int div = quality >= 0.75f ? 2 : 4;
        ensureFbo(Math.max(1, Gdx.graphics.getBackBufferWidth() / div), Math.max(1, Gdx.graphics.getBackBufferHeight() / div));
        if (floor != cachedFloor) {
            cachedFloor = floor;
            cachedLights = floor == null ? new ArrayList<>() : floor.lightSources();
        }

        fbo.begin();
        Gdx.gl.glClearColor(ambient * 0.85f, ambient * 0.9f, Math.min(1f, ambient * 1.1f), 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        float halfW = cam.viewportWidth * cam.zoom / 2f + 8;
        float halfH = cam.viewportHeight * cam.zoom / 2f + 8;
        for (int[] l : cachedLights) {
            float lx = l[0] + 0.5f;
            float ly = mapHeight - 1 - l[1] + 0.5f;
            if (Math.abs(lx - cam.position.x) > halfW || Math.abs(ly - cam.position.y) > halfH) continue;
            float r = l[2] * 1.15f;
            batch.setColor(warm);
            batch.draw(lightTexture, lx - r, ly - r, r * 2, r * 2);
        }
        if (playerLight > 0) {
            float r = playerLight * 1.2f;
            batch.setColor(1f, 0.92f, 0.75f, 0.9f);
            batch.draw(lightTexture, playerX - r, playerY - r, r * 2, r * 2);
        }
        batch.end();
        fbo.end();

        screenMatrix.setToOrtho2D(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        batch.setProjectionMatrix(screenMatrix);
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        batch.setColor(Color.WHITE);
        batch.begin();
        batch.draw(fbo.getColorBufferTexture(), 0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(),
                0, 0, 1, 1);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void dispose() {
        if (fbo != null) fbo.dispose();
    }
}
