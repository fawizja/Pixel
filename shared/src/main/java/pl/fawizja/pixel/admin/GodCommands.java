package pl.fawizja.pixel.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.Blessing;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.character.Skills;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.i18n.Messages;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.npc.DialogueRegistry;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Town;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Polecenia GOD (wzorowane na talkactions z Canary) wspolne dla gry lokalnej i serwera: tabela polecen z
 * poziomem dostepu, parser tekstu czatu ("/i miecz 3" -> {@code GOD ITEM "miecz 3"}) i wykonanie na sesji gracza.
 * <p>
 * Poziom dostepu wykonujacego dostarcza HOST ({@link Context#level()}): serwer liczy go z nazwy postaci
 * (admins.txt, gods.txt), gra lokalna z flagi {@code -Dpixel.god=true}; klient nigdy nie przesyla swojego poziomu.
 * Kazde polecenie sprawdza poziom tutaj, a jego wynik (takze odmowa) trafia do {@link Context#audit}.
 */
public final class GodCommands {

    private GodCommands() {}

    /** Wiersz tabeli polecen. {@code prefix} = pola komunikatu bez argumentu; null = polecenie istniejace gdzie indziej (tylko pomoc). */
    public record Entry(String sub, AccessLevel level, String[] aliases, String usage, String descKey, boolean needsArg,
                        String[] prefix) {
        boolean parsed() { return prefix != null; }
    }

    /** Host polecen: sesja wykonujaca, jej poziom (zaufany), czas, odpowiedzi i operacje serwerowe. */
    public interface Context {
        /** Poziom dostepu wykonujacego - ustalony po stronie hosta, nie przez klienta. */
        AccessLevel level();

        GameSession session();

        long now();

        boolean online();

        /** Gracz o tym imieniu (online: dowolny zalogowany; offline: tylko wlasna postac) albo null. */
        GameSession find(String name);

        /** Wpis do dziennika administratora ({@code admin.log} na serwerze). */
        void audit(String line);

        /** Ogloszenie do wszystkich (offline: tylko do siebie). */
        void broadcast(String text);

        /** Rozpoczyna inwazje; odpowiedz tekstowa albo null, gdy host jej nie obsluguje. */
        default String invasion(String town) { return null; }

        /** Natychmiastowy zapis postaci/swiata. */
        void save();

        /** Zapowiedz wylaczenia ("5", "30s", "-"); odpowiedz albo null, gdy host nie obsluguje. */
        default String shutdown(String arg) { return null; }

        /** Wiadomosc dla innego gracza (cel polecenia) w jego jezyku, gdy host go zna. */
        default void notify(GameSession to, String key, Object... args) {
            to.log().add(now(), EventLog.Kind.INFO, Messages.get(key, args));
        }
    }

    private static final AccessLevel T = AccessLevel.TUTOR;
    private static final AccessLevel GM = AccessLevel.GAMEMASTER;
    private static final AccessLevel G = AccessLevel.GOD;

    private static String[] a(String... s) { return s; }

    private static Entry e(String sub, AccessLevel lv, String[] aliases, String usage, boolean needsArg) {
        return new Entry(sub, lv, aliases, usage, "god.cmd_" + sub.toLowerCase(Locale.ROOT), needsArg, a("GOD", sub));
    }

    /** Istniejace polecenia administratora (parser w ChatCommands) - tylko do listy pomocy. */
    private static Entry shown(String sub, AccessLevel lv, String usage, String descKey) {
        return new Entry(sub, lv, new String[0], usage, descKey, false, null);
    }

    private static Entry admin(String sub, AccessLevel lv, String[] aliases, String usage, String descKey, boolean needsArg) {
        return new Entry("ADMIN_" + sub, lv, aliases, usage, descKey, needsArg, a("ADMIN", sub));
    }

    /** Tabela polecen w kolejnosci listy pomocy. */
    public static final List<Entry> COMMANDS = List.of(
            e("HELP", AccessLevel.PLAYER, a("komendy", "commands", "polecenia-gm"), "/komendy", false),
            e("POS", T, a("pos", "pozycja", "position"), "/pos", false),
            e("INFO", T, a("info", "gracz-info"), "/info Imię", false),
            e("GOTO", T, a("goto", "tp", "teleport"), "/goto Imię | x,y,z | Miasto", true),
                        e("BRING", GM, a("c", "przynies", "sprowadz"), "/c Imię", true),
            shown("WHO", GM, "/kto", "god.cmd_who"),
            shown("KICK", GM, "/kick Imię", "god.cmd_kick"),
            shown("BAN", GM, "/ban Imię [godziny]", "god.cmd_ban"),
            shown("UNBAN", GM, "/unban Imię", "god.cmd_unban"),
            shown("MUTE", GM, "/wycisz Imię [minuty]", "god.cmd_mute"),
            shown("REPORTS", GM, "/zgloszenia", "god.cmd_reports"),
            shown("STATUS", GM, "/serwer", "god.cmd_status"),
            admin("SAY", GM, a("broadcast", "bc"), "/ogloszenie tekst  (/broadcast)", "god.cmd_say", true),
            admin("INVASION", GM, a("raid"), "/inwazja [Miasto]  (/raid)", "god.cmd_invasion", false),
            admin("SAVE", GM, a("save"), "/zapisz  (/save)", "god.cmd_save", false),
            shown("MOTD", GM, "/motd [tekst]", "god.cmd_motd"),
            shown("EXPEVENT", GM, "/bonusexp [%  min]", "god.cmd_expevent"),
            admin("SHUTDOWN", G, a("shutdown", "wylacz"), "/shutdown [minuty | 30s | -]", "god.cmd_shutdown", false),
            e("ITEM", G, a("i", "item", "przedmiot", "daj"), "/i nazwa_lub_id [ilość]", true),
            e("MONSTER", G, a("m", "monster", "stworz"), "/m potwór", true),
            e("AHEAD", G, a("a", "naprzod", "ahead"), "/a N", true),
            e("UP", G, a("up", "gora"), "/up", false),
            e("DOWN", G, a("down", "dol"), "/down", false),
            e("TEMPLE", G, a("t", "swiatynia", "temple"), "/t [Miasto]", false),
            e("GHOST", G, a("ghost", "duch"), "/ghost", false),
            e("GODMODE", G, a("godmode", "bog", "niesmiertelnosc"), "/godmode [on|off]", false),
            e("LEVEL", G, a("level", "poziom"), "/level N", true),
            e("ADDEXP", G, a("addexp", "doswiadczenie", "exp"), "/addexp N", true),
            e("ADDSKILL", G, a("addskill", "umiejetnosc", "skill"), "/addskill umiejętność N", true),
            e("SETHP", G, a("sethp", "hp"), "/sethp N", true),
            e("SETMANA", G, a("setmana", "mana"), "/setmana N", true),
            e("HEAL", G, a("heal", "ulecz"), "/heal", false),
            e("MONEY", G, a("money", "kasa", "zloto"), "/money N", true),
            e("PREMIUM", G, a("premium"), "/premium [Imię] dni", true),
            e("PROMOTE", G, a("promote", "awans"), "/promote [Imię]", false),
            e("BLESS", G, a("bless", "blogoslaw"), "/bless [Imię]", false),
            e("FLAG", G, a("flag", "flaga", "questflag"), "/flag [Imię,] flaga [off]", true),
            e("CLEAN", G, a("clean", "sprzatnij"), "/clean", false),
            e("KILL", G, a("kill", "zabij"), "/kill", false),
            e("SPEED", G, a("speed", "szybkosc"), "/speed N", true));

    private static final Map<String, Entry> BY_ALIAS = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_SUB = new LinkedHashMap<>();

    static {
        for (Entry en : COMMANDS) {
            BY_SUB.put(en.sub(), en);
            for (String al : en.aliases()) BY_ALIAS.put(al, en);
        }
    }

    /** Nazwa polecenia bez wielkosci liter i polskich znakow. */
    static String key(String word) {
        String t = word.toLowerCase(Locale.ROOT).replace('ł', 'l');
        return java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /**
     * Tekst czatu -> komunikat protokolu ({@code GOD SUB [argument]} albo {@code ADMIN SUB [argument]}); null, gdy to
     * nie jest polecenie z tabeli albo brakuje wymaganego argumentu.
     */
    public static String[] parse(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (!t.startsWith("/") || t.length() < 2) return null;
        String[] w = t.split("\\s+", 2);
        Entry en = BY_ALIAS.get(key(w[0].substring(1)));
        if (en == null || !en.parsed()) return null;
        String arg = w.length > 1 ? w[1].trim() : "";
        if (en.needsArg() && arg.isEmpty()) return null;
        if (arg.isEmpty()) return en.prefix().clone();
        String[] out = java.util.Arrays.copyOf(en.prefix(), en.prefix().length + 1);
        out[out.length - 1] = arg;
        return out;
    }

    /** Wiersze listy polecen dostepnych na tym poziomie (/komendy). */
    public static List<String> helpLines(AccessLevel level) {
        List<String> out = new ArrayList<>();
        out.add(Messages.get("god.help_header", level.label(), level.tag().isEmpty() ? "-" : level.tag()));
        boolean any = false;
        for (Entry en : COMMANDS) {
            if (!level.atLeast(en.level())) continue;
            if (en.level() != AccessLevel.PLAYER) any = true;
            out.add(en.usage() + " - " + Messages.get(en.descKey()));
        }
        if (!any) out.add(Messages.get("god.help_none"));
        return out;
    }

    /** Wiersze okna /komendy: {polecenie, opis} dla poleceń dostępnych na tym poziomie. */
    public static List<String[]> helpRows(AccessLevel level) {
        List<String[]> rows = new ArrayList<>();
        for (Entry en : COMMANDS) {
            if (level.atLeast(en.level())) rows.add(new String[] {en.usage(), Messages.get(en.descKey())});
        }
        if (!level.staff()) rows.add(new String[] {"-", Messages.get("god.help_none")});
        return rows;
    }

    /** Najnizszy poziom potrzebny do polecenia z tabeli (sub); GOD dla nieznanych. */
    public static AccessLevel required(String sub) {
        Entry en = BY_SUB.get(sub);
        return en == null ? AccessLevel.GOD : en.level();
    }

    // ------------------------------------------------------------------ wykonanie

    private record Res(boolean ok, String text) {
        static Res ok(String key, Object... args) { return new Res(true, Messages.get(key, args)); }
        static Res no(String key, Object... args) { return new Res(false, Messages.get(key, args)); }
    }

    /**
     * Wykonuje komunikat {@code GOD ...} (albo {@code ADMIN ...} w grze lokalnej, gdzie nie ma serwera moderacji).
     * Sprawdza poziom, wpisuje wynik do dziennika i odpowiada w dzienniku zdarzen wykonujacego.
     */
    public static void execute(Context ctx, String[] f) {
        if (f == null || f.length < 2) return;
        String sub = f[0].equals("ADMIN") ? "ADMIN_" + f[1].toUpperCase(Locale.ROOT) : f[1].toUpperCase(Locale.ROOT);
        String arg = f.length > 2 ? f[2].trim() : "";
        Entry en = BY_SUB.get(sub);
        String who = ctx.session().character().name();
        String line = who + " [" + ctx.level().tag() + "] " + String.join(" ", f);
        if (en == null) {
            if (f[0].equals("ADMIN") && ctx.level().atLeast(GM)) {
                reply(ctx, Res.no("god.online_only"));
                return;
            }
            reply(ctx, Res.no("god.unknown"));
            return;
        }
        if (!ctx.level().atLeast(en.level())) {
            ctx.audit(line + " -> REFUSED (level " + ctx.level() + " < " + en.level() + ")");
            reply(ctx, Res.no("god.denied", en.level().label()));
            return;
        }
        Res r;
        try {
            r = run(ctx, sub, arg);
        } catch (RuntimeException ex) {
            r = Res.no("god.error", String.valueOf(ex.getMessage()));
        }
        ctx.audit(line + " -> " + (r.ok() ? "OK " : "FAIL ") + r.text().replace('\n', '|'));
        reply(ctx, r);
    }

    private static void reply(Context ctx, Res r) {
        if (r == null) return;
        for (String l : r.text().split("\n")) {
            ctx.session().log().add(ctx.now(), r.ok() ? EventLog.Kind.INFO : EventLog.Kind.WARNING, l);
        }
    }

    private static Res run(Context ctx, String sub, String arg) {
        GameSession s = ctx.session();
        PlayerCharacter pc = s.character();
        long now = ctx.now();
        switch (sub) {
            case "HELP": {
                List<String> lines = helpLines(ctx.level());
                return new Res(true, String.join("\n", lines));
            }
            case "POS": {
                Position p = pc.position();
                pl.fawizja.pixel.world.Region r = s.world().regionAt(p);
                return Res.ok("god.pos", p.x(), p.y(), p.z(), r == null ? "-" : r.name());
            }
            case "INFO": return info(ctx, arg);
            case "GOTO": return gotoCmd(ctx, arg);
            case "BRING": return bring(ctx, arg);
            case "ITEM": return item(ctx, arg);
            case "MONSTER": return monster(ctx, arg);
            case "AHEAD": return ahead(ctx, arg);
            case "UP": return floor(ctx, 1);
            case "DOWN": return floor(ctx, -1);
            case "TEMPLE": return temple(ctx, arg);
            case "GHOST": {
                pc.setGhost(!pc.isGhost());
                return pc.isGhost() ? Res.ok("god.ghost_on") : Res.ok("god.ghost_off");
            }
            case "GODMODE": {
                boolean on = arg.isEmpty() ? !pc.isGodMode() : !(key(arg).equals("off") || key(arg).equals("wyl") || key(arg).equals("nie"));
                pc.setGodMode(on);
                return on ? Res.ok("god.godmode_on") : Res.ok("god.godmode_off");
            }
            case "LEVEL": {
                Long n = number(arg);
                if (n == null || n < 1 || n > Balance.MAX_LEVEL) return Res.no("god.bad_level", Balance.MAX_LEVEL);
                pc.godSetLevel(n.intValue());
                s.checkLevelRewards(now);
                return Res.ok("god.level_set", pc.level());
            }
            case "ADDEXP": {
                Long n = number(arg);
                if (n == null || n <= 0) return Res.no("god.bad_number");
                int gained = pc.addExperience(n);
                return Res.ok("god.exp_added", n, pc.level(), gained);
            }
            case "ADDSKILL": return addSkill(ctx, arg);
            case "SETHP": {
                Long n = number(arg);
                if (n == null || n < 1) return Res.no("god.bad_number");
                pc.godSetHealth((int) Math.min(n, Integer.MAX_VALUE));
                return Res.ok("god.hp_set", pc.health(), pc.maxHealth());
            }
            case "SETMANA": {
                Long n = number(arg);
                if (n == null || n < 0) return Res.no("god.bad_number");
                pc.godSetMana((int) Math.min(n, Integer.MAX_VALUE));
                return Res.ok("god.mana_set", pc.mana(), pc.maxMana());
            }
            case "HEAL": {
                if (pc.isDead()) return Res.no("god.dead");
                pc.godSetHealth(pc.maxHealth());
                pc.godSetMana(pc.maxMana());
                pc.conditions().clear();
                return Res.ok("god.healed", pc.health(), pc.mana());
            }
            case "MONEY": {
                long n = pl.fawizja.pixel.net.ChatCommands.amount(arg);
                if (n <= 0) return Res.no("god.bad_number");
                n = Math.min(n, 2_000_000_000L);
                InventoryResult r = Money.add(pc.backpack(), n);
                if (!r.ok()) {
                    pc.bank().credit(n);
                    return Res.ok("god.money_bank", n);
                }
                return Res.ok("god.money", n);
            }
            case "PREMIUM": return premium(ctx, arg);
            case "PROMOTE": return promote(ctx, arg);
            case "BLESS": return bless(ctx, arg);
            case "FLAG": return flag(ctx, arg);
            case "CLEAN": return clean(ctx);
            case "KILL": return kill(ctx);
            case "SPEED": return speed(ctx, arg);
            case "ADMIN_SAY":
                if (arg.isEmpty()) return Res.no("god.bad_text");
                ctx.broadcast(arg);
                return Res.ok("god.broadcast_sent");
            case "ADMIN_INVASION": {
                String r = ctx.invasion(arg.isEmpty() ? null : arg);
                return r == null ? Res.no("god.online_only") : new Res(true, r);
            }
            case "ADMIN_SAVE":
                ctx.save();
                return Res.ok("god.saved");
            case "ADMIN_SHUTDOWN": {
                String r = ctx.shutdown(arg.isEmpty() ? null : arg);
                return r == null ? Res.no("god.online_only") : new Res(true, r);
            }
            default:
                return Res.no("god.online_only");
        }
    }

    // ------------------------------------------------------------------ pomocnicze

    private static Long number(String arg) {
        try {
            long n = pl.fawizja.pixel.net.ChatCommands.amount(arg.trim());
            if (n > 0 || arg.trim().equals("0")) return n;
        } catch (RuntimeException ignored) {
            // nizej null
        }
        return null;
    }

    /** Gracz docelowy: puste imie = wykonujacy; inaczej gracz zalogowany o tym imieniu (albo null). */
    private static GameSession target(Context ctx, String name) {
        if (name == null || name.isBlank()) return ctx.session();
        return ctx.find(name.trim());
    }

    private static String norm(String s) { return DialogueRegistry.normalize(s); }

    /** Ostatni czlon liczbowy tekstu: [reszta, liczba] albo [tekst, null]. */
    private static String[] splitTrailingNumber(String arg) {
        String t = arg.trim();
        int sp = t.lastIndexOf(' ');
        if (sp > 0 && t.substring(sp + 1).matches("\\d{1,10}[kK]*")) return new String[] {t.substring(0, sp).trim(), t.substring(sp + 1)};
        return new String[] {t, null};
    }

    /** Przedmiot po id, kluczu albo nazwie (polskiej lub w jezyku gracza): dokladna nazwa, potem poczatek, potem fragment. */
    static ItemType findItem(GameSession s, String query) {
        String q = norm(query);
        if (q.isEmpty()) return null;
        if (q.matches("\\d{1,9}") && s.items().contains(Integer.parseInt(q))) return s.items().get(Integer.parseInt(q));
        ItemType best = null;
        int bestScore = 0;
        for (ItemType t : s.items().all()) {
            int sc = score(q, norm(t.key()), norm(t.polishName()), norm(t.name()));
            if (sc > bestScore || (sc == bestScore && sc > 0 && best != null && t.polishName().length() < best.polishName().length())) {
                best = t;
                bestScore = sc;
            }
        }
        return best;
    }

    static MonsterType findMonster(GameSession s, String query) {
        String q = norm(query);
        if (q.isEmpty()) return null;
        MonsterType best = null;
        int bestScore = 0;
        for (MonsterType t : s.monsters().registry().all()) {
            int sc = score(q, norm(t.key()), norm(t.polishName()), norm(t.name()));
            if (sc > bestScore || (sc == bestScore && sc > 0 && best != null && t.polishName().length() < best.polishName().length())) {
                best = t;
                bestScore = sc;
            }
        }
        return best;
    }

    private static int score(String q, String... names) {
        int best = 0;
        for (String n : names) {
            if (n.equals(q)) return 3;
            if (n.startsWith(q)) best = Math.max(best, 2);
            else if (n.contains(q)) best = Math.max(best, 1);
        }
        return best;
    }

    /** Pole do stania (takze w strefie ochronnej, bo GOD moze wejsc do swiatyni): przechodnie i bez potwora. */
    private static boolean standable(GameSession s, Position q) {
        return s.world().isWalkable(q) && (s.monsters() == null || s.monsters().monsterAt(q) == null)
                && s.world().transitionAt(q) == null;
    }

    private static Position walkableNear(GameSession s, Position p, int radius) {
        if (standable(s, p)) return p;
        for (int r = 1; r <= radius; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    Position q = p.translate(dx, dy);
                    if (standable(s, q)) return q;
                }
            }
        }
        return null;
    }

    private static boolean inside(GameSession s, Position p) {
        return p.x() >= 0 && p.y() >= 0 && p.x() < s.world().width() && p.y() < s.world().height()
                && p.z() >= Position.MIN_FLOOR && p.z() <= Position.MAX_FLOOR;
    }

    private static Res moveTo(Context ctx, GameSession who, Position dest) {
        Position spot = inside(ctx.session(), dest) ? walkableNear(ctx.session(), dest, 3) : null;
        if (spot == null) return Res.no("god.no_spot", dest.x(), dest.y(), dest.z());
        who.teleport(spot, ctx.now());
        return Res.ok("god.teleported", spot.x(), spot.y(), spot.z());
    }

    // ------------------------------------------------------------------ polecenia

    private static final Pattern COORDS = Pattern.compile("^\\(?\\s*(-?\\d+)\\s*[ ,;]\\s*(-?\\d+)\\s*(?:[ ,;]\\s*(-?\\d+))?\\s*\\)?$");

    private static Res gotoCmd(Context ctx, String arg) {
        GameSession s = ctx.session();
        Matcher m = COORDS.matcher(arg.trim());
        if (m.matches()) {
            int z = m.group(3) == null ? s.character().position().z() : Integer.parseInt(m.group(3));
            return moveTo(ctx, s, new Position(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), z));
        }
        GameSession t = ctx.find(arg);
        if (t != null && t != s) return moveTo(ctx, s, t.character().position());
        if (t == s) return Res.ok("god.teleported", s.character().position().x(), s.character().position().y(), s.character().position().z());
        Town town = s.world().town(arg);
        if (town != null) return moveTo(ctx, s, town.temple());
        return Res.no("god.goto_unknown", arg);
    }

    private static Res bring(Context ctx, String arg) {
        GameSession t = ctx.find(arg);
        if (t == null || t == ctx.session()) return Res.no("god.no_player", arg);
        Res r = moveTo(ctx, t, ctx.session().character().position());
        if (r.ok()) {
            ctx.notify(t, "god.you_were_brought", ctx.session().character().name());
            return Res.ok("god.brought", t.character().name());
        }
        return r;
    }

    private static Res item(Context ctx, String arg) {
        GameSession s = ctx.session();
        String[] parts = splitTrailingNumber(arg);
        ItemType t = findItem(s, parts[0]);
        long count = 1;
        if (t == null && parts[1] != null) {
            // "/i 2160" zamiast nazwy+ilosci: cala fraza jako nazwa albo id
            t = findItem(s, arg);
        } else if (parts[1] != null) {
            count = pl.fawizja.pixel.net.ChatCommands.amount(parts[1]);
        }
        if (t == null) return Res.no("god.item_unknown", arg);
        long cap = t.stackable() ? 100_000L : 50L;
        if (count < 1) count = 1;
        if (count > cap) count = cap;
        long left = count;
        boolean toGround = false;
        Position here = s.character().position();
        while (left > 0) {
            int chunk = (int) Math.min(left, t.maxStack());
            InventoryResult r = s.character().backpack().add(new ItemStack(t.id(), chunk));
            if (!r.ok()) {
                s.ground().place(here, new ItemStack(t.id(), chunk));
                toGround = true;
            }
            left -= chunk;
        }
        return Res.ok(toGround ? "god.item_ground" : "god.item_created", count, t.name());
    }

    private static Res monster(Context ctx, String arg) {
        GameSession s = ctx.session();
        MonsterType t = findMonster(s, arg);
        if (t == null) return Res.no("god.monster_unknown", arg);
        Position me = s.character().position();
        Position spot = s.monsters().freeSpotNear(me, 6);
        if (spot == null) return Res.no("god.no_room");
        List<Monster> made = s.monsters().spawnEvent(t.key(), spot, 0, 1, ctx.now());
        if (made.isEmpty()) return Res.no("god.no_room");
        return Res.ok("god.monster_created", t.name(), spot.x(), spot.y(), spot.z());
    }

    private static Res ahead(Context ctx, String arg) {
        GameSession s = ctx.session();
        Long n = number(arg);
        if (n == null || n < 1 || n > 200) return Res.no("god.bad_distance");
        Direction d = s.facing();
        Position from = s.character().position();
        Position best = null;
        for (int i = 1; i <= n; i++) {
            Position q = new Position(from.x() + d.dx * i, from.y() + d.dy * i, from.z());
            if (!inside(s, q)) break;
            if (standable(s, q)) best = q;
        }
        if (best == null) return Res.no("god.blocked");
        s.teleport(best, ctx.now());
        return Res.ok("god.teleported", best.x(), best.y(), best.z());
    }

    private static Res floor(Context ctx, int dz) {
        GameSession s = ctx.session();
        Position p = s.character().position();
        Position dest = p.withFloor(p.z() + dz);
        if (dest.z() < Position.MIN_FLOOR || dest.z() > Position.MAX_FLOOR) return Res.no("god.no_floor");
        Position spot = walkableNear(s, dest, 4);
        if (spot == null) return Res.no("god.no_spot", dest.x(), dest.y(), dest.z());
        s.teleport(spot, ctx.now());
        return Res.ok("god.floor", spot.z());
    }

    private static Res temple(Context ctx, String arg) {
        GameSession s = ctx.session();
        Position t;
        if (arg.isBlank()) t = s.world().templeOf(s.character().homeTown());
        else {
            Town town = s.world().town(arg.trim());
            t = town == null ? null : town.temple();
            if (town == null) return Res.no("god.town_unknown", arg.trim());
        }
        if (t == null) t = s.world().spawn();
        return moveTo(ctx, s, t);
    }

    private static Res addSkill(Context ctx, String arg) {
        String[] parts = splitTrailingNumber(arg);
        if (parts[1] == null) return Res.no("god.skill_usage");
        Skill sk = skill(parts[0]);
        if (sk == null) return Res.no("god.skill_unknown", parts[0]);
        long n = pl.fawizja.pixel.net.ChatCommands.amount(parts[1]);
        if (n < 1) return Res.no("god.bad_number");
        Skills sks = ctx.session().character().skills();
        int level = (int) Math.min(Skills.MAX_SKILL, sks.level(sk) + n);
        sks.set(sk, level, 0);
        return Res.ok("god.skill_set", sk.label(), level);
    }

    static Skill skill(String name) {
        String k = norm(name);
        for (Skill sk : Skill.values()) if (norm(sk.name()).equals(k) || norm(sk.label()).equals(k)) return sk;
        switch (k) {
            case "ml": case "magic level": case "magia": case "mag": return Skill.MAGIC;
            case "sword": case "miecz": return Skill.SWORD;
            case "axe": case "topor": return Skill.AXE;
            case "club": case "maczuga": return Skill.CLUB;
            case "dist": case "distance": case "dystans": return Skill.DISTANCE;
            case "shield": case "tarcza": return Skill.SHIELDING;
            case "fish": case "wedkarstwo": return Skill.FISHING;
            case "fist": case "piesci": return Skill.FIST;
            default: return null;
        }
    }

    /** "[Imie] reszta" gdzie reszta to ostatni czlon (liczba/flaga): [imie lub "", reszta]. */
    private static String[] nameAndLast(String arg) {
        String t = arg.trim();
        int comma = t.indexOf(',');
        if (comma > 0) return new String[] {t.substring(0, comma).trim(), t.substring(comma + 1).trim()};
        int sp = t.lastIndexOf(' ');
        return sp > 0 ? new String[] {t.substring(0, sp).trim(), t.substring(sp + 1).trim()} : new String[] {"", t};
    }

    private static Res premium(Context ctx, String arg) {
        String[] p = nameAndLast(arg);
        Long days = number(p[1]);
        if (days == null) return Res.no("god.premium_usage");
        GameSession t = target(ctx, p[0]);
        if (t == null) return Res.no("god.no_player", p[0]);
        t.character().setPremium(days > 0);
        if (t != ctx.session()) ctx.notify(t, days > 0 ? "god.you_premium" : "god.you_no_premium", days);
        return days > 0 ? Res.ok("god.premium_on", t.character().name(), days) : Res.ok("god.premium_off", t.character().name());
    }

    private static Res promote(Context ctx, String arg) {
        GameSession t = target(ctx, arg);
        if (t == null) return Res.no("god.no_player", arg);
        t.character().setPromoted(true);
        if (t != ctx.session()) ctx.notify(t, "god.you_promoted");
        return Res.ok("god.promoted", t.character().name(), t.character().vocationTitle());
    }

    private static Res bless(Context ctx, String arg) {
        GameSession t = target(ctx, arg);
        if (t == null) return Res.no("god.no_player", arg);
        for (Blessing b : Blessing.values()) t.character().addBlessing(b);
        if (t != ctx.session()) ctx.notify(t, "god.you_blessed");
        return Res.ok("god.blessed", t.character().name());
    }

    private static Res flag(Context ctx, String arg) {
        String t = arg.trim();
        boolean remove = false;
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" off") || lower.endsWith(" usun") || lower.endsWith(" remove")) {
            remove = true;
            t = t.substring(0, t.lastIndexOf(' ')).trim();
        }
        String[] p = nameAndLast(t);
        String flag = p[1].toLowerCase(Locale.ROOT);
        if (!flag.matches("[a-z0-9_]{2,40}")) return Res.no("god.flag_usage");
        GameSession target = target(ctx, p[0]);
        if (target == null) return Res.no("god.no_player", p[0]);
        if (remove) {
            boolean had = target.character().questFlags().remove(flag);
            return had ? Res.ok("god.flag_removed", flag, target.character().name()) : Res.no("god.flag_absent", flag);
        }
        boolean added = target.character().setQuestFlag(flag);
        return added ? Res.ok("god.flag_set", flag, target.character().name()) : Res.ok("god.flag_already", flag, target.character().name());
    }

    private static final int CLEAN_RADIUS = 8;

    private static Res clean(Context ctx) {
        GameSession s = ctx.session();
        Position me = s.character().position();
        List<Position> spots = new ArrayList<>();
        s.ground().forEachNear(me, CLEAN_RADIUS, (p, st) -> spots.add(p));
        int items = 0;
        for (Position p : new java.util.LinkedHashSet<>(spots)) {
            while (!s.ground().at(p).isEmpty()) {
                s.ground().take(p, 0);
                items++;
            }
        }
        List<Position> bodies = new ArrayList<>();
        s.ground().forEachCorpseNear(me, CLEAN_RADIUS, (p, c) -> bodies.add(p));
        for (Position p : bodies) s.ground().removeCorpse(p);
        return Res.ok("god.cleaned", items, bodies.size());
    }

    private static final int KILL_RADIUS = 10;

    private static Res kill(Context ctx) {
        GameSession s = ctx.session();
        Position me = s.character().position();
        int n = 0;
        for (Monster m : new ArrayList<>(s.monsters().near(me, KILL_RADIUS))) {
            if (m.isDead() || m.position().z() != me.z() || m.isSummon()) continue;
            m.damage(m.health());
            s.monsters().kill(m, ctx.now());
            n++;
        }
        return Res.ok("god.killed", n);
    }

    private static Res speed(Context ctx, String arg) {
        PlayerCharacter pc = ctx.session().character();
        Long n = number(arg);
        if (n == null || n > 2000) return Res.no("god.bad_number");
        if (n == 0) {
            pc.addHaste(0, 0);
            return Res.ok("god.speed_reset", pc.speed());
        }
        pc.addHaste(0, 0);
        int base = pc.speed();
        int percent = (int) Math.max(0, (n - base) * 100 / Math.max(1, base));
        pc.addHaste(percent, 24L * 3600_000L);
        return Res.ok("god.speed_set", pc.speed());
    }

    private static Res info(Context ctx, String arg) {
        GameSession t = target(ctx, arg);
        if (t == null) return Res.no("god.no_player", arg);
        PlayerCharacter c = t.character();
        Position p = c.position();
        StringBuilder sb = new StringBuilder();
        sb.append(Messages.get("god.info_head", c.name(), c.vocationTitle(), c.level())).append('\n');
        sb.append(Messages.get("god.info_stats", c.health(), c.maxHealth(), c.mana(), c.maxMana(), c.speed())).append('\n');
        sb.append(Messages.get("god.info_pos", p.x(), p.y(), p.z(), c.homeTown())).append('\n');
        sb.append(Messages.get("god.info_gold", Money.totalValue(c.backpack()), c.bank().balance(),
                Messages.get(c.isPremium() ? "god.yes" : "god.no"), Messages.get(c.isPromoted() ? "god.yes" : "god.no"),
                c.blessingCount()));
        if (c.accessLevel().staff()) sb.append('\n').append(Messages.get("god.info_staff", c.accessLevel().label()));
        return new Res(true, sb.toString());
    }
}
