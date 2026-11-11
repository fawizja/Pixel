package pl.fawizja.pixel.wheel;

import java.util.Collections;
import java.util.List;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.magic.SpellKind;
import pl.fawizja.pixel.magic.SpellType;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Warstwa modyfikatorow czarow z Kola Przeznaczenia (wywolywana z SpellSystem): mana, odnowienie, obrazenia i leczenie,
 * kradziez, krytyk, cele i promien ({@link SpellAugment}), czary objawien (odblokowanie i premie stopni) oraz efekty
 * zalezne od celu (Rzut kata, Blizniacze wybuchy, Boski granat).
 */
public final class WheelSpells {
    /** Czary skupienia sorcerera (Canary m_focusSpells): Piekielny rdzen i Gniew niebios. */
    public static final List<String> FOCUS_SPELLS = List.of("hells_core", "rage_of_the_skies");
    public static final long FOCUS_MASTERY_MS = 12_000;
    public static final double FOCUS_MASTERY_PERCENT = 35;
    public static final long SANCTUARY_MS = 5_000;
    public static final long EMPOWERMENT_MS = 5_000;

    private WheelSpells() {}

    /** Objawienie, ktore odblokowuje czar (czar jest niedostepny bez stopnia 1), albo null. */
    public static WheelRevelation requiredRevelation(String key) {
        switch (key) {
            case "divine_grenade": return WheelRevelation.DIVINE_GRENADE;
            case "great_death_beam": return WheelRevelation.BEAM_MASTERY;
            case "terra_burst": case "ice_burst": return WheelRevelation.TWIN_BURST;
            case "divine_empowerment": return WheelRevelation.DIVINE_EMPOWERMENT;
            case "avatar_of_steel": return WheelRevelation.AVATAR_OF_STEEL;
            case "avatar_of_light": return WheelRevelation.AVATAR_OF_LIGHT;
            case "avatar_of_storm": return WheelRevelation.AVATAR_OF_STORM;
            case "avatar_of_nature": return WheelRevelation.AVATAR_OF_NATURE;
            case "avatar_of_balance": return WheelRevelation.AVATAR_OF_BALANCE;
            default: return null;
        }
    }

    /** Stopien objawienia czaru dla postaci (0 = zablokowany albo czar bez objawienia). */
    public static int revelationStage(PlayerCharacter pc, String key) {
        WheelRevelation r = requiredRevelation(key);
        if (r == null) {
            for (WheelRevelation x : WheelRevelation.values()) if (key.equals(x.spell)) r = x;
        }
        return r == null ? 0 : pc.wheelBonus().stage(r, pc.vocation());
    }

    /** Czy postac moze rzucic czar (czary objawien wymagaja stopnia co najmniej 1). */
    public static boolean unlocked(PlayerCharacter pc, SpellType s) {
        WheelRevelation r = requiredRevelation(s.key());
        return r == null || pc.wheelBonus().stage(r, pc.vocation()) > 0;
    }

    /** Czary o wspolnym odnowieniu (Wybuch ziemi i Wybuch lodu: grupa wtorna 22 s w Canary). */
    public static List<String> linked(String key) {
        if (key.equals("terra_burst")) return List.of("ice_burst");
        if (key.equals("ice_burst")) return List.of("terra_burst");
        return Collections.emptyList();
    }

