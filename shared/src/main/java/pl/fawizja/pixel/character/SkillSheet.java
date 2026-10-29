package pl.fawizja.pixel.character;

import java.util.ArrayList;
import java.util.List;

/**
 * Wiersze okna umiejetnosci (jak okno Skills w Tibii): poziom, doswiadczenie, stamina (hh:mm), potem umiejetnosci
 * w kolejnosci {@link Skill#values()} - piesci, maczuga, miecz, topor, dystans, tarcza, wedkarstwo, magia.
 * Wspolne dla HUD-u na komputer i telefon.
 */
public final class SkillSheet {

    /** Kolor paska wiersza. */
    public enum Bar { NONE, EXPERIENCE, SKILL, STAMINA_BONUS, STAMINA, STAMINA_LOW, STAMINA_EMPTY }

    /** label, wartosc, postep paska 0..1 (ujemny = bez paska) i jego kolor. */
    public record Row(String label, String value, double progress, Bar bar) {}

    /** Tyle wierszy przed umiejetnosciami (poziom, doswiadczenie, stamina). */
    public static final int HEAD_ROWS = 3;

    private SkillSheet() {}

    public static int rowCount() { return HEAD_ROWS + Skill.values().length; }

    public static List<Row> rows(PlayerCharacter pc) {
        List<Row> out = new ArrayList<>(rowCount());
        out.add(new Row("Poziom", String.valueOf(pc.level()), -1, Bar.NONE));
        out.add(new Row("Doświadczenie", (int) (pc.levelProgress() * 100) + "%", pc.levelProgress(), Bar.EXPERIENCE));
        out.add(stamina(pc));
        for (Skill sk : Skill.values()) {
            double prog = (double) pc.skills().tries(sk) / Math.max(1, Skills.triesForNext(pc.skills().level(sk)));
            out.add(new Row(sk.label(), String.valueOf(pc.skills().level(sk)), prog, Bar.SKILL));
        }
        return out;
    }

    public static Row stamina(PlayerCharacter pc) {
        Stamina s = pc.stamina();
        Bar bar;
        switch (s.zone()) {
            case BONUS: bar = Bar.STAMINA_BONUS; break;
            case LOW: bar = Bar.STAMINA_LOW; break;
            case EMPTY: bar = Bar.STAMINA_EMPTY; break;
            default: bar = Bar.STAMINA;
        }
        return new Row("Stamina", s.display(), s.fraction(), bar);
    }

    /** Kolor paska RGB (0xRRGGBB): zielony premii, pomaranczowy niskiej staminy, czerwony pustej. */
    public static int color(Bar bar) {
        switch (bar) {
            case EXPERIENCE: return 0xf2cc33;
            case STAMINA_BONUS: return 0x3fd94a;
            case STAMINA: return 0xd8d040;
            case STAMINA_LOW: return 0xf08a20;
            case STAMINA_EMPTY: return 0xd02a20;
            default: return 0x4dbf66;
        }
    }

    /** Opis do okna postaci: "41:37 (premia 150% exp)" itp. */
    public static String staminaDetail(PlayerCharacter pc) {
        Stamina s = pc.stamina();
        switch (s.zone()) {
            case BONUS: return s.display() + (pc.isPremium() ? " (premia 150% exp)" : " (premia dla premium)");
            case LOW: return s.display() + " (50% exp)";
            case EMPTY: return s.display() + " (bez exp)";
            default: return s.display();
        }
    }
}
