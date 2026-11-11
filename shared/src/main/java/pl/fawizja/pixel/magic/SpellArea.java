package pl.fawizja.pixel.magic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

/**
 * Obszar czaru albo runy jako macierz jak w Canary (data/scripts/lib/register_spells.lua, AreaCombat): 1 = pole
 * trafione, 3 = srodek trafiony, 2 = srodek nietrafiony, 0 = nic. Macierz jest zapisana dla kierunku polnocnego;
 * pozostale kierunki to obroty jak w Canary AreaCombat::setupArea (wschod 90, poludnie 180, zachod 270 stopni).
 * Obszar ukosny (np. sciana z runy rzuconej po skosie) zapisany jest dla polnocnego zachodu; inne skosy to odbicia
 * jak w AreaCombat::setupExtArea. Srodek obszaru (kotwica) to: pole celu runy, pole rzucajacego (exori, exevo gran
 * mas...), a w czarach kierunkowych (fale, promienie) pole przed rzucajacym - jak Spells::getCasterPosition.
 * <p>
 * Kazdy obszar ma numer ({@link #id()}), wiec klient dostaje jedno zdarzenie efektu z ksztaltem i kierunkiem
 * ({@link #encode}) i rysuje dokladnie te pola, ktore trafil czar.
 */
public final class SpellArea {

    private static final List<SpellArea> ALL = new ArrayList<>();
    private static final Map<String, SpellArea> BY_NAME = new LinkedHashMap<>();


    private final String name;
    private final int id;
    /** Przesuniecia trafionych pol wzgledem srodka dla kierunku polnocnego (albo polnocnego zachodu w ukosnym). */
    private final List<int[]> north;
    /** Czy obszar obraca sie z kierunkiem (fale, promienie, sciany). */
    private final boolean directional;
    /** Wariant ukosny (polnocny zachod) albo null. */
    private SpellArea diagonal;

