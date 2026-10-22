package pl.fawizja.pixel.character;

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
     * Nowa postac na podanym poziomie (gra offline: {@link StartingConfig#offlineStartLevel()}). Powyzej zwyklego
     * poziomu startowego umiejetnosci sa podniesione do typowych dla tego poziomu ({@link #skillsForLevel}),
     * a HP i mana wynikaja z poziomu i sa pelne.
     */
    public PlayerCharacter create(String name, Vocation vocation, Position startTemple, int level) {
        PlayerCharacter pc = new PlayerCharacter(UUID.randomUUID().toString(), name, vocation,
                level, startTemple, items, config.startBankBalance(), config.startTown());
        if (level > BASE_LEVEL) skillsForLevel(pc);
        for (String key : StarterKits.equipped(vocation, level)) {
            ItemStack s = ItemStack.of(items.byKey(key).id());
            EquipSlot slot = items.get(s.itemId()).slot();
            if (pc.equipment().get(slot) != null) throw new IllegalStateException("kit slot used twice: " + slot);
            pc.equipment().setDirect(slot, s);
        }
        if (level >= StarterKits.VETERAN_LEVEL) pc.attachBag(); // weteran zaczyna z drugim plecakiem (40 miejsc)
        InventoryResult r = Money.add(pc.backpack(), config.startGoldInBackpack());
        if (!r.ok()) throw new IllegalStateException("starting gold does not fit: " + r);
        for (StarterKits.Entry e : StarterKits.backpack(vocation, level)) {
            r = pc.backpack().add(new ItemStack(items.byKey(e.key()).id(), e.count()));
            if (!r.ok()) throw new IllegalStateException("starter kit does not fit: " + e.key());
        }
        return pc;
    }

    /**
     * Umiejetnosci typowe dla poziomu postaci (wg progow z docs/GAME_BALANCE.md: poziom ~300 to bron ~90,
     * magia magow ~70, obrona ~80). Magowie: slaba bron i obrona, mocna magia; paladyn i mnich odwrotnie.
     */
    static void skillsForLevel(PlayerCharacter pc) {
        int lvl = pc.level();
        boolean mage = pc.vocation() == Vocation.DRUID || pc.vocation() == Vocation.SORCERER;
        int weapon = Math.min(Skills.MAX_SKILL, 10 + (int) (lvl * 0.27));
        int magic = Math.min(Skills.MAX_SKILL, (int) (lvl * 0.235));
        int shield = Math.min(Skills.MAX_SKILL, 10 + (int) (lvl * 0.24));
        Skill main = pc.vocation() == Vocation.PALADIN ? Skill.DISTANCE : pc.vocation() == Vocation.MONK ? Skill.FIST : null;
        for (Skill s : Skill.values()) {
            int v;
            if (s == Skill.MAGIC) v = mage ? magic : magic / 4;
            else if (s == Skill.SHIELDING) v = mage ? Math.min(shield, 10 + lvl / 20) : shield;
            else if (s == main) v = weapon;
            else if (s == Skill.FISHING) v = Skills.START_SKILL;
            else v = Math.min(weapon, mage ? 30 : 10 + lvl / 10);
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
                default: throw new IllegalArgumentException(v.name());
            }
        }

        public static List<Entry> backpack(Vocation v, int level) {
            if (level < VETERAN_LEVEL) return backpack(v);
            List<Entry> common = Arrays.asList(new Entry("torch", 1), new Entry("rope", 1), new Entry("shovel", 1),
                    new Entry("fishing_rod", 1), new Entry("worm", 10), new Entry("bread", 5));
            switch (v) {
                case PALADIN: return concat(common, new Entry("spear", 20), new Entry("great_spirit_potion", 30),
                        new Entry("strong_mana_potion", 10));
                case DRUID:
                case SORCERER: return concat(common, new Entry("health_potion", 10), new Entry("ultimate_mana_potion", 30),
                        new Entry("blank_rune", 10), new Entry("ultimate_healing_rune", 5), new Entry("great_fireball_rune", 20));
                case MONK: return concat(common, new Entry("ultimate_health_potion", 30), new Entry("mana_potion", 10));
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
