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
    /** Pietro wyzej (i dach) przesuniete o pole w gore i w lewo, jak w Tibii - lezy na wierzchu murow. */
    private static final float UP = 1f;

    private final WorldMap world;
    private final TileArt art;
    private final Color tmp = new Color();
    /** Drzewa i palmy rysowane wieksze niz kafelek (wystaja ponad sasiednie pola). */
    private final boolean[] tall;
    /** Wiatrak: grafika 64x64 rysowana na 2x2 pola nad swoim polem (jak duze obiekty w Tibii). */
    private final int windmillId;
    private static final float HUGE = 2.2f;
    /** Ozdoba na wierzchu skaly (glaz, zaspa) dla scian skalnych; 0 = brak. */
    private final int[] rockDetail;
    /** Drzwi lacza mury (mur obok drzwi nie konczy sie slupkiem). */
    private final java.util.Set<Integer> doors;
    /** Podlogi wnetrz: mur budynku obok nich dostaje okna. */
    private final boolean[] indoorGround;

    public WorldRenderer(WorldMap world, TileArt art) {
        this.world = world;
        this.art = art;
        int max = 0;
        for (pl.fawizja.pixel.world.TileType t : world.tiles().all()) max = Math.max(max, t.id());
        tall = new boolean[max + 1];
        java.util.Set<Integer> doors = new java.util.HashSet<>();
        for (pl.fawizja.pixel.world.TileType t : world.tiles().all()) if (t.key().startsWith("door")) doors.add(t.id());
        this.doors = doors;
        indoorGround = new boolean[max + 1];
        for (pl.fawizja.pixel.world.TileType t : world.tiles().all()) {
            indoorGround[t.id()] = java.util.Set.of("wood_floor", "stone_floor", "sandstone_floor", "frozen_floor",
                    "marble_floor", "brick_paving").contains(t.key());
        }
        for (pl.fawizja.pixel.world.TileType t : world.tiles().all()) {
            tall[t.id()] = java.util.Set.of("tree", "pine", "palm", "dead_tree", "ship_mast").contains(t.key());
        }
        pl.fawizja.pixel.world.TileType mill = world.tiles().byKeyOrNull("windmill");
        windmillId = mill == null ? -1 : mill.id();
        // skaly gor i jaskin: na czesci pol glaz albo zaspa, zeby lita skala nie byla plaska plyta
        rockDetail = new int[max + 1];
        java.util.Map<String, String> detail = java.util.Map.of("mountain", "rock", "snow_mountain", "snow_rock",
                "sand_cliff", "sandstone_rock", "cave_wall", "rubble");
        for (java.util.Map.Entry<String, String> e : detail.entrySet()) {
            pl.fawizja.pixel.world.TileType wall = world.tiles().byKeyOrNull(e.getKey());
            pl.fawizja.pixel.world.TileType rock = world.tiles().byKeyOrNull(e.getValue());
            if (wall != null && rock != null) rockDetail[wall.id()] = rock.id();
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
        // podloze, obrzeza i dekoracje nie wychodza poza swoje pole, wiec rysujemy je trzema przebiegami:
        // kazdy z jednej tekstury, zamiast przelaczac atlasy na kazdym polu (etap 18: mniej wywolan rysowania)
        for (int y = y0; y <= y1; y++) {
            float wy = worldY(y);
            for (int x = x0; x <= x1; x++) drawGround(batch, f, f.get(Layer.GROUND, x, y), x, y, wy);
        }
        for (int y = y0; y <= y1; y++) {
            float wy = worldY(y);
            for (int x = x0; x <= x1; x++) drawBorders(batch, f, f.get(Layer.GROUND, x, y), x, y, wy);
        }
        for (int y = y0; y <= y1; y++) {
            float wy = worldY(y);
            for (int x = x0; x <= x1; x++) draw(batch, f.get(Layer.DECORATION, x, y), x, y, x, wy);
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
                drawObject(batch, f.get(Layer.OBJECT, x, y), x, y, x, wy);
                drawWall(batch, f, x, y, x, wy);
            }
            if (rows != null) rows.drawRow(batch, y);
        }
    }

    private static final int[] SIDE_DX = {0, 1, 0, -1, 1, 1, -1, -1};
    private static final int[] SIDE_DY = {-1, 0, 1, 0, -1, 1, 1, -1};

    /**
     * Obrzeza jak w Tibii: podloze sasiada o wyzszym priorytecie nachodzi na to pole poszarpana krawedzia.
     * Kazde podloze sasiadow osobno, od najnizszego priorytetu. Dwie stykajace sie krawedzie tego samego podloza
     * (np. polnoc i zachod) daja duzy rog, jesli podloze ma kawalki z paczki; inaczej dwie krawedzie.
     * Rog po skosie tylko wtedy, gdy obie sasiednie krawedzie maja inne podloze (inaczej juz go przykrywaja).
     */
    private void drawBorders(SpriteBatch batch, FloorMap f, int g, int x, int y, float wy) {
        GroundBorders b = art.borders();
        if (b == null || g == 0) return;
        int own = b.priority(g);
        if (own < 0) return;
        int[] side = sideTiles;
        int count = 0;
        for (int k = 0; k < 8; k++) {
            int nx = x + SIDE_DX[k], ny = y + SIDE_DY[k];
            side[k] = f.inBounds(nx, ny) ? f.get(Layer.GROUND, nx, ny) : 0;
            int n = side[k];
            if (n == 0 || n == g || b.priority(n) <= own || art.autotile(n) != null) continue;
            boolean seen = false;
            for (int i = 0; i < count; i++) seen |= borderGrounds[i] == n;
            if (seen) continue;
            int at = count++;
            while (at > 0 && b.priority(borderGrounds[at - 1]) > b.priority(n)) {
                borderGrounds[at] = borderGrounds[at - 1];
                at--;
            }
            borderGrounds[at] = n;
        }
        for (int i = 0; i < count; i++) {
            int n = borderGrounds[i];
            boolean north = side[GroundBorders.N] == n, east = side[GroundBorders.E] == n;
            boolean south = side[GroundBorders.S] == n, west = side[GroundBorders.W] == n;
            boolean edgeN = north, edgeE = east, edgeS = south, edgeW = west;
            if (b.piece(n, GroundBorders.IN_NW) != null) {
                if (north && west) edgeN = edgeW = !draw(batch, b.piece(n, GroundBorders.IN_NW), x, wy);
                if (north && east) edgeN = edgeE = !draw(batch, b.piece(n, GroundBorders.IN_NE), x, wy);
                if (south && west) edgeS = edgeW = !draw(batch, b.piece(n, GroundBorders.IN_SW), x, wy);
                if (south && east) edgeS = edgeE = !draw(batch, b.piece(n, GroundBorders.IN_SE), x, wy);
            }
            if (edgeN) draw(batch, b.piece(n, GroundBorders.N), x, wy);
            if (edgeE) draw(batch, b.piece(n, GroundBorders.E), x, wy);
            if (edgeS) draw(batch, b.piece(n, GroundBorders.S), x, wy);
            if (edgeW) draw(batch, b.piece(n, GroundBorders.W), x, wy);
            if (side[GroundBorders.NE] == n && !north && !east) draw(batch, b.piece(n, GroundBorders.NE), x, wy);
            if (side[GroundBorders.SE] == n && !south && !east) draw(batch, b.piece(n, GroundBorders.SE), x, wy);
            if (side[GroundBorders.SW] == n && !south && !west) draw(batch, b.piece(n, GroundBorders.SW), x, wy);
            if (side[GroundBorders.NW] == n && !north && !west) draw(batch, b.piece(n, GroundBorders.NW), x, wy);
        }
    }

    /**
     * Podloze z autokafelkiem (paczka WsT): pole na granicy z nizszym podlozem jest skladane z czterech cwiartek
     * z poszarpana krawedzia, a pod nie idzie pelne pole tego nizszego sasiada. Sasiad o rownym albo wyzszym
     * priorytecie, bez obrzezy (posadzki, mosty) albo poza mapa laczy sie bez krawedzi.
     */
    private void drawGround(SpriteBatch batch, FloorMap f, int g, int x, int y, float wy) {
        TextureRegion[] auto = art.autotile(g);
        GroundBorders b = art.borders();
        int own = b == null ? -1 : b.priority(g);
        if (auto == null || own < 0) {
            draw(batch, g, x, y, x, wy);
            return;
        }
        int open = 0, under = 0;
        for (int k = 0; k < 8; k++) {
            int nx = x + SIDE_DX[k], ny = y + SIDE_DY[k];
            int n = f.inBounds(nx, ny) ? f.get(Layer.GROUND, nx, ny) : 0;
            int p = b.priority(n);
            if (n == 0 || n == g || p < 0 || p >= own) continue;
            open |= 1 << k;
            // pod krawedz: nizsze podloze z boku (przed skosem), z nich to o najwyzszym priorytecie
            if (under == 0 || (k < 4 && (open & 0xf) == 1 << k) || (k < 4 || (open & 0xf) == 0) && p > b.priority(under)) under = n;
        }
        if (open == 0) {
            draw(batch, g, x, y, x, wy);
            return;
        }
        draw(batch, under, x, y, x, wy);
        boolean n = (open & 1) != 0, e = (open & 2) != 0, s = (open & 4) != 0, w = (open & 8) != 0;
        quarter(batch, auto, 0, w, n, (open & (1 << GroundBorders.NW)) != 0, x, wy + 0.5f);
        quarter(batch, auto, 1, e, n, (open & (1 << GroundBorders.NE)) != 0, x + 0.5f, wy + 0.5f);
        quarter(batch, auto, 2, w, s, (open & (1 << GroundBorders.SW)) != 0, x, wy);
        quarter(batch, auto, 3, e, s, (open & (1 << GroundBorders.SE)) != 0, x + 0.5f, wy);
    }

    /** Cwiartka autokafelka: side/vert = odslonieta krawedz z boku / od gory albo dolu, diag = odsloniety skos. */
    private static void quarter(SpriteBatch batch, TextureRegion[] auto, int row, boolean side, boolean vert,
                                boolean diag, float x, float y) {
        int kind = side && vert ? 0 : vert ? 1 : side ? 2 : diag ? 3 : 4;
        batch.draw(auto[row * 5 + kind], x, y, 0.5f, 0.5f);
    }

    private static boolean draw(SpriteBatch batch, TextureRegion r, int x, float wy) {
        if (r == null) return false;
        batch.draw(r, x, wy, 1f, 1f);
        return true;
    }

    private final int[] borderGrounds = new int[8];
    private final int[] sideTiles = new int[8];

    private void drawObject(SpriteBatch batch, int id, int mx, int my, float x, float wy) {
        if (id == 0) return;
        TextureRegion r = art.tile(id, mx, my);
        if (r == null) return;
        if (id == windmillId) batch.draw(r, x - (HUGE - 1f) / 2f, wy, HUGE, HUGE);
        else if (id < tall.length && tall[id]) batch.draw(r, x - (TALL - 1f) / 2f, wy, TALL, TALL);
        else batch.draw(r, x, wy, 1f, 1f);
    }

    private void drawWall(SpriteBatch batch, FloorMap f, int x, int y, float sx, float wy) {
        int id = f.get(Layer.WALL, x, y);
        if (id == 0) return;
        if (art.rockShape(id, 0) != null && art.wallPiece(id, TileArt.WALL_V) != null) {
            drawMountain(batch, f, id, x, y, sx, wy);
            return;
        }
        int shape = wallShape(f, x, y);
        if (hasWindow(f, id, x, y, shape)) shape = shape == TileArt.WALL_V ? TileArt.WALL_V_WINDOW : TileArt.WALL_H_WINDOW;
        TextureRegion piece = art.wallPiece(id, shape, x, y);
        if (piece != null) {
            // mur jak w Tibii: sciana na prawej/dolnej krawedzi pola, wysokosc w gore i w lewo (2x2 pola)
            batch.draw(piece, sx - 1, wy, 2f, 2f);
            return;
        }
        TextureRegion r = art.tile(id);
        TextureRegion shaped = art.rockShape(id, rockMask(f, x, y));
        if (shaped != null) r = shaped;
        if (r == null) return;
        if (f.get(Layer.WALL, x, y + 1) == 0) {
            tmp.set(batch.getColor());
            batch.setColor(tmp.r * 0.62f, tmp.g * 0.62f, tmp.b * 0.62f, tmp.a);
            // zaokraglona skala: zbocze to przyciemniona kopia ksztaltu przesunieta w dol, wiec ma te same luki
            if (shaped != null) batch.draw(r, sx, wy, 1f, 1f);
            else batch.draw(r, sx, wy, 1f, WALL_RAISE);
            batch.setColor(tmp);
        }
        batch.draw(r, sx, wy + WALL_RAISE, 1f, 1f);
        int d = id < rockDetail.length ? rockDetail[id] : 0;
        if (d != 0) {
            int h = (x * 73856093) ^ (y * 19349663);
            if ((h & 7) < 2) {
                TextureRegion rock = art.tile(d);
                float off = ((h >>> 3) & 3) * 0.08f;
                if (rock != null) batch.draw(rock, sx + off, wy + WALL_RAISE + 0.15f - off, 0.8f, 0.8f);
            }
        }
    }

    /**
     * Gora albo sciana jaskini jak w Tibii: plaski wierzch skaly (zaokraglony tam, gdzie konczy sie skala),
     * a na krawedzi od poludnia i wschodu odcinek zbocza z paczki (2x2 pola, prawy dolny = pole skaly):
     * poludnie - poziomy, wschod - pionowy, rog poludniowo-wschodni - naroznik, samotna skala - slupek.
     */
    private void drawMountain(SpriteBatch batch, FloorMap f, int id, int x, int y, float sx, float wy) {
        int mask = rockMask(f, x, y);
        batch.draw(art.rockShape(id, mask), sx, wy, 1f, 1f);
        boolean south = (mask & TileArt.ROCK_S) != 0, east = (mask & TileArt.ROCK_E) != 0;
        int shape;
        if (south && east) shape = (mask & (TileArt.ROCK_N | TileArt.ROCK_W)) == (TileArt.ROCK_N | TileArt.ROCK_W)
                ? TileArt.WALL_POLE : TileArt.WALL_CORNER;
        else if (south) shape = TileArt.WALL_H;
        else if (east) shape = TileArt.WALL_V;
        else return;
        TextureRegion piece = art.wallPiece(id, shape, x, y);
        if (piece != null) batch.draw(piece, sx - 1, wy, 2f, 2f);
    }

    /** Boki skaly, przy ktorych nie ma innej skaly (TileArt.ROCK_*): tam skala jest zaokraglona. */
    int rockMask(FloorMap f, int x, int y) {
        int m = 0, id = f.get(Layer.WALL, x, y);
        if (!rock(f, id, x, y - 1)) m |= TileArt.ROCK_N;
        if (!rock(f, id, x + 1, y)) m |= TileArt.ROCK_E;
        if (!rock(f, id, x, y + 1)) m |= TileArt.ROCK_S;
        if (!rock(f, id, x - 1, y)) m |= TileArt.ROCK_W;
        return m;
    }

    /** Ta sama skala obok (gora przy osniezonej gorze ma wlasne zbocze). */
    private boolean rock(FloorMap f, int id, int x, int y) {
        if (!f.inBounds(x, y)) return true;
        return f.get(Layer.WALL, x, y) == id;
    }

    /**
     * Ksztalt muru z sasiadow (drzwi lacza mury): mur na polnoc i zachod - naroznik, na polnoc albo poludnie - pionowy,
     * na zachod - poziomy, inaczej slupek (mury odchodzace na wschod i poludnie zaczynaja sie w jego rogu).
     */
    int wallShape(FloorMap f, int x, int y) {
        boolean n = joins(f, x, y - 1), s = joins(f, x, y + 1), w = joins(f, x - 1, y);
        if (n && w) return TileArt.WALL_CORNER;
        if (n || (s && !joins(f, x + 1, y))) return TileArt.WALL_V;
        if (w) return TileArt.WALL_H;
        return TileArt.WALL_POLE;
    }

    /**
     * Okno w murze budynku: prosty odcinek (mur tego samego rodzaju po obu stronach wzdluz, bez drzwi obok),
     * co trzecie pole, tylko na powierzchni i wyzej i tylko gdy po jednej stronie jest podloga wnetrza.
     * Mury miejskie i jaskinie (bruk, trawa, skala po obu stronach) zostaja pelne.
     */
    boolean hasWindow(FloorMap f, int id, int x, int y, int shape) {
        if (f.z() < 0) return false;
        if (shape == TileArt.WALL_V) {
            if (Math.floorMod(y + x, 3) != 1) return false;
            if (!sameWall(f, id, x, y - 1) || !sameWall(f, id, x, y + 1)) return false;
            return indoor(f, x, y) || indoor(f, x + 1, y);
        }
        if (shape == TileArt.WALL_H) {
            if (Math.floorMod(x + y, 3) != 1) return false;
            if (!sameWall(f, id, x - 1, y) || !sameWall(f, id, x + 1, y)) return false;
            return indoor(f, x, y) || indoor(f, x, y + 1);
        }
        return false;
    }

    private boolean sameWall(FloorMap f, int id, int x, int y) {
        return f.inBounds(x, y) && f.get(Layer.WALL, x, y) == id;
    }

    private boolean indoor(FloorMap f, int x, int y) {
        if (!f.inBounds(x, y)) return false;
        int g = f.get(Layer.GROUND, x, y);
        return g > 0 && g < indoorGround.length && indoorGround[g];
    }

    private boolean joins(FloorMap f, int x, int y) {
        if (!f.inBounds(x, y)) return false;
        return f.get(Layer.WALL, x, y) != 0 || doors.contains(f.get(Layer.OBJECT, x, y));
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
        int x1 = Math.min(world.width() - 1, (int) Math.ceil(cam.position.x + halfW) + 2);
        int wy0 = (int) Math.floor(cam.position.y - halfH) - 2;
        int wy1 = (int) Math.ceil(cam.position.y + halfH) + 2;
        int y0 = Math.max(0, world.height() - 1 - wy1);
        int y1 = Math.min(world.height() - 1, world.height() - 1 - wy0);
        for (int z = player.z(); z <= 5; z++) {
            FloorMap f = world.floor(z);
            if (f == null) continue;
            if (z > player.z()) {
                for (int y = y0; y <= y1; y++) {
                    float wy = worldY(y) + UP;
                    for (int x = x0; x <= x1; x++) {
                        draw(batch, f.get(Layer.GROUND, x, y), x, y, x - UP, wy);
                        draw(batch, f.get(Layer.DECORATION, x, y), x, y, x - UP, wy);
                    }
                }
                for (int y = y0; y <= y1; y++) {
                    float wy = worldY(y) + UP;
                    for (int x = x0; x <= x1; x++) {
                        drawObject(batch, f.get(Layer.OBJECT, x, y), x, y, x - UP, wy);
                        drawWall(batch, f, x, y, x - UP, wy);
                    }
                }
            }
            FloorMap upper = world.floor(z + 1);
            for (int y = y0; y <= y1; y++) {
                float wy = worldY(y) + UP;
                for (int x = x0; x <= x1; x++) {
                    byte kind = roofs.at(x, y, z);
                    if (kind == Roofs.NONE) continue;
                    if (upper != null && upper.get(Layer.GROUND, x, y) != 0) continue;
                    batch.draw(art.roof(kind), x - UP, wy, 1f, 1f);
                }
            }
        }
    }

    /** Pole mapy (mx, my) rysowane w (x, y) swiata, z wariantem grafiki wg pozycji. */
    private void draw(SpriteBatch batch, int id, int mx, int my, float x, float y) {
        if (id == 0) return;
        TextureRegion r = art.tile(id, mx, my);
        if (r != null) batch.draw(r, x, y, 1f, 1f);
    }
}
