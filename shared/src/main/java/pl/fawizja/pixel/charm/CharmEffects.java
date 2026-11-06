package pl.fawizja.pixel.charm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.monster.LootEntry;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.world.Position;

/**
 * Dzialanie charmow w walce PvM jak w Tibii (Canary IOBestiary::parse*CharmCombat, Game::applyCharmRune,
 * Combat::applyExtensions, Game::applyLifeLeech/applyManaLeech). Charmy dzialaja tylko na potwory, nigdy w PvP.
 * {@link pl.fawizja.pixel.combat.CombatSystem} wola te metody w kilku malych punktach (po trafieniu, przy ataku
 * potwora, przy lupie i smierci); tu jest cala logika i wszystkie liczby.
 */
public final class CharmEffects {
    /** Paraliz z Okaleczenia i Odretwienia (Canary: 10 s). */
    public static final long PARALYZE_MS = 10_000L;
    /** Sparalizowany potwor chodzi tyle razy wolniej (Pixel; w Tibii paraliz zbija szybkosc prawie do minimum). */
    public static final int PARALYZE_STEP_FACTOR = 3;
    /** Zastrzyk adrenaliny: przyspieszenie na 10 s (Canary ConditionSpeed haste, wzor 2,5 x szybkosc + 40). */
    public static final long ADRENALINE_MS = 10_000L;
    public static final int ADRENALINE_HASTE_PERCENT = 250;
    /** Smiertelny chwyt: potwor nie ucieka przez 30 s. */
    public static final long FATAL_HOLD_MS = 30_000L;
    /** Charmy zywiolow: 5% maks. zdrowia potwora, najwyzej 2 x poziom gracza. */
    public static final double ELEMENTAL_PERCENT = 5;
    public static final int ELEMENTAL_LEVEL_FACTOR = 2;
    /** Rzez: 15% maks. zdrowia zabitego, najwyzej 6 x poziom, w 4 sasiednie pola. */
    public static final double CARNAGE_PERCENT = 15;
    public static final int CARNAGE_LEVEL_FACTOR = 6;
    /** Przytloczenie 5% maks. zdrowia gracza, Przeplyw many 2,5% maks. many; oba najwyzej 8% zdrowia potwora. */
    public static final double OVERPOWER_PERCENT = 5;
    public static final double OVERFLUX_PERCENT = 2.5;
    public static final double OVER_TARGET_LIMIT_PERCENT = 8;
    /** Zdarzenie walki z charmu: efekt "CHARM:NAZWA" (klient rysuje fioletowa liczbe z nazwa charmu). */
    public static final String EVENT_PREFIX = "CHARM:";

    private CharmEffects() {}

    public static String eventEffect(Charm c) { return EVENT_PREFIX + c.name(); }

    /** Charm ze zdarzenia walki albo null. */
    public static Charm fromEvent(String effect) {
        return effect != null && effect.startsWith(EVENT_PREFIX) ? Charm.parse(effect.substring(EVENT_PREFIX.length())) : null;
    }

    /** Rzut w setnych czesciach procenta (jak normal_random(1, 10000) / 100 w Canary). */
    public static boolean roll(double chancePercent, Random rng) {
        return chancePercent > 0 && rng.nextInt(10_000) < (int) Math.round(chancePercent * 100);
    }

    private static double value(PlayerCharacter pc, Charm c, MonsterType t) {
        return t == null ? 0 : c.value(pc.charms().active(c, t.key()));
    }

    // --- krytyk i kradziez (Niski cios, Dziki cios, Wampirzy uscisk, Zew pustki) ---

    /** Niski cios: dodatkowa szansa na krytyk w procentach przeciw temu potworowi. */
    public static double critChanceBonus(PlayerCharacter pc, MonsterType t) { return value(pc, Charm.LOW_BLOW, t); }

    /** Dziki cios: dodatkowe obrazenia krytyczne w procentach przeciw temu potworowi. */
    public static double critDamageBonus(PlayerCharacter pc, MonsterType t) { return value(pc, Charm.SAVAGE_BLOW, t); }

