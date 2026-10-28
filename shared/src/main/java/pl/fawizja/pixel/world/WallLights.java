package pl.fawizja.pixel.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pochodnie i latarnie wiszace na murach (Darek 2026-10-28: "pochodnie maja wisiec na scianach i naciskajac na nie
 * mozna zapalic lub zgasic, nie na siebie"). Na mapie to obiekt {@code wall_torch} albo {@code wall_lantern} na polu
 * muru; wisi na scianie widocznej od poludnia (mur poziomy) albo od wschodu (pionowy) - zob. {@link #face}.
 * Dotkniecie z sasiedniego pola zapala albo gasi (jak drzwi), przedmiotu nie da sie wziac.
 * <p>
 * Stan: domyslnie wszystko sie pali; zapamietywane sa tylko odstepstwa od stanu domyslnego (pole -> zapalona?).
 * Gra offline zapisuje je w postaci, serwer we wlasnym pliku i rozsyla zmiany ({"WLIGHT", x, y, z, "1"|"0"}).
 * <p>
 * API dla zadan (np. oltarze zapalajace pochodnie): {@link #isLit}, {@link #setLit}, {@link #toggle},
 * {@link #lightsIn} (pochodnie w prostokacie), {@link #setDefaultLit} (np. pochodnie zgaszone, dopoki gracz nie
 * stanie na oltarzu).
 */
public final class WallLights {

    public static final String TORCH = "wall_torch", LANTERN = "wall_lantern";
    /** Strona muru, na ktorej wisi: brak, sciana poludniowa (mur poziomy), sciana wschodnia (mur pionowy). */
    public static final int FACE_NONE = -1, FACE_SOUTH = Openings.H, FACE_EAST = Openings.V;

    private final WorldMap world;
    private final boolean[] kind;
    private final int torchId, lanternId;
    /** Odstepstwa od stanu domyslnego: pole -> zapalona. */
    private final Map<Position, Boolean> state = new LinkedHashMap<>();
    /** Stan domyslny pol ustawiony przez zadania (bez wpisu = zapalona). Nie jest zapisywany - zadanie ustawia go przy starcie. */
    private final Map<Position, Boolean> defaults = new LinkedHashMap<>();
    private Consumer<String[]> listener;
    private volatile int version;

    public WallLights(WorldMap world) {
        this.world = world;
        int max = 0;
        for (TileType t : world.tiles().all()) max = Math.max(max, t.id());
        kind = new boolean[max + 1];
        TileType torch = world.tiles().byKeyOrNull(TORCH), lantern = world.tiles().byKeyOrNull(LANTERN);
        torchId = torch == null ? -1 : torch.id();
        lanternId = lantern == null ? -1 : lantern.id();
        if (torchId > 0) kind[torchId] = true;
        if (lanternId > 0) kind[lanternId] = true;
    }

    /** Zmiany stanu (serwer rozsyla je klientom i zapisuje): {"WLIGHT", x, y, z, "1"|"0"}. */
    public void setListener(Consumer<String[]> listener) { this.listener = listener; }

    /** Rosnie przy kazdej zmianie (renderer swiatla odswieza wtedy liste zrodel). */
    public int version() { return version; }

    public boolean isLightTile(int id) { return id > 0 && id < kind.length && kind[id]; }

    /** Pochodnia albo latarnia na murze w tym polu. */
    public boolean isWallLight(Position p) {
        FloorMap f = world.floor(p.z());
        return f != null && f.inBounds(p.x(), p.y()) && isLightTile(f.get(Layer.OBJECT, p.x(), p.y()))
                && f.get(Layer.WALL, p.x(), p.y()) != 0;
    }

    public boolean isTorch(Position p) { return isWallLight(p) && objectAt(p) == torchId; }

    public boolean isLantern(Position p) { return isWallLight(p) && objectAt(p) == lanternId; }

    private int objectAt(Position p) { return world.floor(p.z()).get(Layer.OBJECT, p.x(), p.y()); }

    public boolean isLit(int x, int y, int z) { return isLit(new Position(x, y, z)); }

    /** Czy pochodnia/latarnia sie pali (false tez wtedy, gdy w polu jej nie ma). */
    public synchronized boolean isLit(Position p) {
        if (!isWallLight(p)) return false;
        Boolean s = state.get(p);
        return s != null ? s : defaults.getOrDefault(p, true);
    }

    /** Zapala albo gasi; zwraca true, gdy stan sie zmienil (wtedy powiadamia sluchacza). */
    public synchronized boolean setLit(Position p, boolean lit) {
        if (!isWallLight(p) || isLit(p) == lit) return false;
        if (defaults.getOrDefault(p, true) == lit) state.remove(p);
        else state.put(p, lit);
        version++;
        if (listener != null) listener.accept(new String[] {"WLIGHT", s(p.x()), s(p.y()), s(p.z()), lit ? "1" : "0"});
        return true;
    }

    public boolean setLit(int x, int y, int z, boolean lit) { return setLit(new Position(x, y, z), lit); }

    /** Zapala zgaszona, gasi zapalona; zwraca nowy stan (false, gdy w polu nic nie wisi). */
    public synchronized boolean toggle(Position p) {
        if (!isWallLight(p)) return false;
        boolean lit = !isLit(p);
        setLit(p, lit);
        return lit;
    }

    /**
     * Stan domyslny (bez zapisu): np. pochodnie przy oltarzach zgaszone, dopoki zadanie ich nie zapali. Zapisane
     * odstepstwo zgodne z nowym stanem domyslnym jest usuwane.
     */
    public synchronized void setDefaultLit(Position p, boolean lit) {
        if (lit) defaults.remove(p); else defaults.put(p, false);
        Boolean s = state.get(p);
        if (s != null && s == lit) state.remove(p);
        version++;
    }

    /** Pochodnie i latarnie na murach w prostokacie pietra z (wlacznie z brzegami). */
    public List<Position> lightsIn(int z, int x0, int y0, int x1, int y1) {
        List<Position> out = new ArrayList<>();
        FloorMap f = world.floor(z);
        if (f == null) return out;
        for (int[] l : f.lightSources(x0, y0, x1, y1)) {
            Position p = new Position(l[0], l[1], z);
            if (isWallLight(p)) out.add(p);
        }
        return out;
    }

    /**
     * Sciana, na ktorej wisi swiatlo w polu muru (x, y): poludniowa, gdy na poludnie jest podloga bez muru,
     * inaczej wschodnia, gdy na wschod jest podloga; {@link #FACE_NONE}, gdy obie strony sa zasloniete.
     * Te sama regule stosuje generator mapy (tools/mapgen/wall_lights.py).
     */
    public static int face(FloorMap f, int x, int y) {
        if (open(f, x, y + 1)) return FACE_SOUTH;
        if (open(f, x + 1, y)) return FACE_EAST;
        return FACE_NONE;
    }

    private static boolean open(FloorMap f, int x, int y) {
        return f.inBounds(x, y) && f.get(Layer.WALL, x, y) == 0 && f.get(Layer.GROUND, x, y) != 0;
    }

    /** Pole przed swiatlem (tam pada blask, stamtad sie je zapala); samo pole, gdy brak sciany. */
    public Position front(Position p) {
        FloorMap f = world.floor(p.z());
        if (f == null) return p;
        int face = face(f, p.x(), p.y());
        if (face == FACE_SOUTH) return p.translate(0, 1);
        if (face == FACE_EAST) return p.translate(1, 0);
        return p;
    }

    // ------------------------------------------------------------------ zapis
    /** Odstepstwa od stanu domyslnego: "x,y,z=1|x,y,z=0". */
    public synchronized String encode() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Position, Boolean> e : state.entrySet()) {
            if (b.length() > 0) b.append('|');
            Position p = e.getKey();
            b.append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append('=').append(e.getValue() ? '1' : '0');
        }
        return b.toString();
    }

    /** Przywraca stan z {@link #encode()}; wpisy bez swiatla na mapie (inna wersja swiata) sa pomijane. */
    public synchronized void restore(String text) {
        if (text == null || text.isBlank()) return;
        for (String part : text.split("\\|")) {
            try {
                int eq = part.indexOf('=');
                String[] f = part.substring(0, eq).split(",");
                Position p = new Position(Integer.parseInt(f[0].trim()), Integer.parseInt(f[1].trim()), Integer.parseInt(f[2].trim()));
                if (!isWallLight(p)) continue;
                boolean lit = part.substring(eq + 1).trim().equals("1");
                if (defaults.getOrDefault(p, true) != lit) state.put(p, lit);
            } catch (RuntimeException e) {
                // uszkodzony wpis - pomijamy
            }
        }
        version++;
    }

    /** Wszystko wraca do stanu domyslnego (nowa sesja offline, inna postac). */
    public synchronized void reset() {
        state.clear();
        version++;
    }

    /** Stan z serwera (klient online), bez powiadamiania sluchacza. */
    public synchronized void apply(Position p, boolean lit) {
        if (!isWallLight(p)) return;
        if (defaults.getOrDefault(p, true) == lit) state.remove(p);
        else state.put(p, lit);
        version++;
    }

    /** Odstepstwa (dla nowego klienta po zalogowaniu): pole -> zapalona. */
    public synchronized Map<Position, Boolean> changes() { return new LinkedHashMap<>(state); }

    /** Klucze kafelkow swiatel na murach. */
    public static Set<String> keys() { return Set.of(TORCH, LANTERN); }

    private static String s(int v) { return Integer.toString(v); }
}
