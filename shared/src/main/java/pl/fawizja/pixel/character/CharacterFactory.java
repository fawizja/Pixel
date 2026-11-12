package pl.fawizja.pixel.character;

import pl.fawizja.pixel.i18n.Messages;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Tworzy nowe postacie zgodnie z konfiguracja startowa i zestawem profesji. */
public final class CharacterFactory {

    private final StartingConfig config;
    private final ItemRegistry items;

    public CharacterFactory(StartingConfig config, ItemRegistry items) {
        this.config = config;
        this.items = items;
    }

    /** Poziom, od ktorego umiejetnosci zaczynaja sie od wartosci bazowych (wyzej - typowe dla poziomu). */
    static final int BASE_LEVEL = 8;

    public PlayerCharacter create(String name, Vocation vocation, Position startTemple) {
        return create(name, vocation, startTemple, config.startLevel());
    }

    /**
     * Nowa postac w miescie startowym wybranym przez gracza; pusty albo nieznany wybor = miasto domyslne
     * ({@link StartingConfig#startTown()}). Pozycja startowa to swiatynia tego miasta.
     */
    public PlayerCharacter createIn(pl.fawizja.pixel.world.WorldMap world, String name, Vocation vocation, String town, int level) {
        String home = StartingTowns.resolve(world, town, config.startTown());
        return create(name, vocation, home, world.templeOf(home), level);
    }

    /**
     * Nowa postac na podanym poziomie (gra offline: {@link StartingConfig#offlineStartLevel()}). Powyzej zwyklego
     * poziomu startowego umiejetnosci sa podniesione do typowych dla tego poziomu ({@link #skillsForLevel}),
     * a HP i mana wynikaja z poziomu i sa pelne.
     */
    public PlayerCharacter create(String name, Vocation vocation, Position startTemple, int level) {
        return create(name, vocation, config.startTown(), startTemple, level);
    }

    /**
     * Nowa postac w wybranym miescie startowym (ekran tworzenia postaci, jak w Tibii): homeTown to klucz miasta
     * (male litery), startTemple jego swiatynia. Wybor sprawdza wywolujacy ({@link StartingTowns#resolve}).
     */
    public PlayerCharacter create(String name, Vocation vocation, String homeTown, Position startTemple, int level) {
        PlayerCharacter pc = new PlayerCharacter(UUID.randomUUID().toString(), name, vocation,
                level, startTemple, items, config.startBankBalance(), homeTown);
        if (level > BASE_LEVEL) skillsForLevel(pc);
        for (String key : StarterKits.equipped(vocation, level)) {
            ItemStack s = ItemStack.of(items.byKey(key).id());
            EquipSlot slot = items.get(s.itemId()).slot();
            if (pc.equipment().get(slot) != null) throw new IllegalStateException("kit slot used twice: " + slot);
            pc.equipment().setDirect(slot, s);
        }
        if (level >= StarterKits.VETERAN_LEVEL) pc.setBackpackSlots(pl.fawizja.pixel.inventory.ContainerType.TRAVELER.slots()); // weteran zaczyna z plecakiem podroznika (24 pola)
        InventoryResult r = Money.add(pc.backpack(), config.startGoldInBackpack());
        if (!r.ok()) throw new IllegalStateException("starting gold does not fit: " + r);
        for (StarterKits.Entry e : StarterKits.backpack(vocation, level)) {
            ItemStack stack = new ItemStack(items.byKey(e.key()).id(), e.count());
            r = pc.backpack().add(stack);
            if (!r.ok()) throw new IllegalStateException("starter kit does not fit: " + e.key());
        }
        return pc;
    }

