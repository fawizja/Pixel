package pl.fawizja.pixel.character;

import java.util.List;
import pl.fawizja.pixel.item.ItemStack;

/**
 * Wynik smierci postaci ({@link PlayerCharacter#applyDeath}): ile blogoslawienstw chronilo, jaki ulamek doswiadczenia,
 * umiejetnosci i magii stracono oraz co wypadlo do ciala. Szanse na utrate przedmiotow jak w serwerach Tibii (TFS):
 * plecak (kontener) wypada ze 100% bez blogoslawienstw, a kazdy zalozony przedmiot z 10%; kolejne blogoslawienstwa
 * zmniejszaja to do 70/45/25/10/0% (kontener) i 7/4,5/2,5/1/0% (przedmiot). Pieciu blogoslawienstw nic nie zabiera.
 */
public record DeathLoss(int blessings, double lossFraction, long expLost, int levelsLost, int skillLevelsLost,
                        boolean pvp, boolean skulled, boolean twistUsed, boolean amuletUsed, boolean backpackDropped,
                        List<ItemStack> dropped) {

    /** Klucz przedmiotu Amulet Straty (items.csv). */
    public static final String AMULET_OF_LOSS = "amulet_of_loss";

    private static final double[] CHANCE = {100, 70, 45, 25, 10, 0};

    /** Szansa (0..1), ze plecak (kontener) wypadnie przy smierci z danymi blogoslawienstwami (0..5). */
    public static double backpackChance(int blessings) { return CHANCE[Math.max(0, Math.min(5, blessings))] / 100.0; }

    /** Szansa (0..1), ze pojedynczy zalozony przedmiot wypadnie. */
    public static double itemChance(int blessings) { return CHANCE[Math.max(0, Math.min(5, blessings))] / 1000.0; }

    public int itemCount() {
        int n = 0;
        for (ItemStack s : dropped) n += s.count();
        return n;
    }
}
