package pl.fawizja.pixel.character;

/** Liczniki postaci do okna /statystyki: czas gry, smierci i najwyzszy osiagniety poziom. */
public final class CharacterStats {
    private long playMs;
    private int deaths;
    private int bestLevel;

    public long playMs() { return playMs; }
    public int deaths() { return deaths; }
    public int bestLevel() { return bestLevel; }

    public void addPlayTime(long dtMs) {
        if (dtMs > 0) playMs = Math.min(Long.MAX_VALUE / 2, playMs + dtMs);
    }

    public void recordDeath() { if (deaths < Integer.MAX_VALUE) deaths++; }

    public void noteLevel(int level) { bestLevel = Math.max(bestLevel, level); }

    /** "12 h 05 min" albo "7 min". */
    public static String describePlayTime(long ms) {
        long min = ms / 60_000;
        if (min < 60) return min + " min";
        long h = min / 60;
        return (h >= 24 ? (h / 24) + " d " + (h % 24) + " h " : h + " h ") + String.format("%02d", min % 60) + " min";
    }

    /** Zapis: "czasMs,smierci,poziom". */
    public String encode() { return playMs + "," + deaths + "," + bestLevel; }

    public void restore(String text) {
        playMs = 0;
        deaths = 0;
        bestLevel = 0;
        if (text == null || text.isBlank()) return;
        String[] f = text.split(",");
        try {
            if (f.length > 0) playMs = Math.max(0, Long.parseLong(f[0].trim()));
            if (f.length > 1) deaths = Math.max(0, Integer.parseInt(f[1].trim()));
            if (f.length > 2) bestLevel = Math.max(0, Integer.parseInt(f[2].trim()));
        } catch (NumberFormatException ignored) {
            // uszkodzony wpis - liczniki od zera
        }
    }

    public void copyFrom(CharacterStats o) {
        playMs = o.playMs;
        deaths = o.deaths;
        bestLevel = o.bestLevel;
    }
}
