package pl.fawizja.pixel.item;

import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;

/**
 * Efekty tierow zalozonego sprzetu (Canary): bron - Natarcie, zbroja - Unik, helm - Rozped, spodnie - Transcendencja,
 * buty - Wzmocnienie (mnozy pozostale szanse x (1 + wzmocnienie / 100), jak Player::getDodgeChance, triggerMomentum,
 * triggerTranscendence i Combat::applyExtensions). Szanse liczy sie z tieru przedmiotu w polu.
 */
public final class ForgeEffects {
    private ForgeEffects() {}

    /** Mnoznik z butow z tierem (1 bez tieru). */
    public static double amplifier(PlayerCharacter pc) {
        return 1 + Forge.amplification(pc.equipment().tier(EquipSlot.FEET)) / 100.0;
    }

    private static double chance(PlayerCharacter pc, EquipSlot slot, java.util.function.IntToDoubleFunction f) {
        int t = pc.equipment().tier(slot);
        return t <= 0 ? 0 : f.applyAsDouble(t) * amplifier(pc);
    }

    /** Natarcie (bron): szansa w procentach na +60% obrazen. */
    public static double onslaughtChance(PlayerCharacter pc) { return chance(pc, EquipSlot.WEAPON, Forge::onslaught); }
    /** Unik (zbroja): szansa w procentach na unikniecie ciosu. */
    public static double ruseChance(PlayerCharacter pc) { return chance(pc, EquipSlot.ARMOR, Forge::ruse); }
    /** Rozped (helm): szansa co 2 s walki. */
    public static double momentumChance(PlayerCharacter pc) { return chance(pc, EquipSlot.HEAD, Forge::momentum); }
    /** Transcendencja (spodnie): szansa co 2 s po agresywnej akcji. */
    public static double transcendenceChance(PlayerCharacter pc) { return chance(pc, EquipSlot.LEGS, Forge::transcendence); }

    /** Rzut z szansy w procentach (setne czesci procenta jak w Canary); bez szansy - bez losowania. */
    public static boolean roll(double percent, Random rng) {
        return percent > 0 && rng.nextInt(10_000) < (int) Math.round(percent * 100);
    }

    public static boolean rollRuse(PlayerCharacter pc, Random rng) { return roll(ruseChance(pc), rng); }

    /** Obrazenia przyjete w czasie awatara (Transcendencja): -15%. */
    public static int avatarIncoming(PlayerCharacter pc, int dmg) {
        return dmg > 0 && pc.avatarActive() ? dmg - dmg * Forge.AVATAR_REDUCTION / 100 : dmg;
    }

    /** Opis do okna postaci: "Natarcie 3,3%, Unik 3%" (pusty bez tierow). */
    public static String describe(PlayerCharacter pc) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        double[] v = {onslaughtChance(pc), ruseChance(pc), momentumChance(pc), transcendenceChance(pc)};
        for (int i = 0; i < v.length; i++) if (v[i] > 0) parts.add(Forge.EFFECT_NAMES[i] + " " + ItemBonus.number(Math.round(v[i] * 100) / 100.0) + "%");
        int boots = pc.equipment().tier(EquipSlot.FEET);
        if (boots > 0) parts.add(Forge.EFFECT_NAMES[4] + " +" + ItemBonus.number(Math.round(Forge.amplification(boots) * 10) / 10.0) + "%");
        return String.join(", ", parts);
    }
}
