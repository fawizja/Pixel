package pl.fawizja.pixel.wheel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.combat.CombatFormulas;
import pl.fawizja.pixel.combat.CriticalHit;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.combat.FightMode;
import pl.fawizja.pixel.combat.Leech;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Symulacja balansu: postac 300 poziomu (250 punktow promocji) z kolem jest silniejsza od tej samej postaci bez kola,
 * ale nie absurdalnie. Mierzymy srednie obrazenia ciosu w potwora (premie procentowe, plaska premia, krytyki),
 * obrazenia otrzymane (pancerz + mitygacja), maks. HP i kradziez zycia, a takze okno awatara i pelne kolo na 1000.
 */
class WheelBalanceTest {
    static final int SAMPLES = 20_000;

    static PlayerCharacter geared(Vocation v, int level) {
        PlayerCharacter pc = WheelFixtures.hero(v, level);
        int main = Math.min(130, level / 3); // ok. 100 umiejetnosci na 300 poziomie
        for (Skill s : Skill.values()) pc.skills().set(s, 10, 0);
        switch (v) {
            case KNIGHT: pc.skills().set(Skill.SWORD, main, 0); pc.skills().set(Skill.SHIELDING, main, 0); break;
            case PALADIN: pc.skills().set(Skill.DISTANCE, main, 0); pc.skills().set(Skill.SHIELDING, main * 3 / 4, 0); break;
            case MONK: pc.skills().set(Skill.FIST, main, 0); pc.skills().set(Skill.SHIELDING, main * 3 / 4, 0); break;
            default: pc.skills().set(Skill.MAGIC, main, 0); pc.skills().set(Skill.SHIELDING, 30, 0);
        }
        // najlepszy pancerz na kazde miejsce, jaki postac moze nosic
        for (EquipSlot slot : EquipSlot.values()) {
            if (slot == EquipSlot.NONE || slot == EquipSlot.WEAPON) continue;
            ItemType best = null;
            for (ItemType t : TestData.ITEMS.all()) {
                if (t.slot() != slot || !t.usableBy(v) || t.minLevel() > level) continue;
                if (best == null || t.armor() + t.defense() > best.armor() + best.defense()) best = t;
            }
            if (best != null) pc.equipment().setDirect(slot, new ItemStack(best.id(), 1));
        }
        return pc;
    }

    /** Sredni cios w potwora (bez kola = 1.0): premie kola i krytyki z tej samej sekwencji losowej. */
    static double avgHit(PlayerCharacter pc, int raw) {
        Random rng = new Random(11);
        long sum = 0;
        for (int i = 0; i < SAMPLES; i++) {
            int v = WheelEffects.outgoing(pc, raw);
            sum += CriticalHit.apply(pc, v, CriticalHit.roll(pc, rng));
        }
        return sum / (double) SAMPLES;
    }

    static double avgTaken(PlayerCharacter pc, int hit) {
        Random rng = new Random(13);
        long sum = 0;
        for (int i = 0; i < SAMPLES; i++) sum += CombatFormulas.mitigateOnPlayer(pc, hit, DamageType.PHYSICAL, FightMode.BALANCED, true, rng);
        return sum / (double) SAMPLES;
    }

    /** Sila = (obrazenia zadane) x (przetrwanie: HP / otrzymane) x (1 + kradziez zycia). */
    static double power(PlayerCharacter pc) {
        double dealt = avgHit(pc, 300);
        double survive = pc.maxHealth() / Math.max(1, avgTaken(pc, 450));
        return dealt * survive * (1 + Leech.lifePercent(pc) / 100);
    }

    @Test
    void level300WithTheWheelIsStrongerButNotAbsurd() {
        StringBuilder report = new StringBuilder("Kolo Przeznaczenia - balans (300 poz., 250 pkt):\n");
        for (Vocation v : Vocation.values()) {
            for (String order : new String[] {"GRPB", "RPBG", "PBGR", "BGRP"}) {
                PlayerCharacter base = geared(v, 300);
                base.setPremium(false); // to samo, ale kolo uspione
                PlayerCharacter wheel = geared(v, 300);
                WheelFixtures.fill(wheel, order);
                assertEquals(250, wheel.wheel().spent());
                double dmg = avgHit(wheel, 300) / avgHit(base, 300);
                double hp = wheel.maxHealth() / (double) base.maxHealth();
                double taken = avgTaken(wheel, 450) / avgTaken(base, 450);
                double p = power(wheel) / power(base);
                report.append(String.format(java.util.Locale.ROOT, "%-8s %s  obrazenia x%.3f  HP x%.3f  otrzymane x%.3f  sila x%.3f%n",
                        v, order, dmg, hp, taken, p));
                assertTrue(p > 1.02, v + " " + order + " wheel should help: " + p);
                assertTrue(p < 1.45, v + " " + order + " wheel too strong at 300: " + p);
                assertTrue(dmg < 1.25, v + " " + order + " damage bonus too big: " + dmg);
                assertTrue(taken <= 1.0001, v + " never takes more damage");
            }
        }
        System.out.print(report);
    }

    @Test
    void avatarWindowAndAFullWheelStayBounded() {
        for (Vocation v : Vocation.values()) {
            PlayerCharacter base = geared(v, 1000);
            base.setPremium(false);
            PlayerCharacter full = geared(v, 1000);
            WheelFixtures.fill(full, "PRGB");
            double p = power(full) / power(base);
            assertTrue(p > 1.05 && p < 2.2, v + " full wheel at 1000: " + p);
            full.wheel().startAvatar(full.wheelBonus().stage(WheelDomain.PURPLE), DestinyWheel.AVATAR_COOLDOWN_MS);
            double burst = power(full) / power(base);
            assertTrue(burst > p, v + " avatar is a real burst");
            assertTrue(burst < 3.5, v + " 15 s avatar window too strong: " + burst);
            System.out.printf(java.util.Locale.ROOT, "%-8s 1000 poz.: sila x%.3f, z awatarem x%.3f%n", v, p, burst);
        }
    }

    @Test
    void noSingleSpellUpgradeMultipliesDamageAbsurdly() {
        for (Vocation v : Vocation.values()) {
            for (int g = 0; g < 5; g++) {
                SpellAugment a = WheelPerks.upgrade(v, g).at(2);
                assertTrue(a.damagePercent <= 50, v + " group " + g + ": +" + a.damagePercent + "% (Canary SES +380% is scaled down)");
                assertTrue(a.healPercent <= 150, v + " group " + g + " heal +" + a.healPercent);
            }
        }
    }
}
