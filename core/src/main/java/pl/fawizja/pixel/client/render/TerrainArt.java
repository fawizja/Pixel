package pl.fawizja.pixel.client.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.TileRegistry;
import pl.fawizja.pixel.world.TileType;

/**
 * Proceduralne wykonczenie terenu (2026-10-29, Darek: "ladniej mosty, kladki, miejsca gdzie mapa jest wylaczona,
 * zaokraglone rogi; skaly nie jako sciany, tylko rozbudowane 3D, tarasy"). Wszystko w jednej teksturze, budowanej raz
 * przy starcie z kafelkow 32 px gry (bez obcych grafik):
 * <ul>
 * <li>tarasy gor i jaskin: wierzch skaly (16 ksztaltow wg nizszych sasiadow, zaokraglone rogi) z licem pod spodem
 *     (warstwy skalne, cien u stopy) - rysowane wyzej o {@link #STEP} pola na kazdy poziom;</li>
 * <li>rozpadlina: skalny brzeg z zaokraglonymi rogami, sciana od polnocy i ciemna glebia zamiast plaskiego pola;</li>
 * <li>mgla przy pustce (miejsca bez mapy pod ziemia) i przy krawedzi swiata - miekkie przejscie w ciemnosc;</li>
 * <li>mosty i kladki: deski w poprzek drogi, poreczy ze slupkami, belki na koncach, zjazd na lad, cien i pale w wodzie.</li>
 * </ul>
 */
public final class TerrainArt implements Disposable {

    public static final int N = 0, E = 1, S = 2, W = 3;
    /** Bity masek jak {@link TileArt#ROCK_N}: odsloniety bok. */
    static final int MN = 1, ME = 2, MS = 4, MW = 8;
    static final int PX = 32;
    /** Wysokosc lica tarasu w pikselach i w polach. */
    static final int FACE = 16;
    public static final float STEP = FACE / (float) PX;
    private static final int TH = PX + FACE, TW = PX + FACE;
    /** Poczatek pozostalych kawalkow (pod tarasami). */
    private static final int BY = 512;
    private static final int ATLAS_W = 1024, ATLAS_H = 1024;
    /** Liczba odmian desek (wybierana wg kolumny albo wiersza mostu, wiec deski ciagna sie bez szwow). */
    public static final int PLANK_VARIANTS = 4;

    private Texture texture;
    private final TextureRegion[][] terrace;
    private final TextureRegion[] terraceFlat;
    private final TextureRegion[] chasm = new TextureRegion[16];
    private final TextureRegion[] chasmCorner = new TextureRegion[4];
    private final TextureRegion[] fog = new TextureRegion[16];
    private final TextureRegion[] fogCorner = new TextureRegion[4];
    private final TextureRegion[] edge = new TextureRegion[4];
    private final TextureRegion solid;
    /** [styl][0 = deski w poprzek drogi poziomej, 1 = pionowej][odmiana]. */
    private final TextureRegion[][][] planks = new TextureRegion[2][2][PLANK_VARIANTS];
    private final TextureRegion[][] rail = new TextureRegion[2][4];
    private final TextureRegion[][] endBeam = new TextureRegion[2][4];
    private final TextureRegion[][] ramp = new TextureRegion[2][4];
    private final TextureRegion[] shadow = new TextureRegion[4];
    private final TextureRegion[] piling = new TextureRegion[4];
    private final TextureRegion[] ao = new TextureRegion[4];
    private final int[] bridgeStyle;

