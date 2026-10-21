package pl.fawizja.pixel.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.List;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Rysuje widoczny fragment swiata. Jednostka swiata = 1 kafelek; wiersz y mapy
 * jest odwracany (mapa ma y w dol, libGDX w gore). Rysowane sa tylko kafelki
 * w kadrze kamery, wiec koszt nie zalezy od rozmiaru mapy.
 */
public final class WorldRenderer {

    /** Callback do rysowania postaci miedzy wierszami obiektow (prawidlowe zaslanianie). */
    public interface RowDrawer {
        void drawRow(SpriteBatch batch, int mapRow);
    }

    /** Sciany sa "wysokie" jak w Tibii: wierzch podniesiony o tyle kafelka, pod nim ciemniejsze lico. */
    public static final float WALL_RAISE = 0.4f;
    private static final float TALL = 1.4f;

    private final WorldMap world;
    private final TileArt art;
    private final Color tmp = new Color();
    /** Drzewa i palmy rysowane wieksze niz kafelek (wystaja ponad sasiednie pola). */
    private final boolean[] tall;

    public WorldRenderer(WorldMap world, TileArt art) {
        this.world = world;
        this.art = art;
        int max = 0;
        for (pl.fawizja.pixel.world.TileType t : world.tiles().all()) max = Math.max(max, t.id());
        tall = new boolean[max + 1];
        for (pl.fawizja.pixel.world.TileType t : world.tiles().all()) {
            tall[t.id()] = java.util.Set.of("tree", "pine", "palm", "dead_tree").contains(t.key());
        }
    }

    public float worldY(int mapY) {
        return world.height() - 1 - mapY;
    }

    public float worldY(float mapY) {
        return world.height() - 1 - mapY;
    }

    public void render(SpriteBatch batch, OrthographicCamera cam, int currentFloor, GroundItems ground, RowDrawer rows) {
        float halfW = cam.viewportWidth * cam.zoom / 2f;
        float halfH = cam.viewportHeight * cam.zoom / 2f;
        int x0 = Math.max(0, (int) Math.floor(cam.position.x - halfW) - 1);
        int x1 = Math.min(world.width() - 1, (int) Math.ceil(cam.position.x + halfW) + 1);
        int wy0 = (int) Math.floor(cam.position.y - halfH) - 1;
        int wy1 = (int) Math.ceil(cam.position.y + halfH) + 1;
        int y0 = Math.max(0, world.height() - 1 - wy1);
        int y1 = Math.min(world.height() - 1, world.height() - 1 - wy0);

        // pietra ponizej (widok z wiezy) przyciemnione
        if (currentFloor > 0) {
            for (int z = 0; z < currentFloor; z++) {
                FloorMap f = world.floor(z);
                if (f == null) continue;
                float shade = 0.45f + 0.1f * z;
                batch.setColor(shade, shade, shade, 1f);
                drawFloor(batch, f, x0, x1, y0, y1, null, null, z);
            }
        }
        batch.setColor(Color.WHITE);
        FloorMap f = world.floor(currentFloor);
        if (f != null) drawFloor(batch, f, x0, x1, y0, y1, ground, rows, currentFloor);
        batch.setColor(Color.WHITE);
    }

    private void drawFloor(SpriteBatch batch, FloorMap f, int x0, int x1, int y0, int y1,
                           GroundItems ground, RowDrawer rows, int z) {
        for (int y = y0; y <= y1; y++) {
            float wy = worldY(y);
            for (int x = x0; x <= x1; x++) {
                draw(batch, f.get(Layer.GROUND, x, y), x, wy);
                draw(batch, f.get(Layer.DECORATION, x, y), x, wy);
            }
        }
        for (int y = y0; y <= y1; y++) {
            float wy = worldY(y);
            if (ground != null) {
                for (int x = x0; x <= x1; x++) {
                    List<ItemStack> items = ground.at(new Position(x, y, z));
                    for (int i = 0; i < items.size(); i++) {
                        TextureRegion r = art.item(items.get(i).itemId());
                        if (r != null) batch.draw(r, x + 0.15f + i * 0.08f, wy + 0.1f, 0.7f, 0.7f);
                    }
                }
            }
            for (int x = x0; x <= x1; x++) {
                drawObject(batch, f.get(Layer.OBJECT, x, y), x, wy);
                drawWall(batch, f, x, y, wy);
            }
            if (rows != null) rows.drawRow(batch, y);
        }
    }