    /**
     * Niski cios przy rzucie, ktory juz wypadl bez krytyka: drugi rzut z szansa l / (100 - p), wiec lacznie
     * p + l (opis Tibii "adds x% critical hit chance"; Canary rzuca drugi raz z p + l, co daje troche wiecej).
     */
    public static boolean lowBlowReroll(PlayerCharacter pc, MonsterType t, double baseChance, Random rng) {
        double l = critChanceBonus(pc, t);
        if (l <= 0 || baseChance >= 100) return false;
        return roll(Math.min(100, l * 100 / (100 - baseChance)), rng);
    }

    /** Wampirzy uscisk: dodatkowa kradziez zycia (tylko gdy postac ma juz kradziez zycia, jak w opisie Tibii). */
    public static double lifeLeechBonus(PlayerCharacter pc, MonsterType t, double basePercent) {
        return basePercent > 0 ? value(pc, Charm.VAMPIRIC_EMBRACE, t) : 0;
    }

    /** Zew pustki: dodatkowa kradziez many (tylko gdy postac ma juz kradziez many). */
    public static double manaLeechBonus(PlayerCharacter pc, MonsterType t, double basePercent) {
        return basePercent > 0 ? value(pc, Charm.VOIDS_CALL, t) : 0;
    }

    // --- obrazenia charmow ---

    /** Charmy zywiolow: min(ceil(2 x poziom), ceil(5% maks. zdrowia potwora)). */
    public static int elementalDamage(int level, int monsterMaxHealth) {
        return (int) Math.min(Math.ceil(level * (double) ELEMENTAL_LEVEL_FACTOR), Math.ceil(monsterMaxHealth * ELEMENTAL_PERCENT / 100.0));
    }

    /** Przytloczenie: min(ceil(8% zdrowia potwora), ceil(5% maks. zdrowia gracza)). */
    public static int overpowerDamage(int playerMaxHealth, int monsterMaxHealth) {
        return (int) Math.min(Math.ceil(monsterMaxHealth * OVER_TARGET_LIMIT_PERCENT / 100.0), Math.ceil(playerMaxHealth * OVERPOWER_PERCENT / 100.0));
    }

    /** Przeplyw many: min(ceil(8% zdrowia potwora), ceil(2,5% maks. many gracza)). */
    public static int overfluxDamage(int playerMaxMana, int monsterMaxHealth) {
        return (int) Math.min(Math.ceil(monsterMaxHealth * OVER_TARGET_LIMIT_PERCENT / 100.0), Math.ceil(playerMaxMana * OVERFLUX_PERCENT / 100.0));
    }

    /** Rzez: min(ceil(15% maks. zdrowia zabitego), 6 x poziom). */
    public static int carnageDamage(int level, int killedMaxHealth) {
        return (int) Math.min(Math.ceil(killedMaxHealth * CARNAGE_PERCENT / 100.0), (double) CARNAGE_LEVEL_FACTOR * level);
    }

    /**
     * Obrazenia charmu po odpornosciach potwora: charm zywiolu jak czar danego zywiolu (bez pancerza - Canary
     * blockedByArmor = false), charmy "neutralne" (Rzez, Przytloczenie, Przeplyw many, Riposta; type null) bez odpornosci.
     */
    public static int resisted(MonsterType t, int amount, DamageType type) {
        if (amount <= 0) return 0;
        if (type == null) return amount;
        return (int) Math.max(0, (long) amount * (100 - t.resistance(type)) / 100);
    }

    /** Obrazenia charmu do zadania: cel, rodzaj (null = neutralne) i ilosc przed odpornosciami. */
    public record Proc(Charm charm, Monster target, int amount, DamageType type) {}

    /** Znajduje potwora na polu (Rzez). */
    public interface MonsterLookup { Monster at(Position p); }

