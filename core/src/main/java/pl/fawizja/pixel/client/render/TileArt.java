package pl.fawizja.pixel.client.render;

import com.badlogic.gdx.Gdx;
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
    private static final int NPC_BASE = 800;
    /** Kon (wierzchowiec): w prawo i w lewo. */
    private static final int HORSE_CELL = 1000;
    private TextureRegion[] horse;
    /** Skrzynia skarbow: zamknieta i otwarta. */
    private static final int CHEST_CELL = 1002;
    private TextureRegion[] chest;
    /** Manekin treningowy. */
    private static final int DUMMY_CELL = 1004;
    /** Pupile (0.58.0), zwrocone w prawo; w lewo rysowane odbiciem. */
    private static final String[] PETS = {"pet_dog", "pet_cat", "pet_raven", "pet_fennec"};
    private static final int PET_CELL = 1005;
    /** Pulapka z kolcami (lochy). */
    private static final int TRAP_CELL = 1010;
    private TextureRegion trap;
    /** Dachy budynkow (widok jak w Tibii): dachowka (drewno), lupek (kamien), piaskowa dachowka (piaskowiec);
     *  gont i brazowa dachowka w komorkach ROOF_EXTRA_CELL.. (domy z muru pruskiego i tynkowane). */
    private static final int ROOF_CELL = 1011;
    private static final int ROOF_EXTRA_CELL = 1015;
    private TextureRegion[] roof;
    /** Plecak w gniezdzie ekwipunku. */
    private static final int BACKPACK_CELL = 1014;
    private TextureRegion backpack;
    /** Stroje w kolorach (Outfit 1..): profesja * kolory * 4 kierunki od tej komorki. */
    private static final int OUTFIT_BASE = 1100;
    private final java.util.Map<String, TextureRegion> pets = new java.util.HashMap<>();
    private TextureRegion dummy;

    private final Texture atlas;
    private final Texture lightTexture;
    private final TextureRegion[] tiles;
    private final TextureRegion[] items;
    private final TextureRegion[][] characters;
    private final TextureRegion white;
    private final Map<String, TextureRegion> monsters = new HashMap<>();
    private final Map<String, TextureRegion> npcs = new HashMap<>();

    private final ItemRegistry itemRegistry;

    /** Rejestr przedmiotow, z ktorego zbudowano grafiki (rzadkosc ramek w interfejsie). */
    public ItemRegistry itemRegistry() { return itemRegistry; }

    public TileArt(TileRegistry tileRegistry, ItemRegistry itemRegistry, MonsterRegistry monsterRegistry,
                   List<pl.fawizja.pixel.npc.Npc> npcList) {
        this.itemRegistry = itemRegistry;
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
                paintCharacter(pm, cellX(cell), cellY(cell), v, d, null);
                for (int o = 1; o < pl.fawizja.pixel.character.Outfit.count(); o++) {
                    int oc = outfitCell(v, o, d);
                    paintCharacter(pm, cellX(oc), cellY(oc), v, d, Color.valueOf(pl.fawizja.pixel.character.Outfit.COLORS[o]));
                }
            }
        }
        List<MonsterType> mtypes = new ArrayList<>(monsterRegistry.all());
        mtypes.sort((a, b) -> a.key().compareTo(b.key()));
        for (int i = 0; i < mtypes.size(); i++) {
            int cell = MONSTER_BASE + i;
            paintMonster(pm, cellX(cell), cellY(cell), mtypes.get(i));
        }
        for (int i = 0; i < npcList.size(); i++) {
            int cell = NPC_BASE + i;
            paintNpc(pm, cellX(cell), cellY(cell), npcList.get(i));
        }
        paintHorse(pm, cellX(HORSE_CELL), cellY(HORSE_CELL), false);
        paintHorse(pm, cellX(HORSE_CELL + 1), cellY(HORSE_CELL + 1), true);
        paintChest(pm, cellX(CHEST_CELL), cellY(CHEST_CELL), false);
        paintChest(pm, cellX(CHEST_CELL + 1), cellY(CHEST_CELL + 1), true);
        paintDummy(pm, cellX(DUMMY_CELL), cellY(DUMMY_CELL));
        paintTrap(pm, cellX(TRAP_CELL), cellY(TRAP_CELL));
        paintRoof(pm, cellX(ROOF_CELL), cellY(ROOF_CELL), Color.valueOf("#a8402c"));
        paintRoof(pm, cellX(ROOF_CELL + 1), cellY(ROOF_CELL + 1), Color.valueOf("#5a6270"));
        paintRoof(pm, cellX(ROOF_CELL + 2), cellY(ROOF_CELL + 2), Color.valueOf("#c89a5a"));
        paintRoof(pm, cellX(ROOF_EXTRA_CELL), cellY(ROOF_EXTRA_CELL), Color.valueOf("#6a4a30"));
        paintRoof(pm, cellX(ROOF_EXTRA_CELL + 1), cellY(ROOF_EXTRA_CELL + 1), Color.valueOf("#7a5a58"));
        for (int i = 0; i < PETS.length; i++) paintPet(pm, cellX(PET_CELL + i), cellY(PET_CELL + i), PETS[i]);
        paintBackpack(pm, cellX(BACKPACK_CELL), cellY(BACKPACK_CELL));
        applyOverrides(pm, tileRegistry, itemRegistry, mtypes, npcList);
        int whiteCell = COLS * COLS - 1;
        pm.setColor(Color.WHITE);
        pm.fillRectangle(cellX(whiteCell), cellY(whiteCell), SIZE, SIZE);

        final Pixmap base = pm;
        borders = new GroundBorders(tileRegistry, new GroundBorders.Source() {
            @Override
            public Pixmap ground32(TileType t) {
                return pixmap32(base, t.id());
            }

            @Override
            public Pixmap borderPiece(TileType t, int piece) {
                // kawalki obrzezy z paczki OTSP: borders/klucz_krawedz.png (tools/art/otsp_import.py)
                com.badlogic.gdx.files.FileHandle f = Gdx.files.internal("art/borders/" + t.key() + "_" + BORDER_EDGES[piece] + ".png");
                if (!f.exists()) f = Gdx.files.internal("assets/art/borders/" + t.key() + "_" + BORDER_EDGES[piece] + ".png");
                if (!f.exists()) return null;
                try {
                    return new Pixmap(f);
                } catch (RuntimeException e) {
                    Gdx.app.error("TileArt", "Nie można wczytać " + f.path() + ": " + e.getMessage());
                    return null;
                }
            }
        });
        loadAutotiles(tileRegistry);
        loadRockShapes(tileRegistry, base);
        loadBigTiles(tileRegistry);
        loadNative(tileRegistry);
        loadEffects();
        loadRoofPieces();
        atlas = new Texture(pm);
        atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        if (wallPm != null) {
            wallAtlas = new Texture(wallPm);
            wallAtlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            wallPm.dispose();
            wallPm = null;
            for (Map.Entry<Integer, int[]> e : wallSlots.entrySet()) {
                TextureRegion[] pieces = new TextureRegion[e.getValue().length];
                for (int k = 0; k < pieces.length; k++) {
                    int slot = e.getValue()[k];
                    pieces[k] = new TextureRegion(wallAtlas, (slot % WALL_COLS) * WALL_PX, (slot / WALL_COLS) * WALL_PX, WALL_PX, WALL_PX);
                }
                wallPieces.put(e.getKey(), pieces);
            }
            for (Map.Entry<Integer, int[][]> e : wallVariantSlots.entrySet()) {
                TextureRegion[][] v = new TextureRegion[e.getValue().length][];
                for (int k = 0; k < v.length; k++) {
                    int[] sl = e.getValue()[k];
                    v[k] = new TextureRegion[sl.length];
                    for (int n = 0; n < sl.length; n++) {
                        v[k][n] = new TextureRegion(wallAtlas, (sl[n] % WALL_COLS) * WALL_PX, (sl[n] / WALL_COLS) * WALL_PX, WALL_PX, WALL_PX);
                    }
                }
                wallVariants.put(e.getKey(), v);
            }
            for (Map.Entry<Integer, List<int[]>> e : wallAltSlots.entrySet()) {
                TextureRegion[][] v = new TextureRegion[e.getValue().size()][];
                for (int k = 0; k < v.length; k++) {
                    int[] sl = e.getValue().get(k);
                    v[k] = new TextureRegion[sl.length];
                    for (int n = 0; n < sl.length; n++) {
                        v[k][n] = new TextureRegion(wallAtlas, (sl[n] % WALL_COLS) * WALL_PX, (sl[n] / WALL_COLS) * WALL_PX, WALL_PX, WALL_PX);
                    }
                }
                wallAlts.put(e.getKey(), v);
            }
        }
        if (hiPm != null) {
            hiAtlas = new Texture(hiPm);
            hiAtlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            hiPm.dispose();
            hiPm = null;
            Gdx.app.log("TileArt", "Grafiki 32 px: " + hiCells.size() + " / " + HI_COLS * HI_COLS);
        }

        for (TileType t : tileRegistry.all()) tiles[t.id()] = region(t.id());
        buildRoofPieces();
        buildVariants(tileRegistry);
        for (ItemType it : itemRegistry.all()) items[it.id()] = region(ITEM_BASE + it.id());
        for (Vocation v : Vocation.values()) {
            for (int d = 0; d < 4; d++) characters[v.ordinal()][d] = region(SPRITE_BASE + v.ordinal() * 4 + d);
            for (int o = 1; o < pl.fawizja.pixel.character.Outfit.count(); o++) {
                for (int d = 0; d < 4; d++) outfits.put(outfitCell(v, o, d), region(outfitCell(v, o, d)));
            }
        }
        for (int i = 0; i < mtypes.size(); i++) monsters.put(mtypes.get(i).key(), region(MONSTER_BASE + i));
        for (int i = 0; i < npcList.size(); i++) npcs.put(npcList.get(i).key(), region(NPC_BASE + i));
        horse = new TextureRegion[] {region(HORSE_CELL), region(HORSE_CELL + 1)};
        chest = new TextureRegion[] {region(CHEST_CELL), region(CHEST_CELL + 1)};
        dummy = region(DUMMY_CELL);
        trap = region(TRAP_CELL);
        backpack = region(BACKPACK_CELL);
        roof = new TextureRegion[] {region(ROOF_CELL), region(ROOF_CELL + 1), region(ROOF_CELL + 2),
                region(ROOF_EXTRA_CELL), region(ROOF_EXTRA_CELL + 1)};
        for (int i = 0; i < PETS.length; i++) pets.put(PETS[i], region(PET_CELL + i));
        for (Map.Entry<String, Integer> e : walkCells.entrySet()) {
            if (hiAtlas == null || !hiCells.containsKey(e.getValue()) || !hiCells.containsKey(e.getValue() + 1)) continue;
            walk.put(e.getKey(), new TextureRegion[] {region(e.getValue()), region(e.getValue() + 1)});
        }
        TextureRegion w = region(whiteCell);
        white = new TextureRegion(w, 4, 4, 8, 8);

        lightTexture = buildLightTexture();
    }

    /** Kierunki postaci w nazwach plikow: przod, tyl, lewo, prawo (jak indeksy d). */
    public static final String[] DIRS = {"down", "up", "left", "right"};

    /**
     * Grafiki od grafikow: plik PNG w assets/art/ zastepuje wygenerowany obrazek o tej nazwie (np. items/health_potion.png,
     * monsters/wolf.png, characters/paladin_down.png). Obrazki do 16 px trafiaja do atlasu 16x16, wieksze sa trzymane
     * w osobnym atlasie 32x32 (pelne szczegoly np. grafik 32 px). Skalowanie zawsze bez rozmywania.
     * Brakujace pliki zostaja wygenerowane. Lista nazw: docs/GRAPHICS.md.
     */
    private void applyOverrides(Pixmap pm, TileRegistry tileRegistry, ItemRegistry itemRegistry, List<MonsterType> mtypes,
                                List<pl.fawizja.pixel.npc.Npc> npcList) {
        if (Gdx.files == null) return;
        try {
            overrideAll(pm, tileRegistry, itemRegistry, mtypes, npcList);
        } finally {
            if (exportDir != null) exportNamed(pm);
        }
    }

    private void exportNamed(Pixmap pm) {
        for (Map.Entry<String, Integer> e : namedCells.entrySet()) {
            Pixmap one = new Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888);
            one.setBlending(Pixmap.Blending.None);
            one.drawPixmap(pm, cellX(e.getValue()), cellY(e.getValue()), SIZE, SIZE, 0, 0, SIZE, SIZE);
            com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(exportDir + "/" + e.getKey() + ".png"), one);
            one.dispose();
        }
        Gdx.app.log("TileArt", "Zapisano " + namedCells.size() + " obrazków do " + exportDir);
    }

    private void overrideAll(Pixmap pm, TileRegistry tileRegistry, ItemRegistry itemRegistry, List<MonsterType> mtypes,
                             List<pl.fawizja.pixel.npc.Npc> npcList) {
        for (TileType t : tileRegistry.all()) override(pm, t.id(), "tiles/" + t.key());
        for (ItemType it : itemRegistry.all()) override(pm, ITEM_BASE + it.id(), "items/" + it.key());
        for (Vocation v : Vocation.values()) {
            for (int d = 0; d < 4; d++) {
                if (override(pm, SPRITE_BASE + v.ordinal() * 4 + d, "characters/" + v.name().toLowerCase(java.util.Locale.ROOT) + "_" + DIRS[d])) {
                    drawnCharacters[v.ordinal()] = true;
                }
            }
        }
        for (int i = 0; i < mtypes.size(); i++) override(pm, MONSTER_BASE + i, "monsters/" + mtypes.get(i).key());
        for (int i = 0; i < npcList.size(); i++) override(pm, NPC_BASE + i, "npcs/" + npcList.get(i).key());
        for (Vocation v : Vocation.values()) {
            for (String d : DIRS) loadWalk("characters/" + v.name().toLowerCase(java.util.Locale.ROOT) + "_" + d);
        }
        for (MonsterType m : mtypes) loadWalk("monsters/" + m.key());
        for (TileType t : tileRegistry.all()) loadWallPieces(t);
        for (TileType t : tileRegistry.all()) loadVariants(t);
        override(pm, HORSE_CELL, "objects/horse_right");
        override(pm, HORSE_CELL + 1, "objects/horse_left");
        override(pm, CHEST_CELL, "objects/chest_closed");
        override(pm, CHEST_CELL + 1, "objects/chest_open");
        override(pm, DUMMY_CELL, "objects/training_dummy");
        override(pm, TRAP_CELL, "objects/spike_trap");
        override(pm, ROOF_CELL, "objects/roof_red");
        override(pm, ROOF_CELL + 1, "objects/roof_slate");
        override(pm, ROOF_CELL + 2, "objects/roof_sand");
        override(pm, ROOF_EXTRA_CELL, "objects/roof_wood");
        override(pm, ROOF_EXTRA_CELL + 1, "objects/roof_brown");
        override(pm, BACKPACK_CELL, "objects/backpack");
        for (int i = 0; i < PETS.length; i++) override(pm, PET_CELL + i, "pets/" + PETS[i]);
        if (overrides > 0) Gdx.app.log("TileArt", "Grafiki z assets/art: " + overrides);
    }

    private int overrides;
    /** Profesje z postacia podmieniona plikiem PNG: kolorowe stroje z kodu do niej nie pasuja, wiec ich nie uzywamy. */
    private final boolean[] drawnCharacters = new boolean[Vocation.values().length];

    /** Ile obrazkow podmieniono plikami PNG (diagnostyka). */
    public int overrides() { return overrides; }

    /** Narzedzie dla grafikow (desktop -Dpixel.exportArt=katalog): zapisuje obecne obrazki pod nazwami z docs/GRAPHICS.md. */
    public static String exportDir;
    private final Map<String, Integer> namedCells = new java.util.LinkedHashMap<>();

    private boolean override(Pixmap pm, int cell, String name) {
        namedCells.put(name, cell);
        com.badlogic.gdx.files.FileHandle f = Gdx.files.internal("art/" + name + ".png");
        if (!f.exists()) f = Gdx.files.internal("assets/art/" + name + ".png"); // desktop uruchamiany z katalogu repozytorium
        if (!f.exists()) return false;
        try {
            Pixmap src = new Pixmap(f);
            pm.setColor(0, 0, 0, 0);
            pm.fillRectangle(cellX(cell), cellY(cell), SIZE, SIZE);
            pm.setFilter(Pixmap.Filter.NearestNeighbour);
            pm.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(), cellX(cell), cellY(cell), SIZE, SIZE);
            if (src.getWidth() > SIZE) putHi(cell, src);
            src.dispose();
            overrides++;
            return true;
        } catch (RuntimeException e) {
            Gdx.app.error("TileArt", "Nie można wczytać " + f.path() + ": " + e.getMessage());
            return false;
        }
    }

    private void putHi(int cell, Pixmap src) {
        if (hiCells.size() >= HI_COLS * HI_COLS) return;
        if (hiPm == null) {
            hiPm = new Pixmap(HI_COLS * HI_SIZE, HI_COLS * HI_SIZE, Pixmap.Format.RGBA8888);
            hiPm.setBlending(Pixmap.Blending.None);
            hiPm.setFilter(Pixmap.Filter.NearestNeighbour);
        }
        int slot = hiCells.size();
        hiCells.put(cell, slot);
        hiPm.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(),
                (slot % HI_COLS) * HI_SIZE, (slot / HI_COLS) * HI_SIZE, HI_SIZE, HI_SIZE);
    }

    /** Ksztalty murow jak w Tibii (pliki walls/klucz_ksztalt.png, 64x64): pionowy, poziomy, slupek, naroznik. */
    public static final String[] WALL_SHAPES = {"v", "h", "pole", "corner"};
    public static final int WALL_V = 0, WALL_H = 1, WALL_POLE = 2, WALL_CORNER = 3;
    /** Odcinki z oknem (pliki walls/klucz_vwin.png i _hwin.png, opcjonalne): pionowy i poziomy. */
    public static final int WALL_V_WINDOW = 4, WALL_H_WINDOW = 5;
    private static final String[] WINDOW_SHAPES = {"vwin", "hwin"};
    /** Okna otwarte i drzwi w murze (zamkniete / otwarte, pionowe / poziome), pliki walls/klucz_ksztalt.png. */
    public static final int WALL_V_WINDOW_OPEN = 6, WALL_H_WINDOW_OPEN = 7, DOOR_V = 8, DOOR_V_OPEN = 9, DOOR_H = 10, DOOR_H_OPEN = 11;
    private static final String[] OPENING_SHAPES = {"vwin_open", "hwin_open", "door_v", "door_vopen", "door_h", "door_hopen"};
    private final java.util.Set<Integer> wallDoors = new java.util.HashSet<>();
    private static final int WALL_PX = 64, WALL_COLS = 24;
    private Pixmap wallPm;
    private Texture wallAtlas;
    private final Map<Integer, int[]> wallSlots = new HashMap<>();
    private final Map<Integer, TextureRegion[]> wallPieces = new HashMap<>();
    private final Map<Integer, int[][]> wallVariantSlots = new HashMap<>();
    private final Map<Integer, TextureRegion[][]> wallVariants = new HashMap<>();
    private int wallSlotCount;
    /** Odmiany murow budynkow: ksztalty v, h, slupek, naroznik, okna zamkniete i otwarte. */
    private static final String[] ALT_SHAPES = {"v", "h", "pole", "corner", "vwin", "hwin", "vwin_open", "hwin_open"};
    private final Map<Integer, List<int[]>> wallAltSlots = new HashMap<>();
    private final Map<Integer, TextureRegion[][]> wallAlts = new HashMap<>();

    /** Wczytuje komplet ksztaltow muru (wszystkie cztery albo zaden - wtedy mur zostaje bryla). */
    private void loadWallPieces(TileType t) {
        com.badlogic.gdx.files.FileHandle[] files = new com.badlogic.gdx.files.FileHandle[WALL_SHAPES.length];
        for (int k = 0; k < files.length; k++) {
            String name = "walls/" + t.key() + "_" + WALL_SHAPES[k] + ".png";
            files[k] = Gdx.files.internal("art/" + name);
            if (!files[k].exists()) files[k] = Gdx.files.internal("assets/art/" + name);
            if (!files[k].exists()) return;
        }
        com.badlogic.gdx.files.FileHandle[] windows = new com.badlogic.gdx.files.FileHandle[WINDOW_SHAPES.length];
        for (int k = 0; k < windows.length; k++) {
            String name = "walls/" + t.key() + "_" + WINDOW_SHAPES[k] + ".png";
            windows[k] = Gdx.files.internal("art/" + name);
            if (!windows[k].exists()) windows[k] = Gdx.files.internal("assets/art/" + name);
            if (!windows[k].exists()) windows[k] = files[k]; // bez okien: zwykly odcinek
        }
        files = java.util.Arrays.copyOf(files, files.length + windows.length);
        System.arraycopy(windows, 0, files, WALL_SHAPES.length, windows.length);
        // okna otwarte (bez nich: zamkniete) i drzwi (bez nich: stare drzwi jako obiekt)
        com.badlogic.gdx.files.FileHandle[] openings = new com.badlogic.gdx.files.FileHandle[OPENING_SHAPES.length];
        boolean doorsFound = true;
        for (int k = 0; k < openings.length; k++) {
            String name = "walls/" + t.key() + "_" + OPENING_SHAPES[k] + ".png";
            openings[k] = Gdx.files.internal("art/" + name);
            if (!openings[k].exists()) openings[k] = Gdx.files.internal("assets/art/" + name);
            if (!openings[k].exists()) {
                if (k >= 2) doorsFound = false;
                openings[k] = k < 2 ? files[WALL_SHAPES.length + k] : files[k % 2 == 0 ? WALL_V : WALL_H];
            }
        }
        if (doorsFound) wallDoors.add(t.id());
        int before = files.length;
        files = java.util.Arrays.copyOf(files, before + openings.length);
        System.arraycopy(openings, 0, files, before, openings.length);
        if (wallSlotCount + files.length > WALL_COLS * WALL_COLS) return;
        try {
            if (wallPm == null) {
                wallPm = new Pixmap(WALL_COLS * WALL_PX, WALL_COLS * WALL_PX, Pixmap.Format.RGBA8888);
                wallPm.setBlending(Pixmap.Blending.None);
                wallPm.setFilter(Pixmap.Filter.NearestNeighbour);
            }
            int[] slots = new int[files.length];
            for (int k = 0; k < files.length; k++) {
                Pixmap src = new Pixmap(files[k]);
                slots[k] = wallSlotCount + k;
                wallPm.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(),
                        (slots[k] % WALL_COLS) * WALL_PX, (slots[k] / WALL_COLS) * WALL_PX, WALL_PX, WALL_PX);
                src.dispose();
            }
            wallSlotCount += files.length;
            wallSlots.put(t.id(), slots);
            // warianty ksztaltow (gory z paczki maja po kilka odcinkow): walls/klucz_ksztalt_N.png
            int[][] extra = new int[WALL_SHAPES.length][];
            for (int k = 0; k < WALL_SHAPES.length; k++) {
                List<Integer> more = new ArrayList<>();
                for (int n = 1; n < 8; n++) {
                    Pixmap src = loadArt("walls/" + t.key() + "_" + WALL_SHAPES[k] + "_" + n + ".png");
                    if (src == null || wallSlotCount >= WALL_COLS * WALL_COLS) {
                        if (src != null) src.dispose();
                        break;
                    }
                    wallPm.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(),
                            (wallSlotCount % WALL_COLS) * WALL_PX, (wallSlotCount / WALL_COLS) * WALL_PX, WALL_PX, WALL_PX);
                    src.dispose();
                    more.add(wallSlotCount++);
                }
                extra[k] = more.stream().mapToInt(Integer::intValue).toArray();
            }
            wallVariantSlots.put(t.id(), extra);
            // odmiany muru dla calych budynkow: walls/klucz_altN_ksztalt.png (8 ksztaltow, ALT_SHAPES)
            List<int[]> alts = new ArrayList<>();
            for (int n = 1; n <= 4; n++) {
                if (wallSlotCount + ALT_SHAPES.length > WALL_COLS * WALL_COLS) break;
                Pixmap[] src = new Pixmap[ALT_SHAPES.length];
                boolean all = true;
                for (int k = 0; k < src.length; k++) {
                    src[k] = loadArt("walls/" + t.key() + "_alt" + n + "_" + ALT_SHAPES[k] + ".png");
                    if (src[k] == null) all = false;
                }
                if (!all) {
                    for (Pixmap p : src) if (p != null) p.dispose();
                    break;
                }
                int[] sl = new int[src.length];
                for (int k = 0; k < src.length; k++) {
                    sl[k] = wallSlotCount++;
                    wallPm.drawPixmap(src[k], 0, 0, src[k].getWidth(), src[k].getHeight(),
                            (sl[k] % WALL_COLS) * WALL_PX, (sl[k] / WALL_COLS) * WALL_PX, WALL_PX, WALL_PX);
                    src[k].dispose();
                }
                alts.add(sl);
            }
            if (!alts.isEmpty()) wallAltSlots.put(t.id(), alts);
        } catch (RuntimeException e) {
            Gdx.app.error("TileArt", "Nie można wczytać murów " + t.key() + ": " + e.getMessage());
        }
    }

    /** Komorki wariantow (tylko w atlasie 32 px). */
    private static final int VARIANT_BASE = 1 << 21;
    private final Map<Integer, int[]> variantCells = new HashMap<>();
    private int variantCount;
    private final Map<Integer, TextureRegion[]> variants = new HashMap<>();
    /**
     * Podloza bez wyraznego wzoru: odbicia w poziomie i pionie wg pola, zeby trawa czy piasek nie ukladaly sie
     * w powtarzalna kratke (wzory z cegiel, desek i plyt zostaja bez zmian).
     */
    private static final java.util.Set<String> FLIPPED_GROUNDS = java.util.Set.of("grass", "dirt", "sand", "snow",
            "jungle_grass", "swamp", "ash", "dark_sand", "blighted_grass", "cave_floor", "field", "abyss_floor");

    /** Warianty grafiki tiles/klucz_v2.png ... _v17.png (np. rozne gatunki drzew), wybierane wg pozycji pola. */
    private void loadVariants(TileType t) {
        List<Integer> cells = new ArrayList<>();
        for (int k = 2; k <= 17; k++) {
            String name = "tiles/" + t.key() + "_v" + k + ".png";
            com.badlogic.gdx.files.FileHandle f = Gdx.files.internal("art/" + name);
            if (!f.exists()) f = Gdx.files.internal("assets/art/" + name);
            if (!f.exists()) break;
            try {
                Pixmap src = new Pixmap(f);
                int cell = VARIANT_BASE + variantCount++;
                putHi(cell, src);
                src.dispose();
                cells.add(cell);
            } catch (RuntimeException e) {
                Gdx.app.error("TileArt", "Nie można wczytać " + f.path() + ": " + e.getMessage());
            }
        }
        if (!cells.isEmpty()) variantCells.put(t.id(), cells.stream().mapToInt(Integer::intValue).toArray());
    }

    private void buildVariants(TileRegistry tileRegistry) {
        for (TileType t : tileRegistry.all()) {
            TextureRegion base = tiles[t.id()];
            int[] cells = variantCells.get(t.id());
            if (cells != null && hiAtlas != null) {
                TextureRegion[] v = new TextureRegion[cells.length + 1];
                v[0] = base;
                for (int k = 0; k < cells.length; k++) v[k + 1] = hiCells.containsKey(cells[k]) ? region(cells[k]) : base;
                variants.put(t.id(), v);
            } else if (t.layer() == pl.fawizja.pixel.world.Layer.GROUND && FLIPPED_GROUNDS.contains(t.key())) {
                TextureRegion[] v = new TextureRegion[4];
                for (int k = 0; k < 4; k++) {
                    v[k] = new TextureRegion(base);
                    v[k].flip((k & 1) != 0, (k & 2) != 0);
                }
                variants.put(t.id(), v);
            }
        }
    }

    /** Grafika pola z wariantem wg pozycji (stala dla danego pola, rozna dla sasiadow). */
    public TextureRegion tile(int id, int x, int y) {
        TextureRegion[] b = id > 0 && id < bigTiles.length ? bigTiles[id] : null;
        if (b != null) return b[spread(x, y) % b.length];
        TextureRegion[] v = variants.get(id);
        if (v == null) return tile(id);
        return v[spread(x, y) % v.length];
    }

    /** Rozrzut pozycji pola na liczbe nieujemna (bez widocznych powtorzen w rzedach i kolumnach). */
    public static int spread(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /** Liczba odmian muru dla budynkow (0 = tylko podstawowy). */
    public int wallAltCount(int tileId) {
        TextureRegion[][] a = wallAlts.get(tileId);
        return a == null ? 0 : a.length;
    }

    /**
     * Ksztalt odmiany muru (alt od 1): WALL_V, WALL_H, WALL_POLE, WALL_CORNER, WALL_V_WINDOW, WALL_H_WINDOW,
     * WALL_V_WINDOW_OPEN, WALL_H_WINDOW_OPEN; null dla alt 0 albo braku odmiany.
     */
    public TextureRegion wallAltPiece(int tileId, int alt, int shape) {
        TextureRegion[][] a = wallAlts.get(tileId);
        if (alt <= 0 || a == null || alt > a.length || shape < 0 || shape >= a[alt - 1].length) return null;
        return a[alt - 1][shape];
    }

    /** Czy mur ma wlasne drzwi z paczki (DOOR_V ... DOOR_H_OPEN). */
    public boolean hasWallDoors(int tileId) { return wallDoors.contains(tileId); }

    /** Ksztalt muru jak w Tibii (2x2 pola, prawy dolny = pole muru) albo null, gdy mur jest rysowany bryla. */
    public TextureRegion wallPiece(int tileId, int shape) {
        TextureRegion[] p = wallPieces.get(tileId);
        return p == null ? null : p[shape];
    }

    /** Ksztalt muru w wariancie dla pola (x, y): odcinki gor zmieniaja sie wzdluz grzbietu jak w Tibii. */
    public TextureRegion wallPiece(int tileId, int shape, int x, int y) {
        TextureRegion base = wallPiece(tileId, shape);
        TextureRegion[][] v = wallVariants.get(tileId);
        if (base == null || v == null || shape >= v.length || v[shape].length == 0) return base;
        int n = Math.floorMod(shape == WALL_V ? y : shape == WALL_H ? x : x * 7 + y * 13, v[shape].length + 1);
        return n == 0 ? base : v[shape][n - 1];
    }

    /** Komorki klatek kroku (tylko w atlasie 32 px): numery poza atlasem 16 px. */
    private static final int WALK_BASE = 1 << 20;
    private final Map<String, Integer> walkCells = new HashMap<>();
    private final Map<String, TextureRegion[]> walk = new HashMap<>();

    /** Klatki kroku name_walk1/name_walk2 z assets/art (obie albo zadna). */
    private void loadWalk(String name) {
        int[] cells = new int[2];
        for (int k = 0; k < 2; k++) {
            com.badlogic.gdx.files.FileHandle f = Gdx.files.internal("art/" + name + "_walk" + (k + 1) + ".png");
            if (!f.exists()) f = Gdx.files.internal("assets/art/" + name + "_walk" + (k + 1) + ".png");
            if (!f.exists()) return;
            cells[k] = WALK_BASE + walkCells.size() * 2 + k;
        }
        try {
            for (int k = 0; k < 2; k++) {
                com.badlogic.gdx.files.FileHandle f = Gdx.files.internal("art/" + name + "_walk" + (k + 1) + ".png");
                if (!f.exists()) f = Gdx.files.internal("assets/art/" + name + "_walk" + (k + 1) + ".png");
                Pixmap src = new Pixmap(f);
                putHi(cells[k], src);
                src.dispose();
            }
            walkCells.put(name, cells[0]);
        } catch (RuntimeException e) {
            Gdx.app.error("TileArt", "Nie można wczytać klatek kroku " + name + ": " + e.getMessage());
        }
    }

    /**
     * Klatka chodu jak w Tibii: 0 = postoj, 1 i 2 = kroki. Bez plikow klatek zawsze postoj.
     * Kroki zmieniaja sie w polowie przejscia pola i na kolejnych polach (lewa, prawa noga).
     */
    public static int walkFrame(float renderX, float renderY, int tileX, int tileY) {
        float progress = Math.max(Math.abs(renderX - tileX), Math.abs(renderY - tileY));
        if (progress < 0.02f) return 0;
        return 1 + ((tileX + tileY + (progress < 0.5f ? 0 : 1)) & 1);
    }

    private TextureRegion walkOr(String name, TextureRegion idle, int frame) {
        if (frame <= 0) return idle;
        TextureRegion[] w = walk.get(name);
        return w == null ? idle : w[Math.min(frame, 2) - 1];
    }

    private static int cellX(int cell) { return (cell % COLS) * SIZE; }
    private static int cellY(int cell) { return (cell / COLS) * SIZE; }

    /** Atlas grafik wiekszych niz 16 px (rozmiar komorki 32), tworzony tylko gdy takie pliki istnieja. */
    private static final int HI_SIZE = 32, HI_COLS = 32;
    private Pixmap hiPm;
    private Texture hiAtlas;
    private final Map<Integer, Integer> hiCells = new java.util.HashMap<>();

    /** Ile obrazkow trzymanych w pelnej rozdzielczosci 32 px (diagnostyka). */
    public int hiResOverrides() { return hiCells.size(); }

    private TextureRegion region(int cell) {
        Integer slot = hiCells.get(cell);
        if (slot != null && hiAtlas != null) {
            return new TextureRegion(hiAtlas, (slot % HI_COLS) * HI_SIZE, (slot / HI_COLS) * HI_SIZE, HI_SIZE, HI_SIZE);
        }
        return new TextureRegion(atlas, cellX(cell), cellY(cell), SIZE, SIZE);
    }

    /** Obrzeza podloza (trawa na ziemi, piasek na wodzie...). */
    /**
     * Autokafelki podloza (autotiles/klucz.png z tools/art/wst_import.py): 20 cwiartek 16x16, wiersz = cwiartka
     * pola (TL, TR, BL, BR), kolumna = rodzaj (rog zewnetrzny, krawedz gora/dol, krawedz z boku, rog wewnetrzny,
     * srodek). Wszystkie w jednej teksturze.
     */
    private void loadAutotiles(TileRegistry registry) {
        java.util.List<TileType> found = new ArrayList<>();
        java.util.List<Pixmap> sheets = new ArrayList<>();
        for (TileType t : registry.all()) {
            if (t.layer() != pl.fawizja.pixel.world.Layer.GROUND) continue;
            com.badlogic.gdx.files.FileHandle f = Gdx.files.internal("art/autotiles/" + t.key() + ".png");
            if (!f.exists()) f = Gdx.files.internal("assets/art/autotiles/" + t.key() + ".png");
            if (!f.exists()) continue;
            try {
                sheets.add(new Pixmap(f));
                found.add(t);
            } catch (RuntimeException e) {
                Gdx.app.error("TileArt", "Nie można wczytać " + f.path() + ": " + e.getMessage());
            }
        }
        autotiles = new TextureRegion[registry.maxId() + 1][];
        if (found.isEmpty()) return;
        Pixmap pm = new Pixmap(80, 64 * found.size(), Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int i = 0; i < found.size(); i++) {
            Pixmap src = sheets.get(i);
            pm.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(), 0, i * 64, 80, 64);
            src.dispose();
        }
        autoAtlas = new Texture(pm);
        autoAtlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        for (int i = 0; i < found.size(); i++) {
            TextureRegion[] q = new TextureRegion[20];
            for (int k = 0; k < 20; k++) q[k] = new TextureRegion(autoAtlas, (k % 5) * 16, i * 64 + (k / 5) * 16, 16, 16);
            autotiles[found.get(i).id()] = q;
        }
    }

    /** Kafelek 32x32 z atlasu wysokiej rozdzielczosci albo powiekszony z atlasu 16 px. */
    private Pixmap pixmap32(Pixmap base, int cell) {
        Pixmap out = new Pixmap(HI_SIZE, HI_SIZE, Pixmap.Format.RGBA8888);
        out.setBlending(Pixmap.Blending.None);
        out.setFilter(Pixmap.Filter.NearestNeighbour);
        Integer slot = hiCells.get(cell);
        if (slot != null && hiPm != null) {
            out.drawPixmap(hiPm, (slot % HI_COLS) * HI_SIZE, (slot / HI_COLS) * HI_SIZE, HI_SIZE, HI_SIZE, 0, 0, HI_SIZE, HI_SIZE);
        } else {
            out.drawPixmap(base, cellX(cell), cellY(cell), SIZE, SIZE, 0, 0, HI_SIZE, HI_SIZE);
        }
        return out;
    }

    /** Odsloniete boki skaly w masce {@link #rockShape}. */
    public static final int ROCK_N = 1, ROCK_E = 2, ROCK_S = 4, ROCK_W = 8;
    private static final int ROCK_R = 14;
    private TextureRegion[][] rockShapes;
    private Texture rockAtlas;

    /**
     * Zaokraglone skaly (gory, jaskinie, klify): dla kazdej skaly 16 wersji wg odslonietych bokow. Odsloniety bok
     * dostaje nierowna krawedz, rog miedzy dwoma odslonietymi bokami - luk, a brzeg jest przyciemniony, zeby
     * lita skala nie wygladala jak kwadratowe pola.
     */
    private void loadRockShapes(TileRegistry registry, Pixmap base) {
        java.util.List<TileType> rocks = new ArrayList<>();
        for (TileType t : registry.all()) {
            if (t.layer() == pl.fawizja.pixel.world.Layer.WALL && "rockwall".equals(t.pattern())) rocks.add(t);
        }
        rockShapes = new TextureRegion[registry.maxId() + 1][];
        if (rocks.isEmpty()) return;
        Pixmap pm = new Pixmap(16 * HI_SIZE, rocks.size() * HI_SIZE, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int i = 0; i < rocks.size(); i++) {
            Pixmap src = pixmap32(base, rocks.get(i).id());
            for (int mask = 0; mask < 16; mask++) {
                for (int y = 0; y < HI_SIZE; y++) {
                    for (int x = 0; x < HI_SIZE; x++) {
                        float d = rockInside(mask, x + 0.5f, y + 0.5f);
                        int c = src.getPixel(x, y);
                        if (d < 0) c = 0;
                        else if (d < 2.2f) c = shadeRgba(c, d < 1.1f ? 0.55f : 0.75f);
                        else if (d < 4f && rockLit(mask, x + 0.5f, y + 0.5f)) c = shadeRgba(c, 1.18f);
                        pm.drawPixel(mask * HI_SIZE + x, i * HI_SIZE + y, c);
                    }
                }
            }
            src.dispose();
        }
        rockAtlas = new Texture(pm);
        rockAtlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        for (int i = 0; i < rocks.size(); i++) {
            TextureRegion[] v = new TextureRegion[16];
            for (int mask = 0; mask < 16; mask++) v[mask] = new TextureRegion(rockAtlas, mask * HI_SIZE, i * HI_SIZE, HI_SIZE, HI_SIZE);
            rockShapes[rocks.get(i).id()] = v;
        }
    }

    /** Odleglosc piksela od brzegu skaly w masce (ujemna = poza skala). */
    static float rockInside(int mask, float x, float y) {
        float n = HI_SIZE, d = Float.MAX_VALUE;
        boolean north = (mask & ROCK_N) != 0, east = (mask & ROCK_E) != 0, south = (mask & ROCK_S) != 0, west = (mask & ROCK_W) != 0;
        if (north) d = Math.min(d, y - wobble(x, 0));
        if (south) d = Math.min(d, n - y - wobble(x, 1));
        if (west) d = Math.min(d, x - wobble(y, 2));
        if (east) d = Math.min(d, n - x - wobble(y, 3));
        float r = ROCK_R;
        if (north && west) d = Math.min(d, corner(x, y, r, r, r));
        if (north && east) d = Math.min(d, corner(x, y, n - r, r, r));
        if (south && west) d = Math.min(d, corner(x, y, r, n - r, r));
        if (south && east) d = Math.min(d, corner(x, y, n - r, n - r, r));
        return d;
    }

    /** Wglebienie nierownej krawedzi: zero na koncach boku (sasiednie pola sie stykaja), do ~3 px w srodku. */
    private static float wobble(float t, int side) {
        double u = t / HI_SIZE;
        double bump = Math.sin(Math.PI * u);
        return (float) (bump * (1.8 + 1.4 * Math.sin(u * Math.PI * 2 + side * 1.7)));
    }

    private static float corner(float x, float y, float cx, float cy, float r) {
        boolean inX = (cx < HI_SIZE / 2f) ? x < cx : x > cx;
        boolean inY = (cy < HI_SIZE / 2f) ? y < cy : y > cy;
        if (!inX || !inY) return Float.MAX_VALUE;
        return r - (float) Math.hypot(x - cx, y - cy);
    }

    /** Brzeg od polnocy i zachodu jest oswietlony, reszta w cieniu. */
    private static boolean rockLit(int mask, float x, float y) {
        return ((mask & ROCK_N) != 0 && y < HI_SIZE / 2f) || ((mask & ROCK_W) != 0 && x < HI_SIZE / 2f);
    }

    private static int shadeRgba(int c, float f) {
        int r = Math.min(255, (int) (((c >>> 24) & 0xff) * f));
        int g = Math.min(255, (int) (((c >>> 16) & 0xff) * f));
        int b = Math.min(255, (int) (((c >>> 8) & 0xff) * f));
        return (r << 24) | (g << 16) | (b << 8) | (c & 0xff);
    }

    /** Skala o zaokraglonych odslonietych bokach (maska ROCK_*) albo null, gdy kafelek nie jest skala. */
    public TextureRegion rockShape(int tileId, int mask) {
        if (rockShapes == null || tileId <= 0 || tileId >= rockShapes.length || rockShapes[tileId] == null) return null;
        return rockShapes[tileId][mask & 15];
    }

    /** Cwiartki autokafelka podloza (indeks = wiersz * 5 + rodzaj) albo null, gdy podloze go nie ma. */
    public TextureRegion[] autotile(int tileId) {
        return autotiles != null && tileId > 0 && tileId < autotiles.length ? autotiles[tileId] : null;
    }

    private Texture autoAtlas;
    private TextureRegion[][] autotiles;

    /** Efekty czarow (klatki effects/nazwa_N.png) i pociski (missiles/nazwa_XY.png) z paczki OTSP. */
    public static final String[] EFFECT_NAMES = {"blood", "energy", "poof", "sparks", "fire", "water", "earth", "dust",
            "flame", "explosion", "ice", "holy", "death", "heal", "haste", "sparkle", "magic", "stones",
            // kaluze krwi i ciala (jedna klatka)
            "blood_pool", "blood_small", "blood_splash", "blood_drops", "slime_pool", "slime_splash",
            "corpse_human", "corpse_human_old", "corpse_bones", "corpse_goblin", "corpse_beast", "corpse_centipede",
            "corpse_wasp", "corpse_beetle"};
    public static final String[] MISSILE_NAMES = {"arrow", "spear", "fire", "ice", "earth", "energy", "stone", "star",
            "knife", "holy", "death", "arrow_red", "bolt", "star_small", "fire_arrow", "poison", "shadow_star"};
    private static final int FX = 64, FX_COLS = 16;
    private Texture fxAtlas;
    private final Map<String, TextureRegion[]> effects = new java.util.HashMap<>();
    private final Map<String, TextureRegion[]> missiles = new java.util.HashMap<>();

    private void loadEffects() {
        List<String> keys = new ArrayList<>();
        List<Pixmap> images = new ArrayList<>();
        for (String name : EFFECT_NAMES) {
            for (int f = 0; f < 32; f++) {
                Pixmap p = loadArt("effects/" + name + "_" + f + ".png");
                if (p == null) break;
                keys.add("e:" + name + ":" + f);
                images.add(p);
            }
        }
        for (String name : MISSILE_NAMES) {
            for (int k = 0; k < 9; k++) {
                if (k == 4) continue;
                Pixmap p = loadArt("missiles/" + name + "_" + (k % 3) + (k / 3) + ".png");
                if (p == null) continue;
                keys.add("m:" + name + ":" + k);
                images.add(p);
            }
        }
        if (images.isEmpty()) return;
        int rows = (images.size() + FX_COLS - 1) / FX_COLS;
        Pixmap pm = new Pixmap(FX_COLS * FX, rows * FX, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        int[] sizes = new int[images.size()];
        for (int i = 0; i < images.size(); i++) {
            Pixmap src = images.get(i);
            sizes[i] = Math.min(FX, src.getWidth());
            pm.drawPixmap(src, 0, 0, sizes[i], sizes[i], (i % FX_COLS) * FX, (i / FX_COLS) * FX, sizes[i], sizes[i]);
            src.dispose();
        }
        fxAtlas = new Texture(pm);
        fxAtlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        for (int i = 0; i < keys.size(); i++) {
            String[] k = keys.get(i).split(":");
            TextureRegion r = new TextureRegion(fxAtlas, (i % FX_COLS) * FX, (i / FX_COLS) * FX, sizes[i], sizes[i]);
            Map<String, TextureRegion[]> into = k[0].equals("e") ? effects : missiles;
            int idx = Integer.parseInt(k[2]);
            TextureRegion[] arr = into.get(k[1]);
            if (arr == null || arr.length <= idx) {
                TextureRegion[] grown = new TextureRegion[k[0].equals("m") ? 9 : idx + 1];
                if (arr != null) System.arraycopy(arr, 0, grown, 0, arr.length);
                arr = grown;
                into.put(k[1], arr);
            }
            arr[idx] = r;
        }
    }

    private Pixmap loadArt(String name) {
        com.badlogic.gdx.files.FileHandle f = Gdx.files.internal("art/" + name);
        if (!f.exists()) f = Gdx.files.internal("assets/art/" + name);
        if (!f.exists()) return null;
        try {
            return new Pixmap(f);
        } catch (RuntimeException e) {
            Gdx.app.error("TileArt", "Nie można wczytać " + f.path() + ": " + e.getMessage());
            return null;
        }
    }

    /** Klatki efektu (32 albo 64 px; 64 = efekt na 2x2 pola, prawy dolny kwadrat na polu) albo null. */
    public TextureRegion[] effect(String name) { return effects.get(name); }

    /** Pocisk lecacy w kierunku (dx, dy w ukladzie mapy, -1..1) albo null. */
    public TextureRegion missile(String name, int dx, int dy) {
        TextureRegion[] m = missiles.get(name);
        if (m == null) return null;
        int k = (Integer.signum(dx) + 1) + (Integer.signum(dy) + 1) * 3;
        return k == 4 ? m[5] : m[k];
    }

    /** Nazwy kawalkow obrzezy w kolejnosci GroundBorders.N..IN_NW (nazwy krawedzi jak w RME). */
    private static final String[] BORDER_EDGES = {"n", "e", "s", "w", "cne", "cse", "csw", "cnw", "dne", "dse", "dsw", "dnw"};

    public GroundBorders borders() { return borders; }
    private GroundBorders borders;

    public TextureRegion tile(int id) {
        if (id > 0 && id < bigTiles.length && bigTiles[id] != null) return bigTiles[id][0];
        return id > 0 && id < tiles.length ? tiles[id] : null;
    }

    /**
     * Obiekty w naturalnym rozmiarze jak w Tibii (native/klucz.png i native/klucz_vN.png, plotno 64x64 z obiektem
     * przy prawym dolnym rogu): rysowane 2x2 pola od prawego dolnego rogu pola, bez sciskania do 32 px.
     * Indeks wariantu jak w {@link #tile(int, int, int)}; brak pliku dla wariantu = zwykla grafika.
     */
    private TextureRegion[][] nativeTiles = new TextureRegion[0][];
    private TextureRegion[] nativeChest;
    private Texture nativeAtlas;
    /** Obrazy, gobeliny i tkaniny na murach wnetrz: poziome (mur polnocny) i pionowe (mur zachodni). */
    private final List<TextureRegion> decoH = new ArrayList<>(), decoV = new ArrayList<>();

    private void loadNative(TileRegistry registry) {
        nativeTiles = new TextureRegion[registry.maxId() + 1][];
        List<Pixmap> images = new ArrayList<>();
        List<int[]> slots = new ArrayList<>();   // {tileId (-1 = skrzynia), indeks wariantu}
        for (TileType t : registry.all()) {
            for (int k = 0; k <= 17; k++) {
                if (k == 1) continue;
                Pixmap p = loadArt("native/" + t.key() + (k == 0 ? "" : "_v" + k) + ".png");
                if (p == null) continue;
                images.add(p);
                slots.add(new int[] {t.id(), k == 0 ? 0 : k - 1});
            }
        }
        for (int k = 0; k < 2; k++) {
            Pixmap p = loadArt(k == 0 ? "native/chest_closed.png" : "native/chest_open.png");
            if (p == null) continue;
            images.add(p);
            slots.add(new int[] {-1, k});
        }
        for (int v = 0; v < 2; v++) {
            for (int k = 0; k < 16; k++) {
                Pixmap p = loadArt("native/wall_deco_" + (v == 0 ? "h_" : "v_") + k + ".png");
                if (p == null) break;
                images.add(p);
                slots.add(new int[] {-2 - v, k});
            }
        }
        if (images.isEmpty()) return;
        int rows = (images.size() + BIG_COLS - 1) / BIG_COLS;
        Pixmap pm = new Pixmap(BIG_COLS * BIG, rows * BIG, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int i = 0; i < images.size(); i++) {
            Pixmap src = images.get(i);
            pm.drawPixmap(src, 0, 0, Math.min(BIG, src.getWidth()), Math.min(BIG, src.getHeight()),
                    (i % BIG_COLS) * BIG, (i / BIG_COLS) * BIG, Math.min(BIG, src.getWidth()), Math.min(BIG, src.getHeight()));
            src.dispose();
        }
        nativeAtlas = new Texture(pm);
        nativeAtlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        for (int i = 0; i < slots.size(); i++) {
            int[] o = slots.get(i);
            TextureRegion r = new TextureRegion(nativeAtlas, (i % BIG_COLS) * BIG, (i / BIG_COLS) * BIG, BIG, BIG);
            if (o[0] < -1) {
                List<TextureRegion> deco = o[0] == -2 ? decoH : decoV;
                deco.add(r);
                continue;
            }
            if (o[0] < 0) {
                if (nativeChest == null) nativeChest = new TextureRegion[2];
                nativeChest[o[1]] = r;
                continue;
            }
            if (nativeTiles[o[0]] == null) nativeTiles[o[0]] = new TextureRegion[17];
            nativeTiles[o[0]][o[1]] = r;
        }
    }

    /** Obiekt w naturalnym rozmiarze dla pola (plotno 2x2 pola) albo null - wtedy zwykla grafika z tile(). */
    public TextureRegion nativeTile(int id, int x, int y) {
        TextureRegion[] n = id > 0 && id < nativeTiles.length ? nativeTiles[id] : null;
        if (n == null) return null;
        int count = 1;
        if (id < bigTiles.length && bigTiles[id] != null) count = bigTiles[id].length;
        else if (variants.get(id) != null) count = variants.get(id).length;
        int k = count == 1 ? 0 : spread(x, y) % count;
        return k < n.length ? n[k] : null;
    }

    /** Ozdoba muru wnetrza (plotno 2x2 pola) wybrana wg liczby albo null, gdy brak grafik. */
    public TextureRegion wallDeco(boolean vertical, int pick) {
        List<TextureRegion> d = vertical ? decoV : decoH;
        return d.isEmpty() ? null : d.get(Math.floorMod(pick, d.size()));
    }

    /** Skrzynia skarbow w naturalnym rozmiarze (plotno 2x2 pola) albo null. */
    public TextureRegion nativeChest(boolean open) { return nativeChest == null ? null : nativeChest[open ? 1 : 0]; }

    /** Duze obiekty (drzewa 64 px z paczki) w pelnej rozdzielczosci: klucz.png i warianty klucz_vN.png. */
    private TextureRegion[][] bigTiles = new TextureRegion[0][];
    private Texture bigAtlas;
    private static final int BIG = 64, BIG_COLS = 16;

    private void loadBigTiles(TileRegistry registry) {
        bigTiles = new TextureRegion[registry.maxId() + 1][];
        List<Pixmap> images = new ArrayList<>();
        List<int[]> owners = new ArrayList<>();   // {tileId, liczba obrazkow}
        for (TileType t : registry.all()) {
            Pixmap base = loadArt("tiles/" + t.key() + ".png");
            if (base == null) continue;
            if (base.getWidth() <= 40) {
                base.dispose();
                continue;
            }
            int count = 1;
            images.add(base);
            for (int k = 2; k <= 17; k++) {
                Pixmap p = loadArt("tiles/" + t.key() + "_v" + k + ".png");
                if (p == null) break;
                images.add(p);
                count++;
            }
            owners.add(new int[] {t.id(), count});
        }
        if (images.isEmpty()) return;
        int rows = (images.size() + BIG_COLS - 1) / BIG_COLS;
        Pixmap pm = new Pixmap(BIG_COLS * BIG, rows * BIG, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setFilter(Pixmap.Filter.NearestNeighbour);
        for (int i = 0; i < images.size(); i++) {
            Pixmap src = images.get(i);
            int w = Math.min(BIG, src.getWidth()), h = Math.min(BIG, src.getHeight());
            // mniejsze drzewo stoi posrodku na dole komorki (bez rozciagania)
            pm.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(),
                    (i % BIG_COLS) * BIG + (BIG - w) / 2, (i / BIG_COLS) * BIG + BIG - h, w, h);
            src.dispose();
        }
        bigAtlas = new Texture(pm);
        bigAtlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        int i = 0;
        for (int[] o : owners) {
            TextureRegion[] r = new TextureRegion[o[1]];
            for (int k = 0; k < o[1]; k++, i++) r[k] = new TextureRegion(bigAtlas, (i % BIG_COLS) * BIG, (i / BIG_COLS) * BIG, BIG, BIG);
            bigTiles[o[0]] = r;
        }
    }
    public TextureRegion item(int id) { return id > 0 && id < items.length ? items[id] : null; }
    public TextureRegion white() { return white; }
    public TextureRegion monster(String key) { return monsters.get(key); }

    /** Potwor w klatce chodu ({@link #walkFrame}). */
    public TextureRegion monster(String key, int frame) { return walkOr("monsters/" + key, monsters.get(key), frame); }
    public TextureRegion npc(String key) { return npcs.get(key); }

    /** Kon pod jezdzcem, zwrocony w strone ruchu (w lewo albo w prawo). */
    public TextureRegion horse(Direction facing) { return horse[facing != null && facing.dx < 0 ? 1 : 0]; }

    public TextureRegion chest(boolean open) { return chest[open ? 1 : 0]; }

    public TextureRegion dummy() { return dummy; }
    public TextureRegion trap() { return trap; }
    public TextureRegion backpack() { return backpack; }

    /** Dach nad budynkiem wg Roofs: 1 dachowka (drewno), 2 lupek (kamien), 3 piaskowa dachowka (piaskowiec),
     *  4 gont (mur pruski, bale), 5 brazowa dachowka (tynk). */
    public TextureRegion roof(int kind) { return roof[Math.max(1, Math.min(kind, roof.length)) - 1]; }

    /** Dach z brzegiem: top = kalenica (nad polem nie ma dachu), bottom = okap; bez osobnej grafiki zwykly dach. */
    public TextureRegion roof(int kind, boolean top, boolean bottom) {
        int k = Math.max(1, Math.min(kind, Roofs.KINDS)) - 1;
        TextureRegion[] p = roofPieces[k];
        if (p != null) {
            if (top && p[1] != null) return p[1];
            if (bottom && p[2] != null) return p[2];
            if (p[0] != null) return p[0];
        }
        return roof(kind);
    }

    /** Rodzaje dachu (kolejnosc jak Roofs.RED..ROYAL) i pliki objects/roof_klucz[_top|_bottom].png. */
    private static final String[] ROOF_KEYS = {"red", "slate", "sand", "wood", "brown", "snow", "orange", "royal"};
    private static final int ROOF_BASE = 1 << 22;
    private final TextureRegion[][] roofPieces = new TextureRegion[ROOF_KEYS.length][];

    private void loadRoofPieces() {
        String[] suffix = {"", "_top", "_bottom"};
        for (int k = 0; k < ROOF_KEYS.length; k++) {
            for (int p = 0; p < 3; p++) {
                String name = "objects/roof_" + ROOF_KEYS[k] + suffix[p] + ".png";
                com.badlogic.gdx.files.FileHandle f = Gdx.files.internal("art/" + name);
                if (!f.exists()) f = Gdx.files.internal("assets/art/" + name);
                if (!f.exists()) continue;
                try {
                    Pixmap src = new Pixmap(f);
                    putHi(ROOF_BASE + k * 3 + p, src);
                    src.dispose();
                } catch (RuntimeException e) {
                    Gdx.app.error("TileArt", "Nie można wczytać " + f.path() + ": " + e.getMessage());
                }
            }
        }
    }

    private void buildRoofPieces() {
        if (hiAtlas == null) return;
        for (int k = 0; k < ROOF_KEYS.length; k++) {
            TextureRegion[] p = new TextureRegion[3];
            boolean any = false;
            for (int i = 0; i < 3; i++) {
                if (hiCells.containsKey(ROOF_BASE + k * 3 + i)) {
                    p[i] = region(ROOF_BASE + k * 3 + i);
                    any = true;
                }
            }
            if (any) roofPieces[k] = p;
        }
    }

    /** Pupil po kluczu przedmiotu (null = nieznany). */
    public TextureRegion pet(String key) { return key == null ? null : pets.get(key); }
    public Texture lightTexture() { return lightTexture; }

    private final java.util.Map<Integer, TextureRegion> outfits = new java.util.HashMap<>();

    private static int outfitCell(Vocation v, int outfit, int dir) {
        return OUTFIT_BASE + (v.ordinal() * pl.fawizja.pixel.character.Outfit.count() + outfit) * 4 + dir;
    }

    /** Sprite postaci w kolorze stroju (0 = kolor profesji). */
    public TextureRegion character(Vocation v, Direction facing, int outfit) {
        TextureRegion base = character(v, facing);
        if (outfit <= 0 || outfit >= pl.fawizja.pixel.character.Outfit.count() || drawnCharacters[v.ordinal()]) return base;
        int d = 0;
        for (int i = 0; i < 4; i++) if (characters[v.ordinal()][i] == base) d = i;
        TextureRegion r = outfits.get(outfitCell(v, outfit, d));
        return r == null ? base : r;
    }

    /** Postac w klatce chodu ({@link #walkFrame}); stroje w kolorach z kodu nie maja klatek kroku. */
    public TextureRegion character(Vocation v, Direction facing, int outfit, int frame) {
        TextureRegion r = character(v, facing, outfit);
        if (frame <= 0 || r != character(v, facing)) return r;
        return walkOr("characters/" + v.name().toLowerCase(java.util.Locale.ROOT) + "_" + DIRS[dirIndex(facing)], r, frame);
    }

    private static int dirIndex(Direction facing) {
        if (facing == null) return 0;
        if (facing.dy < 0 && facing.dx == 0) return 1;
        if (facing.dx < 0) return 2;
        if (facing.dx > 0) return 3;
        return 0;
    }

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
            case "pine":
                rect(pm, ox, oy, 7, 12, 2, 4, Color.valueOf("#4a3020"));
                for (int k = 0; k < 3; k++) {
                    int top = 1 + k * 4, half = 2 + k * 2;
                    for (int y = 0; y < 5; y++) {
                        int hw = Math.max(1, half * (y + 1) / 5);
                        rect(pm, ox, oy, 8 - hw, top + y, hw * 2, 1, shade(c, 0.85f + y * 0.05f));
                    }
                    rect(pm, ox, oy, 8 - half / 2, top + 1, half / 2 + 1, 1, Color.valueOf("#f0f6fa")); // snieg
                }
                break;
            case "palm":
                for (int y = 6; y < 16; y++) px(pm, ox, oy, 7 + (y < 10 ? 1 : 0), y, Color.valueOf("#8a6a3a"));
                for (int k = -6; k <= 6; k++) {
                    int yy = 4 + Math.abs(k) / 2;
                    px(pm, ox, oy, 8 + k, yy, c);
                    px(pm, ox, oy, 8 + k, yy + 1, shade(c, 0.75f));
                }
                for (int k = 0; k < 4; k++) {
                    px(pm, ox, oy, 8, 1 + k, shade(c, 1.2f));
                    px(pm, ox, oy, 5 - k, 6 + k, shade(c, 0.9f));
                    px(pm, ox, oy, 11 + k, 6 + k, shade(c, 0.9f));
                }
                px(pm, ox, oy, 7, 6, Color.valueOf("#6a4a20"));
                px(pm, ox, oy, 9, 6, Color.valueOf("#6a4a20"));
                break;
            case "cactus":
                rect(pm, ox, oy, 6, 3, 4, 13, c);
                rect(pm, ox, oy, 7, 3, 1, 13, shade(c, 1.25f));
                rect(pm, ox, oy, 2, 6, 2, 5, c);
                rect(pm, ox, oy, 2, 10, 4, 2, c);
                rect(pm, ox, oy, 12, 4, 2, 5, c);
                rect(pm, ox, oy, 10, 8, 4, 2, c);
                px(pm, ox, oy, 8, 2, Color.valueOf("#f070a0"));
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
            case "rope_spot":
                // plama swiatla z otworu w stropie i zwisajaca lina
                disc(pm, ox, oy, 8f, 8.5f, 6.5f, shade(c, 0.75f));
                disc(pm, ox, oy, 8f, 8.5f, 4.5f, c);
                for (int y = 0; y < 13; y++) px(pm, ox, oy, 8 + (y / 2 % 2), y, Color.valueOf("#a07840"));
                break;
            case "magma":
                disc(pm, ox, oy, 8f, 8f, 7f, shade(c, 0.6f));
                disc(pm, ox, oy, 8f, 8f, 5f, c);
                for (int i = 0; i < 5; i++) px(pm, ox, oy, 4 + r.nextInt(8), 4 + r.nextInt(8), Color.valueOf("#ffe080"));
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

    private static final java.util.Set<String> FISH_KEYS = java.util.Set.of("fish", "ice_cod", "swamp_eel", "oasis_carp");

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
                if (FISH_KEYS.contains(it.key())) {
                    // ryba: tulow, ogon i oko
                    disc(pm, ox, oy, 7f, 9f, 3.5f, c);
                    disc(pm, ox, oy, 9.5f, 9f, 3f, c);
                    for (int i = 0; i < 4; i++) rect(pm, ox, oy, 12 + i, 7 - i / 2, 1, 4 + i, shade(c, 0.8f));
                    px(pm, ox, oy, 5, 8, dark);
                    break;
                }
                // fall through
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

    /** NPC: dluga szata w kolorze z npcs.csv, rola widoczna po dodatkach (kaplan - aureola, bankier - sakwa). */
    private void paintNpc(Pixmap pm, int ox, int oy, pl.fawizja.pixel.npc.Npc n) {
        Color robe = Color.valueOf(n.color());
        Color skin = Color.valueOf("#e8c09a");
        Color hair = n.role() == pl.fawizja.pixel.npc.NpcRole.PRIEST ? Color.valueOf("#d8d8d8") : Color.valueOf("#4a3020");
        Color outline = Color.valueOf("#1a1410");
        disc(pm, ox, oy, 8f, 14.5f, 4f, new Color(0, 0, 0, 0.35f));
        rect(pm, ox, oy, 4, 6, 8, 9, robe);
        rect(pm, ox, oy, 4, 14, 8, 1, shade(robe, 0.6f));
        rect(pm, ox, oy, 7, 6, 2, 8, shade(robe, 1.2f));
        rect(pm, ox, oy, 2, 7, 2, 5, shade(robe, 0.85f));
        rect(pm, ox, oy, 12, 7, 2, 5, shade(robe, 0.85f));
        rect(pm, ox, oy, 5, 1, 6, 5, skin);
        rect(pm, ox, oy, 5, 1, 6, 2, hair);
        px(pm, ox, oy, 6, 3, outline);
        px(pm, ox, oy, 9, 3, outline);
        switch (n.role()) {
            case PRIEST: rect(pm, ox, oy, 5, 0, 6, 1, Color.valueOf("#ffe680")); break;
            case BANKER: rect(pm, ox, oy, 11, 10, 3, 3, Color.valueOf("#e8c040")); break;
            case GUIDE: rect(pm, ox, oy, 13, 2, 1, 12, Color.valueOf("#8a6a42")); break;
            case CARRIER: // kapelusz z rondem i bat
                rect(pm, ox, oy, 5, 0, 6, 1, Color.valueOf("#3a2a1a"));
                rect(pm, ox, oy, 4, 1, 8, 1, Color.valueOf("#3a2a1a"));
                rect(pm, ox, oy, 13, 3, 1, 10, Color.valueOf("#5a3a1a"));
                px(pm, ox, oy, 14, 2, Color.valueOf("#5a3a1a"));
                break;
            case KING: // korona i gronostajowy kolnierz
                rect(pm, ox, oy, 5, 0, 6, 1, Color.valueOf("#e8c040"));
                px(pm, ox, oy, 5, 0, Color.valueOf("#f8e070"));
                px(pm, ox, oy, 7, 0, Color.valueOf("#d83040"));
                px(pm, ox, oy, 10, 0, Color.valueOf("#f8e070"));
                rect(pm, ox, oy, 4, 6, 8, 1, Color.valueOf("#f0ece0"));
                break;
            case GUARD: // helm, napiersnik i halabarda
                rect(pm, ox, oy, 5, 0, 6, 2, Color.valueOf("#9aa0a8"));
                rect(pm, ox, oy, 5, 7, 6, 4, Color.valueOf("#8a9098"));
                rect(pm, ox, oy, 13, 1, 1, 13, Color.valueOf("#6b4423"));
                rect(pm, ox, oy, 12, 1, 3, 2, Color.valueOf("#c8ccd0"));
                break;
            default: rect(pm, ox, oy, 4, 10, 8, 1, Color.valueOf("#e8d8b0")); break;
        }
    }

    private void paintCharacter(Pixmap pm, int ox, int oy, Vocation v, int dir, Color custom) {
        Color outfit;
        Color accent;
        switch (v) {
            case PALADIN: outfit = Color.valueOf("#b0472f"); accent = Color.valueOf("#e8c040"); break;
            case DRUID: outfit = Color.valueOf("#3f8f3a"); accent = Color.valueOf("#a8e070"); break;
            case SORCERER: outfit = Color.valueOf("#4a3fa8"); accent = Color.valueOf("#ff7a30"); break;
            default: outfit = Color.valueOf("#d0882a"); accent = Color.valueOf("#f2e0b0"); break;
        }
        if (custom != null) outfit = custom; // wybrany kolor stroju; pas i dodatki zostaja w kolorach profesji
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

    /** Skrzynia: drewno z okuciami i zamkiem; otwarta - podniesione wieko i ciemne, puste wnetrze. */
    /** Manekin: slup na podstawie, worek ze sloma jako tulow, poprzeczka na ramiona i glowa z tarcza celu. */
    private void paintDummy(Pixmap pm, int ox, int oy) {
        Color wood = Color.valueOf("#7a5230");
        Color sack = Color.valueOf("#c8a868");
        Color straw = Color.valueOf("#e0c060");
        Color red = Color.valueOf("#b02a20");
        disc(pm, ox, oy, 8f, 15f, 5f, new Color(0, 0, 0, 0.35f));
        rect(pm, ox, oy, 5, 14, 6, 2, shade(wood, 0.7f)); // podstawa
        rect(pm, ox, oy, 7, 2, 2, 13, wood);              // slup
        rect(pm, ox, oy, 2, 6, 12, 2, wood);              // ramiona
        rect(pm, ox, oy, 4, 7, 8, 7, shade(wood, 0.6f));  // obrys tulowia
        rect(pm, ox, oy, 5, 7, 6, 6, sack);               // tulow
        rect(pm, ox, oy, 5, 12, 6, 1, shade(sack, 0.75f));
        rect(pm, ox, oy, 4, 10, 1, 2, straw);             // wystajaca sloma
        rect(pm, ox, oy, 11, 9, 1, 2, straw);
        disc(pm, ox, oy, 8f, 9.5f, 1.6f, red);            // tarcza celu
        disc(pm, ox, oy, 8f, 9.5f, 0.7f, Color.WHITE);
        disc(pm, ox, oy, 8f, 3.5f, 2.9f, shade(wood, 0.6f)); // obrys glowy (widoczny tez na piasku)
        disc(pm, ox, oy, 8f, 3.5f, 2.2f, sack);           // glowa
    }

    /** Dachowki w rzedach: kazdy rzad zachodzi na nizszy, jasna krawedz u gory. */
    private void paintRoof(Pixmap pm, int ox, int oy, Color base) {
        rect(pm, ox, oy, 0, 0, SIZE, SIZE, base);
        for (int row = 0; row < 4; row++) {
            int y = row * 4;
            int shift = (row % 2) * 2;
            rect(pm, ox, oy, 0, y, SIZE, 1, shade(base, 1.25f));
            rect(pm, ox, oy, 0, y + 3, SIZE, 1, shade(base, 0.65f));
            for (int x = shift; x < SIZE; x += 4) rect(pm, ox, oy, x, y + 1, 1, 2, shade(base, 0.8f));
        }
    }

    /** Metalowa plyta z kolcami na podlodze lochu. */
    /** Zapasowy plecak (gdy brak objects/backpack.png): brazowy worek z klapa i paskiem. */
    private void paintBackpack(Pixmap pm, int ox, int oy) {
        pm.setColor(Color.valueOf("#3a2414"));
        pm.fillRectangle(ox + 3, oy + 3, 10, 12);
        pm.setColor(Color.valueOf("#7a5230"));
        pm.fillRectangle(ox + 4, oy + 4, 8, 10);
        pm.setColor(Color.valueOf("#5a3a20"));
        pm.fillRectangle(ox + 4, oy + 4, 8, 4);
        pm.setColor(Color.valueOf("#c8a050"));
        pm.fillRectangle(ox + 7, oy + 7, 2, 2);
    }

    private void paintTrap(Pixmap pm, int ox, int oy) {
        Color plate = Color.valueOf("#5a5a62");
        Color edge = shade(plate, 0.6f);
        Color spike = Color.valueOf("#c8ccd4");
        Color blood = Color.valueOf("#8a1a1a");
        rect(pm, ox, oy, 2, 2, 12, 12, edge);
        rect(pm, ox, oy, 3, 3, 10, 10, plate);
        for (int y = 4; y <= 10; y += 3) {
            for (int x = 4; x <= 10; x += 3) {
                rect(pm, ox, oy, x, y + 1, 2, 1, edge);   // cien kolca
                rect(pm, ox, oy, x, y, 2, 1, spike);       // kolec
                rect(pm, ox, oy, x, y - 1, 1, 1, Color.WHITE);
            }
        }
        rect(pm, ox, oy, 10, 11, 2, 1, blood);
    }

    /** Male zwierze w dolnej czesci kafelka, glowa po prawej. */
    private void paintPet(Pixmap pm, int ox, int oy, String key) {
        Color eye = Color.valueOf("#101010");
        disc(pm, ox, oy, 8f, 15f, 4.5f, new Color(0, 0, 0, 0.3f));
        switch (key) {
            case "pet_dog": {
                Color fur = Color.valueOf("#b07a40");
                Color dark = shade(fur, 0.6f);
                rect(pm, ox, oy, 4, 9, 7, 4, dark);     // obrys tulowia
                rect(pm, ox, oy, 5, 10, 6, 3, fur);     // tulow
                rect(pm, ox, oy, 5, 13, 1, 2, dark);    // lapy
                rect(pm, ox, oy, 9, 13, 1, 2, dark);
                rect(pm, ox, oy, 3, 8, 1, 3, fur);      // ogon
                rect(pm, ox, oy, 9, 6, 5, 4, dark);     // glowa
                rect(pm, ox, oy, 10, 7, 3, 3, fur);
                rect(pm, ox, oy, 13, 8, 2, 2, fur);     // pysk
                rect(pm, ox, oy, 14, 8, 1, 1, eye);     // nos
                rect(pm, ox, oy, 9, 5, 2, 2, shade(fur, 0.45f)); // ucho
                rect(pm, ox, oy, 12, 7, 1, 1, eye);
                break;
            }
            case "pet_cat": {
                Color fur = Color.valueOf("#e09a40");
                Color dark = shade(fur, 0.6f);
                rect(pm, ox, oy, 4, 10, 7, 3, dark);
                rect(pm, ox, oy, 5, 10, 5, 3, fur);
                rect(pm, ox, oy, 6, 11, 1, 2, dark);    // pregi
                rect(pm, ox, oy, 8, 11, 1, 2, dark);
                rect(pm, ox, oy, 5, 13, 1, 2, dark);
                rect(pm, ox, oy, 9, 13, 1, 2, dark);
                rect(pm, ox, oy, 2, 6, 1, 4, fur);      // ogon do gory
                rect(pm, ox, oy, 3, 9, 2, 1, fur);
                rect(pm, ox, oy, 10, 7, 4, 4, dark);    // glowa
                rect(pm, ox, oy, 11, 8, 2, 3, fur);
                rect(pm, ox, oy, 10, 6, 1, 1, dark);    // uszy
                rect(pm, ox, oy, 13, 6, 1, 1, dark);
                rect(pm, ox, oy, 12, 8, 1, 1, Color.valueOf("#40a040"));
                break;
            }
            case "pet_raven": {
                Color body = Color.valueOf("#303040");
                Color edge = Color.valueOf("#8a8aa0");
                rect(pm, ox, oy, 5, 8, 6, 4, edge);     // jasny obrys (widoczny na ciemnym)
                rect(pm, ox, oy, 6, 9, 5, 3, body);
                rect(pm, ox, oy, 3, 9, 3, 2, body);     // ogon
                rect(pm, ox, oy, 6, 7, 4, 2, shade(body, 1.4f)); // skrzydlo
                rect(pm, ox, oy, 10, 6, 3, 3, body);    // glowa
                rect(pm, ox, oy, 13, 7, 2, 1, Color.valueOf("#d0a030")); // dziob
                rect(pm, ox, oy, 11, 7, 1, 1, Color.WHITE);
                rect(pm, ox, oy, 7, 12, 1, 2, Color.valueOf("#d0a030")); // nogi
                rect(pm, ox, oy, 9, 12, 1, 2, Color.valueOf("#d0a030"));
                break;
            }
            default: { // pet_fennec
                Color fur = Color.valueOf("#e8c890");
                Color dark = shade(fur, 0.6f);
                rect(pm, ox, oy, 4, 10, 7, 3, dark);
                rect(pm, ox, oy, 5, 10, 5, 3, fur);
                rect(pm, ox, oy, 5, 13, 1, 2, dark);
                rect(pm, ox, oy, 9, 13, 1, 2, dark);
                rect(pm, ox, oy, 1, 10, 4, 2, fur);     // puszysty ogon
                rect(pm, ox, oy, 1, 10, 1, 2, Color.WHITE);
                rect(pm, ox, oy, 10, 8, 4, 3, dark);    // glowa
                rect(pm, ox, oy, 11, 9, 2, 2, fur);
                rect(pm, ox, oy, 14, 10, 1, 1, eye);    // nos
                rect(pm, ox, oy, 10, 4, 2, 4, dark);    // wielkie uszy
                rect(pm, ox, oy, 13, 4, 2, 4, dark);
                rect(pm, ox, oy, 11, 5, 1, 3, Color.valueOf("#f0a0a0"));
                rect(pm, ox, oy, 12, 9, 1, 1, eye);
            }
        }
    }

    private void paintChest(Pixmap pm, int ox, int oy, boolean open) {
        Color wood = Color.valueOf("#8a5a2a");
        Color dark = shade(wood, 0.6f);
        Color metal = Color.valueOf("#d8b040");
        disc(pm, ox, oy, 8f, 14.5f, 6f, new Color(0, 0, 0, 0.35f));
        rect(pm, ox, oy, 2, 8, 12, 6, wood);       // skrzynia
        rect(pm, ox, oy, 2, 13, 12, 1, dark);
        rect(pm, ox, oy, 2, 8, 1, 6, metal);       // okucia
        rect(pm, ox, oy, 13, 8, 1, 6, metal);
        if (open) {
            rect(pm, ox, oy, 2, 3, 12, 4, dark);   // podniesione wieko
            rect(pm, ox, oy, 3, 8, 10, 2, Color.valueOf("#2a1a0a"));
        } else {
            rect(pm, ox, oy, 2, 5, 12, 3, shade(wood, 1.15f)); // wieko
            rect(pm, ox, oy, 2, 7, 12, 1, metal);
            rect(pm, ox, oy, 7, 7, 2, 3, metal);   // zamek
        }
    }

    /** Kon z boku: tulow, nogi, szyja i glowa z przodu, grzywa i ogon. */
    private void paintHorse(Pixmap pm, int ox, int oy, boolean left) {
        Color coat = Color.valueOf("#8a5a30");
        Color dark = shade(coat, 0.6f);
        Color mane = Color.valueOf("#2a1a10");
        Color saddle = Color.valueOf("#b02a20");
        int[][] parts = {
            {2, 8, 11, 4},   // tulow
            {3, 12, 2, 4},   // nogi
            {6, 12, 1, 4},
            {9, 12, 1, 4},
            {11, 12, 2, 4},
            {11, 4, 3, 5},   // szyja
            {12, 3, 4, 3},   // glowa
        };
        disc(pm, ox, oy, 8f, 15f, 6f, new Color(0, 0, 0, 0.35f));
        for (int i = 0; i < parts.length; i++) {
            int[] r = parts[i];
            int x = left ? 16 - r[0] - r[2] : r[0];
            rect(pm, ox, oy, x, r[1], r[2], r[3], i >= 1 && i <= 4 ? dark : coat);
        }
        rect(pm, ox, oy, left ? 3 : 11, 3, 2, 5, mane);           // grzywa
        rect(pm, ox, oy, left ? 14 : 0, 8, 2, 5, mane);           // ogon
        rect(pm, ox, oy, left ? 6 : 5, 7, 5, 2, saddle);          // siodlo
        px(pm, ox, oy, left ? 1 : 14, 4, Color.valueOf("#101010")); // oko
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
        if (borders != null) borders.dispose();
        if (autoAtlas != null) autoAtlas.dispose();
        if (rockAtlas != null) rockAtlas.dispose();
        if (bigAtlas != null) bigAtlas.dispose();
        if (nativeAtlas != null) nativeAtlas.dispose();
        if (fxAtlas != null) fxAtlas.dispose();
        if (hiAtlas != null) hiAtlas.dispose();
        if (wallAtlas != null) wallAtlas.dispose();
        lightTexture.dispose();
    }
}
