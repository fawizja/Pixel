package pl.fawizja.pixel.training;

import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.i18n.Messages;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Trening offline jak w Canary ({@code data/scripts/creaturescripts/player/offline_training.lua},
 * {@code skill_trainer.lua}, {@code Player::addOfflineTrainingTries}).
 *
 * <ul>
 * <li>Posag w swiatyni wymaga konta premium (Canary: RETURNVALUE_YOUNEEDPREMIUMACCOUNT) i braku blokady PZ.
 *     Uzycie wybiera umiejetnosc i wylogowuje postac.</li>
 * <li>Pasek "Trening offline" ma najwyzej 12 godzin ({@link #MAX_BAR_MS}); w grze odnawia sie 1:1 z czasem
 *     (Canary: {@code addOfflineTrainingTime(interval)} w {@code Player::onThink}); po wylogowaniu bez posagu
 *     odnawia sie rowniez o caly czas offline (maks. 21 dni liczone od wylogowania).</li>
 * <li>Przy kolejnym wejsciu: trzeba byc offline min. 10 minut, trening trwa
 *     {@code min(czasOffline, 12 h, pasek)}, reszta czasu wraca na pasek.</li>
 * <li>Proby: miecz/topor/maczuga/piesci {@code (czas / szybkoscAtaku[s]) / 2}, dystans {@code (czas / szybkoscAtaku) / 4},
 *     magia: czas * mana na sekunde (regeneracja profesji); tarcza zawsze {@code czas / 4} (tylko gdy cos awansowalo
 *     lub zdobylo proby w wybranej umiejetnosci). Proby przechodza przez mnozniki etapow serwera jak proby z gry.</li>
 * </ul>
 */
public final class OfflineTraining {

    /** Maksymalna dlugosc paska i jednego treningu (12 godzin). */
    public static final long MAX_BAR_MS = 12L * 3600_000L;
    /** Offline krocej niz 10 minut: trening sie nie liczy. */
    public static final long MIN_OFFLINE_MS = 10L * 60_000L;
    /** Offline dluzej niz 21 dni liczy sie jako 21 dni. */
    public static final long MAX_OFFLINE_MS = 21L * 24 * 3600_000L;
    /** Trening krotszy niz minuta nie daje prob. */
    public static final long MIN_TRAINING_MS = 60_000L;

    /** Wybor na posagu: umiejetnosc, ktora trenuje (tarcza zawsze razem z nia). */
    public enum Choice {
        SWORD(Skill.SWORD), AXE(Skill.AXE), CLUB(Skill.CLUB), FIST(Skill.FIST), DISTANCE(Skill.DISTANCE), MAGIC(Skill.MAGIC);

        private final Skill skill;

        Choice(Skill skill) { this.skill = skill; }

        public Skill skill() { return skill; }

        public static Choice of(String name) {
            if (name == null) return null;
            try {
                return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private long barMs = MAX_BAR_MS;
    private Choice choice;
    /** Czas zegara sciennego zapisu (do liczenia czasu offline); 0 = brak. */
    private long savedAtWallMs;

    public long barMs() { return barMs; }

    public Choice choice() { return choice; }

    public long savedAtWallMs() { return savedAtWallMs; }

    public void setBarMs(long ms) { barMs = Math.max(0, Math.min(MAX_BAR_MS, ms)); }

    /** Wybor umiejetnosci (null = brak treningu). */
    public void setChoice(Choice c) { choice = c; }

    void setSavedAtWallMs(long t) { savedAtWallMs = Math.max(0, t); }

    /** Pelny pasek 0..1. */
    public float fraction() { return barMs / (float) MAX_BAR_MS; }

    /** "hh:mm", np. "12:00". */
    public String display() {
        long min = barMs / 60_000L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", min / 60, min % 60);
    }

    /** Czas w grze: pasek odnawia sie 1:1 (Canary: co tik myslenia gracza). */
    public void regenerate(long deltaMs) {
        if (deltaMs > 0) setBarMs(barMs + deltaMs);
    }

    // ------------------------------------------------------------------ wzory Canary

    /** Mana na sekunde w wersji Canary: ilosc / odstep regeneracji profesji; w Pixelu to regeneracja z Balance. */
    public static double manaPerSecond(Vocation v, int level) {
        return Balance.manaRegenPerTick(v, level) / (double) Balance.REGEN_TICK_SECONDS;
    }

    /**
     * Surowe proby (przed mnoznikiem serwera) wybranej umiejetnosci za trainingMs treningu: Canary
     * {@code (trainingTime / modifier) / (distance ? 4 : 2)}, modifier = szybkosc ataku profesji w sekundach;
     * magia {@code trainingTime * manaNaSekunde}.
     */
    public static long baseTries(Vocation v, int level, Choice c, long trainingMs) {
        double seconds = Math.floor(trainingMs / 1000.0);
        if (seconds <= 0) return 0;
        if (c == Choice.MAGIC) return (long) (seconds * manaPerSecond(v, level));
        double modifier = v.attackIntervalMs() / 1000.0;
        return (long) ((seconds / modifier) / (c == Choice.DISTANCE ? 4 : 2));
    }

    /** Surowe proby tarczy za trainingMs: {@code trainingTime / 4} (sekundy). */
    public static long shieldTries(long trainingMs) { return (long) Math.floor(trainingMs / 1000.0) / 4; }

    // ------------------------------------------------------------------ wylogowanie przy posagu

    /** Czy postac moze uzyc posagu; null albo klucz komunikatu odmowy. */
    public static String refusal(PlayerCharacter pc, long nowMs) {
        if (!pc.isPremium()) return "offline_training.need_premium";
        if (pc.isDead()) return "offline_training.dead";
        if (pc.logoutBlockedMs(nowMs) > 0) return "offline_training.in_combat";
        return null;
    }

    // ------------------------------------------------------------------ zapis

    public String encode(long wallMs) {
        return barMs / 1000L + "," + (choice == null ? "-" : choice.name()) + "," + (savedAtWallMs > 0 ? savedAtWallMs : wallMs);
    }

    public void restore(String v) {
        if (v == null || v.isBlank()) return;
        try {
            String[] f = v.split(",");
            setBarMs(Long.parseLong(f[0].trim()) * 1000L);
            if (f.length > 1) choice = Choice.of(f[1]);
            if (f.length > 2) setSavedAtWallMs(Long.parseLong(f[2].trim()));
        } catch (NumberFormatException e) {
            barMs = MAX_BAR_MS;
            choice = null;
        }
    }

    public void copyFrom(OfflineTraining o) {
        barMs = o.barMs;
        choice = o.choice;
    }

    // ------------------------------------------------------------------ wejscie do gry

    /** Wynik rozliczenia treningu: komunikaty dla dziennika i dane do testow. */
    public record Report(long offlineMs, long trainedMs, Choice choice, long tries, long shieldTries, int levelsGained,
                         int shieldLevelsGained, List<String> messages) {
        public boolean trained() { return trainedMs >= MIN_TRAINING_MS; }
    }

    /**
     * Po zalogowaniu (zegar scienny w ms od 1970): Canary {@code OfflineTraining.onLogin}. Odnawia pasek za czas offline,
     * a gdy wybrano umiejetnosc - rozlicza trening i dopisuje proby. Wywolywac raz, po wczytaniu postaci.
     */
    public Report onLogin(PlayerCharacter pc, long nowWallMs) {
        long saved = savedAtWallMs;
        savedAtWallMs = 0;
        long offline = saved > 0 && nowWallMs > saved ? Math.min(nowWallMs - saved, MAX_OFFLINE_MS) : 0;
        Choice chosen = choice;
        List<String> msgs = new ArrayList<>();
        if (chosen == null) {
            regenerate(offline);
            return new Report(offline, 0, null, 0, 0, 0, 0, msgs);
        }
        choice = null;
        if (offline < MIN_OFFLINE_MS) {
            msgs.add(Messages.get("offline_training.too_short"));
            return new Report(offline, 0, chosen, 0, 0, 0, 0, msgs);
        }
        long training = Math.max(0, Math.min(offline, Math.min(MAX_BAR_MS, barMs)));
        barMs = Math.max(0, barMs - training);
        long remainder = offline - training;
        if (remainder > 0) regenerate(remainder);
        if (training < MIN_TRAINING_MS) return new Report(offline, training, chosen, 0, 0, 0, 0, msgs);

        msgs.add(Messages.get("offline_training.absence", durationText(training)));
        Skill sk = chosen.skill();
        long tries = baseTries(pc.vocation(), pc.level(), chosen, training);
        int levels = 0, shieldLevels = 0;
        long shield = 0;
        if (tries > 0) {
            int lvlBefore = pc.skills().level(sk);
            int pctBefore = pc.skills().percentToNext(sk);
            levels = pc.skills().train(sk, tries);
            msgs.add(Messages.get("offline_training.changed", sk.label(), lvlBefore, pctBefore, pc.skills().level(sk),
                    pc.skills().percentToNext(sk)));
            // Canary: tarcza tylko gdy wybrana umiejetnosc sie zmienila (awans albo inny procent postepu)
            if (levels > 0 || pc.skills().percentToNext(sk) != pctBefore) {
                shield = shieldTries(training);
                shieldLevels = pc.skills().train(Skill.SHIELDING, shield);
            }
        }
        return new Report(offline, training, chosen, tries, shield, levels, shieldLevels, msgs);
    }

    /** "12 godz. 5 min" - z paczki jezykowej. */
    static String durationText(long ms) {
        long total = ms / 1000;
        long h = total / 3600, m = (total % 3600) / 60;
        if (h > 0 && m > 0) return Messages.get("offline_training.hm", h, m);
        if (h > 0) return Messages.get("offline_training.h", h);
        return Messages.get("offline_training.m", m);
    }
}