    private static final int[][] CARNAGE_OFFSETS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    /**
     * Po trafieniu potwora przez gracza (bron, czar, runa; nie obrazenia z samego charmu): rzut Major i Minor
     * zalozonych na tego potwora (Canary applyCharmRune: kazdy charm ofensywny osobno, uniform_random(1, 100) <=
     * szansa), potem Smiertelny chwyt. Paraliz i chwyt dzialaja od razu; obrazenia zwraca jako liste do zadania.
     */
    public static List<Proc> onHit(PlayerCharacter pc, Monster m, MonsterLookup lookup, Random rng, long now) {
        List<Proc> out = new ArrayList<>(1);
        MonsterType t = m.type();
        Charms ch = pc.charms();
        for (Charm c : ch.charmsOn(t.key())) {
            if (c.kind() != Charm.Kind.OFFENSIVE) continue;
            if (!roll(c.value(ch.tier(c)), rng)) continue;
            switch (c) {
                case CARNAGE:
                    if (!m.isDead()) break;
                    int dmg = carnageDamage(pc.level(), t.maxHealth());
                    for (int[] o : CARNAGE_OFFSETS) {
                        Monster n = lookup == null ? null : lookup.at(m.position().translate(o[0], o[1]));
                        if (n != null && n != m && !n.isDead()) out.add(new Proc(c, n, dmg, null));
                    }
                    if (out.isEmpty()) out.add(new Proc(c, m, 0, null)); // sam efekt (krew wokol ciala), bez celow
                    break;
                case OVERPOWER:
                    if (!m.isDead()) out.add(new Proc(c, m, overpowerDamage(pc.maxHealth(), t.maxHealth()), null));
                    break;
                case OVERFLUX:
                    if (!m.isDead()) out.add(new Proc(c, m, overfluxDamage(pc.maxMana(), t.maxHealth()), null));
                    break;
                case CRIPPLE:
                    if (!m.isDead()) {
                        m.paralyze(now + PARALYZE_MS);
                        out.add(new Proc(c, m, 0, null));
                    }
                    break;
                default:
                    if (c.elemental() && !m.isDead()) out.add(new Proc(c, m, elementalDamage(pc.level(), t.maxHealth()), c.damageType()));
            }
        }
        // Smiertelny chwyt (Canary: po kazdym trafieniu, osobny rzut)
        int fh = ch.active(Charm.FATAL_HOLD, t.key());
        if (fh > 0 && !m.isDead() && roll(Charm.FATAL_HOLD.value(fh), rng)) {
            m.holdFromFleeing(now + FATAL_HOLD_MS);
            out.add(new Proc(Charm.FATAL_HOLD, m, 0, null));
        }
        return out;
    }

    /** Wynik charmow obronnych przy ataku potwora. */
    public record Defense(Charm prevented, Charm parry, List<Charm> triggered, boolean invertManaDrain) {
        /** Atak nie zadaje obrazen (Unik albo Oczyszczenie). */
        public boolean noDamage() { return prevented != null; }
    }

    private static final Defense NONE = new Defense(null, null, java.util.Collections.emptyList(), false);

