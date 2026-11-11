package pl.fawizja.pixel.treasure;

import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.monster.LootRules;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Region;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Nagroda ze skrzyni wg trudnosci miejsca (Darek 2026-11-11: "wiecej zlota i przedmioty w zaleznosci od trudnosci").
 * <p>
 * Trudnosc (poziom postaci, dla ktorej to miejsce) = najwyzsza z: poziomu drzwi poziomu na drodze do skrzyni,
 * srodka przedzialu poziomow regionu, poziomu najsilniejszych potworow w promieniu {@link #NEAR} pol na tym pietrze
 * (z bossem) i progu glebokosci (8 x pietro pod ziemia).
 * <p>
 * Jak w Tibii skrzynia zadania (za drzwiami poziomu, przy bossie, w skarbcu zadania z oltarzami albo z nagroda
 * wpisana na mapie) daje jeden wybrany dobry przedmiot dla profesji otwierajacego, duzo zlota i zapas mikstur;
 * skrzynia w slepej komorze lochu (i losowa skrzynia skarbow) - mniej zlota, mikstury i czasem slabszy przedmiot
 * (co trzecia skrzynia lupu, co druga skrzynia skarbow).
 * Zawartosc wpisana na mapie (chests.csv) jest minimum. Losowanie zalezy tylko od skrzyni i profesji, wiec klient
 * i serwer licza to samo.
 */
public final class ChestRewards {

    /** Promien szukania potworow wokol skrzyni. */
    public static final int NEAR = 12;
    /** Zloto ze skrzyni zadania: 100 x trudnosc + trudnosc^2 / 2 (np. poziom 50: 6 250 gp, 200: 40 000 gp). */
    public static long questGold(int difficulty) {
        long d = Math.max(1, difficulty);
        return roundTo(100 * d + d * d / 2, 100);
    }

    /** Zloto ze skrzyni lupu: 40 + 12 x trudnosc (np. poziom 50: 640 gp, 200: 2 440 gp). */
    public static long lootGold(int difficulty) {
        return roundTo(40 + 12L * Math.max(1, difficulty), 10);
    }

    private static long roundTo(long v, long step) { return (v + step / 2) / step * step; }

    public enum Kind { QUEST, LOOT, TREASURE }

    /** Gotowa nagroda: rodzaj, trudnosc, zloto i przedmioty. */
    public record Reward(Kind kind, int difficulty, long gold, List<ItemStack> items) {}

    private ChestRewards() {}

    // ------------------------------------------------------------------ trudnosc

    /** Trudnosc skrzyni (poziom postaci). monsters moze byc null (bez potworow). */
    public static int difficulty(TreasureChests.Chest c, WorldMap world, MonsterManager monsters) {
        Position p = c.position();
        int d = Math.max(0, c.doorLevel());
        Region r = world == null ? null : world.regionAt(p);
        if (r != null) d = Math.max(d, (r.levelMin() + r.levelMax()) / 2);
        if (monsters != null) {
            for (SpawnPoint sp : monsters.spawns()) {
                if (sp.center().z() == p.z() && sp.center().distanceTo(p) <= NEAR && monsters.registry().contains(sp.monsterKey())) {
                    d = Math.max(d, monsters.registry().get(sp.monsterKey()).level());
                }
            }
        }
        if (p.z() < 0) d = Math.max(d, 8 * -p.z());
        return Math.max(5, Math.min(1000, d));
    }

    /** Boss w promieniu {@link #NEAR} pol (skrzynia za bossem jest nagroda zadania). */
    static boolean bossNear(Position p, MonsterManager monsters) {
        if (monsters == null) return false;
        for (SpawnPoint sp : monsters.spawns()) {
            if (sp.center().z() == p.z() && sp.center().distanceTo(p) <= NEAR + 3 && monsters.registry().contains(sp.monsterKey())
                    && monsters.registry().get(sp.monsterKey()).boss()) return true;
        }
        return false;
    }

    /** Rodzaj skrzyni. */
    public static Kind kind(TreasureChests.Chest c, WorldMap world, MonsterManager monsters, ItemRegistry items) {
        if (c.vocation() != null) return Kind.QUEST;
        if (!c.id().startsWith(TreasureChests.LOOT_PREFIX)) return Kind.TREASURE;
        Position p = c.position();
        if (c.doorLevel() > 0 || world != null && (world.inBossQuestRooms(p) || world.inAltarQuestRooms(p)) || bossNear(p, monsters)) {
            return Kind.QUEST;
        }
        ItemType base = c.itemKey() == null || !items.containsKey(c.itemKey()) ? null : items.byKey(c.itemKey());
        if (base != null && equipment(base)) return Kind.QUEST; // nagroda wpisana na mapie (np. luk elfow w obozie)
        return Kind.LOOT;
    }

    // ------------------------------------------------------------------ nagroda

    public static Reward reward(TreasureChests.Chest c, WorldMap world, MonsterManager monsters, ItemRegistry items, Vocation v) {
        int d = difficulty(c, world, monsters);
        Kind kind = kind(c, world, monsters, items);
        long seed = seed(c.id());
        List<ItemStack> out = new ArrayList<>();
        ItemType base = c.itemKey() == null || !items.containsKey(c.itemKey()) ? null : items.byKey(c.itemKey());
        long gold;
        if (kind == Kind.QUEST) {
            gold = Math.max(c.gold(), questGold(d));
            // jeden wybrany dobry przedmiot dla profesji: wpisany na mapie albo z najwyzszej polki do tego poziomu
            ItemType item = base != null && equipment(base) && base.usableBy(v) && !LootRules.isBossOnly(base) ? base
                    : pick(items, v, d, seed);
            if (item != null) out.add(new ItemStack(item.id(), 1));
            addPotions(out, items, v, d, Math.min(20, 5 + d / 20), seed);
            if (base != null && !equipment(base) && base.usableBy(v) && c.count() > 0) out.add(new ItemStack(base.id(), c.count()));
        } else {
            gold = Math.max(c.gold(), lootGold(d));
            int n = Math.min(10, 2 + d / 25);
            if (base != null && base.category() == ItemCategory.POTION && base.usableBy(v) && c.count() >= n) {
                out.add(new ItemStack(base.id(), c.count()));
            } else {
                addPotions(out, items, v, d, Math.max(n, c.count()), seed);
            }
            // czasem slabszy przedmiot (co trzecia skrzynia lupu, co druga losowa skrzynia skarbow)
            if (Math.floorMod(seed, kind == Kind.TREASURE ? 2 : 3) == 0) {
                ItemType item = pick(items, v, Math.max(1, d * 6 / 10), seed >>> 7);
                if (item != null) out.add(new ItemStack(item.id(), 1));
            }
        }
        return new Reward(kind, d, gold, merge(out));
    }

    /** Opis nagrody w jednym wierszu, np. "Nagroda za zadanie (poziom ~120): 26 400 gp. Otrzymujesz: Smocza tarcza, 11x Mikstura many." */
    public static String message(Reward r, ItemRegistry items) {
        return String.join(" ", lines(r, items));
    }

    /**
     * Opis nagrody do dziennika w dwoch krotkich wierszach (mieszcza sie na telefonie): zloto z trudnoscia miejsca,
     * potem przedmioty.
     */
    public static List<String> lines(Reward r, ItemRegistry items) {
        String head = (r.kind() == Kind.QUEST ? "Nagroda za zadanie" : "Skrzynia skarbów") + " (poziom ~" + r.difficulty() + "): "
                + String.format(java.util.Locale.ROOT, "%,d gp", r.gold()).replace(',', ' ') + (r.kind() == Kind.QUEST ? "!" : ".");
        if (r.items().isEmpty()) return List.of(head);
        StringBuilder b = new StringBuilder("Otrzymujesz: ");
        for (int i = 0; i < r.items().size(); i++) {
            ItemStack s = r.items().get(i);
            if (i > 0) b.append(", ");
            b.append(s.count() > 1 ? s.count() + "x " : "").append(items.get(s.itemId()).name());
        }
        return List.of(head, b.append('.').toString());
    }

    // ------------------------------------------------------------------ wybor przedmiotow

    static boolean equipment(ItemType t) {
        return (t.category() == ItemCategory.ARMOR || t.category() == ItemCategory.SHIELD || t.category() == ItemCategory.WEAPON)
                && t.slot() != EquipSlot.NONE && t.value() > 0;
    }

    /**
     * Przedmiot dla profesji do poziomu d: z najwyzszej polki (wymagany poziom co najmniej 80% najwyzszego dostepnego),
     * bez przedmiotow tylko z bossow; wybor powtarzalny wg ziarna.
     */
    static ItemType pick(ItemRegistry items, Vocation v, int d, long seed) {
        List<ItemType> ok = new ArrayList<>();
        int top = 0;
        for (ItemType t : items.all()) {
            if (!equipment(t) || !t.usableBy(v) || t.minLevel() > d || LootRules.isBossOnly(t)) continue;
            ok.add(t);
            top = Math.max(top, t.minLevel());
        }
        final int floor = top * 8 / 10;
        List<ItemType> shelf = new ArrayList<>();
        for (ItemType t : ok) if (t.minLevel() >= floor) shelf.add(t);
        if (shelf.isEmpty()) return null;
        shelf.sort((a, b) -> a.key().compareTo(b.key()));
        return shelf.get((int) Math.floorMod(seed, (long) shelf.size()));
    }

    /** Mikstury dla profesji do poziomu d: zdrowia dla rycerza, many dla magow, u paladyna i mnicha wg ziarna. */
    static void addPotions(List<ItemStack> out, ItemRegistry items, Vocation v, int d, int count, long seed) {
        boolean mana = v == Vocation.SORCERER || v == Vocation.DRUID || (v == Vocation.PALADIN && Math.floorMod(seed >>> 3, 2) == 0);
        String kind = mana ? "mana_potion" : "health_potion";
        ItemType best = null;
        for (ItemType t : items.all()) {
            if (t.category() != ItemCategory.POTION || !t.key().endsWith(kind) || !t.usableBy(v) || t.minLevel() > d) continue;
            if (best == null || t.minLevel() > best.minLevel()) best = t;
        }
        if (best != null && count > 0) out.add(new ItemStack(best.id(), count));
    }

    /** Laczy stosy tego samego przedmiotu (mikstury z mapy i z nagrody). */
    private static List<ItemStack> merge(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : in) {
            int i = 0;
            while (i < out.size() && out.get(i).itemId() != s.itemId()) i++;
            if (i < out.size()) out.set(i, out.get(i).withCount(out.get(i).count() + s.count()));
            else out.add(s);
        }
        return List.copyOf(out);
    }

    static long seed(String id) {
        long h = 1125899906842597L;
        for (int i = 0; i < id.length(); i++) h = 31 * h + id.charAt(i);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h & Long.MAX_VALUE;
    }
}