    public TerrainArt(TileRegistry registry, IntFunction<Pixmap> tile32) {
        int max = registry.maxId();
        terrace = new TextureRegion[max + 1][];
        terraceFlat = new TextureRegion[max + 1];
        bridgeStyle = new int[max + 1];
        java.util.Arrays.fill(bridgeStyle, -1);
        List<TileType> rocks = new ArrayList<>();
        for (TileType t : registry.all()) {
            if (t.layer() == Layer.WALL && "rockwall".equals(t.pattern())) rocks.add(t);
        }
        TileType bridge = registry.byKeyOrNull("bridge"), rope = registry.byKeyOrNull("rope_bridge");
        if (bridge != null) bridgeStyle[bridge.id()] = 0;
        if (rope != null) bridgeStyle[rope.id()] = 1;
        // tarasy: wiersz na skale (16 ksztaltow po 32x44); reszta od x = 512
        int rows = Math.min(rocks.size(), BY / TH);
        Pixmap pm = new Pixmap(ATLAS_W, ATLAS_H, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        int[][] slots = new int[rows][];
        for (int i = 0; i < rows; i++) {
            Pixmap src = tile32.apply(rocks.get(i).id());
            if (src == null) continue;
            int[] tex = pixels(src);
            src.dispose();
            for (int mask = 0; mask < 16; mask++) paintTerrace(pm, mask * TW, i * TH, mask, tex, rocks.get(i).id());
            slots[i] = new int[] {i * TH};
        }
        int bx = 0;
        int[] rim = texOf(tile32, registry, "cave_wall", 0xff5a5048);
        for (int mask = 0; mask < 16; mask++) paintChasm(pm, bx + mask * PX, BY, mask, rim);
        for (int k = 0; k < 4; k++) paintChasmCorner(pm, bx + k * PX, BY + PX * 2, k, rim);
        for (int mask = 0; mask < 16; mask++) paintFog(pm, bx + mask * PX, BY + PX, mask);
        for (int k = 0; k < 4; k++) paintFogCorner(pm, bx + (4 + k) * PX, BY + PX * 2, k);
        for (int s = 0; s < 4; s++) paintEdge(pm, bx + (8 + s) * PX, BY + PX * 2, s);
        fillRect(pm, bx + 12 * PX, BY + PX * 2, PX, PX, 0xffffffff);
        for (int s = 0; s < 4; s++) {
            paintShadow(pm, bx + s * PX, BY + PX * 3, s);
            paintPiling(pm, bx + (4 + s) * PX, BY + PX * 3, s);
            paintAo(pm, bx + (8 + s) * PX, BY + PX * 3, s);
        }
        int[][] wood = {
                avg(texOf(tile32, registry, "bridge", 0xff6a4428)),
                avg(texOf(tile32, registry, "rope_bridge", 0xff8a6a40))};
        for (int st = 0; st < 2; st++) {
            int y0 = BY + PX * 4 + st * PX * 3;
            Random rnd = new Random(7700 + st);
            for (int v = 0; v < PLANK_VARIANTS; v++) {
                paintPlanks(pm, bx + v * PX, y0, st, wood[st], false, rnd);
                paintPlanks(pm, bx + (4 + v) * PX, y0, st, wood[st], true, rnd);
            }
            for (int s = 0; s < 4; s++) {
                paintRail(pm, bx + (8 + s) * PX, y0, st, s, wood[st]);
                paintEnd(pm, bx + s * PX, y0 + PX, st, s, wood[st]);
                paintRamp(pm, bx + (4 + s) * PX, y0 + PX, st, s, wood[st]);
            }
        }
        texture = new Texture(pm);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        for (int i = 0; i < rows; i++) {
            if (slots[i] == null) continue;
            TextureRegion[] v = new TextureRegion[16];
            for (int mask = 0; mask < 16; mask++) v[mask] = new TextureRegion(texture, mask * TW, i * TH, TW, TH);
            terrace[rocks.get(i).id()] = v;
            terraceFlat[rocks.get(i).id()] = new TextureRegion(texture, 0, i * TH, PX, PX);
        }
        for (int mask = 0; mask < 16; mask++) {
            chasm[mask] = new TextureRegion(texture, bx + mask * PX, BY, PX, PX);
            fog[mask] = new TextureRegion(texture, bx + mask * PX, BY + PX, PX, PX);
        }
        for (int k = 0; k < 4; k++) {
            chasmCorner[k] = new TextureRegion(texture, bx + k * PX, BY + PX * 2, PX, PX);
            fogCorner[k] = new TextureRegion(texture, bx + (4 + k) * PX, BY + PX * 2, PX, PX);
            edge[k] = new TextureRegion(texture, bx + (8 + k) * PX, BY + PX * 2, PX, PX);
            shadow[k] = new TextureRegion(texture, bx + k * PX, BY + PX * 3, PX, PX);
            piling[k] = new TextureRegion(texture, bx + (4 + k) * PX, BY + PX * 3, PX, PX);
            ao[k] = new TextureRegion(texture, bx + (8 + k) * PX, BY + PX * 3, PX, PX);
        }
        solid = new TextureRegion(texture, bx + 12 * PX + 8, BY + PX * 2 + 8, 16, 16);
        for (int st = 0; st < 2; st++) {
            int y0 = BY + PX * 4 + st * PX * 3;
            for (int v = 0; v < PLANK_VARIANTS; v++) {
                planks[st][0][v] = new TextureRegion(texture, bx + v * PX, y0, PX, PX);
                planks[st][1][v] = new TextureRegion(texture, bx + (4 + v) * PX, y0, PX, PX);
            }
            for (int s = 0; s < 4; s++) {
                rail[st][s] = new TextureRegion(texture, bx + (8 + s) * PX, y0, PX, PX);
                endBeam[st][s] = new TextureRegion(texture, bx + s * PX, y0 + PX, PX, PX);
                ramp[st][s] = new TextureRegion(texture, bx + (4 + s) * PX, y0 + PX, PX, PX);
            }
        }
    }

    // ------------------------------------------------------------------ dostep

    /** Taras skaly: wierzch (gorne 32 px) i lico pod nim (FACE px), mask = boki z nizszym sasiadem; null = brak. */
    public TextureRegion terrace(int rockId, int mask) {
        TextureRegion[] t = rockId > 0 && rockId < terrace.length ? terrace[rockId] : null;
        return t == null ? null : t[mask & 15];
    }

    /** Pelny wierzch skaly bez lica (podklad pod zaokraglone rogi wyzszego tarasu). */
    public TextureRegion terraceFlat(int rockId) {
        return rockId > 0 && rockId < terraceFlat.length ? terraceFlat[rockId] : null;
    }

    public TextureRegion chasm(int mask) { return chasm[mask & 15]; }

    /** Maly rog skalnego brzegu rozpadliny: k = 0 NE, 1 SE, 2 SW, 3 NW. */
    public TextureRegion chasmCorner(int k) { return chasmCorner[k]; }

    /** Mgla nad polem przy pustce (bity N/E/S/W jak skaly); alfa 1 przy pustce, zanika do srodka pola. */
    public TextureRegion fog(int mask) { return fog[mask & 15]; }

    /** Mgla z rogu po skosie: k = 0 NE, 1 SE, 2 SW, 3 NW. */
    public TextureRegion fogCorner(int k) { return fogCorner[k]; }

    /** Gradient alfy: pelny przy boku side, zero przy przeciwnym (krawedz swiata). */
    public TextureRegion edge(int side) { return edge[side]; }

    /** Bialy kwadrat do zabarwienia (pustka, mgla). */
    public TextureRegion solid() { return solid; }

    /** Styl mostu podloza: 0 = drewniany most/kladka, 1 = most wiszacy (linowy), -1 = nie most. */
    public int bridgeStyle(int groundId) {
        return groundId > 0 && groundId < bridgeStyle.length ? bridgeStyle[groundId] : -1;
    }

    public TextureRegion planks(int style, boolean vertical, int variant) {
        return planks[style][vertical ? 1 : 0][variant % PLANK_VARIANTS];
    }

    public TextureRegion rail(int style, int side) { return rail[style][side]; }

    public TextureRegion endBeam(int style, int side) { return endBeam[style][side]; }

    /** Zjazd z mostu na lad: rysowany na polu ladu, side = strona, po ktorej jest most. */
    public TextureRegion ramp(int style, int side) { return ramp[style][side]; }

    /** Cien mostu na wodzie: side = strona pola wody, po ktorej jest most. */
    public TextureRegion shadow(int side) { return shadow[side]; }

    /** Cien u stopy wyzszego tarasu na nizszym (side = strona, po ktorej jest wyzszy taras). */
    public TextureRegion ao(int side) { return ao[side]; }

    /** Pal pod mostem wystajacy z wody (side jak {@link #shadow}). */
    public TextureRegion piling(int side) { return piling[side]; }

    // ------------------------------------------------------------------ tarasy

    /**
     * Taras 48x48: wierzch 32x32 w lewym gornym rogu, pod nim i z prawej lico (wierzch wyciagniety o FACE px po skosie
     * w prawo w dol, jak wyzsze rzeczy w Tibii przesuniete w lewo w gore). Lico od poludnia jasniejsze niz od wschodu.
     */
    private static void paintTerrace(Pixmap pm, int ox, int oy, int mask, int[] tex, int seed) {
        boolean[] inside = new boolean[PX * PX];
        float[] dist = new float[PX * PX];
        for (int y = 0; y < PX; y++) {
            for (int x = 0; x < PX; x++) {
                float d = TileArt.rockInside(mask, x + 0.5f, y + 0.5f);
                dist[y * PX + x] = d;
                inside[y * PX + x] = d >= 0;
            }
        }
        Random crack = new Random(seed * 31L + 5);
        int[] cracks = new int[PX];
        for (int x = 0; x < PX; x++) cracks[x] = crack.nextInt(7) == 0 ? 1 : 0;
        for (int y = 0; y < TH; y++) {
            for (int x = 0; x < TW; x++) {
                int c = 0;
                if (x < PX && y < PX && inside[y * PX + x]) {
                    float d = dist[y * PX + x];
                    c = tex[y * PX + x];
                    boolean lit = ((mask & MN) != 0 && y < PX / 2) || ((mask & MW) != 0 && x < PX / 2);
                    if (d < 1.1f) c = shade(c, lit ? 0.7f : 0.5f);
                    else if (d < 2.4f) c = shade(c, lit ? 1.25f : 0.8f);
                    else if (d < 4.5f && lit) c = shade(c, 1.1f);
                } else {
                    // lico: pierwszy piksel wierzchu po skosie w gore w lewo w odleglosci do FACE
                    int t = -1;
                    for (int k = 1; k <= FACE; k++) {
                        int xx = x - k, yy = y - k;
                        if (xx < 0 || yy < 0) break;
                        if (xx < PX && yy < PX && inside[yy * PX + xx]) { t = k - 1; break; }
                    }
                    if (t >= 0) {
                        // lico poludniowe: wierzch tuz nad pikselem; za prawym brzegiem pola to dalszy ciag lica
                        // sasiada ze wschodu (ten sam poziom), chyba ze wschodni bok jest odsloniety
                        boolean south = x >= PX && (mask & ME) == 0;
                        for (int k = 1; k <= FACE + 1 && !south; k++) {
                            int yy = y - k;
                            if (yy < 0) break;
                            if (x < PX && yy < PX && inside[yy * PX + x]) south = k <= t + 2;
                        }
                        c = faceColor(tex, x % PX, t, cracks[x % PX] != 0, south);
                    }
                }
                pm.drawPixel(ox + x, oy + y, c);
            }
        }
    }

    /** Kolor lica skaly: jasna krawedz u gory, nierowne warstwy, ciemniejsza stopa; od wschodu w cieniu. */
    private static int faceColor(int[] tex, int x, int t, boolean crack, boolean south) {
        int src = tex[((t * 2 + 9) % PX) * PX + x];
        float f = t == 0 ? 1.02f : t == 1 ? 0.9f : 0.78f - 0.018f * t;
        int wob = (int) (1.5f + 1.5f * Math.sin(x * 0.55f) + Math.sin(x * 1.7f));   // nierowne warstwy
        int band = (t + wob) % 6;
        if (band == 5) f *= 0.78f;
        else if (band == 0 && t > 1) f *= 1.07f;
        if (crack && t > 2 && t < FACE - 2) f *= 0.72f;
        if (!south) f *= 0.7f;
        if (t >= FACE - 3) f *= 0.8f;
        if (t >= FACE - 1) f *= 0.8f;
        return shade(src, f);
    }

    // ------------------------------------------------------------------ rozpadlina

    private static void paintChasm(Pixmap pm, int ox, int oy, int mask, int[] rim) {
        for (int y = 0; y < PX; y++) {
            for (int x = 0; x < PX; x++) {
                float d = TileArt.rockInside(mask, x + 0.5f, y + 0.5f);
                int c;
                if (d < 0) {
                    c = shade(rim[y * PX + x], d > -1.6f ? 0.62f : 1.05f);
                } else if (d < 1.2f) {
                    c = 0x0c0a0cff;
                } else {
                    float fy = (mask & MN) != 0 ? northDepth(mask, x, y) : 99f;
                    if (fy < 14f) {
                        // sciana rozpadliny od polnocy: skala ciemniejaca w glab
                        float f = 0.78f - 0.05f * fy;
                        if (((int) fy) % 4 == 3) f *= 0.75f;
                        c = shade(rim[(((int) fy * 2 + 5) % PX) * PX + x], Math.max(0.08f, f));
                    } else {
                        float g = Math.min(1f, d / 8f);
                        int v = (int) (26 - 16 * g);
                        c = (v << 24) | ((v - 4 > 0 ? v - 4 : 0) << 16) | ((v + 2) << 8) | 0xff;
                    }
                }
                pm.drawPixel(ox + x, oy + y, c);
            }
        }
    }

    /** Glebokosc ponizej polnocnego brzegu (ten sam ksztalt brzegu co skala). */
    private static float northDepth(int mask, int x, int y) {
        float best = 99f;
        for (int k = 0; k <= y; k++) {
            if (TileArt.rockInside(mask, x + 0.5f, y - k + 0.5f) < 0) { best = k; break; }
        }
        return best;
    }

    private static void paintChasmCorner(Pixmap pm, int ox, int oy, int k, int[] rim) {
        float cx = (k == 0 || k == 1) ? PX : 0, cy = (k == 1 || k == 2) ? PX : 0;
        for (int y = 0; y < PX; y++) {
            for (int x = 0; x < PX; x++) {
                float r = (float) Math.hypot(x + 0.5f - cx, y + 0.5f - cy);
                int c = 0;
                if (r < 5f) c = shade(rim[y * PX + x], 1.05f);
                else if (r < 6.3f) c = shade(rim[y * PX + x], 0.55f);
                pm.drawPixel(ox + x, oy + y, c);
            }
        }
    }

    // ------------------------------------------------------------------ mgla i krawedz swiata

    private static final float FOG_W = 24f, FOG_R = 12f;

    private static void paintFog(Pixmap pm, int ox, int oy, int mask) {
        for (int y = 0; y < PX; y++) {
            for (int x = 0; x < PX; x++) {
                float fx = x + 0.5f, fy = y + 0.5f, d = Float.MAX_VALUE;
                float dn = fy, ds = PX - fy, dw = fx, de = PX - fx;
                if ((mask & MN) != 0) d = Math.min(d, dn);
                if ((mask & MS) != 0) d = Math.min(d, ds);
                if ((mask & MW) != 0) d = Math.min(d, dw);
                if ((mask & ME) != 0) d = Math.min(d, de);
                if ((mask & (MN | MW)) == (MN | MW)) d = roundCorner(d, dw, dn);
                if ((mask & (MN | ME)) == (MN | ME)) d = roundCorner(d, de, dn);
                if ((mask & (MS | MW)) == (MS | MW)) d = roundCorner(d, dw, ds);
                if ((mask & (MS | ME)) == (MS | ME)) d = roundCorner(d, de, ds);
                pm.drawPixel(ox + x, oy + y, fogPixel(mask == 0 ? Float.MAX_VALUE : d));
            }
        }
    }

    private static float roundCorner(float d, float a, float b) {
        if (a >= FOG_R || b >= FOG_R) return d;
        return Math.min(d, FOG_R - (float) Math.hypot(FOG_R - a, FOG_R - b));
    }

    private static int fogPixel(float d) {
        float a = d <= 0 ? 1f : Math.max(0f, 1f - d / FOG_W);
        a = a * a * (3 - 2 * a);
        return 0x05030600 | Math.round(a * 255);
    }

    private static void paintFogCorner(Pixmap pm, int ox, int oy, int k) {
        float cx = (k == 0 || k == 1) ? PX : 0, cy = (k == 1 || k == 2) ? PX : 0;
        for (int y = 0; y < PX; y++) {
            for (int x = 0; x < PX; x++) {
                float r = (float) Math.hypot(x + 0.5f - cx, y + 0.5f - cy);
                pm.drawPixel(ox + x, oy + y, fogPixel(r));
            }
        }
    }

    private static void paintEdge(Pixmap pm, int ox, int oy, int side) {
        for (int y = 0; y < PX; y++) {
            for (int x = 0; x < PX; x++) {
                float t = side == N ? y : side == S ? PX - 1 - y : side == W ? x : PX - 1 - x;
                float a = 1f - (t + 0.5f) / PX;
                a = a * a * (3 - 2 * a);
                pm.drawPixel(ox + x, oy + y, 0xffffff00 | Math.round(a * 255));
            }
        }
    }

    // ------------------------------------------------------------------ mosty

    /** Deski w poprzek drogi: dla drogi poziomej deski pionowe (co 8 px), dla pionowej - poziome. */
    private static void paintPlanks(Pixmap pm, int ox, int oy, int style, int[] wood, boolean vertical, Random rnd) {
        float[] board = new float[4];
        for (int b = 0; b < 4; b++) board[b] = 0.86f + rnd.nextFloat() * 0.24f;
        float[] grain = new float[PX];
        for (int i = 0; i < PX; i++) grain[i] = 0.92f + rnd.nextFloat() * 0.14f;
        int knotB = rnd.nextInt(4), knotA = 4 + rnd.nextInt(24);
        for (int y = 0; y < PX; y++) {
            for (int x = 0; x < PX; x++) {
                int across = vertical ? y : x, along = vertical ? x : y;
                int b = across / 8, in = across % 8;
                int c;
                if (in == 0) {
                    c = style == 1 ? 0 : rgb(wood, 0.38f);
                } else if (style == 1 && in == 7) {
                    c = rgb(wood, 0.55f);
                } else {
                    float f = board[b] * grain[across];
                    f *= 1f + 0.05f * (float) Math.sin(Math.PI * 2 * along / PX * 2 + b * 1.3f);
                    if (in == 1) f *= 1.12f;
                    if (in == 7) f *= 0.8f;
                    if (b == knotB && Math.abs(along - knotA) <= 1 && Math.abs(in - 4) <= 1) f *= 0.65f;
                    c = rgb(wood, f);
                }
                pm.drawPixel(ox + x, oy + y, c);
            }
        }
    }

    /** Porecz z boku mostu (od wody albo pustki): belka, porecz i slupki co pol pola. */
    private static void paintRail(Pixmap pm, int ox, int oy, int style, int side, int[] wood) {
        for (int u = 0; u < PX; u++) {            // u: wzdluz boku, v: od krawedzi do srodka
            for (int v = 0; v < 9; v++) {
                int c = 0;
                if (style == 0) {
                    boolean post = (u % 16) >= 2 && (u % 16) <= 5;
                    if (post && v <= 6) c = rgb(wood, v == 0 ? 1.0f : (u % 16) == 2 ? 0.95f : 0.68f);
                    else if (v <= 2) c = rgb(wood, v == 0 ? 1.05f : v == 1 ? 0.8f : 0.55f);
                    else if (v <= 4) c = rgb(wood, 0.42f);
                    else if (v <= 6) c = 0x00000050;
                    else if (post && v == 7) c = 0x00000040;
                } else {
                    int sag = (int) Math.round(2.2 * Math.sin(Math.PI * (u + 0.5) / PX));
                    boolean post = u <= 2;
                    if (post && v <= 5) c = rgb(wood, v == 0 ? 0.95f : 0.6f);
                    else if (v == 1 + sag) c = 0xd8c08cff;
                    else if (v == 2 + sag) c = 0x5a4428ff;
                    else if (v == 3 + sag) c = 0x00000040;
                    else if (v == 0) c = rgb(wood, 0.5f);
                }
                if (c == 0) continue;
                putSide(pm, ox, oy, side, u, v, c);
            }
        }
    }

    /** Belka na koncu mostu (przy ladzie) z gwozdziami. */
    private static void paintEnd(Pixmap pm, int ox, int oy, int style, int side, int[] wood) {
        for (int u = 0; u < PX; u++) {
            for (int v = 0; v < 6; v++) {
                int c;
                if (v == 5) c = 0x00000048;
                else {
                    float f = v == 0 ? 0.62f : v == 1 ? 1.08f : 0.86f;
                    if (v == 4) f = 0.5f;
                    if ((v == 2) && (u % 8 == 4)) f = 0.35f;
                    c = rgb(wood, f * (style == 1 ? 1.05f : 1f));
                }
                putSide(pm, ox, oy, side, u, v, c);
            }
        }
    }

    /** Zjazd na lad: deski schodzace pod ziemie, ciemniejace i z cieniem na krawedzi. */
    private static void paintRamp(Pixmap pm, int ox, int oy, int style, int side, int[] wood) {
        for (int u = 0; u < PX; u++) {
            for (int v = 0; v < 11; v++) {
                int c;
                if (v >= 9) c = v == 9 ? 0x00000060 : 0x00000030;
                else if (v % 3 == 2) c = rgb(wood, 0.4f);
                else {
                    float f = (1.0f - v * 0.045f) * (v % 3 == 0 ? 1.08f : 1f);
                    if (u % 16 == 0) f *= 0.7f;
                    c = rgb(wood, f * (style == 1 ? 1.05f : 1f));
                }
                putSide(pm, ox, oy, side, u, v, c);
            }
        }
    }

    /** Cien mostu na wodzie (od strony mostu). */
    private static void paintShadow(Pixmap pm, int ox, int oy, int side) {
        for (int u = 0; u < PX; u++) {
            for (int v = 0; v < 12; v++) {
                float a = (1f - v / 12f);
                putSide(pm, ox, oy, side, u, v, 0x05101800 | Math.round(a * a * 120));
            }
        }
    }

    private static void paintAo(Pixmap pm, int ox, int oy, int side) {
        for (int u = 0; u < PX; u++) {
            for (int v = 0; v < 9; v++) {
                float a = 1f - v / 9f;
                putSide(pm, ox, oy, side, u, v, 0x180e0600 | Math.round(a * a * 130));
            }
        }
    }

    /** Pal pod mostem: ciemny slupek przy krawedzi mostu i jasny krag wody wokol. */
    private static void paintPiling(Pixmap pm, int ox, int oy, int side) {
        for (int u = 0; u < PX; u++) {
            for (int v = 0; v < 10; v++) {
                int m = u % 16, c = 0;
                if (m >= 3 && m <= 6 && v <= 6) c = v == 6 ? 0x2a1a0cff : m == 3 ? 0x6a4a2aff : 0x3e2a16ff;
                else if (m >= 1 && m <= 8 && v == 7) c = 0xd8f0ff90;
                else if (m >= 2 && m <= 7 && v == 8) c = 0xb8e0ff50;
                if (c != 0) putSide(pm, ox, oy, side, u, v, c);
            }
        }
    }

    /** Piksel (u wzdluz boku, v od krawedzi boku do srodka pola) w ukladzie pola. */
    private static void putSide(Pixmap pm, int ox, int oy, int side, int u, int v, int c) {
        int x, y;
        switch (side) {
            case N: x = u; y = v; break;
            case S: x = u; y = PX - 1 - v; break;
            case W: x = v; y = u; break;
            default: x = PX - 1 - v; y = u; break;
        }
        pm.drawPixel(ox + x, oy + y, c);
    }

    // ------------------------------------------------------------------ pomocnicze

    private static int[] texOf(IntFunction<Pixmap> tile32, TileRegistry registry, String key, int fallback) {
        TileType t = registry.byKeyOrNull(key);
        Pixmap p = t == null ? null : tile32.apply(t.id());
        if (p == null) {
            int[] out = new int[PX * PX];
            java.util.Arrays.fill(out, (fallback << 8) | 0xff);
            return out;
        }
        int[] out = pixels(p);
        p.dispose();
        return out;
    }

    private static int[] pixels(Pixmap p) {
        int[] out = new int[PX * PX];
        for (int y = 0; y < PX; y++) {
            for (int x = 0; x < PX; x++) {
                int c = p.getPixel(x * p.getWidth() / PX, y * p.getHeight() / PX);
                out[y * PX + x] = c | 0xff;
            }
        }
        return out;
    }

    private static int[] avg(int[] tex) {
        long r = 0, g = 0, b = 0;
        for (int c : tex) {
            r += (c >>> 24) & 0xff;
            g += (c >>> 16) & 0xff;
            b += (c >>> 8) & 0xff;
        }
        int n = tex.length;
        return new int[] {(int) (r / n), (int) (g / n), (int) (b / n)};
    }

    private static int rgb(int[] base, float f) {
        int r = Math.min(255, Math.max(0, (int) (base[0] * f)));
        int g = Math.min(255, Math.max(0, (int) (base[1] * f)));
        int b = Math.min(255, Math.max(0, (int) (base[2] * f)));
        return (r << 24) | (g << 16) | (b << 8) | 0xff;
    }

    static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >>> 24) & 0xff) * f));
        int g = Math.min(255, (int) (((c >>> 16) & 0xff) * f));
        int b = Math.min(255, (int) (((c >>> 8) & 0xff) * f));
        return (r << 24) | (g << 16) | (b << 8) | (c & 0xff);
    }

    private static void fillRect(Pixmap pm, int x, int y, int w, int h, int c) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) pm.drawPixel(x + i, y + j, c);
    }

    @Override
    public void dispose() {
        if (texture != null) texture.dispose();
    }
}
