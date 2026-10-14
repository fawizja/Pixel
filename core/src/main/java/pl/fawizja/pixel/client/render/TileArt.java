package pl.fawizja.pixel.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.TileRegistry;
import pl.fawizja.pixel.world.TileType;

/**
 * Wlasna grafika pikselowa generowana proceduralnie do jednego atlasu tekstur (jeden
 * bind tekstury na klatke). To grafika tymczasowa, ale w pelni legalna i spojna
 * (kafelki 16x16, filtr NEAREST - ostre piksele).
 */
public final class TileArt implements Disposable {

    public static final int SIZE = 16;
    private static final int ATLAS = 1024;
    private static final int COLS = ATLAS / SIZE;
    private static final int ITEM_BASE = 256;
    private static final int SPRITE_BASE = 512;
    private static final int MONSTER_BASE = 640;

    private final Texture atlas;
    private final Texture lightTexture;
    private final TextureRegion[] tiles;
    private final TextureRegion[] items;
    private final TextureRegion[][] characters;
    private final TextureRegion white;
    private final Map<String, TextureRegion> monsters = new HashMap<>();

    public TileArt(TileRegistry tileRegistry, ItemRegistry itemRegistry, MonsterRegistry monsterRegistry) {
        Pixmap pm = new Pixmap(ATLAS, ATLAS, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();

        tiles = new TextureRegion[tileRegistry.maxId() + 1];
        items = new TextureRegion[512];
        characters = new TextureRegion[Vocation.values().length][4];

        for (TileType t : tileRegistry.all()) paintTile(pm, cellX(t.id()), cellY(t.id()), t);
        for (ItemType it : itemRegistry.all()) {
            int cell = ITEM_BASE + it.id();
            paintItem(pm, cellX(cell), cellY(cell), it);
        }
        for (Vocation v : Vocation.values()) {
            for (int d = 0; d < 4; d++) {
                int cell = SPRITE_BASE + v.ordinal() * 4 + d;
                paintCharacter(pm, cellX(cell), cellY(cell), v, d);
            }
        }
        List<MonsterType> mtypes = new ArrayList<>(monsterRegistry.all());
        mtypes.sort((a, b) -> a.key().compareTo(b.key()));
        for (int i = 0; i < mtypes.size(); i++) {
            int cell = MONSTER_BASE + i;
            paintMonster(pm, cellX(cell), cellY(cell), mtypes.get(i));
        }
        int whiteCell = COLS * COLS - 1;
        pm.setColor(Color.WHITE);
        pm.fillRectangle(cellX(whiteCell), cellY(whiteCell), SIZE, SIZE);

        atlas = new Texture(pm);
        atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();

        for (TileType t : tileRegistry.all()) tiles[t.id()] = region(t.id());
        for (ItemType it : itemRegistry.all()) items[it.id()] = region(ITEM_BASE + it.id());
        for (Vocation v : Vocation.values()) {
            for (int d = 0; d < 4; d++) characters[v.ordinal()][d] = region(SPRITE_BASE + v.ordinal() * 4 + d);
        }
        for (int i = 0; i < mtypes.size(); i++) monsters.put(mtypes.get(i).key(), region(MONSTER_BASE + i));
        TextureRegion w = region(whiteCell);
        white = new TextureRegion(w, 4, 4, 8, 8);

        lightTexture = buildLightTexture();
    }

    private static int cellX(int cell) { return (cell % COLS) * SIZE; }
    private static int cellY(int cell) { return (cell / COLS) * SIZE; }

    private TextureRegion region(int cell) {
        return new TextureRegion(atlas, cellX(cell), cellY(cell), SIZE, SIZE);
    }

    public TextureRegion tile(int id) { return id > 0 && id < tiles.length ? tiles[id] : null; }
    public TextureRegion item(int id) { return id > 0 && id < items.length ? items[id] : null; }
    public TextureRegion white() { return white; }
    public TextureRegion monster(String key) { return monsters.get(key); }
    public Texture lightTexture() { return lightTexture; }

    /** Sprite postaci: 0 = dol, 1 = gora, 2 = lewo, 3 = prawo. */
    public TextureRegion character(Vocation v, Direction facing) {
        int d;
        if (facing == null) d = 0;
        else if (facing.dy < 0 && facing.dx == 0) d = 1;
        else if (facing.dx < 0) d = 2;
        else if (facing.dx > 0) d = 3;
        else d = 0;
        return characters[v.ordinal()][d];
    }

    private static Texture buildLightTexture() {
        int s = 128;
        Pixmap p = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        float c = (s - 1) / 2f;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float d = (float) Math.sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c;
                float a = Math.max(0f, 1f - d);
                a = a * a * (3 - 2 * a);
                p.setColor(1f, 1f, 1f, a);
                p.drawPixel(x, y);
            }
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        return t;
    }

