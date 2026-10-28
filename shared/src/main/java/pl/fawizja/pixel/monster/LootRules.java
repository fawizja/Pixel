package pl.fawizja.pixel.monster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import pl.fawizja.pixel.combat.CombatFormulas;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;

/**
 * Zasady tabel lupow (data/loot.csv) - sprawdzane w testach i przy wczytaniu potworow:
 * <ul>
 *   <li>Zloto: oczekiwane zloto z zabicia zwyklego potwora to {@link #targetGold} (poziom^1,12) w pasie
 *       {@link #GOLD_BAND_MIN}..{@link #GOLD_BAND_MAX}; boss daje co najmniej {@link #BOSS_GOLD_MIN_FACTOR}x tyle.</li>
 *   <li>Rzadkosc maleje z wartoscia: szansa przedmiotu (promile) nie wieksza niz {@link #maxChancePermille}
 *       (1000 * sqrt(250 / wartosc)); u bossa {@link #BOSS_CHANCE_FACTOR}x wiecej.</li>
 *   <li>Wartosc przedmiotu rosnie z poziomem: zwykly potwor nie upuszcza nic drozszego niz {@link #maxItemValue}.</li>
 *   <li>Przedmioty z data/boss_items.csv spadaja tylko z bossow, rzadko: od 1% do {@link #bossOnlyMaxPermille}.</li>
 * </ul>
 * Do przyszlych wypraw na bossa w 5 graczy: {@link #rollBossLootPerPlayer} - kazdy uczestnik losuje osobno.
 */
public final class LootRules {

    public static final double GOLD_EXPONENT = 1.12;
    public static final double GOLD_BAND_MIN = 0.5;
    public static final double GOLD_BAND_MAX = 2.0;
    public static final int BOSS_GOLD_MIN_FACTOR = 8;
    public static final int BOSS_CHANCE_FACTOR = 10;
    /** Najmniejsza szansa przedmiotu tylko z bossow (1%). */
    public static final int BOSS_ONLY_MIN_PERMILLE = 10;
    /** Od tej wartosci przedmiot jest legendarny i musi byc tylko z bossow (gdy w ogole spada). */
    public static final long LEGENDARY_VALUE = 400_000;

    private static volatile List<String> bossOnlyKeys;

    private LootRules() {}

    /** Oczekiwane zloto (gp) z zabicia zwyklego potwora danego poziomu. */
    public static double targetGold(int level) {
        return Math.pow(Math.max(1, level), GOLD_EXPONENT);
    }

    /** Najwyzsza dozwolona szansa (promile) przedmiotu o danej wartosci: drozsze rzadziej. */
    public static int maxChancePermille(long value, boolean boss) {
        double c = value <= 250 ? 1000 : 1000 * Math.sqrt(250.0 / value);
        if (boss) c *= BOSS_CHANCE_FACTOR;
        return (int) Math.min(1000, Math.round(c));
    }

    /** Najdrozszy przedmiot, jaki moze upuscic zwykly potwor danego poziomu. */
    public static long maxItemValue(int level) {
        return 400L * level + 1000;
    }

    /** Szansa (promile) przedmiotu tylko z bossow: 5% tanszych, do 1% najdrozszych. */
    public static int bossOnlyMaxPermille(long value) {
        if (value >= 800_000) return 10;
        if (value >= LEGENDARY_VALUE) return 15;
        if (value >= 150_000) return 25;
        if (value >= 50_000) return 35;
        return 50;
    }

    /** Klucze przedmiotow tylko z bossow (data/boss_items.csv). */
    public static List<String> bossOnlyKeys() {
        List<String> k = bossOnlyKeys;
        if (k == null) {
            try {
                k = parseBossOnly(MonsterRegistry.readLines("/data/boss_items.csv"));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read boss_items.csv", e);
            }
            bossOnlyKeys = k;
        }
        return k;
    }

