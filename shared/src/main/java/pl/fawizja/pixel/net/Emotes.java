package pl.fawizja.pixel.net;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emotki czatu: "/taniec" -> "* tańczy *", "/ja tekst" -> "* tekst *". Wysylane jak zwykly czat (dymek nad
 * glowa, ta sama moderacja), wyrozniane w dymku innym kolorem.
 */
public final class Emotes {

    private Emotes() {}

    /** Polecenie (bez polskich znakow) -> czynnosc. */
    private static final Map<String, String> ACTIONS = new LinkedHashMap<>();

    static {
        ACTIONS.put("/taniec", "tańczy");
        ACTIONS.put("/machaj", "macha");
        ACTIONS.put("/uklon", "kłania się");
        ACTIONS.put("/smiech", "śmieje się");
        ACTIONS.put("/brawo", "klaszcze");
        ACTIONS.put("/hura", "wiwatuje");
        ACTIONS.put("/placz", "płacze");
        ACTIONS.put("/siad", "siada");
        ACTIONS.put("/zzz", "drzemie");
    }

    /** Lista do pomocy: "/taniec, /machaj, ...". */
    public static final String LIST = "/taniec, /machaj, /ukłon, /śmiech, /brawo, /hura, /płacz, /siad, /zzz, /ja tekst";

    /**
     * "/kostka" albo "/kostka 20" -> liczba scian (2..100, domyslnie 6); -1 gdy to nie rzut kostka.
     * Wynik losuje serwer (online), zeby nikt go nie podrobil.
     */
    public static int diceSides(String typed) {
        if (typed == null) return -1;
        String[] w = typed.trim().split("\\s+");
        if (w.length > 2 || !ChatCommands.key(w[0]).equals("/kostka")) return -1;
        if (w.length == 1) return 6;
        try {
            int n = Integer.parseInt(w[1].replaceFirst("^[kKdD]", ""));
            return Math.max(2, Math.min(100, n));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Tekst rzutu w dymku: "* rzuca kostką k6: 4 *". */
    public static String diceText(int sides, int result) {
        return "* rzuca kostką k" + sides + ": " + result + " *";
    }

    /** Wiersze okna /emotki: polecenie, czynnosc, "", akcja "cmd:/polecenie" (dotkniecie wysyla emotke). */
    public static java.util.List<String[]> rows() {
        String[] shown = {"/taniec", "/machaj", "/ukłon", "/śmiech", "/brawo", "/hura", "/płacz", "/siad", "/zzz"};
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        for (String c : shown) rows.add(new String[] {c, ACTIONS.get(ChatCommands.key(c)) + " (dotknij)", "", "cmd:" + c});
        rows.add(new String[] {"/kostka", "rzut kostką k6 widoczny dla innych (dotknij); /kostka 20", "", "cmd:/kostka"});
        rows.add(new String[] {"/ja tekst", "własna czynność, np. /ja ostrzy miecz"});
        return rows;
    }

    /** Tekst emotki albo null, gdy to nie emotka. */
    public static String text(String typed) {
        if (typed == null) return null;
        String t = typed.trim();
        if (!t.startsWith("/")) return null;
        String[] w = t.split("\\s+", 2);
        String k = ChatCommands.key(w[0]);
        if (k.equals("/ja")) {
            if (w.length < 2 || w[1].isBlank()) return null;
            String r = w[1].trim();
            return "* " + (r.length() > 80 ? r.substring(0, 80) : r) + " *";
        }
        String a = ACTIONS.get(k);
        return a == null || w.length > 1 ? null : "* " + a + " *";
    }

    /**
     * Ruch postaci w trakcie emotki (w kafelkach, dodatni = w gore): taniec i wiwaty to podskoki, uklon
     * i siadanie lekko obnizaja postac, inne emotki stoja w miejscu.
     */
    public static float rise(String chatText, long ms) {
        if (!isEmote(chatText)) return 0f;
        switch (chatText) {
            case "* tańczy *":
            case "* wiwatuje *":
                return 0.18f * (float) Math.abs(Math.sin(ms * Math.PI / 400.0));
            case "* macha *":
            case "* klaszcze *":
            case "* śmieje się *":
                return 0.06f * (float) Math.abs(Math.sin(ms * Math.PI / 250.0));
            case "* kłania się *":
            case "* siada *":
            case "* drzemie *":
                return -0.12f;
            default:
                return 0f;
        }
    }

    public static boolean isEmote(String chatText) {
        return chatText != null && chatText.length() >= 4 && chatText.startsWith("* ") && chatText.endsWith(" *");
    }
}
