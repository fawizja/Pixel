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

    public PlayerCharacter create(String name, Vocation vocation, Position startTemple) {
        PlayerCharacter pc = new PlayerCharacter(UUID.randomUUID().toString(), name, vocation,
                config.startLevel(), startTemple, items, config.startBankBalance(), config.startTown());
        for (String key : StarterKits.equipped(vocation)) {
            ItemStack s = ItemStack.of(items.byKey(key).id());
            EquipSlot slot = items.get(s.itemId()).slot();
            if (pc.equipment().get(slot) != null) throw new IllegalStateException("kit slot used twice: " + slot);
            pc.equipment().setDirect(slot, s);
        }
        InventoryResult r = Money.add(pc.backpack(), config.startGoldInBackpack());
        if (!r.ok()) throw new IllegalStateException("starting gold does not fit: " + r);
        for (StarterKits.Entry e : StarterKits.backpack(vocation)) {
            r = pc.backpack().add(new ItemStack(items.byKey(e.key()).id(), e.count()));
            if (!r.ok()) throw new IllegalStateException("starter kit does not fit: " + e.key());
        }
        return pc;
    }

    /** Podstawowe wyposazenie zalezne od profesji. */
    public static final class StarterKits {
        public record Entry(String key, int count) {}

        private StarterKits() {}

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
