package pl.fawizja.pixel.wheel;

import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Punkty zaczepienia Kola Przeznaczenia w walce (male, odizolowane wywolania z CombatFormulas, CombatSystem,
 * CriticalHit, Leech i PlayerCharacter). Wszystkie metody zwracaja wartosc neutralna, gdy kolo jest nieaktywne.
 */
public final class WheelEffects {
    /** Dar Zycia: procent HP i odnowienie w godzinach gry (Canary getGiftOfLifeValue / getGiftOfLifeTotalCooldown). */
    static final int[] GIFT_PERCENT = {20, 25, 30};
    static final int[] GIFT_COOLDOWN_H = {30, 20, 10};
    /** Leczenie w boju najwyzej co tyle (u nas przy ciosie potwora). */
    public static final long BATTLE_HEAL_COOLDOWN_MS = 2_000;

    private WheelEffects() {}

    public static WheelBonus bonus(PlayerCharacter pc) { return pc.wheelBonus(); }

    // ------------------------------------------------------------------ umiejetnosci

    /**
     * Premia do umiejetnosci: +1 za pole przekonania (wrecz/dystans/magia/piesci wg profesji), Instynkt bitewny
     * (rycerz: 5+ potworow obok, +1 wrecz i +6 tarczy za kazdego ponad 4), Taktyka pozycyjna (paladyn: bez potwora obok
     * +3 dystansu, z potworem +3 magii).
     */
    public static int skillBonus(PlayerCharacter pc, Skill s) {
        WheelBonus b = pc.wheelBonus();
        if (b == WheelBonus.NONE) return 0;
        int bonus = 0;
        Vocation v = pc.vocation();
        if (b.skill > 0 && ownSkill(v, s)) bonus += b.skill;
        int near = pc.wheel().adjacentMonsters();
        if (b.has(WheelPassive.BATTLE_INSTINCT) && near >= 5) {
            if (s.isMeleeWeapon()) bonus += near - 4;
            if (s == Skill.SHIELDING) bonus += 6 * (near - 4);
        }
        if (b.has(WheelPassive.POSITIONAL_TACTICS)) {
            if (near == 0 && s == Skill.DISTANCE) bonus += 3;
            if (near > 0 && s == Skill.MAGIC) bonus += 3;
        }
        return bonus;
    }

    private static boolean ownSkill(Vocation v, Skill s) {
        switch (v) {
            case KNIGHT: return s.isMeleeWeapon();
            case PALADIN: return s == Skill.DISTANCE;
            case MONK: return s == Skill.FIST;
            default: return s == Skill.MAGIC;
        }
    }

    // ------------------------------------------------------------------ obrazenia zadawane

