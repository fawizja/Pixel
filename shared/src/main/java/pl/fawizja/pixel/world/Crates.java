package pl.fawizja.pixel.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;

/**
 * Zwykle skrzynie, beczki i worki na mapie (Darek 2026-10-28: "skrzynki zeby mozna przesuwac, otwierac, zamykac
 * i chowac w nich rozne rzeczy"). Wejscie w skrzynie pcha ja o pole dalej (gdy tam wolno), dotkniecie obok otwiera
 * schowek na {@link #SLOTS} przedmiotow. Lady, gabloty i skrzynie skarbow sie nie ruszaja.
 * <p>
 * Stan to przesuniecia (skad - dokad, przedmiot mapy zostaje ten sam) i zawartosc schowkow wg obecnego pola.
 * Gra offline zapisuje go w postaci, serwer we wlasnym pliku i rozsyla zmiany do klientow.
 */
public final class Crates {

    public static final int SLOTS = 8;
    /** Dalej niz tyle pol od miejsca na mapie skrzyni nie da sie odepchnac (bez zastawiania ulic calego miasta). */
    public static final int MAX_DRIFT = 6;
    /** Daje sie pchac. */
    public static final Set<String> MOVABLE = Set.of("crate", "barrel", "sack", "chest");
    /** Daje sie otworzyc i schowac w nim przedmioty. */
    public static final Set<String> STORAGE = Set.of("crate", "barrel", "sack", "chest", "wardrobe");

    private final WorldMap world;
    private final boolean[] movable;
    private final boolean[] storage;
    /** Obecne pole przesunietej skrzyni -> pole, na ktorym stala na mapie. */
    private final Map<Position, Position> origin = new LinkedHashMap<>();
    private final Map<Position, Container> contents = new HashMap<>();
    private Consumer<String[]> listener;

    public Crates(WorldMap world) {
        this.world = world;
        int max = 0;
        for (TileType t : world.tiles().all()) max = Math.max(max, t.id());
        movable = new boolean[max + 1];
        storage = new boolean[max + 1];
        for (TileType t : world.tiles().all()) {
            movable[t.id()] = MOVABLE.contains(t.key());
            storage[t.id()] = STORAGE.contains(t.key());
        }
    }

    /** Zmiany (serwer rozsyla je klientom): {"CRATEMV", fx, fy, fz, tx, ty, tz} i {"CRATE", x, y, z, zawartosc}. */
    public void setListener(Consumer<String[]> listener) { this.listener = listener; }

    private int objectAt(Position p) {
        FloorMap f = world.floor(p.z());
        return f == null || !f.inBounds(p.x(), p.y()) ? 0 : f.get(Layer.OBJECT, p.x(), p.y());
    }

    public boolean isMovable(Position p) {
        int id = objectAt(p);
        return id > 0 && id < movable.length && movable[id];
    }

    public boolean isStorage(Position p) {
        int id = objectAt(p);
        return id > 0 && id < storage.length && storage[id];
    }

    /**
     * Czy skrzynie z pola from mozna pchnac w kierunku d: pole za nia ma podloze, nie ma muru, obiektu, przejscia
     * (schody, kratka) ani nikogo (occupied). Tylko krok prosty, nie po skosie.
     */
    public boolean canPush(Position from, Direction d, Predicate<Position> occupied) {
        if (d.diagonal() || !isMovable(from)) return false;
        Position to = from.translate(d.dx, d.dy);
        FloorMap f = world.floor(to.z());
        if (f == null || !f.inBounds(to.x(), to.y())) return false;
        if (!f.isWalkable(to.x(), to.y()) || f.get(Layer.OBJECT, to.x(), to.y()) != 0) return false;
        if (f.transitionAt(to.x(), to.y()) != null) return false;
        Position o = origin.getOrDefault(from, from);
        if (Math.abs(to.x() - o.x()) + Math.abs(to.y() - o.y()) > MAX_DRIFT) return false;
        return occupied == null || !occupied.test(to);
    }

