package pl.fawizja.pixel.character;

/** Liczniki postaci do okna /statystyki: czas gry, smierci i najwyzszy osiagniety poziom. */
public final class CharacterStats {
    private long playMs;
    private int deaths;
    private int bestLevel;
    private int challenges;
    private int restedKills;
    private int duelsWon;

    /** Wygrane pojedynki (liczy serwer). */
    public int duelsWon() { return duelsWon; }
    public void recordDuelWin() { if (duelsWon < Integer.MAX_VALUE) duelsWon++; }

    /** Potwory pokonane z premia wypoczecia. */
    public int restedKills() { return restedKills; }
    public void recordRestedKill() { if (restedKills < Integer.MAX_VALUE) restedKills++; }

    /** Ukonczone wyzwania ze zwojow. */
    public int challenges() { return challenges; }
    public void recordChallenge() { if (challenges < Integer.MAX_VALUE) challenges++; }

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

    /** Zapis: "czasMs,smierci,poziom,wyzwania,wypoczete,pojedynki" (starsze zapisy bez koncowych pol). */
    public String encode() { return playMs + "," + deaths + "," + bestLevel + "," + challenges + "," + restedKills + "," + duelsWon; }

    public void restore(String text) {
        playMs = 0;
        deaths = 0;
        bestLevel = 0;
        challenges = 0;
        restedKills = 0;
        duelsWon = 0;
        if (text == null || text.isBlank()) return;
        String[] f = text.split(",");
        try {
            if (f.length > 0) playMs = Math.max(0, Long.parseLong(f[0].trim()));
            if (f.length > 1) deaths = Math.max(0, Integer.parseInt(f[1].trim()));
            if (f.length > 2) bestLevel = Math.max(0, Integer.parseInt(f[2].trim()));
            if (f.length > 3) challenges = Math.max(0, Integer.parseInt(f[3].trim()));
            if (f.length > 4) restedKills = Math.max(0, Integer.parseInt(f[4].trim()));
            if (f.length > 5) duelsWon = Math.max(0, Integer.parseInt(f[5].trim()));
        } catch (NumberFormatException ignored) {
            // uszkodzony wpis - liczniki od zera
        }
    }

    public void copyFrom(CharacterStats o) {
        playMs = o.playMs;
        deaths = o.deaths;
        bestLevel = o.bestLevel;
        challenges = o.challenges;
        restedKills = o.restedKills;
        duelsWon = o.duelsWon;
    }
}
