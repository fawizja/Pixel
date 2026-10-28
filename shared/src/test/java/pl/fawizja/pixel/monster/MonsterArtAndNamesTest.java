package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;

/**
 * Kazdy potwor z monsters.csv ma grafike (postoj i dwie klatki kroku z OpenTibia Sprite Pack) i nie nosi nazwy
 * znanego potwora z Tibii. Gra nie jest na licencji GPL i nie uzywa nazw ani projektow CipSoftu.
 */
class MonsterArtAndNamesTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    /**
     * Charakterystyczne nazwy potworow i bossow z Tibii (~150). Pospolite slowa, ktore Tibia dzieli z cala fantastyka
     * i ktore nasze starsze potwory maja w kluczach (wilk, szkielet, mumia, goblin, ork...), nie sa tu wpisane.
     */
    static final Set<String> TIBIA_NAMES = Stream.of(
            "demon", "orshabaal", "ferumbras", "morgaroth", "ghazbaran", "zushuka", "gaz'haragoth", "omrafir", "apocalypse",
            "infernatil", "bazir", "verminor", "ashfalor", "pumin", "tafariel", "dracola", "hellgorak", "zugurosh",
            "hydra", "behemoth", "warlock", "medusa", "serpent spawn", "hellhound", "hellfire fighter", "juggernaut",
            "grim reaper", "destroyer", "dark torturer", "diabolic imp", "plaguesmith", "undead dragon", "dragon lord",
            "frost dragon", "ghastly dragon", "draken elite", "draken spellweaver", "draken warmaster", "lizard chosen",
            "lizard templar", "lizard sentinel", "lizard high guard", "wyrm", "elder wyrm", "hero", "black knight",
            "vampire lord", "vampire bride", "vampire viscount", "lich", "banshee", "betrayed wraith", "blightwalker",
            "phantasm", "nightmare", "nightmare scion", "defiler", "son of verminor", "bog raider", "spectre", "lost soul",
            "souleater", "retching horror", "choking fear", "frazzlemaw", "silencer", "guzzlemaw", "hellspawn",
            "minotaur", "minotaur archer", "minotaur guard", "minotaur mage", "rotworm", "carrion worm", "rotworm queen",
            "larva", "scarab", "ancient scarab", "tarantula", "giant spider", "the old widow", "crystal spider",
            "wailing widow", "dwarf guard", "dwarf geomancer", "dworc voodoomaster", "amazon", "valkyrie",
            "barbarian brutetamer", "barbarian skullhunter", "ice witch", "dark monk", "nomad", "smuggler", "poacher",
            "stone golem", "bonebeast", "lost basher", "wisp", "centipede", "ghoul", "crypt shambler", "gozzler",
            "stalker", "cult believer", "cult enforcer", "acolyte of the cult", "novice of the cult",
            "enlightened of the cult", "fury", "dark apprentice", "dark magician", "blood crab", "quara constrictor",
            "quara hydromancer", "deepling warrior", "sea serpent", "thornback tortoise", "terramite", "lancer beetle",
            "sandcrawler", "gnarlhound", "kongra", "merlkin", "sibang", "chakoya toolshaper", "nightstalker",
            "massive fire elemental", "massive energy elemental", "energy elemental", "earth elemental",
            "water elemental", "fire devil", "dharalion", "ghostly apparition", "kraknaknork", "lethal lissy",
            "tiquandas revenge", "the horned fox", "rukor zad", "yakchal", "barbaria", "the handmaiden", "the imperor",
            "the plasmother", "mr. punish", "countess sorrow", "massacre", "the pale count", "zulazza the corruptor",
            "bullwark", "gorgo", "mazoran", "ragiaz", "razzagorn", "shulgrax", "tarbaz", "zamulosh", "plagirath",
            "grand master oberon", "scarlett etzel", "chizzoron the distorter", "morshabaal", "drume",
            "urmahlullu the weakened", "faceless bane", "bakragore", "the time guardian", "gnomevil", "abyssador",
            "deathstrike", "warlord ruzad", "bloodback", "deathbringer", "dreadmaw", "bonelord", "elder bonelord",
            "beholder", "cyclops", "dragon hatchling", "swamp troll", "frost troll", "island troll", "demon skeleton",
            "pirate buccaneer", "pirate corsair", "pirate marauder", "pirate skeleton", "tortoise", "toad", "war wolf",
            "werewolf", "winter wolf", "gargoyle", "devourer", "crawler", "hunter", "assassin", "witch", "priestess")
            .collect(Collectors.toUnmodifiableSet());

    static Path artDir() {
        for (String p : new String[] {"../assets/art/monsters", "assets/art/monsters"}) {
            if (Files.isDirectory(Path.of(p))) return Path.of(p);
        }
        throw new IllegalStateException("assets/art/monsters not found");
    }

    @Test
    void everyMonsterHasASpriteWithWalkFrames() {
        Path dir = artDir();
        List<String> missing = new ArrayList<>();
        for (MonsterType t : REG.all()) {
            for (String suffix : new String[] {"", "_walk1", "_walk2"}) {
                if (!Files.isRegularFile(dir.resolve(t.key() + suffix + ".png"))) missing.add(t.key() + suffix + ".png");
            }
        }
        assertEquals(List.of(), missing, "monsters without OTSP art");
    }

    @Test
    void noMonsterIsNamedAfterAWellKnownTibiaMonster() {
        assertTrue(TIBIA_NAMES.size() >= 150, "name list size " + TIBIA_NAMES.size());
        for (MonsterType t : REG.all()) {
            String key = t.key().replace('_', ' ').toLowerCase(Locale.ROOT);
            String name = t.name().toLowerCase(Locale.ROOT);
            String shortName = name.contains(",") ? name.substring(0, name.indexOf(',')).trim() : name;
            for (String candidate : List.of(key, name, shortName)) {
                assertFalse(TIBIA_NAMES.contains(candidate), t.key() + " is named like a Tibia monster: " + candidate);
            }
        }
    }

    @Test
    void theNewMonstersSpreadOverLevelsBiomesAndBehaviours() {
        // potwory z danych Canary (2026-10-28): wszystkie pasma poziomow od 5 do 1000 maja wiecej niz jeden gatunek
        int[] bands = {5, 25, 60, 150, 300, 500, 750, 1001};
        for (int i = 0; i + 1 < bands.length; i++) {
            int lo = bands[i], hi = bands[i + 1];
            long n = REG.all().stream().filter(t -> !t.boss() && t.level() >= lo && t.level() < hi).count();
            assertTrue(n >= 4, "band " + lo + "-" + hi + " has " + n + " monsters");
        }
        for (String biome : List.of("underground", "temperate", "north", "equatorial", "desert")) {
            long n = REG.all().stream().filter(t -> !t.boss() && biome.equals(t.biome())).count();
            assertTrue(n >= 8, biome + " has " + n + " monsters");
        }
        for (String boss : List.of("wroniec", "szlamiara", "velmira", "suweren")) assertTrue(REG.get(boss).boss(), boss);
    }
}
