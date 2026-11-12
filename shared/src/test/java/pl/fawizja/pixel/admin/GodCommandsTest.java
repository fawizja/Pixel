package pl.fawizja.pixel.admin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.i18n.Language;
import pl.fawizja.pixel.i18n.Messages;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.net.ChatCommands;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Polecenia GOD w grze lokalnej (ta sama logika wykonuje je na serwerze): poziomy dostepu i kazde glowne polecenie. */
class GodCommandsTest {

    @BeforeEach
    void polish() { Messages.setLanguage(Language.PL); }

    /** Host testowy: wlasna postac jest jedynym graczem; wpisy audytu zbierane do listy. */
    private static final class Host implements GodCommands.Context {
        final GameSession session;
        AccessLevel level;
        final List<String> audit = new ArrayList<>();
        final List<String> broadcasts = new ArrayList<>();
        long now = 1000;
        int saves;

        Host(GameSession s, AccessLevel level) {
            this.session = s;
            this.level = level;
        }

        public AccessLevel level() { return level; }
        public GameSession session() { return session; }
        public long now() { return now; }
        public boolean online() { return false; }
        public GameSession find(String name) { return name.equalsIgnoreCase(session.character().name()) ? session : null; }
        public void audit(String line) { audit.add(line); }
        public void broadcast(String text) { broadcasts.add(text); }
        public void save() { saves++; }

        /** Wykonuje tekst czatu tak jak klient: parser czatu -> wykonanie. */
        String run(String chat) {
            String[] msg = ChatCommands.toMessage(chat);
            assertNotNull(msg, "not parsed: " + chat);
            long before = session.log().total();
            GodCommands.execute(this, msg);
            StringBuilder sb = new StringBuilder();
            for (EventLog.Entry e : session.log().latest((int) Math.min(session.log().size(), session.log().total() - before))) {
                sb.append(e.text()).append('\n');
            }
            return sb.toString().trim();
        }
    }

    private static Host host(AccessLevel level) {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.setPosition(TestData.world().towns().get(0).temple());
        pc.setAccessLevel(level);
        MonsterManager mm = new MonsterManager(TestData.world(), MonsterRegistry.loadDefault(TestData.ITEMS), Collections.emptyList(), new Random(2));
        GameSession s = new GameSession(TestData.ITEMS, TestData.world(), pc, mm, new Random(1), 8);
        return new Host(s, level);
    }

    @Test
    void levelsAreOrderedAndParsed() {
        assertTrue(AccessLevel.GOD.atLeast(AccessLevel.GAMEMASTER));
        assertTrue(AccessLevel.GAMEMASTER.atLeast(AccessLevel.TUTOR));
        assertFalse(AccessLevel.TUTOR.atLeast(AccessLevel.GAMEMASTER));
        assertEquals(AccessLevel.GOD, AccessLevel.parse("God"));
        assertEquals(AccessLevel.GAMEMASTER, AccessLevel.parse("gm"));
        assertEquals(AccessLevel.TUTOR, AccessLevel.parse("TUTOR"));
        assertNull(AccessLevel.parse("king"));
        assertEquals(AccessLevel.GOD, AccessLevel.fromTag("GOD"));
        assertEquals(AccessLevel.PLAYER, AccessLevel.fromTag("-"));
    }

    @Test
    void aPlayerCannotRunAnyGodCommandAndTheRefusalIsAudited() {
        Host h = host(AccessLevel.PLAYER);
        long gold = h.session.gold();
        assertTrue(h.run("/i miecz").contains("Brak uprawnień"));
        assertTrue(h.run("/level 500").contains("Brak uprawnień"));
        assertTrue(h.run("/money 5k").contains("Brak uprawnień"));
        assertEquals(gold, h.session.gold());
        int level = h.session.character().level();
        assertEquals(level, h.session.character().level(), "level untouched");
        assertTrue(h.audit.stream().allMatch(l -> l.contains("REFUSED")), h.audit.toString());
        assertEquals(3, h.audit.size());
    }