    static List<String> parseBossOnly(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            out.add(t.split(";")[0].trim());
        }
        return Collections.unmodifiableList(out);
    }

    /** Bossowie wymienieni przy przedmiocie w data/boss_items.csv (opis, sprawdzany testem). */
    public static Map<String, List<String>> bossOnlySources() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        try {
            for (String line : MonsterRegistry.readLines("/data/boss_items.csv")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                String[] f = t.split(";");
                List<String> b = new ArrayList<>();
                if (f.length > 1) for (String s : f[1].split(",")) if (!s.isBlank()) b.add(s.trim());
                out.put(f[0].trim(), b);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read boss_items.csv", e);
        }
        return out;
    }

    public static boolean isBossOnly(ItemType t) {
        return t != null && bossOnlyKeys().contains(t.key());
    }

    /** Oczekiwane zloto (gp, wg wartosci monet) z jednego zabicia. */
    public static double expectedGold(MonsterType t, ItemRegistry items) {
        double sum = 0;
        for (LootEntry e : t.loot()) {
            ItemType it = items.get(e.itemId());
            if (it.category() == ItemCategory.CURRENCY) sum += e.chancePermille() / 1000.0 * (e.min() + e.max()) / 2.0 * it.value();
        }
        return sum;
    }

    /** Oczekiwana wartosc przedmiotow (bez monet) z jednego zabicia. */
    public static double expectedItemValue(MonsterType t, ItemRegistry items) {
        double sum = 0;
        for (LootEntry e : t.loot()) {
            ItemType it = items.get(e.itemId());
            if (it.category() != ItemCategory.CURRENCY) sum += e.chancePermille() / 1000.0 * (e.min() + e.max()) / 2.0 * it.value();
        }
        return sum;
    }

    /** Sprawdza tabele lupow wszystkich potworow; zwraca opisy naruszen (pusta lista = wszystko zgodne). */
    public static List<String> audit(MonsterRegistry monsters, ItemRegistry items) {
        List<String> bad = new ArrayList<>();
        for (MonsterType t : monsters.all()) bad.addAll(audit(t, items));
        for (ItemType it : items.all()) {
            if (it.value() >= LEGENDARY_VALUE && it.category() != ItemCategory.CURRENCY && !isBossOnly(it) && dropped(monsters, it)) {
                bad.add(it.key() + ": legendary item (value " + it.value() + ") must be boss-only");
            }
        }
        for (String k : bossOnlyKeys()) if (!items.containsKey(k)) bad.add("boss_items.csv: unknown item " + k);
        return bad;
    }

    private static boolean dropped(MonsterRegistry monsters, ItemType it) {
        for (MonsterType t : monsters.all()) for (LootEntry e : t.loot()) if (e.itemId() == it.id()) return true;
        return false;
    }

    public static List<String> audit(MonsterType t, ItemRegistry items) {
        List<String> bad = new ArrayList<>();
        double gold = expectedGold(t, items);
        double target = targetGold(t.level());
        if (t.boss()) {
            if (gold < BOSS_GOLD_MIN_FACTOR * target) bad.add(t.key() + ": boss gold " + Math.round(gold) + " < " + BOSS_GOLD_MIN_FACTOR + "x " + Math.round(target));
        } else if (gold < GOLD_BAND_MIN * target || gold > GOLD_BAND_MAX * target) {
            bad.add(t.key() + ": gold " + Math.round(gold) + " outside " + GOLD_BAND_MIN + ".." + GOLD_BAND_MAX + " x " + Math.round(target));
        }
        for (LootEntry e : t.loot()) {
            ItemType it = items.get(e.itemId());
            if (it.category() == ItemCategory.CURRENCY) continue;
            if (isBossOnly(it)) {
                if (!t.boss()) bad.add(t.key() + ": boss-only item " + it.key() + " on a normal monster");
                else if (e.chancePermille() < BOSS_ONLY_MIN_PERMILLE || e.chancePermille() > bossOnlyMaxPermille(it.value())) {
                    bad.add(t.key() + ": boss-only " + it.key() + " chance " + e.chancePermille() + " not in "
                            + BOSS_ONLY_MIN_PERMILLE + ".." + bossOnlyMaxPermille(it.value()));
                }
                continue;
            }
            if (e.chancePermille() > maxChancePermille(it.value(), t.boss())) {
                bad.add(t.key() + ": " + it.key() + " (value " + it.value() + ") chance " + e.chancePermille() + " > "
                        + maxChancePermille(it.value(), t.boss()));
            }
            if (!t.boss() && it.value() > maxItemValue(t.level())) {
                bad.add(t.key() + ": " + it.key() + " value " + it.value() + " > " + maxItemValue(t.level()) + " for level " + t.level());
            }
        }
        return bad;
    }

    /**
     * Rzut lupu bossa dla jednego gracza: kazda pozycja tabeli losowana niezaleznie (bez premii potwora dnia
     * i zaklec), stosy dzielone do maksymalnego rozmiaru. Pod wyprawy w 5 graczy - kazdy dostaje wlasny rzut.
     */
    public static List<ItemStack> rollBossLootForPlayer(MonsterType boss, ItemRegistry items, Random rng) {
        if (!boss.boss()) throw new IllegalArgumentException(boss.key() + " is not a boss");
        List<ItemStack> out = new ArrayList<>();
        for (LootEntry e : boss.loot()) {
            if (rng.nextInt(1000) >= e.chancePermille()) continue;
            int count = CombatFormulas.rollBetween(e.min(), e.max(), rng);
            int max = Math.max(1, items.maxStack(e.itemId()));
            while (count > 0) {
                int c = Math.min(count, max);
                out.add(new ItemStack(e.itemId(), c));
                count -= c;
            }
        }
        return out;
    }

    /**
     * Osobny rzut lupu bossa dla kazdego uczestnika (np. 5 graczy wyprawy). Kolejnosc graczy zachowana;
     * kazdy gracz losuje z wlasnego generatora (ziarno z rng i identyfikatora), wiec wynik jednego nie zalezy od innych.
     */
    public static Map<String, List<ItemStack>> rollBossLootPerPlayer(MonsterType boss, ItemRegistry items,
                                                                     List<String> playerIds, Random rng) {
        Map<String, List<ItemStack>> out = new LinkedHashMap<>();
        long base = rng.nextLong();
        for (String id : playerIds) {
            Random own = new Random(base ^ (id.hashCode() * 0x9E3779B97F4A7C15L));
            out.put(id, rollBossLootForPlayer(boss, items, own));
        }
        return out;
    }
}
