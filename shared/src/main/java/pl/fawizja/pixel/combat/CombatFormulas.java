package pl.fawizja.pixel.combat;

import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Centralne formuly walki. Losowosc pochodzi z przekazanego Random (testy uzywaja
 * stalego ziarna). Wszystkie wyniki sa nieujemne i ograniczone do int.
 */
public final class CombatFormulas {

    /** Atak golymi rekami. */
    public static final int FIST_ATTACK = 7;
    /** Minimalny udzial maksymalnych obrazen w losowaniu (40%..100%). */
    public static final double MIN_ROLL = 0.4;
    /** Postawa obronna: mniej zadawanych i otrzymywanych obrazen. */
    public static final double DEFENSIVE_OUTGOING = 0.8;
    public static final double DEFENSIVE_INCOMING = 0.75;

    private CombatFormulas() {}

    /** Profil ataku postaci na podstawie broni, umiejetnosci i profesji. */
    public static AttackProfile attackProfile(PlayerCharacter pc, ItemRegistry items) {
        Vocation v = pc.vocation();
        ItemStack w = pc.equipment().get(EquipSlot.WEAPON);
        ItemType weapon = w == null ? null : items.get(w.itemId());
        int attack = weapon == null ? FIST_ATTACK : weapon.attack();
        int range = weapon == null ? 1 : Math.max(1, weapon.range());
        DamageType type = weapon == null ? DamageType.PHYSICAL : weapon.damageType();
        double bonus = pc.level() / 5.0;
        double max;
        Skill skill;
        if (type != DamageType.PHYSICAL && range > 1) {
            skill = Skill.MAGIC;
            max = attack * (1.0 + pc.skills().level(Skill.MAGIC) / 25.0) * v.magicFactor() + bonus;
        } else if (range > 1) {
            skill = Skill.DISTANCE;
            max = attack * (pc.skills().level(Skill.DISTANCE) + 10) / 20.0 * v.distanceFactor() + bonus;
        } else {
            skill = v == Vocation.MONK ? Skill.FIST : Skill.SWORD;
            max = attack * (pc.skills().level(skill) + 10) / 20.0 * v.meleeFactor() + bonus;
        }
        return new AttackProfile((int) Math.min(Integer.MAX_VALUE, Math.round(max)), range, type, skill, v.attackIntervalMs());
    }

    public static int roll(int max, Random rng) {
        if (max <= 0) return 0;
        int min = (int) Math.floor(max * MIN_ROLL);
        return min + rng.nextInt(max - min + 1);
    }

    public static int rollBetween(int min, int max, Random rng) {
        if (max <= min) return Math.max(0, min);
        return min + rng.nextInt(max - min + 1);
    }

    /** Obrazenia po pancerzu i odpornosciach potwora. */
    public static int mitigateOnMonster(MonsterType m, int damage, DamageType type, Random rng) {
        if (damage <= 0) return 0;
        long d = damage;
        if (type == DamageType.PHYSICAL && m.armor() > 0) {
            d -= rollBetween(m.armor() / 2, m.armor(), rng);
        }
        d = d * (100 - m.resistance(type)) / 100;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, d));
    }

    /** Obrazenia po pancerzu, tarczy i postawie postaci. */
    public static int mitigateOnPlayer(PlayerCharacter pc, int damage, DamageType type, boolean defensive, Random rng) {
        if (damage <= 0) return 0;
        double d = damage;
        if (type == DamageType.PHYSICAL) {
            int armor = pc.equipment().totalArmor();
            d -= rollBetween(0, armor / 2, rng);
            double defense = pc.equipment().totalDefense() * pc.vocation().defenseFactor()
                    * (pc.skills().level(Skill.SHIELDING) + 10) / 20.0;
            d -= rollBetween(0, (int) (defense / 3), rng);
        }
        if (defensive) d *= DEFENSIVE_INCOMING;
        return (int) Math.max(0, Math.round(d));
    }
}