    @Test
    void ghostAndGodModeNeedGodAndStopDamageAndMonsterTargeting() {
        Host h = host(AccessLevel.GAMEMASTER);
        PlayerCharacter pc = h.session.character();
        pc.setGhost(true);
        pc.setGodMode(true);
        assertFalse(pc.isGhost(), "a game master cannot be a ghost");
        assertFalse(pc.isGodMode());

        Host g = host(AccessLevel.GOD);
        PlayerCharacter god = g.session.character();
        assertFalse(god.isInvisible());
        assertTrue(g.run("/ghost").contains("Duch włączony"));
        assertTrue(god.isGhost() && god.isInvisible());
        int hp = god.health();
        assertEquals(0, god.damage(50), "ghost takes no damage");
        assertEquals(hp, god.health());
        assertFalse(god.breakInvisibility(), "attacking does not end a GOD ghost");
        g.run("/ghost");
        assertFalse(god.isGhost());
        assertTrue(god.damage(5) > 0);
        g.run("/godmode");
        assertTrue(god.isGodMode());
        assertEquals(0, god.damage(500));
        g.run("/godmode off");
        assertFalse(god.isGodMode());
        // utrata poziomu GOD zdejmuje ducha i tryb boga
        g.run("/ghost");
        god.setAccessLevel(AccessLevel.PLAYER);
        assertFalse(god.isGhost());
        assertEquals(AccessLevel.PLAYER, god.accessLevel());
    }

    /** Pole przechodnie poza strefa ochronna (potwory nie wchodza do swiatyn). */
    private static Position outsideProtection() {
        Position t = TestData.world().towns().get(0).temple();
        for (int r = 3; r < 40; r++) {
            for (int dx = -r; dx <= r; dx++) {
                Position q = t.translate(dx, r);
                if (TestData.world().isWalkable(q) && !TestData.world().isProtectionZone(q)
                        && TestData.world().isWalkable(q.translate(1, 0)) && TestData.world().isWalkable(q.translate(0, 1))
                        && !TestData.world().isProtectionZone(q.translate(1, 0)) && !TestData.world().isProtectionZone(q.translate(0, 1))) return q;
            }
        }
        throw new IllegalStateException("no tile outside the protection zone");
    }

    @Test
    void itemAndMoneyAndMonsterAndHeal() {
        Host h = host(AccessLevel.GOD);
        PlayerCharacter pc = h.session.character();
        pc.setPosition(outsideProtection());
        int rope = TestData.ITEMS.byKey("rope").id();
        int before = pc.backpack().count(rope);
        assertTrue(h.run("/i " + TestData.ITEMS.byKey("rope").polishName()).contains("Stworzono"));
        assertEquals(before + 1, pc.backpack().count(rope));
        int torch = TestData.ITEMS.byKey("torch").id();
        int torches = pc.backpack().count(torch);
        h.run("/i " + torch + " 5");
        assertEquals(torches + 5, pc.backpack().count(torch), "numeric id and a count");
        assertTrue(h.run("/i nie-istnieje-takie").contains("Nie znaleziono przedmiotu"));
        long gold = h.session.gold();
        h.run("/money 5k");
        assertEquals(gold + 5000, h.session.gold());

        int wolves = (int) h.session.monsters().monsters().stream().filter(m -> m.type().key().equals("wolf")).count();
        assertTrue(h.run("/m wilk").contains("Przywołano"));
        assertEquals(wolves + 1, h.session.monsters().monsters().stream().filter(m -> m.type().key().equals("wolf")).count());
        assertTrue(h.run("/m zzzz").contains("Nie znaleziono potwora"));
        assertEquals(1, h.session.monsters().near(pc.position(), 6).size());
        assertTrue(h.run("/kill").contains("Zabito potworów: 1"));
        assertTrue(h.session.monsters().near(pc.position(), 6).isEmpty());

        pc.damage(40);
        h.run("/sethp 10");
        assertEquals(10, pc.health());
        h.run("/setmana 3");
        assertEquals(3, pc.mana());
        h.run("/heal");
        assertEquals(pc.maxHealth(), pc.health());
        assertEquals(pc.maxMana(), pc.mana());
    }