    /** Suma premii czaru: ulepszenia z pol przekonania i premie stopni objawien. */
    public static SpellAugment augment(PlayerCharacter pc, SpellType s) {
        WheelBonus b = pc.wheelBonus();
        if (b == WheelBonus.NONE) return SpellAugment.NONE;
        Vocation v = pc.vocation();
        SpellAugment a = SpellAugment.NONE;
        for (int g = 0; g < 5; g++) {
            WheelPerks.Upgrade u = WheelPerks.upgrade(v, g);
            if (u.spells.contains(s.key())) a = a.plus(u.at(b.grade(g)));
        }
        a = a.plus(b.gems().spell(s.key())); // klejnoty: mody najwyzsze czarow (obrazenia, krytyk, leczenie, odnowienie)
        int st = revelationStage(pc, s.key());
        int extra = Math.max(0, st - 1); // stopnie 2 i 3
        switch (s.key()) {
            case "executioners_throw": a = a.plus(SpellAugment.cooldown(2000 * extra)); break;
            case "divine_grenade": a = a.plus(SpellAugment.cooldown(6000 * extra)); break;
            case "divine_empowerment": case "terra_burst": case "ice_burst": case "spiritual_outburst":
                a = a.plus(SpellAugment.cooldown(4000 * extra));
                break;
            case "great_death_beam":
                a = a.plus(new SpellAugment(0, 2000 * extra, 6 * extra + beamMastery(st), 0, 0, 0, 0, 0, 0, extra));
                break;
            default:
        }
        if (requiredRevelation(s.key()) != null && requiredRevelation(s.key()).isAvatar() && st > 0) {
            a = a.plus(SpellAugment.cooldown((int) (DestinyWheel.AVATAR_COOLDOWN_CUT_MS * extra)));
        }
        if (s.kind().isOffensive() && !s.kind().isRune()) {
            int drain = b.stage(WheelRevelation.DRAIN_BODY, v);
            if (drain > 0) a = a.plus(new SpellAugment(0, 0, 0, 0, 2 + drain, 1 + drain, 0, 0, 0, 0));
        }
        if (s.kind().isHeal() && b.has(WheelPassive.HEALING_LINK)) a = a.plus(SpellAugment.heal(10));
        return a;
    }

    /** Mistrzostwo promienia: +10/12/14% obrazen promieni. */
    private static double beamMastery(int stage) {
        return stage <= 0 ? 0 : 8 + 2 * stage;
    }

    /** Koszt many z premia (nie mniej niz 0). */
    public static int mana(PlayerCharacter pc, SpellType s) {
        return Math.max(0, s.mana() - augment(pc, s).manaCut);
    }

    /** Odnowienie z premia (nie krocej niz wspolne odnowienie grupy). */
    public static long cooldownMs(PlayerCharacter pc, SpellType s) {
        long cut = augment(pc, s).cooldownCutMs;
        return Math.max(Math.min(s.cooldownMs(), s.group().groupCooldownMs), s.cooldownMs() - cut);
    }

    /**
     * Mnoznik wartosci czaru na caly rzut (wolany raz na rzucenie): obrazenia/leczenie z ulepszen, Boski granat
     * (x1,3/1,6/2), Mistrzostwo skupienia (+35% nastepnego ataku po czarze skupienia), Sanktuarium i Blogoslawienstwo
     * gaju dla leczenia. Zuzywa jednorazowe premie (skupienie).
     */
    public static double castFactor(PlayerCharacter pc, SpellType s, SpellAugment a) {
        WheelBonus b = pc.wheelBonus();
        if (b == WheelBonus.NONE) return 1;
        double f = 1;
        if (s.kind().isHeal()) {
            double pct = a.healPercent + pc.wheel().sanctuaryPercent();
            int grove = b.stage(WheelRevelation.BLESSING_OF_THE_GROVE, pc.vocation());
            if (grove > 0) {
                int hp = (int) Math.round(pc.health() * 100.0 / Math.max(1, pc.maxHealth()));
                if (hp <= 30) pct += 6 * (grove + 1);
                else if (hp <= 60) pct += 3 * (grove + 1);
            }
            return 1 + pct / 100.0;
        }
        if (!s.kind().isOffensive()) return 1;
        double pct = a.damagePercent;
        if (b.has(WheelPassive.FOCUS_MASTERY) && !s.kind().isRune()) {
            if (pc.wheel().useFocus()) pct += FOCUS_MASTERY_PERCENT;
            if (FOCUS_SPELLS.contains(s.key())) pc.wheel().startFocus(FOCUS_MASTERY_MS);
        }
        f *= 1 + pct / 100.0;
        if (s.key().equals("divine_grenade")) {
            int st = b.stage(WheelRevelation.DIVINE_GRENADE, pc.vocation());
            f *= st >= 3 ? 2.0 : st == 2 ? 1.6 : 1.3;
        }
        if (s.kind().isRune() && (b.has(WheelPassive.RUNIC_MASTERY))) {
            // Canary Combat::getLevelFormula: 25% szansy na +20% poziomu magii w runie - u nas +20% wartosci runy
            if (pc.wheel().rollRunic()) f *= 1.2;
        }
        return f;
    }

    /** Plaska premia leczenia objawien (+4/+9/+20 za stopien kazdej domeny) dla czarow leczacych. */
    public static int flatHealing(PlayerCharacter pc, SpellType s) {
        return s.kind().isHeal() ? pc.wheelBonus().flatHealing : 0;
    }