    // ------------------------------------------------------------------ malowanie

    private static Color shade(Color c, float f) {
        return new Color(Math.min(1, c.r * f), Math.min(1, c.g * f), Math.min(1, c.b * f), c.a);
    }

    private static void px(Pixmap pm, int ox, int oy, int x, int y, Color c) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        pm.setColor(c);
        pm.drawPixel(ox + x, oy + y);
    }

    private static void rect(Pixmap pm, int ox, int oy, int x, int y, int w, int h, Color c) {
        pm.setColor(c);
        pm.fillRectangle(ox + x, oy + y, w, h);
    }

    private static void disc(Pixmap pm, int ox, int oy, float cx, float cy, float r, Color c) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                if (dx * dx + dy * dy <= r * r) px(pm, ox, oy, x, y, c);
            }
        }
    }

    private static void noise(Pixmap pm, int ox, int oy, Color base, Random r, float amount, int count) {
        rect(pm, ox, oy, 0, 0, SIZE, SIZE, base);
        for (int i = 0; i < count; i++) {
            float f = 1f + (r.nextFloat() * 2f - 1f) * amount;
            px(pm, ox, oy, r.nextInt(SIZE), r.nextInt(SIZE), shade(base, f));
        }
    }

    private void paintTile(Pixmap pm, int ox, int oy, TileType t) {
        Color c = Color.valueOf(t.color());
        Random r = new Random(t.id() * 7919L);
        switch (t.pattern()) {
            case "grass":
                noise(pm, ox, oy, c, r, 0.18f, 60);
                for (int i = 0; i < 6; i++) {
                    int x = r.nextInt(SIZE), y = 1 + r.nextInt(SIZE - 1);
                    px(pm, ox, oy, x, y, shade(c, 1.3f));
                    px(pm, ox, oy, x, y - 1, shade(c, 1.15f));
                }
                break;
            case "dirt":
            case "sand":
            case "snow":
            case "gravel":
                noise(pm, ox, oy, c, r, t.pattern().equals("snow") ? 0.05f : 0.2f, 70);
                if (t.pattern().equals("gravel")) {
                    for (int i = 0; i < 5; i++) px(pm, ox, oy, r.nextInt(SIZE), r.nextInt(SIZE), shade(c, 1.5f));
                }
                break;
            case "cobble":
                rect(pm, ox, oy, 0, 0, SIZE, SIZE, shade(c, 0.7f));
                for (int row = 0; row < 4; row++) {
                    int off = (row % 2) * 2;
                    for (int col = -1; col < 4; col++) {
                        int x = col * 4 + off;
                        rect(pm, ox, oy, Math.max(0, x), row * 4, Math.min(3, 3 + Math.min(0, x)), 3,
                                shade(c, 0.9f + r.nextFloat() * 0.25f));
                    }
                }
                break;
            case "water":
                rect(pm, ox, oy, 0, 0, SIZE, SIZE, c);
                for (int y = 2; y < SIZE; y += 5) {
                    int x0 = r.nextInt(8);
                    for (int x = x0; x < x0 + 5; x++) px(pm, ox, oy, x, y, shade(c, 1.35f));
                }
                break;
            case "planks":
                rect(pm, ox, oy, 0, 0, SIZE, SIZE, c);
                for (int y = 3; y < SIZE; y += 4) rect(pm, ox, oy, 0, y, SIZE, 1, shade(c, 0.7f));
                for (int y = 0; y < SIZE; y += 4) px(pm, ox, oy, (y * 5) % SIZE, y + 1, shade(c, 0.6f));
                break;
            case "tiles":
                rect(pm, ox, oy, 0, 0, SIZE, SIZE, c);
                rect(pm, ox, oy, 0, 7, SIZE, 1, shade(c, 0.8f));
                rect(pm, ox, oy, 7, 0, 1, SIZE, shade(c, 0.8f));
                rect(pm, ox, oy, 0, 15, SIZE, 1, shade(c, 0.8f));
                rect(pm, ox, oy, 15, 0, 1, SIZE, shade(c, 0.8f));
                break;
            case "brick":
                rect(pm, ox, oy, 0, 0, SIZE, SIZE, shade(c, 0.6f));
                for (int row = 0; row < 4; row++) {
                    int off = (row % 2) * 4;
                    for (int col = -1; col < 3; col++) {
                        int x = col * 8 + off;
                        int x0 = Math.max(0, x), x1 = Math.min(SIZE, x + 7);
                        if (x1 > x0) rect(pm, ox, oy, x0, row * 4, x1 - x0, 3, shade(c, 0.95f + r.nextFloat() * 0.15f));
                    }
                }
                rect(pm, ox, oy, 0, 0, SIZE, 1, shade(c, 1.3f));
                break;
            case "rockwall":
                noise(pm, ox, oy, c, r, 0.25f, 90);
                for (int i = 0; i < 3; i++) {
                    int x = r.nextInt(SIZE), y = r.nextInt(SIZE);
                    for (int k = 0; k < 5; k++) px(pm, ox, oy, x + k / 2, y + k, shade(c, 0.6f));
                }
                rect(pm, ox, oy, 0, 0, SIZE, 1, shade(c, 1.4f));
                break;
            case "woodwall":
                rect(pm, ox, oy, 0, 0, SIZE, SIZE, c);
                for (int x = 3; x < SIZE; x += 4) rect(pm, ox, oy, x, 0, 1, SIZE, shade(c, 0.65f));
                rect(pm, ox, oy, 0, 0, SIZE, 2, shade(c, 1.25f));
                break;
            case "tuft":
                for (int i = 0; i < 5; i++) {
                    int x = 2 + r.nextInt(12), y = 8 + r.nextInt(6);
                    for (int k = 0; k < 3; k++) px(pm, ox, oy, x + (k == 2 ? 1 : 0), y - k, shade(c, 1f + k * 0.1f));
                }
                break;
            case "flowers":
                Color[] petals = {c, Color.valueOf("#f2e35b"), Color.valueOf("#ffffff"), Color.valueOf("#9a6bff")};
                for (int i = 0; i < 4; i++) {
                    int x = 2 + r.nextInt(12), y = 2 + r.nextInt(12);
                    Color p = petals[i % petals.length];
                    px(pm, ox, oy, x, y, p);
                    px(pm, ox, oy, x + 1, y, p);
                    px(pm, ox, oy, x, y + 1, p);
                    px(pm, ox, oy, x + 1, y + 1, Color.valueOf("#e8b030"));
                }
                break;
            case "wheat":
                for (int x = 1; x < SIZE; x += 3) {
                    rect(pm, ox, oy, x, 5, 1, 10, shade(c, 0.8f));
                    rect(pm, ox, oy, x, 2, 2, 4, c);
                }
                break;
            case "rug":
                rect(pm, ox, oy, 2, 0, 12, SIZE, c);
                rect(pm, ox, oy, 3, 0, 1, SIZE, Color.valueOf("#d4a43a"));
                rect(pm, ox, oy, 12, 0, 1, SIZE, Color.valueOf("#d4a43a"));
                break;
            case "tree":
                rect(pm, ox, oy, 7, 10, 3, 6, Color.valueOf("#5a3a1e"));
                disc(pm, ox, oy, 8f, 7f, 7f, shade(c, 0.75f));
                disc(pm, ox, oy, 7f, 6f, 5.5f, c);
                disc(pm, ox, oy, 6f, 4.5f, 2.5f, shade(c, 1.35f));
                break;
            case "bush":
                disc(pm, ox, oy, 8f, 10f, 5f, shade(c, 0.8f));
                disc(pm, ox, oy, 7f, 9f, 3.5f, c);
                px(pm, ox, oy, 9, 8, Color.valueOf("#d8304a"));
                break;
            case "rock":
                disc(pm, ox, oy, 8f, 10f, 6f, shade(c, 0.7f));
                disc(pm, ox, oy, 7.5f, 9f, 4.8f, c);
                disc(pm, ox, oy, 6f, 7.5f, 1.8f, shade(c, 1.35f));
                break;
            case "fountain":
                disc(pm, ox, oy, 8f, 8f, 7.5f, Color.valueOf("#9a968c"));
                disc(pm, ox, oy, 8f, 8f, 5.5f, c);
                disc(pm, ox, oy, 8f, 8f, 1.5f, Color.valueOf("#e8f6ff"));
                break;
            case "lamp":
                rect(pm, ox, oy, 7, 5, 2, 11, Color.valueOf("#3a3a3a"));
                rect(pm, ox, oy, 5, 1, 6, 5, Color.valueOf("#2a2a2a"));
                rect(pm, ox, oy, 6, 2, 4, 3, c);
                break;
            case "torch":
                rect(pm, ox, oy, 7, 7, 2, 7, Color.valueOf("#6b4423"));
                disc(pm, ox, oy, 8f, 5f, 2.8f, c);
                disc(pm, ox, oy, 8f, 5.5f, 1.4f, Color.valueOf("#fff2b0"));
                break;
            case "fire":
                rect(pm, ox, oy, 3, 12, 10, 2, Color.valueOf("#5a3a1e"));
                rect(pm, ox, oy, 5, 11, 6, 2, Color.valueOf("#6b4423"));
                disc(pm, ox, oy, 8f, 8f, 4.5f, c);
                disc(pm, ox, oy, 8f, 9f, 2.5f, Color.valueOf("#ffe080"));
                break;
            case "counter":
                rect(pm, ox, oy, 0, 4, SIZE, 9, c);
                rect(pm, ox, oy, 0, 4, SIZE, 2, shade(c, 1.4f));
                rect(pm, ox, oy, 0, 12, SIZE, 1, shade(c, 0.6f));
                break;
            case "fence":
                rect(pm, ox, oy, 0, 5, SIZE, 2, c);
                rect(pm, ox, oy, 0, 10, SIZE, 2, c);
                rect(pm, ox, oy, 2, 2, 2, 13, shade(c, 0.8f));
                rect(pm, ox, oy, 12, 2, 2, 13, shade(c, 0.8f));
                break;
            case "crystal":
                for (int y = 2; y < 15; y++) {
                    int w = y < 8 ? (y - 2) / 2 + 1 : (15 - y) / 2 + 1;
                    rect(pm, ox, oy, 8 - w, y, w * 2, 1, y < 8 ? shade(c, 1.2f) : c);
                }
                px(pm, ox, oy, 7, 5, Color.WHITE);
                break;
            case "rubble":
                for (int i = 0; i < 7; i++) {
                    disc(pm, ox, oy, 3 + r.nextInt(10), 5 + r.nextInt(9), 1.5f + r.nextFloat() * 1.5f, shade(c, 0.8f + r.nextFloat() * 0.5f));
                }
                break;
            case "stairs_up":
                for (int i = 0; i < 4; i++) rect(pm, ox, oy, 2, 2 + i * 3, 12, 3, shade(c, 1.1f - i * 0.12f));
                for (int i = 0; i < 4; i++) rect(pm, ox, oy, 2, 4 + i * 3, 12, 1, shade(c, 0.6f));
                break;
            case "stairs_down":
                for (int i = 0; i < 4; i++) rect(pm, ox, oy, 2, 2 + i * 3, 12, 3, shade(Color.valueOf("#8a8478"), 1f - i * 0.22f));
                rect(pm, ox, oy, 2, 13, 12, 2, c);
                break;
            case "ladder":
                rect(pm, ox, oy, 4, 0, 2, SIZE, c);
                rect(pm, ox, oy, 10, 0, 2, SIZE, c);
                for (int y = 2; y < SIZE; y += 4) rect(pm, ox, oy, 4, y, 8, 1, shade(c, 1.2f));
                break;
            case "hole":
                disc(pm, ox, oy, 8f, 8.5f, 6.5f, shade(c, 3f));
                disc(pm, ox, oy, 8f, 9f, 5.2f, c);
                break;
            case "door":
                rect(pm, ox, oy, 2, 0, 12, SIZE, Color.valueOf("#2a1a0e"));
                rect(pm, ox, oy, 2, 0, 3, SIZE, c);
                px(pm, ox, oy, 4, 8, Color.valueOf("#e8c040"));
                break;
            case "altar":
                rect(pm, ox, oy, 1, 5, 14, 9, c);
                rect(pm, ox, oy, 1, 5, 14, 2, Color.valueOf("#e8c040"));
                rect(pm, ox, oy, 7, 1, 2, 5, Color.valueOf("#e8c040"));
                rect(pm, ox, oy, 5, 2, 6, 1, Color.valueOf("#e8c040"));
                break;
            case "crate":
                rect(pm, ox, oy, 2, 3, 12, 12, c);
                rect(pm, ox, oy, 2, 3, 12, 1, shade(c, 1.3f));
                for (int i = 0; i < 12; i++) {
                    px(pm, ox, oy, 2 + i, 3 + i, shade(c, 0.6f));
                    px(pm, ox, oy, 13 - i, 3 + i, shade(c, 0.6f));
                }
                break;
            default:
                rect(pm, ox, oy, 0, 0, SIZE, SIZE, c);
        }
    }

    private void paintItem(Pixmap pm, int ox, int oy, ItemType it) {
        Color c = Color.valueOf(it.color());
        Color dark = shade(c, 0.6f);
        ItemCategory cat = it.category();
        switch (cat) {
            case CURRENCY:
                disc(pm, ox, oy, 7f, 9f, 4.5f, dark);
                disc(pm, ox, oy, 9f, 7f, 4.5f, dark);
                disc(pm, ox, oy, 9f, 7f, 3.8f, c);
                px(pm, ox, oy, 8, 6, Color.WHITE);
                break;
            case WEAPON:
            case AMMUNITION:
                for (int i = 0; i < 11; i++) {
                    px(pm, ox, oy, 3 + i, 12 - i, c);
                    px(pm, ox, oy, 4 + i, 12 - i, dark);
                }
                rect(pm, ox, oy, 2, 11, 3, 3, Color.valueOf("#6b4423"));
                break;
            case SHIELD:
                disc(pm, ox, oy, 8f, 8f, 6f, dark);
                disc(pm, ox, oy, 8f, 8f, 4.5f, c);
                disc(pm, ox, oy, 8f, 8f, 1.2f, Color.valueOf("#c0c0c0"));
                break;
            case ARMOR:
                if (it.slot() == pl.fawizja.pixel.item.EquipSlot.HEAD) {
                    disc(pm, ox, oy, 8f, 9f, 5.5f, c);
                    rect(pm, ox, oy, 2, 9, 12, 5, new Color(0, 0, 0, 0));
                    rect(pm, ox, oy, 2, 9, 12, 2, dark);
                } else if (it.slot() == pl.fawizja.pixel.item.EquipSlot.FEET) {
                    rect(pm, ox, oy, 3, 4, 3, 8, c);
                    rect(pm, ox, oy, 3, 11, 6, 3, dark);
                    rect(pm, ox, oy, 9, 4, 3, 8, c);
                    rect(pm, ox, oy, 9, 11, 5, 3, dark);
                } else if (it.slot() == pl.fawizja.pixel.item.EquipSlot.LEGS) {
                    rect(pm, ox, oy, 4, 2, 8, 4, c);
                    rect(pm, ox, oy, 4, 6, 3, 9, c);
                    rect(pm, ox, oy, 9, 6, 3, 9, c);
                } else {
                    rect(pm, ox, oy, 3, 3, 10, 11, c);
                    rect(pm, ox, oy, 1, 3, 3, 6, dark);
                    rect(pm, ox, oy, 12, 3, 3, 6, dark);
                    rect(pm, ox, oy, 6, 3, 4, 2, new Color(0, 0, 0, 0));
                }
                break;
            case POTION:
                rect(pm, ox, oy, 7, 2, 2, 3, Color.valueOf("#c8c8c8"));
                disc(pm, ox, oy, 8f, 10f, 4.5f, Color.valueOf("#d8e0e8"));
                disc(pm, ox, oy, 8f, 10.5f, 3.6f, c);
                px(pm, ox, oy, 6, 9, Color.WHITE);
                break;
            case LIGHT:
                rect(pm, ox, oy, 7, 7, 2, 8, Color.valueOf("#6b4423"));
                disc(pm, ox, oy, 8f, 5f, 3f, c);
                break;
            case RUNE:
                rect(pm, ox, oy, 3, 3, 10, 10, dark);
                rect(pm, ox, oy, 4, 4, 8, 8, c);
                break;
            case FOOD:
            case BAIT:
                disc(pm, ox, oy, 8f, 9f, 4.5f, c);
                disc(pm, ox, oy, 7f, 8f, 1.5f, shade(c, 1.3f));
                break;
            case TOOL:
            default:
                for (int i = 0; i < 10; i++) px(pm, ox, oy, 3 + i, 12 - i, Color.valueOf("#6b4423"));
                rect(pm, ox, oy, 10, 2, 4, 4, c);
        }
    }

    private void paintCharacter(Pixmap pm, int ox, int oy, Vocation v, int dir) {
        Color outfit;
        Color accent;
        switch (v) {
            case PALADIN: outfit = Color.valueOf("#b0472f"); accent = Color.valueOf("#e8c040"); break;
            case DRUID: outfit = Color.valueOf("#3f8f3a"); accent = Color.valueOf("#a8e070"); break;
            case SORCERER: outfit = Color.valueOf("#4a3fa8"); accent = Color.valueOf("#ff7a30"); break;
            default: outfit = Color.valueOf("#d0882a"); accent = Color.valueOf("#f2e0b0"); break;
        }
        Color skin = Color.valueOf("#f0c8a0");
        Color hair = Color.valueOf("#5a3a1e");
        Color outline = Color.valueOf("#1a1410");
        // cien
        disc(pm, ox, oy, 8f, 14.5f, 4f, new Color(0, 0, 0, 0.35f));
        // nogi
        rect(pm, ox, oy, 5, 11, 2, 4, shade(outfit, 0.55f));
        rect(pm, ox, oy, 9, 11, 2, 4, shade(outfit, 0.55f));
        // tulow
        rect(pm, ox, oy, 4, 6, 8, 6, outfit);
        rect(pm, ox, oy, 4, 10, 8, 1, accent);
        // rece
        rect(pm, ox, oy, 2, 7, 2, 4, dir == 2 ? shade(outfit, 0.8f) : outfit);
        rect(pm, ox, oy, 12, 7, 2, 4, dir == 3 ? shade(outfit, 0.8f) : outfit);
        // glowa
        rect(pm, ox, oy, 5, 1, 6, 5, skin);
        rect(pm, ox, oy, 5, 1, 6, 2, hair);
        if (dir == 1) {
            rect(pm, ox, oy, 5, 1, 6, 5, hair);
        } else if (dir == 0) {
            px(pm, ox, oy, 6, 3, outline);
            px(pm, ox, oy, 9, 3, outline);
        } else if (dir == 2) {
            px(pm, ox, oy, 6, 3, outline);
            rect(pm, ox, oy, 9, 1, 2, 4, hair);
        } else {
            px(pm, ox, oy, 9, 3, outline);
            rect(pm, ox, oy, 5, 1, 2, 4, hair);
        }
        if (v == Vocation.SORCERER || v == Vocation.DRUID) {
            rect(pm, ox, oy, 4, 0, 8, 1, shade(outfit, 0.7f)); // kaptur
        }
        if (v == Vocation.PALADIN) {
            rect(pm, ox, oy, 13, 2, 1, 10, Color.valueOf("#c9b37e")); // wlocznia
        }
    }

    /** Sylwetki potworow wedlug ksztaltu z monsters.csv, barwione kolorem typu. */
    private void paintMonster(Pixmap pm, int ox, int oy, MonsterType t) {
        Color c = Color.valueOf(t.color());
        Color dark = shade(c, 0.6f);
        Color light = shade(c, 1.3f);
        Color eye = Color.valueOf("#ff3020");
        disc(pm, ox, oy, 8f, 14.5f, 5f, new Color(0, 0, 0, 0.35f));
        switch (t.shape()) {
            case "beast":
                rect(pm, ox, oy, 2, 7, 11, 5, c);          // tulow
                rect(pm, ox, oy, 2, 7, 11, 1, light);
                rect(pm, ox, oy, 3, 12, 2, 3, dark);       // nogi
                rect(pm, ox, oy, 10, 12, 2, 3, dark);
                rect(pm, ox, oy, 11, 4, 4, 5, c);          // glowa
                px(pm, ox, oy, 13, 5, eye);
                rect(pm, ox, oy, 14, 7, 2, 2, dark);       // pysk
                rect(pm, ox, oy, 0, 6, 2, 2, dark);        // ogon
                if (t.key().contains("boar")) {
                    px(pm, ox, oy, 15, 9, Color.valueOf("#f0ead0"));
                    px(pm, ox, oy, 12, 9, Color.valueOf("#f0ead0"));
                }
                break;
            case "spider":
                disc(pm, ox, oy, 8f, 9f, 4f, c);
                disc(pm, ox, oy, 8f, 5f, 2.5f, dark);
                px(pm, ox, oy, 7, 4, eye);
                px(pm, ox, oy, 9, 4, eye);
                for (int i = 0; i < 4; i++) {
                    int ly = 6 + i * 2;
                    rect(pm, ox, oy, 1, ly, 4, 1, dark);
                    rect(pm, ox, oy, 11, ly, 4, 1, dark);
                    px(pm, ox, oy, 0, ly + 1, dark);
                    px(pm, ox, oy, 15, ly + 1, dark);
                }
                break;
            case "golem":
                rect(pm, ox, oy, 3, 4, 10, 8, c);
                rect(pm, ox, oy, 5, 1, 6, 4, light);
                rect(pm, ox, oy, 1, 5, 2, 6, dark);
                rect(pm, ox, oy, 13, 5, 2, 6, dark);
                rect(pm, ox, oy, 4, 12, 3, 3, dark);
                rect(pm, ox, oy, 9, 12, 3, 3, dark);
                px(pm, ox, oy, 6, 2, Color.WHITE);
                px(pm, ox, oy, 9, 2, Color.WHITE);
                px(pm, ox, oy, 7, 7, Color.WHITE);
                px(pm, ox, oy, 10, 9, light);
                break;
            case "boss": {
                Color skin = Color.valueOf("#7a8a4a");
                rect(pm, ox, oy, 2, 5, 12, 8, c);          // pancerz
                rect(pm, ox, oy, 2, 9, 12, 1, dark);
                rect(pm, ox, oy, 4, 13, 3, 3, dark);
                rect(pm, ox, oy, 9, 13, 3, 3, dark);
                rect(pm, ox, oy, 0, 6, 2, 6, skin);
                rect(pm, ox, oy, 14, 6, 2, 6, skin);
                rect(pm, ox, oy, 5, 1, 6, 5, skin);        // glowa
                px(pm, ox, oy, 6, 3, eye);
                px(pm, ox, oy, 9, 3, eye);
                rect(pm, ox, oy, 4, 0, 8, 1, Color.valueOf("#f0c840")); // korona
                px(pm, ox, oy, 4, 0, Color.valueOf("#ff4060"));
                px(pm, ox, oy, 8, 0, Color.valueOf("#40a0ff"));
                px(pm, ox, oy, 11, 0, Color.valueOf("#ff4060"));
                break;
            }
            case "humanoid":
            default: {
                boolean skeleton = t.key().contains("skeleton");
                boolean troll = t.key().contains("troll");
                Color skin = skeleton ? Color.valueOf("#e8e4d0") : troll ? Color.valueOf("#6a7a4a")
                        : t.key().contains("goblin") ? Color.valueOf("#7ab040") : Color.valueOf("#e0b090");
                int top = troll ? 0 : 2;
                rect(pm, ox, oy, 5, 11, 2, 4, dark);
                rect(pm, ox, oy, 9, 11, 2, 4, dark);
                rect(pm, ox, oy, troll ? 3 : 4, 6, troll ? 10 : 8, 6, skeleton ? skin : c);
                if (skeleton) {
                    for (int r = 7; r < 11; r += 2) rect(pm, ox, oy, 5, r, 6, 1, Color.valueOf("#6a6658"));
                }
                rect(pm, ox, oy, 2, 7, 2, 4, skin);
                rect(pm, ox, oy, 12, 7, 2, 4, skin);
                rect(pm, ox, oy, 5, top, 6, 6 - top + 1, skin);
                px(pm, ox, oy, 6, top + 2, skeleton ? Color.BLACK : eye);
                px(pm, ox, oy, 9, top + 2, skeleton ? Color.BLACK : eye);
                if (t.key().contains("goblin")) {
                    px(pm, ox, oy, 4, top + 1, skin);
                    px(pm, ox, oy, 11, top + 1, skin);
                }
                if (t.key().contains("shaman")) {
                    rect(pm, ox, oy, 13, 2, 1, 11, Color.valueOf("#8a5a2b"));
                    px(pm, ox, oy, 13, 1, Color.valueOf("#ff7a30"));
                }
                if (t.key().contains("bandit")) rect(pm, ox, oy, 5, top + 3, 6, 2, dark); // chusta
            }
        }
    }

    @Override
    public void dispose() {
        atlas.dispose();
        lightTexture.dispose();
    }
}