    @Test
    void levelExperienceSkillAndSpeed() {
        Host h = host(AccessLevel.GOD);
        PlayerCharacter pc = h.session.character();
        h.run("/level 120");
        assertEquals(120, pc.level());
        assertEquals(pc.maxHealth(), pc.health());
        h.run("/level 20");
        assertEquals(20, pc.level(), "a GOD can also lower the level");
        int lvl = pc.level();
        h.run("/addexp 100000");
        assertTrue(pc.level() > lvl);
        int sword = pc.skills().level(Skill.SWORD);
        h.run("/addskill miecz 15");
        assertEquals(sword + 15, pc.skills().level(Skill.SWORD));
        h.run("/addskill ml 3");
        assertTrue(h.run("/addskill xyz 3").contains("Nieznana umiejętność"));
        h.run("/speed 400");
        assertEquals(400, pc.speed(), 12, "haste is whole percents, so the speed is approximate");
        h.run("/speed 0");
        assertEquals(pl.fawizja.pixel.balance.Balance.speed(pc.level()), pc.speed());
        assertTrue(h.run("/level 99999").contains("Podaj poziom"));
    }

    /** Pole przechodnie na powierzchni, nad ktorym jest przechodnie pole pietra +1 (wnetrze budynku). */
    private static Position columnWithUpperFloor() {
        pl.fawizja.pixel.world.WorldMap w = TestData.world();
        for (int y = 0; y < w.height(); y++) {
            for (int x = 0; x < w.width(); x++) {
                Position g = new Position(x, y, 0);
                if (w.isWalkable(g) && w.isWalkable(g.withFloor(1)) && w.isWalkable(g.withFloor(-1)) && w.transitionAt(g) == null
                        && w.transitionAt(g.withFloor(1)) == null && w.transitionAt(g.withFloor(-1)) == null) return g;
            }
        }
        throw new IllegalStateException("no column with an upper and a lower floor");
    }

    @Test
    void teleportCommandsAndPosition() {
        Host h = host(AccessLevel.GOD);
        GameSession s = h.session;
        Position start = s.character().position();
        assertTrue(h.run("/pos").startsWith("Pozycja: " + start.x() + "," + start.y() + "," + start.z()));
        Position target = start.translate(3, 0);
        String said = h.run("/goto " + target.x() + "," + target.y() + "," + target.z());
        assertTrue(said.contains("Teleport"), said);
        assertTrue(s.character().position().distanceTo(target) <= 3, "lands next to the requested tile");
        h.run("/goto " + start.x() + " " + start.y());
        assertEquals(start.z(), s.character().position().z());
        String town = TestData.world().towns().get(0).name();
        h.run("/goto " + town);
        h.run("/t");
        assertEquals(TestData.world().templeOf(s.character().homeTown()).z(), s.character().position().z());
        assertTrue(h.run("/t Nieistniejace").contains("Nie ma miasta"));
        h.run("/t " + town);
        assertTrue(s.character().position().distanceTo(TestData.world().town(town).temple()) <= 3);
        Position before = columnWithUpperFloor();
        s.teleport(before, 0);
        h.run("/up");
        assertEquals(before.z() + 1, s.character().position().z());
        h.run("/down");
        assertEquals(before.z(), s.character().position().z());
        h.run("/a 2");
        assertTrue(s.character().position().distanceTo(before) <= 2);
        assertTrue(h.run("/goto Nikt").contains("Nie znaleziono"));
        assertTrue(h.run("/c Nikt").contains("Nie ma w grze gracza"));
    }

