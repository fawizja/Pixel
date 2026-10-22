package pl.fawizja.pixel.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * Konfigurowalne ustawienia startowe nowych postaci.
 * Domyslne wartosci: poziom 8, 10 000 gp w plecaku, 0 gp w banku, start w miescie Varden.
 * Gra offline (bez serwera) moze startowac z wyzszego poziomu ({@code start.level.offline}).
 */
public final class StartingConfig {

    public static final String DEFAULT_RESOURCE = "/data/starting.properties";

    private final int startLevel;
    private final long startGoldInBackpack;
    private final long startBankBalance;
    private final String startTown;
    private final int maxLevel;
    private final int offlineStartLevel;

    public StartingConfig(int startLevel, long startGoldInBackpack, long startBankBalance,
                          String startTown, int maxLevel) {
        this(startLevel, startGoldInBackpack, startBankBalance, startTown, maxLevel, startLevel);
    }

    public StartingConfig(int startLevel, long startGoldInBackpack, long startBankBalance,
                          String startTown, int maxLevel, int offlineStartLevel) {
        if (maxLevel < 1) throw new IllegalArgumentException("maxLevel must be >= 1");
        if (startLevel < 1 || startLevel > maxLevel) {
            throw new IllegalArgumentException("startLevel out of range: " + startLevel);
        }
        if (startGoldInBackpack < 0 || startBankBalance < 0) {
            throw new IllegalArgumentException("starting money cannot be negative");
        }
        this.startLevel = startLevel;
        this.startGoldInBackpack = startGoldInBackpack;
        this.startBankBalance = startBankBalance;
        this.startTown = Objects.requireNonNull(startTown);
        this.maxLevel = maxLevel;
        if (offlineStartLevel < startLevel || offlineStartLevel > maxLevel) {
            throw new IllegalArgumentException("offline start level out of range: " + offlineStartLevel);
        }
        this.offlineStartLevel = offlineStartLevel;
    }

    public static StartingConfig defaults() {
        return new StartingConfig(8, 10_000L, 0L, "varden", 1000);
    }

    /** Wczytuje konfiguracje z pliku properties; brakujace klucze przyjmuja wartosci domyslne. */
    public static StartingConfig fromProperties(Properties p) {
        StartingConfig d = defaults();
        String level = p.getProperty("start.level", String.valueOf(d.startLevel)).trim();
        return new StartingConfig(
                Integer.parseInt(level),
                Long.parseLong(p.getProperty("start.gold.backpack", String.valueOf(d.startGoldInBackpack)).trim()),
                Long.parseLong(p.getProperty("start.gold.bank", String.valueOf(d.startBankBalance)).trim()),
                p.getProperty("start.town", d.startTown).trim(),
                Integer.parseInt(p.getProperty("level.max", String.valueOf(d.maxLevel)).trim()),
                Integer.parseInt(p.getProperty("start.level.offline", level).trim()));
    }

    public static StartingConfig loadDefault() {
        try (InputStream in = StartingConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) return defaults();
            Properties p = new Properties();
            p.load(in);
            // -Dpixel.start.level / -Dpixel.start.gold.bank nadpisuja plik (np. testy sieciowe na poziomie 8)
            for (String key : new String[] {"start.level", "start.gold.bank", "start.gold.backpack"}) {
                String v = System.getProperty("pixel." + key);
                if (v != null) p.setProperty(key, v);
            }
            return fromProperties(p);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + DEFAULT_RESOURCE, e);
        }
    }

    public int startLevel() { return startLevel; }
    public long startGoldInBackpack() { return startGoldInBackpack; }
    public long startBankBalance() { return startBankBalance; }
    public String startTown() { return startTown; }
    public int maxLevel() { return maxLevel; }
    /** Poziom nowej postaci w grze offline (serwer zawsze uzywa {@link #startLevel()}). */
    public int offlineStartLevel() { return offlineStartLevel; }
}