    /**
     * Atak potwora na gracza o obrazeniach dmg (po ochronach) i rodzaju type. Jak w Canary
     * Game::combatChangeHealth: najpierw Minor (Zastrzyk adrenaliny, Odretwienie; Oczyszczenie osobno - tu odpowiednik:
     * zywiolowe trafienie bez obrazen), potem Major (Unik - bez obrazen, Riposta - odbicie). Odwrocenie pustki dziala
     * przy wyssaniu many. Paraliz i przyspieszenie zaklada od razu.
     */
    public static Defense onMonsterAttack(PlayerCharacter pc, Monster m, DamageType type, int dmg, Random rng, long now) {
        if (dmg <= 0) return NONE;
        Charms ch = pc.charms();
        String key = m.type().key();
        Charm minor = ch.on(key, Charm.Category.MINOR);
        Charm major = ch.on(key, Charm.Category.MAJOR);
        if (minor == null && major == null) return NONE;
        List<Charm> fired = new ArrayList<>(2);
        Charm prevented = null;
        Charm parry = null;
        boolean invert = false;
        if (minor != null) {
            double chance = minor.value(ch.tier(minor));
            switch (minor) {
                case ADRENALINE_BURST:
                    if (roll(chance, rng)) {
                        pc.addHaste(ADRENALINE_HASTE_PERCENT, ADRENALINE_MS);
                        fired.add(minor);
                    }
                    break;
                case NUMB:
                    if (roll(chance, rng)) {
                        m.paralyze(now + PARALYZE_MS);
                        fired.add(minor);
                    }
                    break;
                case CLEANSE:
                    // Pixel nie ma stanow (trucizna, podpalenie...), ktore w Tibii daja zywiolowe ataki: oczyszczenie
                    // zdejmuje samo zywiolowe trafienie
                    if (type != DamageType.PHYSICAL && roll(chance, rng)) {
                        prevented = minor;
                        fired.add(minor);
                    }
                    break;
                case VOID_INVERSION:
                    if (type == DamageType.MANA_DRAIN && roll(chance, rng)) {
                        invert = true;
                        fired.add(minor);
                    }
                    break;
                default:
            }
        }
        if (major != null && prevented == null && !invert) {
            double chance = major.value(ch.tier(major));
            if (major == Charm.DODGE && roll(chance, rng)) {
                prevented = major;
                fired.add(major);
            } else if (major == Charm.PARRY && type != DamageType.MANA_DRAIN && roll(chance, rng)) {
                parry = major;
                fired.add(major);
            }
        }
        return fired.isEmpty() ? NONE : new Defense(prevented, parry, fired, invert);
    }

    /** Blogoslawienstwo: o ile procent mniej doswiadczenia traci postac zabita przez tego potwora. */
    public static double blessReduction(PlayerCharacter pc, MonsterType killer) { return value(pc, Charm.BLESS, killer); }

    /**
     * Szansa lupu (promile) z Patroszeniem i Skorowaniem. Patroszenie jak w Canary (generateLootRoll):
     * chance + ceil(chance x % / 100) dla produktow z potworow (u nas przedmioty kategorii MATERIAL).
     * Skorowanie (Pixel nie ma skorowania nozem): najrzadszy material potwora ("trofeum": skora, luska, esencja)
     * ma szanse x (1 + % / 100), jak premia Scavenge do szansy oskorowania.
     */
    public static int lootChance(PlayerCharacter pc, MonsterType t, LootEntry e, ItemRegistry items, int chance) {
        if (t == null || chance <= 0) return chance;
        Charms ch = pc.charms();
        int gut = ch.active(Charm.GUT, t.key());
        int scav = ch.active(Charm.SCAVENGE, t.key());
        if (gut == 0 && scav == 0) return chance;
        ItemType it = items.get(e.itemId());
        if (it == null || it.category() != ItemCategory.MATERIAL) return chance;
        long c = chance;
        if (gut > 0) c += (long) Math.ceil(chance * Charm.GUT.value(gut) / 100.0);
        if (scav > 0 && e == trophy(t, items)) c = Math.round(c * (1 + Charm.SCAVENGE.value(scav) / 100.0));
        return (int) Math.min(1000, c);
    }

    /** Trofeum potwora dla Skorowania: material z najmniejsza szansa (remis: drozszy); null, gdy brak materialow. */
    public static LootEntry trophy(MonsterType t, ItemRegistry items) {
        LootEntry best = null;
        for (LootEntry e : t.loot()) {
            ItemType it = items.get(e.itemId());
            if (it == null || it.category() != ItemCategory.MATERIAL) continue;
            if (best == null || e.chancePermille() < best.chancePermille()
                    || (e.chancePermille() == best.chancePermille() && it.value() > items.get(best.itemId()).value())) best = e;
        }
        return best;
    }
}