    /** Premia zalezna od celu: Rzut kata (cel z HP do 30%), Blizniacze wybuchy (cel z HP ponad 60%). */
    public static int vsTarget(PlayerCharacter pc, SpellType s, Monster m, int amount) {
        if (m == null || amount <= 0) return amount;
        WheelBonus b = pc.wheelBonus();
        if (b == WheelBonus.NONE) return amount;
        int hp = (int) Math.round(m.health() * 100.0 / Math.max(1, m.type().maxHealth()));
        double pct = 0;
        if (s.key().equals("executioners_throw") && hp <= 30) {
            int st = b.stage(WheelRevelation.EXECUTIONERS_THROW, pc.vocation());
            if (st > 0) pct = 75 + 25 * st;
        }
        if ((s.key().equals("terra_burst") || s.key().equals("ice_burst")) && hp > 60) {
            pct = 20 * b.stage(WheelRevelation.TWIN_BURST, pc.vocation());
        }
        return pct == 0 ? amount : (int) Math.min(Integer.MAX_VALUE, Math.round(amount * (1 + pct / 100.0)));
    }

    /** Wybuch ducha z pelna harmonia (5): drugie uderzenie 37,5/50/62,5% (Canary spiritual_outburst.lua); 0 = brak. */
    public static double recast(PlayerCharacter pc, SpellType s) {
        if (!s.key().equals("spiritual_outburst") || pc.harmony().points() < pl.fawizja.pixel.vocation.Harmony.MAX) return 0;
        int st = pc.wheelBonus().stage(WheelRevelation.SPIRITUAL_OUTBURST, pc.vocation());
        return st <= 0 ? 0 : (37.5 + 12.5 * (st - 1)) / 100.0;
    }

    /** Asceta (mnich, niebieska): dodatkowe punkty procentowe bazy premii harmonii (Canary getHarmonyBonus). */
    public static int ascetic(PlayerCharacter pc) {
        return pc.wheelBonus().stage(WheelRevelation.ASCETIC, pc.vocation());
    }

    /** Przewodnia obecnosc (zamiennik mantry): leczenie z harmonii +10%. */
    public static int harmonyHeal(PlayerCharacter pc, int amount) {
        return pc.wheelBonus().has(WheelPassive.GUIDING_PRESENCE) ? (int) Math.round(amount * 1.1) : amount;
    }

    /** Sanktuarium: mnich wydaje harmonie - +2% obrazen i leczenia za punkt na 5 s. */
    public static void onHarmonySpent(PlayerCharacter pc, int spent) {
        if (spent > 0 && pc.wheelBonus().has(WheelPassive.SANCTUARY)) pc.wheel().startSanctuary(2 * spent, SANCTUARY_MS);
    }

    /** Odnowienie awatara pozostale z zapisu (blokuje rzucenie mimo pustego licznika czarow po zalogowaniu). */
    public static boolean avatarCoolingDown(PlayerCharacter pc, SpellType s) {
        WheelRevelation r = requiredRevelation(s.key());
        return r != null && r.isAvatar() && pc.wheel().avatarCooldownMs() > 0;
    }

    /**
     * Czar objawienia bez obrazen (rodzaj WHEEL_BUFF): awatar (15 s) albo Boskie wzmocnienie (pole 3x3 na 5 s).
     * Zwraca opis do dziennika albo null, gdy czar nie jest takim czarem.
     */
    public static String buff(PlayerCharacter pc, SpellType s) {
        if (s.kind() != SpellKind.WHEEL_BUFF) return null;
        WheelRevelation r = requiredRevelation(s.key());
        if (r == null) return null;
        int st = pc.wheelBonus().stage(r, pc.vocation());
        if (st <= 0) return null;
        if (r.isAvatar()) {
            pc.wheel().startAvatar(st, cooldownMs(pc, s));
            return s.name() + ": 15 s, -" + 5 * st + "% otrzymanych obrażeń, każdy cios krytyczny (+" + 5 * st + "%).";
        }
        int pct = st >= 3 ? 12 : st == 2 ? 10 : 8;
        pc.wheel().startEmpowerment(pc.position(), pct, EMPOWERMENT_MS);
        return s.name() + ": pole 3x3 na 5 s, +" + pct + "% obrażeń, gdy na nim stoisz.";
    }
}
