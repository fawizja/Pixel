package pl.fawizja.pixel.character;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.save.SaveFormatException;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldLayout;

/**
 * Tekstowy, wersjonowany format zapisu postaci z suma kontrolna CRC32.
 * Starsze wersje sa migrowane przy odczycie; uszkodzone dane sa odrzucane w calosci.
 */
public final class CharacterCodec {

    public static final String MAGIC = "PIXELSAVE";
    public static final int CURRENT_VERSION = 5;

    private final ItemRegistry items;

    public CharacterCodec(ItemRegistry items) {
        this.items = items;
    }

    public String encode(PlayerCharacter pc) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", pc.id());
        m.put("name", pc.name());
        m.put("vocation", pc.vocation().name());
        m.put("experience", Long.toString(pc.experience()));
        m.put("health", Integer.toString(pc.health()));
        m.put("mana", Integer.toString(pc.mana()));
        Position p = pc.position();
        m.put("position", p.x() + "," + p.y() + "," + p.z());
        m.put("map", WorldLayout.WORLD_MAP);
        m.put("bank", Long.toString(pc.bank().balance()));
        m.put("hometown", pc.homeTown());
        for (java.util.Map.Entry<String, Integer> q : pc.quests().active().entrySet()) {
            m.put("quest." + q.getKey(), "A/" + q.getValue());
        }
        for (String q : pc.quests().done()) m.put("quest." + q, "D");
        m.put("flags", (pc.isRunning() ? "R" : "") + (pc.isDefensive() ? "D" : "") + (pc.hasMount() ? "H" : "") + (pc.isMounted() ? "M" : ""));
        m.put("timers", pc.fedMs() + "," + pc.lightBuffMs() + "," + pc.lightBuffRadius() + ","
                + (pc.isTorchLit() ? 1 : 0) + "," + pc.torchBurnMs());
        if (pc.restedMs() > 0) m.put("rested", Long.toString(pc.restedMs()));
        if (pc.extraBags() > 0) m.put("bags", Integer.toString(pc.extraBags()));
        for (int i = 0; i < pc.backpack().size(); i++) {
            ItemStack s = pc.backpack().get(i);
            if (s != null) m.put("bp." + i, stackText(s));
        }
        for (int i = 0; i < pc.depot().size(); i++) {
            ItemStack s = pc.depot().get(i);
            if (s != null) m.put("dp." + i, stackText(s));
        }
        for (int i = 0; i < pc.friends().size(); i++) m.put("fr." + i, pc.friends().names().get(i));
        for (String k : pc.bestiary().keys()) m.put("kill." + k, pc.bestiary().encodeValue(k));
        if (pc.bestiary().eliteKills() > 0) m.put("elites", String.valueOf(pc.bestiary().eliteKills()));
        if (pc.achievements().count() > 0) m.put("achievements", pc.achievements().encode());
        if (!pc.daily().encode().isEmpty()) m.put("daily", pc.daily().encode());
        if (!pc.hunt().encode().isEmpty()) m.put("hunt", pc.hunt().encode());
        if (!pc.loginStreak().encode().isEmpty()) m.put("login", pc.loginStreak().encode());
        if (pc.isBlessed()) m.put("bless", "1");
        if (pc.isPremium()) m.put("premium", "1");
        if (pc.isPromoted()) m.put("promo", "1");
        m.put("lvlrew", String.valueOf(pc.rewardedLevel()));
        m.put("stats", pc.stats().encode());
        if (pc.weeklyBossWeek() >= 0) m.put("wboss", String.valueOf(pc.weeklyBossWeek()));
        if (!pc.enchantments().encode().isEmpty()) m.put("ench", pc.enchantments().encode());
        if (pc.pet() != null) m.put("pet", pc.pet());
        if (pc.title() != null) m.put("title", pc.title());
        if (pc.house() != null) m.put("house", pc.house());
        if (!pc.houseGuests().isEmpty()) m.put("hguests", pc.houseGuests());
        if (pc.rentPaidUntil() > 0 || pc.rentMissed() > 0) m.put("rent", pc.rentPaidUntil() + "," + pc.rentMissed());
        if (pc.outfit() != 0) m.put("outfit", String.valueOf(pc.outfit()));
        if (pc.tips() != 0) m.put("tips", String.valueOf(pc.tips()));
        if (!pc.openedChests().isEmpty()) m.put("chests", String.join(",", pc.openedChests()));
        if (!pc.crates().isEmpty()) m.put("crates", pc.crates());
        for (Map.Entry<EquipSlot, ItemStack> e : pc.equipment().all().entrySet()) {
            m.put("eq." + e.getKey().name(), stackText(e.getValue()));
        }
        for (Skill s : Skill.values()) {
            m.put("skill." + s.name(), pc.skills().level(s) + "/" + pc.skills().tries(s));
        }
        StringBuilder ids = new StringBuilder();
        for (int id : pc.autoLootFilter().ignoredIds()) {
            if (ids.length() > 0) ids.append(',');
            ids.append(id);
        }
        m.put("autoloot.ignore", ids.toString());
        StringBuilder kept = new StringBuilder();
        for (int id : pc.keptItems()) {
            if (kept.length() > 0) kept.append(',');
            kept.append(id);
        }
        if (kept.length() > 0) m.put("kept", kept.toString());

        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            body.append(e.getKey()).append('=').append(escape(e.getValue())).append('\n');
        }
        String b = body.toString();
        return MAGIC + ' ' + CURRENT_VERSION + '\n' + b + "crc=" + crc(b) + '\n';
    }

    public PlayerCharacter decode(String text) throws SaveFormatException {
        if (text == null || !text.startsWith(MAGIC + ' ')) throw new SaveFormatException("missing header");
        int headerEnd = text.indexOf('\n');
        int crcStart = text.lastIndexOf("crc=");
        if (headerEnd < 0 || crcStart < headerEnd) throw new SaveFormatException("truncated save");
        int version;
        try {
            version = Integer.parseInt(text.substring(MAGIC.length() + 1, headerEnd).trim());
        } catch (NumberFormatException e) {
            throw new SaveFormatException("bad version", e);
        }
        if (version < 1 || version > CURRENT_VERSION) throw new SaveFormatException("unsupported version " + version);
        String body = text.substring(headerEnd + 1, crcStart);
        String crcText = text.substring(crcStart + 4).trim();
        if (!crc(body).equals(crcText)) throw new SaveFormatException("checksum mismatch (corrupted save)");

        Map<String, String> m = new LinkedHashMap<>();
        for (String line : body.split("\n")) {
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) throw new SaveFormatException("bad line: " + line);
            m.put(line.substring(0, eq), unescape(line.substring(eq + 1)));
        }
        migrate(version, m);
        try {
            return build(m);
        } catch (RuntimeException e) {
            throw new SaveFormatException("invalid save data: " + e.getMessage(), e);
        }
    }

    /**
     * Migracje formatu: v1 zapisywal pozycje bez pietra i saldo pod kluczem "gold.bank";
     * v1 i v2 zapisywaly pozycje na mapie Varden - w v3 to wspolrzedne swiata (przesuniecie regionu Varden);
     * v3 to swiat 352x320, v4 kontynent 448x448 - od v5 swiat 1000x1000, starsze pozycje wracaja do swiatyni.
     */
    static void migrate(int version, Map<String, String> m) {
        if (version < 2) {
            String pos = m.get("position");
            if (pos != null && pos.split(",").length == 2) m.put("position", pos + ",0");
            if (m.containsKey("gold.bank") && !m.containsKey("bank")) m.put("bank", m.remove("gold.bank"));
            m.putIfAbsent("hometown", "varden");
        }
        if (version < 3) {
            String[] pos = m.getOrDefault("position", "").split(",");
            if (pos.length == 3) {
                m.put("position", (Integer.parseInt(pos[0].trim()) + WorldLayout.V3_VARDEN_OFFSET_X) + ","
                        + (Integer.parseInt(pos[1].trim()) + WorldLayout.V3_VARDEN_OFFSET_Y) + "," + pos[2].trim());
            }
            m.put("map", WorldLayout.WORLD_MAP);
        }
        if (version < 5) {
            // v4 (kontynent 448x448) i starsze: swiat 1000x1000 z mapy Darka ma miasta w innych miejscach -
            // pole spoza mapy, sesja postawi postac w swiatyni jej miasta (decyzja Darka 2026-10-27)
            if (m.containsKey("position")) m.put("position", "-1,-1,0");
        }
    }

    private PlayerCharacter build(Map<String, String> m) {
        String[] pos = require(m, "position").split(",");
        Position p = new Position(Integer.parseInt(pos[0]), Integer.parseInt(pos[1]), Integer.parseInt(pos[2]));
        long exp = Long.parseLong(require(m, "experience"));
        if (exp < 0 || exp > Balance.maxExperience()) throw new IllegalArgumentException("experience out of range");
        Vocation voc = Vocation.valueOf(require(m, "vocation"));
        long bank = Long.parseLong(m.getOrDefault("bank", "0"));
        PlayerCharacter pc = new PlayerCharacter(require(m, "id"), require(m, "name"), voc,
                Balance.levelForExperience(exp), p, items, bank, m.getOrDefault("hometown", "varden"));
        pc.restoreState(exp, Integer.parseInt(require(m, "health")), Integer.parseInt(require(m, "mana")));
        pc.restoreBags(Integer.parseInt(m.getOrDefault("bags", "0")));
        java.util.TreeMap<Integer, String> friends = new java.util.TreeMap<>();
        for (Map.Entry<String, String> e : m.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("bp.")) {
                int slot = Integer.parseInt(k.substring(3));
                ItemStack s = parseStack(e.getValue());
                if (!pc.backpack().putInSlot(slot, s).ok()) throw new IllegalArgumentException("bad backpack slot " + slot);
            } else if (k.startsWith("dp.")) {
                int slot = Integer.parseInt(k.substring(3));
                ItemStack s = parseStack(e.getValue());
                if (!pc.depot().putInSlot(slot, s).ok()) throw new IllegalArgumentException("bad depot slot " + slot);
            } else if (k.startsWith("kill.")) {
                pc.bestiary().restore(k.substring(5), e.getValue());
            } else if (k.startsWith("fr.")) {
                friends.put(Integer.parseInt(k.substring(3)), e.getValue());
            } else if (k.startsWith("eq.")) {
                EquipSlot slot = EquipSlot.valueOf(k.substring(3));
                ItemStack s = parseStack(e.getValue());
                if (items.get(s.itemId()).slot() != slot) throw new IllegalArgumentException("item in wrong slot " + slot);
                pc.equipment().setDirect(slot, s);
            } else if (k.startsWith("quest.")) {
                String v = e.getValue();
                if (v.equals("D")) pc.quests().restore(k.substring(6), true, 0);
                else if (v.startsWith("A/")) pc.quests().restore(k.substring(6), false, Integer.parseInt(v.substring(2)));
                else throw new IllegalArgumentException("bad quest state " + v);
            } else if (k.startsWith("skill.")) {
                String[] v = e.getValue().split("/");
                pc.skills().set(Skill.valueOf(k.substring(6)), Integer.parseInt(v[0]), Long.parseLong(v[1]));
            }
        }
        String flags = m.getOrDefault("flags", "");
        pc.setRunning(flags.contains("R"));
        pc.setDefensive(flags.contains("D"));
        pc.setHasMount(flags.contains("H"));
        pc.setMounted(flags.contains("M"));
        String timers = m.get("timers");
        if (timers != null) {
            String[] t = timers.split(",");
            pc.restoreTimers(Long.parseLong(t[0]), Long.parseLong(t[1]), Integer.parseInt(t[2]), "1".equals(t[3]),
                    Long.parseLong(t[4]), items.byKey("torch").id());
        }
        String rested = m.get("rested");
        if (rested != null) {
            try {
                pc.restoreRested(Long.parseLong(rested.trim()));
            } catch (NumberFormatException e) {
                // stary lub uszkodzony zapis - bez wypoczecia
            }
        }
        for (String f : friends.values()) pc.friends().restore(f);
        pc.achievements().restore(m.get("achievements"));
        pc.daily().restore(m.get("daily"));
        pc.hunt().restore(m.get("hunt"));
        pc.loginStreak().restore(m.get("login"));
        pc.setBlessed("1".equals(m.get("bless")));
        pc.setPremium("1".equals(m.get("premium")));
        pc.setPromoted("1".equals(m.get("promo")));
        if (m.containsKey("lvlrew")) pc.setRewardedLevel(Integer.parseInt(m.get("lvlrew")));
        pc.stats().restore(m.get("stats"));
        if (m.containsKey("wboss")) pc.setWeeklyBossWeek(Long.parseLong(m.get("wboss")));
        pc.enchantments().restore(m.get("ench"));
        pc.setPet(m.get("pet"));
        pc.setTitle(m.get("title"));
        pc.setHouse(m.get("house"));
        pc.setHouseGuests(m.get("hguests"));
        if (m.containsKey("rent")) {
            String[] r = m.get("rent").split(",");
            pc.setRent(Long.parseLong(r[0].trim()), r.length > 1 ? Integer.parseInt(r[1].trim()) : 0);
        }
        if (m.containsKey("outfit")) pc.setOutfit(Outfit.parse(m.get("outfit")));
        if (m.containsKey("tips")) pc.restoreTips(Long.parseLong(m.get("tips")));
        if (m.containsKey("elites")) pc.bestiary().restoreElites(m.get("elites"));
        pc.setCrates(m.getOrDefault("crates", ""));
        String chests = m.get("chests");
        if (chests != null && !chests.isBlank()) for (String c : chests.split(",")) pc.openedChests().add(c.trim());
        String ignore = m.getOrDefault("autoloot.ignore", "");
        if (!ignore.isEmpty()) {
            for (String id : ignore.split(",")) pc.autoLootFilter().ignore(Integer.parseInt(id.trim()));
        }
        String kept = m.getOrDefault("kept", "");
        if (!kept.isEmpty()) {
            for (String id : kept.split(",")) pc.keptItems().add(Integer.parseInt(id.trim()));
        }
        return pc;
    }

    /** "idxilosc", z ulepszeniem "idxilosc+poziom" (0.62.0). */
    private static String stackText(ItemStack s) {
        return s.itemId() + "x" + s.count() + (s.upgrade() > 0 ? "+" + s.upgrade() : "");
    }

    private ItemStack parseStack(String v) {
        int plus = v.indexOf('+');
        int upgrade = plus < 0 ? 0 : Integer.parseInt(v.substring(plus + 1));
        String[] f = (plus < 0 ? v : v.substring(0, plus)).split("x");
        ItemStack s = new ItemStack(Integer.parseInt(f[0]), Integer.parseInt(f[1]), upgrade);
        if (s.count() > items.maxStack(s.itemId())) throw new IllegalArgumentException("stack too large " + v);
        return s;
    }

    private static String require(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null) throw new IllegalArgumentException("missing " + key);
        return v;
    }

    private static String crc(String body) {
        CRC32 c = new CRC32();
        c.update(body.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(c.getValue());
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 'n' ? '\n' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
