package pl.fawizja.pixel.game;

import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.character.LevelRewards;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.daily.DailyTask;
import pl.fawizja.pixel.item.Enchantments;
import pl.fawizja.pixel.monster.MonsterType;

/** Okno "Wydarzenia" (/wydarzenia): co dzis sie dzieje i co czeka postac. Wiersze {etykieta, szczegol}. */
public final class EventsReport {
    private EventsReport() {}

    /** invasion: opis inwazji od serwera albo null (gra bez serwera). */
    public static List<String[]> rows(GameSession s, String invasion) { return rows(s, invasion, null); }

    /** expEvent = opis wydarzenia premii doswiadczenia z serwera (null albo "-" = brak). */
    public static List<String[]> rows(GameSession s, String invasion, String expEvent) { return rows(s, invasion, expEvent, null); }

    /** weekly = miejsce w rankingu tygodnia od serwera (null = gra bez serwera). */
    public static List<String[]> rows(GameSession s, String invasion, String expEvent, String weekly) {
        PlayerCharacter pc = s.character();
        long today = s.today();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Inwazja", invasion == null ? "tylko w grze na serwerze" : invasion});
        if (expEvent != null && !expEvent.equals("-")) rows.add(new String[] {"Premia exp", expEvent});
        rows.add(new String[] {"Ranking tygodnia", weekly == null || weekly.equals("-") ? "tylko w grze na serwerze" : weekly});
        MonsterType boost = s.boostedCreature();
        rows.add(new String[] {"Potwór dnia", boost == null ? "brak" : pl.fawizja.pixel.daily.BoostedCreature.describe(boost)});
        MonsterType boss = s.weeklyBoss();
        if (boss != null) {
            long week = pl.fawizja.pixel.daily.WeeklyBoss.week(today);
            rows.add(new String[] {"Boss tygodnia", boss.name() + (pc.weeklyBossWeek() == week ? ": nagroda odebrana, nowy za "
                    + (7 - Math.floorMod(today, 7)) + " dni" : ": " + String.format("%,d gp", pl.fawizja.pixel.daily.WeeklyBoss.rewardGold(boss))
                    + " za pierwsze pokonanie")});
        }
        rows.add(new String[] {"Zadanie dnia", daily(pc.daily(), today, s)});
        pl.fawizja.pixel.quest.HuntTask hunt = pc.hunt();
        if (hunt.active()) {
            rows.add(new String[] {"Zlecenie łowcy", s.monsterName(hunt.monster()) + " " + hunt.progress() + " / " + hunt.target()
                    + (hunt.done() ? " - oddaj u przewodnika" : "")});
        }
        int streak = pc.loginStreak().lastDay() == today ? pc.loginStreak().streak() : 0;
        rows.add(new String[] {"Seria logowania", streak > 0 ? "dzień " + streak + " z " + pl.fawizja.pixel.daily.LoginStreak.DAYS
                + (streak < pl.fawizja.pixel.daily.LoginStreak.DAYS ? ", jutro większa nagroda" : ", pełny tydzień") : "nagroda czeka po wejściu"});
        int next = LevelRewards.next(Math.max(pc.level(), pc.rewardedLevel()), Integer.MAX_VALUE);
        if (next > 0) {
            int n = LevelRewards.scrolls(next);
            rows.add(new String[] {"Nagroda za poziom " + next, String.format("%,d gp", LevelRewards.gold(next))
                    + (n > 0 ? " i " + n + "x zwój nauki" : "")});
        }
        StringBuilder ench = new StringBuilder();
        for (Enchantments.Kind k : Enchantments.Kind.values()) {
            if (!pc.enchantments().active(k)) continue;
            if (ench.length() > 0) ench.append(", ");
            ench.append(k.label()).append(' ').append(Enchantments.describe(pc.enchantments().remainingMs(k)));
        }
        rows.add(new String[] {"Zaklęcia", ench.length() == 0 ? "brak (kowal, zwój nauki)" : ench.toString()});
        return rows;
    }

    static String daily(DailyTask d, long today, GameSession s) {
        if (!d.activeOn(today)) return "weź u przewodnika w mieście";
        if (d.claimed()) return "wykonane, następne jutro";
        String name = d.monster();
        if (s.monsters() != null) {
            for (MonsterType t : s.monsters().spawnedTypes()) if (t.key().equals(d.monster())) name = t.name();
        }
        if (d.doneOn(today)) return name + ": gotowe, odbierz nagrodę u przewodnika";
        return name + ": " + d.progress() + " / " + d.target();
    }
}