    @Test
    void premiumPromoteBlessFlagInfoAndClean() {
        Host h = host(AccessLevel.GOD);
        PlayerCharacter pc = h.session.character();
        String me = pc.name();
        h.run("/premium " + me + " 30");
        assertTrue(pc.isPremium());
        h.run("/premium 0");
        assertFalse(pc.isPremium());
        h.run("/promote");
        assertTrue(pc.isPromoted());
        h.run("/bless " + me);
        assertEquals(pl.fawizja.pixel.character.Blessing.values().length, pc.blessingCount() + 1,
                "five regular blessings plus Twist of Fate counted separately");
        assertTrue(pc.boughtBlessing(pl.fawizja.pixel.character.Blessing.TWIST));
        assertTrue(h.run("/flag star_sealed").contains("star_sealed"));
        assertTrue(pc.hasQuestFlag(pl.fawizja.pixel.quest.StarQuest.FLAG_SEALED));
        h.run("/flag " + me + ", boss_done");
        assertTrue(pc.hasQuestFlag("boss_done"));
        h.run("/flag boss_done off");
        assertFalse(pc.hasQuestFlag("boss_done"));
        assertTrue(h.run("/flag Zly Format!").contains("Użycie"));
        String info = h.run("/info");
        assertTrue(info.contains(me) && info.contains("HP") && info.contains("Poziom dostępu: GOD"), info);

        Position here = pc.position();
        h.session.ground().place(here.translate(2, 0), new pl.fawizja.pixel.item.ItemStack(TestData.ITEMS.byKey("rope").id(), 1));
        h.session.ground().place(here.translate(0, 3), new pl.fawizja.pixel.item.ItemStack(TestData.ITEMS.byKey("torch").id(), 2));
        assertTrue(h.run("/clean").contains("2 przedmiotów"));
        assertEquals(0, h.session.ground().totalStacks());
    }

    @Test
    void saveBroadcastAndHelpPerLevel() {
        Host h = host(AccessLevel.GOD);
        h.run("/broadcast restart za 5 minut");
        assertEquals(List.of("restart za 5 minut"), h.broadcasts);
        h.run("/save");
        assertEquals(1, h.saves);
        assertTrue(h.run("/raid").contains("tylko na serwerze"), "raid is online only");

        String god = h.run("/komendy");
        assertTrue(god.contains("/i nazwa_lub_id") && god.contains("/flag") && god.contains("/shutdown"), god);
        Host tutor = host(AccessLevel.TUTOR);
        String t = tutor.run("/komendy");
        assertTrue(t.contains("/pos") && t.contains("/goto"), t);
        assertFalse(t.contains("/i nazwa_lub_id") || t.contains("/ban"), t);
        Host gm = host(AccessLevel.GAMEMASTER);
        String g = gm.run("/komendy");
        assertTrue(g.contains("/ban") && g.contains("/c Imię"), g);
        assertFalse(g.contains("/flag"), g);
        Host p = host(AccessLevel.PLAYER);
        assertTrue(p.run("/komendy").contains("Zwykły gracz nie ma poleceń"));
    }

    @Test
    void everyLanguageHasEveryGodText() {
        for (Language l : Language.values()) {
            for (AccessLevel lv : AccessLevel.values()) {
                Messages.setLanguage(l);
                for (String line : GodCommands.helpLines(lv)) {
                    assertFalse(line.contains("god."), l + " " + lv + ": raw key in " + line);
                }
                assertFalse(lv.label().startsWith("god."));
            }
        }
        for (GodCommands.Entry e : GodCommands.COMMANDS) {
            for (Language l : Language.values()) assertTrue(Messages.has(l, e.descKey()), l + " missing " + e.descKey());
        }
    }

    @Test
    void localGodIsOffByDefaultAndSwitchedOnByFlagOrFile() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("pixel-localgod").toFile();
        String old = System.getProperty(LocalGod.PROPERTY);
        try {
            System.clearProperty(LocalGod.PROPERTY);
            if (System.getenv(LocalGod.ENV) == null) {
                assertEquals(AccessLevel.PLAYER, LocalGod.level(dir), "a normal player is never GOD");
                assertTrue(new java.io.File(dir, LocalGod.FILE).createNewFile());
                assertEquals(AccessLevel.GOD, LocalGod.level(dir), "the owner's marker file enables it");
                assertTrue(new java.io.File(dir, LocalGod.FILE).delete());
            }
            System.setProperty(LocalGod.PROPERTY, "true");
            assertEquals(AccessLevel.GOD, LocalGod.level(dir));
            System.setProperty(LocalGod.PROPERTY, "false");
            assertEquals(AccessLevel.PLAYER, LocalGod.level(dir), "an explicit false wins");
        } finally {
            if (old == null) System.clearProperty(LocalGod.PROPERTY); else System.setProperty(LocalGod.PROPERTY, old);
        }
    }
}