    /**
     * Umiejetnosci, jakie postac tej profesji ma na tym poziomie z samego polowania (wyniki
     * HuntBalanceSimulationTest#skillsFollowTheLevel z Balance.HUNT_TRY_FACTOR i etapami serwera, polowanie od
     * poziomu 1 na potwory swojego poziomu). Kolumny: poziom, glowna umiejetnosc walki (rycerz: bron wrecz - miecz,
     * topor i maczuga; paladyn: dystans; mnich: piesci; magowie: brak), tarcza, magia. Miedzy wierszami liniowo,
     * powyzej ostatniego wiersza tak jak miedzy dwoma ostatnimi (do {@link Skills#MAX_SKILL}). 0.240: przeliczone po
     * czarach i runach z Tibii (wiersze do 200 z symulacji, wyzsze przesuniete o roznice na 200).
     */
    static final int[][] KNIGHT_SKILLS = {{8, 10, 10, 0}, {20, 52, 48, 4}, {35, 60, 56, 6}, {50, 65, 60, 8},
            {75, 77, 75, 9}, {100, 81, 76, 9}, {150, 89, 84, 11}, {200, 97, 96, 12}, {250, 103, 103, 12},
            {300, 106, 105, 13}, {400, 114, 113, 14}, {500, 120, 114, 14}, {600, 124, 119, 15}};
    static final int[][] PALADIN_SKILLS = {{8, 10, 10, 0}, {20, 53, 39, 9}, {35, 65, 54, 16}, {50, 71, 61, 19},
            {75, 82, 77, 25}, {100, 85, 77, 27}, {150, 94, 86, 30}, {200, 102, 97, 32}, {250, 106, 102, 35},
            {300, 110, 105, 37}, {400, 117, 112, 40}, {500, 122, 113, 42}, {600, 127, 118, 44}};
    static final int[][] MONK_SKILLS = {{8, 10, 10, 0}, {20, 48, 31, 13}, {35, 59, 36, 20}, {50, 65, 40, 23},
            {75, 78, 48, 30}, {100, 82, 48, 33}, {150, 92, 54, 38}, {200, 100, 61, 41}, {250, 105, 65, 44},
            {300, 109, 67, 46}, {400, 116, 72, 50}, {500, 121, 72, 52}, {600, 126, 75, 55}};
    /**
     * Czarodziej i druid (srednia obu; od odpornosci jak w Tibii druid poluje wiecej czarami - potwory odporne na
     * ziemie - i ma w symulacji do 3 poziomow magii wiecej): tarcza zostaje 10.
     */
    static final int[][] MAGE_SKILLS = {{8, 10, 10, 0}, {20, 10, 10, 33}, {35, 10, 10, 43}, {50, 10, 10, 50},
            {75, 10, 10, 67}, {100, 10, 10, 73}, {150, 10, 10, 84}, {200, 10, 10, 92}, {250, 10, 10, 101},
            {300, 10, 10, 108}, {400, 10, 10, 118}, {500, 10, 10, 124}, {600, 10, 10, 130}};

    static int[][] skillTable(Vocation v) {
        switch (v) {
            case KNIGHT: return KNIGHT_SKILLS;
            case PALADIN: return PALADIN_SKILLS;
            case MONK: return MONK_SKILLS;
            default: return MAGE_SKILLS;
        }
    }

    /** Wartosc kolumny col tabeli dla poziomu: interpolacja liniowa, ekstrapolacja ostatniego odcinka. */
    static int typicalSkill(int[][] t, int level, int col) {
        int i = 0;
        while (i + 2 < t.length && level > t[i + 1][0]) i++;
        double f = (level - t[i][0]) / (double) (t[i + 1][0] - t[i][0]);
        double v = t[i][col] + Math.max(0, f) * (t[i + 1][col] - t[i][col]);
        return (int) Math.max(0, Math.min(Skills.MAX_SKILL, Math.round(v)));
    }

    /**
     * Umiejetnosci typowe dla poziomu postaci (tabele wyzej, z symulacji polowania): glowna umiejetnosc profesji,
     * tarcza i magia; pozostale zostaja na poziomie startowym. Poziomy nigdy nie sa obnizane.
     */
    static void skillsForLevel(PlayerCharacter pc) {
        int lvl = pc.level();
        int[][] t = skillTable(pc.vocation());
        int main = typicalSkill(t, lvl, 1), shield = typicalSkill(t, lvl, 2), magic = typicalSkill(t, lvl, 3);
        for (Skill s : Skill.values()) {
            int v;
            if (s == Skill.MAGIC) v = magic;
            else if (s == Skill.SHIELDING) v = shield;
            else if (pc.vocation() == Vocation.KNIGHT && s.isMeleeWeapon()) v = main; // miecz, topor i maczuga
            else if (pc.vocation() == Vocation.PALADIN && s == Skill.DISTANCE) v = main;
            else if (pc.vocation() == Vocation.MONK && s == Skill.FIST) v = main;
            else v = Skills.START_SKILL;
            pc.skills().set(s, Math.max(pc.skills().level(s), v), 0);
        }
    }