    private void drawObject(SpriteBatch batch, int id, float x, float wy) {
        if (id == 0) return;
        TextureRegion r = art.tile(id);
        if (r == null) return;
        if (id < tall.length && tall[id]) batch.draw(r, x - (TALL - 1f) / 2f, wy, TALL, TALL);
        else batch.draw(r, x, wy, 1f, 1f);
    }

    private void drawWall(SpriteBatch batch, FloorMap f, int x, int y, float wy) {
        int id = f.get(Layer.WALL, x, y);
        if (id == 0) return;
        TextureRegion r = art.tile(id);
        if (r == null) return;
        if (f.get(Layer.WALL, x, y + 1) == 0) {
            tmp.set(batch.getColor());
            batch.setColor(tmp.r * 0.62f, tmp.g * 0.62f, tmp.b * 0.62f, tmp.a);
            batch.draw(r, x, wy, 1f, WALL_RAISE);
            batch.setColor(tmp);
        }
        batch.draw(r, x, wy + WALL_RAISE, 1f, 1f);
    }

    /**
     * Wyzsze pietra i dachy nad biezacym pietrem (po postaciach). Gdy gracz stoi pod dachem albo pod wyzszym
     * pietrem, nic nie jest rysowane - widac wnetrze budynku.
     */
    public void renderAbove(SpriteBatch batch, OrthographicCamera cam, Position player, Roofs roofs) {
        if (roofs == null || roofs.covered(player)) return;
        float halfW = cam.viewportWidth * cam.zoom / 2f;
        float halfH = cam.viewportHeight * cam.zoom / 2f;
        int x0 = Math.max(0, (int) Math.floor(cam.position.x - halfW) - 1);
        int x1 = Math.min(world.width() - 1, (int) Math.ceil(cam.position.x + halfW) + 1);
        int wy0 = (int) Math.floor(cam.position.y - halfH) - 1;
        int wy1 = (int) Math.ceil(cam.position.y + halfH) + 2;
        int y0 = Math.max(0, world.height() - 1 - wy1);
        int y1 = Math.min(world.height() - 1, world.height() - 1 - wy0);
        for (int z = player.z(); z <= 5; z++) {
            FloorMap f = world.floor(z);
            if (f == null) continue;
            if (z > player.z()) {
                for (int y = y0; y <= y1; y++) {
                    float wy = worldY(y) + WALL_RAISE;
                    for (int x = x0; x <= x1; x++) {
                        draw(batch, f.get(Layer.GROUND, x, y), x, wy);
                        draw(batch, f.get(Layer.DECORATION, x, y), x, wy);
                    }
                }
                for (int y = y0; y <= y1; y++) {
                    float wy = worldY(y) + WALL_RAISE;
                    for (int x = x0; x <= x1; x++) {
                        drawObject(batch, f.get(Layer.OBJECT, x, y), x, wy);
                        drawWall(batch, f, x, y, wy);
                    }
                }
            }
            FloorMap upper = world.floor(z + 1);
            for (int y = y0; y <= y1; y++) {
                float wy = worldY(y) + WALL_RAISE;
                for (int x = x0; x <= x1; x++) {
                    byte kind = roofs.at(x, y, z);
                    if (kind == Roofs.NONE) continue;
                    if (upper != null && upper.get(Layer.GROUND, x, y) != 0) continue;
                    batch.draw(art.roof(kind == Roofs.SLATE), x, wy, 1f, 1f);
                }
            }
        }
    }

    private void draw(SpriteBatch batch, int id, float x, float y) {
        if (id == 0) return;
        TextureRegion r = art.tile(id);
        if (r != null) batch.draw(r, x, y, 1f, 1f);
    }
}