    /** Pcha skrzynie (sprawdz wczesniej canPush). Zawartosc jedzie razem z nia. */
    public synchronized void push(Position from, Direction d) {
        move(from, from.translate(d.dx, d.dy));
        if (listener != null) {
            Position to = from.translate(d.dx, d.dy);
            listener.accept(new String[] {"CRATEMV", s(from.x()), s(from.y()), s(from.z()), s(to.x()), s(to.y()), s(to.z())});
        }
    }

    private void move(Position from, Position to) {
        FloorMap f = world.floor(from.z());
        int id = f.get(Layer.OBJECT, from.x(), from.y());
        if (id == 0 || f.get(Layer.OBJECT, to.x(), to.y()) != 0) return;
        f.set(Layer.OBJECT, from.x(), from.y(), 0);
        f.set(Layer.OBJECT, to.x(), to.y(), id);
        Position o = origin.remove(from);
        if (o == null) o = from;
        if (!o.equals(to)) origin.put(to, o);
        Container c = contents.remove(from);
        if (c != null) contents.put(to, c);
    }

    /** Ruch skrzyni z serwera (klient online). Ignoruje, gdy klient juz ja przesunal albo pole jest zajete. */
    public synchronized void applyMove(Position from, Position to) {
        if (objectAt(from) != 0 && objectAt(to) == 0) move(from, to);
    }

    /** Schowek skrzyni na polu (tworzony przy pierwszym otwarciu) albo null, gdy to nie skrzynia. */
    public synchronized Container open(Position p, ItemRegistry items) {
        if (!isStorage(p)) return null;
        return contents.computeIfAbsent(p, q -> new Container(items, SLOTS));
    }

    /** Zawartosc bez tworzenia schowka (do wyswietlenia); pusta lista, gdy nic tam nie ma. */
    public synchronized List<ItemStack> peek(Position p) {
        Container c = contents.get(p);
        return c == null ? List.of() : c.contents();
    }

    /** Po zmianie zawartosci: powiadamia serwer/klientow. */
    public synchronized void changed(Position p) {
        if (listener != null) listener.accept(new String[] {"CRATE", s(p.x()), s(p.y()), s(p.z()), encodeItems(contents.get(p))});
    }

    /** Zawartosc z serwera (klient online). */
    public synchronized void applyContents(Position p, String items, ItemRegistry registry) {
        Container c = new Container(registry, SLOTS);
        decodeItems(c, items);
        if (c.contents().isEmpty()) contents.remove(p);
        else contents.put(p, c);
    }

