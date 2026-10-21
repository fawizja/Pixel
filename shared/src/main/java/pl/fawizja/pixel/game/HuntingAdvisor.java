package pl.fawizja.pixel.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import pl.fawizja.pixel.monster.MonsterType;

/**
 * Okno "Gdzie polować" (/łowiska, przewodnik): potwory na mape dobrane do poziomu postaci - najblizsze poziomem,
 * z doswiadczeniem i krainami. Wiersze {nazwa, szczegol}.
 */
public final class HuntingAdvisor {
    private HuntingAdvisor() {}

    public static final int SHOWN = 10;

    public static List<String[]> rows(GameSession s) {
        int level = s.character().level();
        MonsterType boosted = s.boostedCreature();
        List<MonsterType> types = new ArrayList<>();
        for (MonsterType t : s.monsters().spawnedTypes()) if (!t.boss()) types.add(t);
        // najlepiej potwory od polowy do poltora poziomu postaci; dalej - najblizsze poziomem
        types.sort(Comparator.comparingDouble((MonsterType t) -> score(t, level)).thenComparing(MonsterType::name));
        // bez potworow duzo za silnych (ponad 2x poziom + 10), chyba ze nie ma nic innego
        List<MonsterType> picked = new ArrayList<>();
        for (MonsterType t : types) {
            if (picked.size() >= SHOWN) break;
            if (t.level() <= level * 2 + 10 || picked.size() < 3) picked.add(t);
        }
        picked.sort(Comparator.comparingInt(MonsterType::level).thenComparing(MonsterType::name));
        List<String[]> rows = new ArrayList<>();
        for (MonsterType t : picked) {
            String where = pl.fawizja.pixel.achievement.BestiaryCard.habitats(t.key(), s.monsters().spawns(), s.world());
            rows.add(new String[] {t.name() + (boosted != null && boosted.key().equals(t.key()) ? " (potwór dnia)" : ""),
                    "poz. " + t.level() + String.format(", %,d exp", t.experience()) + (where.isEmpty() ? "" : ", " + where)});
        }
        if (rows.isEmpty()) rows.add(new String[] {"Brak potworów", "na tej mapie nie ma nikogo do polowania"});
        return rows;
    }

    /** Mniej = lepiej: odleglosc poziomu potwora od przedzialu [0.5, 1.5] poziomu postaci, wzgledem poziomu. */
    static double score(MonsterType t, int level) {
        double lo = level * 0.5, hi = Math.max(level * 1.5, level + 3);
        double off = t.level() < lo ? lo - t.level() : t.level() > hi ? t.level() - hi : 0;
        return off / Math.max(1, level) * 100 + Math.abs(t.level() - level * 0.9) / Math.max(1.0, level);
    }
}
