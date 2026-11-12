package pl.fawizja.pixel.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.monster.LootEntry;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.npc.Shop;
import pl.fawizja.pixel.npc.ShopOffer;
import pl.fawizja.pixel.npc.ShopRegistry;

/** Przedmioty Hinasa (id 256.., tools/art/hinas_import.py): grafika, miejsce w atlasie i skad je wziac. */
class HinasItemsTest {

    /** TileArt.ITEM_LIMIT (core): przedmioty od id 256 w osobnym bloku komorek atlasu. */
    static final int ITEM_LIMIT = 768;

    @Test
    void everyItemFitsTheAtlas() {
        for (ItemType t : TestData.ITEMS.all()) assertTrue(t.id() > 0 && t.id() < ITEM_LIMIT, t.key() + " id " + t.id());
    }

    @Test
    void hinasItemsHaveArtAndASource() {
        Path dir = null;
        for (String p : new String[] {"../assets/art/items", "assets/art/items"}) {
            if (Files.isDirectory(Paths.get(p))) dir = Paths.get(p);
        }
        assertTrue(dir != null, "brak assets/art/items");
        Set<Integer> sources = new HashSet<>();
        for (MonsterType m : MonsterRegistry.loadDefault(TestData.ITEMS).all()) for (LootEntry e : m.loot()) sources.add(e.itemId());
        for (Shop s : ShopRegistry.loadDefault(TestData.ITEMS).all()) for (ShopOffer o : s.offers()) if (o.sold()) sources.add(o.itemId());
        int n = 0;
        for (ItemType t : TestData.ITEMS.all()) {
            if (t.id() < 256 || t.category() == ItemCategory.RUNE) continue; // runy 0.240 (id 400+) to nie przedmioty Hinasa
            if (pl.fawizja.pixel.inventory.ContainerType.ofItem(t.key()) != null) continue; // wieksze plecaki: nagrody za zadania, bez slotu ekwipunku
            if (t.key().equals("amulet_of_loss")) continue; // Amulet Straty: wlasna ikona rysowana proceduralnie (tools/art/amulet_of_loss_art.py)
            n++;
            assertTrue(Files.exists(dir.resolve(t.key() + ".png")), "grafika " + t.key());
            assertTrue(sources.contains(t.id()), t.key() + ": ani lup, ani sklep");
            assertTrue(t.minLevel() > 0 && t.slot() != EquipSlot.NONE, t.key());
        }
        assertEquals(137, n, "przedmioty Hinasa");
    }

    /** Ksiegi czarow to tarcze magow, amulety nie biora udzialu w Kuzni ani nasyceniach. */
    @Test
    void spellbooksAndAmulets() {
        ItemType book = TestData.ITEMS.byKey("moon_spellbook");
        assertEquals(ItemCategory.SHIELD, book.category());
        assertTrue(book.usableBy(pl.fawizja.pixel.vocation.Vocation.SORCERER) && book.usableBy(pl.fawizja.pixel.vocation.Vocation.DRUID));
        assertTrue(!book.usableBy(pl.fawizja.pixel.vocation.Vocation.KNIGHT));
        ItemType amulet = TestData.ITEMS.byKey("phoenix_amulet");
        assertEquals(EquipSlot.AMULET, amulet.slot());
        assertEquals(0, Forge.classOf(amulet));
        assertEquals(6, amulet.bonus().protection(pl.fawizja.pixel.combat.DamageType.FIRE));
    }
}