    /**
     * Obrazenia postaci w potwora (bron, czar, runa) przed krytykiem: premie procentowe (Boskie wzmocnienie,
     * Sanktuarium, Mistrzostwo balistyki ze strzalami) i plaska premia objawien (+4/+9/+20 za stopien domeny).
     */
    public static int outgoing(PlayerCharacter pc, int raw) {
        if (raw <= 0) return raw;
        WheelBonus b = pc.wheelBonus();
        if (b == WheelBonus.NONE) return raw;
        double pct = pc.wheel().empowerment(pc.position()) + pc.wheel().sanctuaryPercent();
        if (b.has(WheelPassive.BALLISTIC_MASTERY) && ammo(pc) == Ammo.ARROW) pct += 2;
        double v = raw * (1 + pct / 100.0) + b.flatDamage;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(v));
    }

    enum Ammo { NONE, ARROW, BOLT }

    /** Luk Pixela: kusza strzela beltami, inny luk strzalami (Mistrzostwo balistyki). */
    static Ammo ammo(PlayerCharacter pc) {
        ItemStack w = pc.equipment().get(EquipSlot.WEAPON);
        if (w == null || pc.equipment().weaponType() != pl.fawizja.pixel.item.WeaponType.DISTANCE) return Ammo.NONE;
        String key = pc.equipment().weaponKey();
        if (key == null || !key.endsWith("bow")) return Ammo.NONE;
        return key.endsWith("crossbow") ? Ammo.BOLT : Ammo.ARROW;
    }

    /** Dodatkowa szansa na krytyk w procentach: awatar 100%, ulepszenie rzucanego czaru. */
    public static double critChance(PlayerCharacter pc) {
        if (pc.wheelBonus() == WheelBonus.NONE) return 0;
        double c = pc.wheel().castAugment().critChance;
        if (pc.wheel().avatarActive()) c += 100;
        return c;
    }

    /**
     * Dodatkowe obrazenia krytyczne w procentach: awatar +5/10/15%, ulepszenie czaru, Mistrzostwo walki (rycerz bez
     * tarczy +4/8/12%), Mistrzostwo balistyki (kusza +10%).
     */
    public static double critDamage(PlayerCharacter pc) {
        WheelBonus b = pc.wheelBonus();
        if (b == WheelBonus.NONE) return 0;
        // awatar kola i Transcendencja z Kuzni to jeden stan awatara w Tibii: przy Transcendencji (+15%) awatar kola nic nie dodaje
        double d = pc.wheel().castAugment().critDamage + (pc.avatarActive() ? 0 : 5 * pc.wheel().avatarStage());
        int cm = b.stage(WheelRevelation.COMBAT_MASTERY, pc.vocation());
        if (cm > 0 && pc.equipment().shieldDefense() <= 0) d += 4 * cm;
        if (b.has(WheelPassive.BALLISTIC_MASTERY) && ammo(pc) == Ammo.BOLT) d += 10;
        return d;
    }

    /** Kradziez zycia z kola w procentach (pola przekonania + czar). */
    public static double lifeLeech(PlayerCharacter pc) {
        WheelBonus b = pc.wheelBonus();
        return b == WheelBonus.NONE ? 0 : b.lifeLeech + pc.wheel().castAugment().lifeLeech;
    }

    /** Kradziez many z kola w procentach (pola przekonania + czar). */
    public static double manaLeech(PlayerCharacter pc) {
        WheelBonus b = pc.wheelBonus();
        return b == WheelBonus.NONE ? 0 : b.manaLeech + pc.wheel().castAugment().manaLeech;
    }

    // ------------------------------------------------------------------ obrazenia otrzymywane

    /** Mnoznik pochlaniania pancerza i obrony (mitygacja z kola, procent wzgledny jak Canary calculateMitigation). */
    public static double mitigationFactor(PlayerCharacter pc) {
        WheelBonus b = pc.wheelBonus();
        return b == WheelBonus.NONE ? 1 : 1 + b.mitigation / 100.0;
    }

    /** Mistrzostwo walki z tarcza: +10/20/30 obrony tarczy (Canary WheelMajor_t::DEFENSE). */
    public static int extraDefense(PlayerCharacter pc) {
        WheelBonus b = pc.wheelBonus();
        if (b == WheelBonus.NONE || pc.equipment().shieldDefense() <= 0) return 0;
        return 10 * b.stage(WheelRevelation.COMBAT_MASTERY, pc.vocation());
    }

    /** Awatar: -5/10/15% otrzymanych obrazen (Canary checkAvatarSkill DAMAGE_REDUCTION, zaokraglenie w gore). */
    public static double incoming(PlayerCharacter pc, double damage) {
        int st = pc.wheel().avatarStage();
        if (st <= 0 || damage <= 0 || pc.avatarActive()) return damage; // Transcendencja juz daje -15% (maks. etapu 3)
        return damage - Math.ceil(damage * 5 * st / 100.0);
    }

    /** Leczenie w boju (rycerz, fioletowe 200): cios potwora leczy 0,2 x tarcza, x2 przy HP do 60%, x3 do 30%. */
    public static int onMonsterStrike(PlayerCharacter pc) {
        WheelBonus b = pc.wheelBonus();
        if (b == WheelBonus.NONE || !b.has(WheelPassive.BATTLE_HEALING) || !pc.wheel().battleHealReady() || pc.isDead()) return 0;
        double amount = pc.effectiveSkill(Skill.SHIELDING) * 0.2;
        int hpPct = pc.health() * 100 / Math.max(1, pc.maxHealth());
        if (hpPct <= 30) amount *= 3;
        else if (hpPct <= 60) amount *= 2;
        pc.wheel().startBattleHealCooldown(BATTLE_HEAL_COOLDOWN_MS);
        return pc.heal((int) amount);
    }

    /**
     * Dar Zycia (Canary Game::applyHealthChange): cios, ktory zabilby postac, gdy nadwyzka obrazen jest najwyzej
     * X% maks. HP, leczy X% HP (20/25/30%) i postac przezywa; odnowienie 30/20/10 h gry. Zwraca HP po ciosie albo -1,
     * gdy Dar nie zadzialal.
     */
    public static int giftOfLife(PlayerCharacter pc, int health, int damage) {
        if (damage < health || health <= 0) return -1;
        WheelBonus b = pc.wheelBonus();
        int st = b.stage(WheelDomain.GREEN);
        if (st <= 0 || pc.wheel().giftCooldownMs() > 0) return -1;
        int max = pc.maxHealth();
        int pct = GIFT_PERCENT[st - 1];
        long overkillPct = (long) (damage - health) * 100 / Math.max(1, max);
        if (overkillPct > pct) return -1;
        int healed = Math.min(max, health + max * pct / 100);
        pc.wheel().setGiftCooldown(GIFT_COOLDOWN_H[st - 1] * 3_600_000L);
        pc.wheel().markGift();
        return Math.max(1, healed - damage);
    }
}