    private SpellArea(String name, boolean directional, String... rows) {
        this.name = name;
        this.directional = directional;
        int cy = -1, cx = -1;
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                char c = rows[y].charAt(x);
                if (c == '2' || c == '3') {
                    if (cy >= 0) throw new IllegalArgumentException("two centres in " + name);
                    cy = y;
                    cx = x;
                }
            }
        }
        if (cy < 0) throw new IllegalArgumentException("no centre in " + name);
        List<int[]> cells = new ArrayList<>();
        for (int y = 0; y < rows.length; y++) {
            if (rows[y].length() != rows[0].length()) throw new IllegalArgumentException("ragged matrix " + name);
            for (int x = 0; x < rows[y].length(); x++) {
                char c = rows[y].charAt(x);
                if (c == '1' || c == '3') cells.add(new int[] {x - cx, y - cy});
            }
        }
        this.north = Collections.unmodifiableList(cells);
        this.id = ALL.size();
        ALL.add(this);
        if (BY_NAME.put(name, this) != null) throw new IllegalArgumentException("duplicate area " + name);
    }

    private static SpellArea area(String name, String... rows) { return new SpellArea(name, false, rows); }

    private static SpellArea dir(String name, String... rows) { return new SpellArea(name, true, rows); }

    private SpellArea withDiagonal(SpellArea d) {
        this.diagonal = d;
        return this;
    }

    // ------------------------------------------------------------------ obszary z Canary (register_spells.lua)

    /** Jedno pole (pole z runy, pojedynczy cel). */
    public static final SpellArea POINT = area("POINT", "3");
    /** Krzyz: runa eksplozji. */
    public static final SpellArea CIRCLE1X1 = area("CIRCLE1X1",
            "010",
            "131",
            "010");
    /** Kwadrat 3x3: exori, exori gran, bomby z run, exeta res. */
    public static final SpellArea SQUARE1X1 = area("SQUARE1X1",
            "111",
            "131",
            "111");
    public static final SpellArea CIRCLE2X2 = area("CIRCLE2X2",
            "01110",
            "11111",
            "11311",
            "11111",
            "01110");
    /** Wielka kula ognia, lawina, burza, deszcz kamieni, exori mas, exevo mas san, exura gran mas res. */
    public static final SpellArea CIRCLE3X3 = area("CIRCLE3X3",
            "0011100",
            "0111110",
            "1111111",
            "1113111",
            "1111111",
            "0111110",
            "0011100");
    public static final SpellArea CIRCLE3X4 = area("CIRCLE3X4",
            "000111000",
            "001111100",
            "011111110",
            "111111111",
            "111131111",
            "111111111",
            "011111110",
            "001111100",
            "000111000");
    /** Exevo gran mas flam, exevo gran mas frigo. */
    public static final SpellArea CIRCLE5X5 = area("CIRCLE5X5",
            "00000100000",
            "00001110000",
            "00011111000",
            "00111111100",
            "01111111110",
            "11111311111",
            "01111111110",
            "00111111100",
            "00011111000",
            "00001110000",
            "00000100000");
    /** Exevo gran mas vis, exevo gran mas tera. */
    public static final SpellArea CIRCLE6X6 = area("CIRCLE6X6",
            "0000001000000",
            "0000011100000",
            "0000111110000",
            "0001111111000",
            "0011111111100",
            "0111111111110",
            "1111113111111",
            "0111111111110",
            "0011111111100",
            "0001111111000",
            "0000111110000",
            "0000011100000",
            "0000001000000");
    /** Wybuch ziemi i lodu (objawienia Kola): pierscien bez pol tuz przy druidzie. */
    public static final SpellArea RING1_BURST3 = area("RING1_BURST3",
            "000111000",
            "001111100",
            "011111110",
            "111000111",
            "111020111",
            "111000111",
            "011111110",
            "001111100",
            "000111000");
    /** Zbiorowe uleczenie ducha (mnich). */
    public static final SpellArea MASS_SPIRIT_MEND = area("MASS_SPIRIT_MEND",
            "00000000000",
            "00001110000",
            "00011111000",
            "00111111100",
            "01111111110",
            "01111311110",
            "01111111110",
            "00111111100",
            "00011111000",
            "00001110000",
            "00000000000");

    // fale (srodek = pole przed rzucajacym, macierz dla kierunku polnocnego)
    /** Exevo flam hur, exevo frigo hur, scorch, chill out. */
    public static final SpellArea WAVE4 = dir("WAVE4",
            "11111",
            "01110",
            "01110",
            "00300");
    /** Exevo gran flam hur. */
    public static final SpellArea WAVE7 = dir("WAVE7",
            "11111",
            "11111",
            "01110",
            "01110",
            "00300");
    /** Exevo vis hur, exevo tera hur. */
    public static final SpellArea SQUAREWAVE5 = dir("SQUAREWAVE5",
            "111",
            "111",
            "111",
            "010",
            "030");
    /** Exevo gran frigo hur. */
    public static final SpellArea SHORTWAVE3 = dir("SHORTWAVE3",
            "111",
            "111",
            "030");
    /** Exori min, exori min flam (trzy pola przed rycerzem). */
    public static final SpellArea WAVE6 = dir("WAVE6",
            "00000",
            "01310",
            "00000");
    /** Grad ciosow (mnich). */
    public static final SpellArea FLURRY_OF_BLOWS = dir("FLURRY_OF_BLOWS",
            "00000",
            "00100",
            "01110",
            "01310",
            "01010",
            "00000");
    public static final SpellArea GREATER_FLURRY_OF_BLOWS = dir("GREATER_FLURRY_OF_BLOWS",
            "00100",
            "01110",
            "01110",
            "11311",
            "01010",
            "00000");
    /** Zamaszyste powalenie: suma obszaru srodkowego i zewnetrznego z Canary (AREA_SWEEPING_CENTER + OUTER). */
    public static final SpellArea SWEEPING_TAKEDOWN = dir("SWEEPING_TAKEDOWN",
            "01110",
            "11111",
            "11111",
            "11311",
            "11011",
            "00000");

    // promienie: linia od pola przed rzucajacym
    public static final SpellArea BEAM5 = beamArea(5);
    public static final SpellArea BEAM6 = beamArea(6);
    public static final SpellArea BEAM7 = beamArea(7);
    public static final SpellArea BEAM8 = beamArea(8);
    public static final SpellArea BEAM9 = beamArea(9);
    public static final SpellArea BEAM10 = beamArea(10);

    // sciany z run: w poprzek kierunku rzutu, srodek na polu celu
    public static final SpellArea DIAGONAL_WALLFIELD = dir("DIAGONAL_WALLFIELD",
            "00001",
            "00011",
            "01310",
            "11000",
            "10000");
    public static final SpellArea WALLFIELD = dir("WALLFIELD", "11311").withDiagonal(DIAGONAL_WALLFIELD);
    public static final SpellArea DIAGONAL_WALLFIELD_ENERGY = dir("DIAGONAL_WALLFIELD_ENERGY",
            "0000001",
            "0000011",
            "0000110",
            "0013100",
            "0110000",
            "0100000",
            "1000000");
    public static final SpellArea WALLFIELD_ENERGY = dir("WALLFIELD_ENERGY", "1113111").withDiagonal(DIAGONAL_WALLFIELD_ENERGY);

    private static SpellArea beamArea(int len) {
        String[] rows = new String[len];
        for (int i = 0; i < len - 1; i++) rows[i] = "1";
        rows[len - 1] = "3";
        return dir("BEAM" + len, rows);
    }

    /** Promien o dlugosci len (od pola przed rzucajacym); dluzsze niz 10 obcina do 10. */
    public static SpellArea beam(int len) {
        int n = Math.max(5, Math.min(10, len));
        return BY_NAME.get("BEAM" + n);
    }

    /**
     * Okrag z Canary dla promienia z spells.csv (czary bez wpisu w spell_areas.csv): 1 - kwadrat 3x3, 2 - AREA_CIRCLE2X2,
     * 3 - AREA_CIRCLE3X3, 4 - AREA_CIRCLE3X4, 5 - AREA_CIRCLE5X5, 6 i wiecej - AREA_CIRCLE6X6; 0 - jedno pole.
     */
    public static SpellArea circle(int r) {
        switch (Math.max(0, r)) {
            case 0: return POINT;
            case 1: return SQUARE1X1;
            case 2: return CIRCLE2X2;
            case 3: return CIRCLE3X3;
            case 4: return CIRCLE3X4;
            case 5: return CIRCLE5X5;
            default: return CIRCLE6X6;
        }
    }

    // ------------------------------------------------------------------ dostep

    public String name() { return name; }

    public int id() { return id; }

    public boolean directional() { return directional; }

    public SpellArea diagonal() { return diagonal; }

    /** Obszar po nazwie z Canary (bez przedrostka AREA_), np. "CIRCLE3X3"; null, gdy nie ma. */
    public static SpellArea byName(String name) {
        if (name == null) return null;
        String n = name.trim().toUpperCase(java.util.Locale.ROOT);
        if (n.startsWith("AREA_")) n = n.substring(5);
        if (n.startsWith("AREADIAGONAL_")) n = "DIAGONAL_" + n.substring(13);
        if (n.startsWith("BEAM")) {
            try {
                return beam(Integer.parseInt(n.substring(4)));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return BY_NAME.get(n);
    }

    public static SpellArea byId(int id) {
        return id >= 0 && id < ALL.size() ? ALL.get(id) : null;
    }

    /** Najwieksza odleglosc (Chebyshev) trafionego pola od srodka. */
    public int extent() {
        int m = 0;
        for (int[] c : north) m = Math.max(m, Math.max(Math.abs(c[0]), Math.abs(c[1])));
        if (diagonal != null) m = Math.max(m, diagonal.extent());
        return m;
    }

    /** Liczba trafionych pol. */
    public int size() { return north.size(); }

    /**
     * Przesuniecia trafionych pol wzgledem srodka dla kierunku d (null = polnoc). Kierunek ukosny uzywa wariantu
     * ukosnego, a bez niego - jak Canary bez "ext area" - kierunku poziomego (wschod/zachod).
     */
    public List<int[]> offsets(Direction d) {
        if (!directional || d == null || d == Direction.NORTH) return north;
        if (d.diagonal()) {
            if (diagonal != null) {
                List<int[]> out = new ArrayList<>(diagonal.north.size());
                for (int[] c : diagonal.north) {
                    // polnocny zachod -> polnocny wschod: odbicie w poziomie; poludnie: odbicie w pionie
                    out.add(new int[] {d.dx > 0 ? -c[0] : c[0], d.dy > 0 ? -c[1] : c[1]});
                }
                return out;
            }
            d = d.dx > 0 ? Direction.EAST : Direction.WEST;
        }
        List<int[]> out = new ArrayList<>(north.size());
        for (int[] c : north) {
            int x = c[0], y = c[1];
            switch (d) {
                case EAST: out.add(new int[] {-y, x}); break;
                case SOUTH: out.add(new int[] {-x, -y}); break;
                case WEST: out.add(new int[] {y, -x}); break;
                default: out.add(new int[] {x, y});
            }
        }
        return out;
    }

    /** Pola obszaru wokol srodka anchor w kierunku d (bez sprawdzania wzroku). */
    public List<Position> tiles(Position anchor, Direction d) {
        List<Position> out = new ArrayList<>();
        for (int[] c : offsets(d)) out.add(anchor.translate(c[0], c[1]));
        return out;
    }


    /**
     * Pola widoczne z punktu origin (linia wzroku jak Canary isSightClear); world == null - wszystkie pola.
     * Klient rysuje efekt na tych polach, serwer na nich zadaje obrazenia.
     */
    public List<Position> visibleTiles(Position anchor, Direction d, Position origin, pl.fawizja.pixel.world.WorldMap world) {
        List<Position> out = new ArrayList<>();
        for (Position p : tiles(anchor, d)) {
            if (world == null || p.equals(origin) || world.hasLineOfSight(origin, p)) out.add(p);
        }
        return out;
    }

    /** Pola efektu ze zdarzenia AREA o kodzie ksztaltu (origin = pole rzucajacego albo null = srodek). */
    public static List<Position> eventTiles(Position anchor, int code, Position origin, pl.fawizja.pixel.world.WorldMap world) {
        SpellArea a = decodeArea(code);
        if (a == null) return Collections.emptyList();
        return a.visibleTiles(anchor, decodeDirection(code), origin == null ? anchor : origin, world);
    }

    // ------------------------------------------------------------------ zdarzenie efektu dla klienta

    /**
     * Kod ksztaltu do pola "amount" zdarzenia AREA: liczba ujemna (dodatnia to stary promien kwadratu).
     * kod = -(1 + id * 8 + numer kierunku).
     */
    public int encode(Direction d) {
        int dirIndex = d == null ? 0 : d.ordinal();
        return -(1 + id * 8 + dirIndex);
    }

    /** Ksztalt z kodu zdarzenia albo null (kod >= 0). */
    public static SpellArea decodeArea(int code) {
        if (code >= 0) return null;
        return byId((-code - 1) / 8);
    }

    /** Kierunek z kodu zdarzenia. */
    public static Direction decodeDirection(int code) {
        if (code >= 0) return null;
        return Direction.values()[(-code - 1) % 8];
    }

    @Override
    public String toString() { return name; }
}