    // ------------------------------------------------------------------ zapis
    /** Stan: "M:ox,oy,oz>x,y,z" (przesuniecia) i "C:x,y,z=id x n[+u]/..." (zawartosc), linie oddzielone '|'. */
    public synchronized String encode() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Position, Position> e : origin.entrySet()) {
            out.add("M:" + pos(e.getValue()) + ">" + pos(e.getKey()));
        }
        for (Map.Entry<Position, Container> e : contents.entrySet()) {
            String items = encodeItems(e.getValue());
            if (!items.isEmpty()) out.add("C:" + pos(e.getKey()) + "=" + items);
        }
        return String.join("|", out);
    }

    /**
     * Przywraca stan zapisany przez {@link #encode()} na swiezej mapie: najpierw zdejmuje skrzynie z pol, na ktorych
     * staly, potem stawia je tam, dokad je przesunieto (kolejnosc przesuniec nie ma znaczenia). Wpisy niepasujace
     * do mapy (inna wersja swiata) sa pomijane.
     */
    public synchronized void restore(String text, ItemRegistry items) {
        if (text == null || text.isBlank()) return;
        Map<Position, Position> moves = new LinkedHashMap<>();
        Map<Position, String> stored = new LinkedHashMap<>();
        for (String line : text.split("\\|")) {
            try {
                if (line.startsWith("M:")) {
                    String[] ab = line.substring(2).split(">");
                    moves.put(parse(ab[1]), parse(ab[0]));
                } else if (line.startsWith("C:")) {
                    int eq = line.indexOf('=');
                    stored.put(parse(line.substring(2, eq)), line.substring(eq + 1));
                }
            } catch (RuntimeException e) {
                // uszkodzony wpis - pomijamy
            }
        }
        Map<Position, Integer> lifted = new HashMap<>();
        for (Map.Entry<Position, Position> e : moves.entrySet()) {
            Position o = e.getValue();
            if (!isMovable(o)) continue;
            lifted.put(e.getKey(), objectAt(o));
            world.floor(o.z()).set(Layer.OBJECT, o.x(), o.y(), 0);
        }
        for (Map.Entry<Position, Integer> e : lifted.entrySet()) {
            Position to = e.getKey();
            FloorMap f = world.floor(to.z());
            Position o = moves.get(to);
            if (f == null || !f.inBounds(to.x(), to.y()) || f.get(Layer.OBJECT, to.x(), to.y()) != 0) {
                world.floor(o.z()).set(Layer.OBJECT, o.x(), o.y(), e.getValue());   // nie da sie - zostaje na miejscu
                continue;
            }
            f.set(Layer.OBJECT, to.x(), to.y(), e.getValue());
            origin.put(to, o);
        }
        for (Map.Entry<Position, String> e : stored.entrySet()) {
            if (!isStorage(e.getKey())) continue;
            Container c = new Container(items, SLOTS);
            decodeItems(c, e.getValue());
            if (!c.contents().isEmpty()) contents.put(e.getKey(), c);
        }
    }

    /** Wszystkie skrzynie wracaja na miejsca z mapy, schowki sa puste (nowa sesja offline, inna postac). */
    public synchronized void reset() {
        Map<Position, Integer> lifted = new HashMap<>();
        for (Position cur : origin.keySet()) {
            FloorMap f = world.floor(cur.z());
            lifted.put(cur, f.get(Layer.OBJECT, cur.x(), cur.y()));
            f.set(Layer.OBJECT, cur.x(), cur.y(), 0);
        }
        for (Map.Entry<Position, Integer> e : lifted.entrySet()) {
            Position o = origin.get(e.getKey());
            if (e.getValue() != 0) world.floor(o.z()).set(Layer.OBJECT, o.x(), o.y(), e.getValue());
        }
        origin.clear();
        contents.clear();
    }

    /** Zawartosc dla wszystkich schowkow (dla nowego klienta po zalogowaniu). */
    public synchronized Map<Position, String> allContents() {
        Map<Position, String> out = new LinkedHashMap<>();
        for (Map.Entry<Position, Container> e : contents.entrySet()) out.put(e.getKey(), encodeItems(e.getValue()));
        return out;
    }

    /** Przesuniecia (obecne pole -> pole na mapie). */
    public synchronized Map<Position, Position> moves() { return new LinkedHashMap<>(origin); }

    static String encodeItems(Container c) {
        if (c == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < c.size(); i++) {
            ItemStack st = c.get(i);
            if (st == null) continue;
            if (b.length() > 0) b.append('/');
            b.append(i).append(':').append(st.itemId()).append('x').append(st.count());
            if (st.upgrade() > 0) b.append('+').append(st.upgrade());
        }
        return b.toString();
    }

    static void decodeItems(Container c, String text) {
        if (text == null || text.isBlank()) return;
        for (String part : text.split("/")) {
            try {
                int colon = part.indexOf(':');
                int slot = Integer.parseInt(part.substring(0, colon));
                String v = part.substring(colon + 1);
                int plus = v.indexOf('+');
                int upgrade = plus < 0 ? 0 : Integer.parseInt(v.substring(plus + 1));
                String[] f = (plus < 0 ? v : v.substring(0, plus)).split("x");
                int id = Integer.parseInt(f[0]);
                if (!c.registry().contains(id)) continue;
                c.putInSlot(slot, new ItemStack(id, Integer.parseInt(f[1]), upgrade));
            } catch (RuntimeException e) {
                // uszkodzony wpis - pomijamy
            }
        }
    }

    private static String pos(Position p) { return p.x() + "," + p.y() + "," + p.z(); }

    private static Position parse(String t) {
        String[] f = t.split(",");
        return new Position(Integer.parseInt(f[0].trim()), Integer.parseInt(f[1].trim()), Integer.parseInt(f[2].trim()));
    }

    private static String s(int v) { return Integer.toString(v); }
}
