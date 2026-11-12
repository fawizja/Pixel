package pl.fawizja.pixel.admin;

import java.util.Locale;
import pl.fawizja.pixel.i18n.Messages;

/**
 * Poziom dostepu postaci jak w Canary (account type): gracz, tutor, game master i GOD (najwyzszy, wszystko).
 * Poziom nadaje wylacznie operator serwera (pliki admins.txt / gods.txt albo konsola) albo wlasciciel gry lokalnej;
 * klient nigdy nie przesyla swojego poziomu - serwer zawsze liczy go sam z nazwy postaci.
 */
public enum AccessLevel {
    PLAYER(0, ""), TUTOR(1, "TUTOR"), GAMEMASTER(2, "GM"), GOD(3, "GOD");

    private final int rank;
    private final String tag;

    AccessLevel(int rank, String tag) {
        this.rank = rank;
        this.tag = tag;
    }

    /** Znacznik widoczny przy imieniu ("GOD", "GM", "TUTOR"); pusty dla zwyklego gracza. */
    public String tag() { return tag; }

    public int rank() { return rank; }

    public boolean atLeast(AccessLevel other) { return rank >= other.rank; }

    public boolean staff() { return rank > 0; }

    /** Nazwa poziomu w jezyku gracza. */
    public String label() { return Messages.get("god.level_" + name().toLowerCase(Locale.ROOT)); }

    /** "god", "gm", "gamemaster", "tutor", "player"/"gracz" (bez wielkosci liter); null, gdy nieznany. */
    public static AccessLevel parse(String text) {
        if (text == null) return null;
        switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "god": return GOD;
            case "gm": case "gamemaster": case "game master": case "admin": case "administrator": return GAMEMASTER;
            case "tutor": return TUTOR;
            case "player": case "gracz": return PLAYER;
            default: return null;
        }
    }

    /** Znacznik z wiersza PL protokolu ("GOD", "GM", "TUTOR" albo "-"). */
    public static AccessLevel fromTag(String tag) {
        if (tag == null) return PLAYER;
        for (AccessLevel l : values()) if (!l.tag.isEmpty() && l.tag.equals(tag)) return l;
        return PLAYER;
    }
}