    /**
     * Wyposazenie startowe zalezne od profesji i poziomu: do poziomu 249 podstawowe (skora, prosta bron),
     * od poziomu {@link #VETERAN_LEVEL} (start serwera na poziomie 300) obsydianowe z bronia profesji i mocniejszymi
     * miksturami - tak jak postac, ktora doszla do tego poziomu sama.
     */
    public static final class StarterKits {
        public record Entry(String key, int count) {}

        /** Od tego poziomu startowy zestaw jest obsydianowy (minimalny poziom tych przedmiotow). */
        public static final int VETERAN_LEVEL = 250;

        private StarterKits() {}

        public static List<String> equipped(Vocation v, int level) {
            if (level < VETERAN_LEVEL) return equipped(v);
            List<String> armor = Arrays.asList("obsidian_helmet", "obsidian_armor", "obsidian_legs", "leather_boots");
            switch (v) {
                case PALADIN: return concat(armor, "magma_spear", "obsidian_shield");
                case DRUID: return concat(armor, "rod_of_hoarfrost");
                case SORCERER: return concat(armor, "obsidian_wand");
                case MONK: return concat(armor, "molten_knuckles");
                case KNIGHT: return concat(armor, "magma_cleaver", "obsidian_shield");
                default: throw new IllegalArgumentException(v.name());
            }
        }

        public static List<Entry> backpack(Vocation v, int level) {
            if (level < VETERAN_LEVEL) return backpack(v);
            List<Entry> common = Arrays.asList(new Entry("torch", 1), new Entry("rope", 1), new Entry("shovel", 1),
                    new Entry("fishing_rod", 1), new Entry("worm", 10), new Entry("bread", 5));
            switch (v) {
                case PALADIN: return concat(common, new Entry("spear", 20), new Entry("great_spirit_potion", 30),
                        new Entry(Messages.get("character_factory.strong_mana_potion"), 10));
                case DRUID:
                case SORCERER: return concat(common, new Entry("health_potion", 10), new Entry(Messages.get("character_factory.ultimate_mana_potion"), 30),
                        new Entry("blank_rune", 10), new Entry("ultimate_healing_rune", 5), new Entry("great_fireball_rune", 20));
                case MONK: return concat(common, new Entry("ultimate_health_potion", 30), new Entry("mana_potion", 10));
                case KNIGHT: return concat(common, new Entry("ultimate_health_potion", 40), new Entry("mana_potion", 10));
                default: throw new IllegalArgumentException(v.name());
            }
        }

        public static List<String> equipped(Vocation v) {
            List<String> armor = Arrays.asList("leather_helmet", "leather_armor", "leather_legs", "leather_boots");
            switch (v) {
                case PALADIN: return concat(armor, "hunting_spear", "wooden_shield");
                case DRUID: return concat(armor, "thornroot_rod");
                case SORCERER: return concat(armor, "wand_of_embers");
                case MONK: return concat(armor, "cloth_wraps");
                case KNIGHT: return concat(armor, "hand_axe", "wooden_shield");
                default: throw new IllegalArgumentException(v.name());
            }
        }

        public static List<Entry> backpack(Vocation v) {
            List<Entry> common = Arrays.asList(new Entry("torch", 1), new Entry("rope", 1),
                    new Entry("fishing_rod", 1), new Entry("worm", 10), new Entry("bread", 5));
            switch (v) {
                case PALADIN: return concat(common, new Entry("spear", 20), new Entry("health_potion", 10), new Entry("mana_potion", 5));
                case DRUID:
                case SORCERER: return concat(common, new Entry("health_potion", 5), new Entry("mana_potion", 15), new Entry("blank_rune", 5));
                case MONK: return concat(common, new Entry("health_potion", 15), new Entry("mana_potion", 3));
                case KNIGHT: return concat(common, new Entry("health_potion", 20), new Entry("mana_potion", 2));
                default: throw new IllegalArgumentException(v.name());
            }
        }

        @SafeVarargs
        private static <T> List<T> concat(List<T> base, T... more) {
            java.util.ArrayList<T> l = new java.util.ArrayList<>(base);
            l.addAll(Arrays.asList(more));
            return Collections.unmodifiableList(l);
        }
    }
}
