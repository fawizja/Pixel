package pl.fawizja.pixel.game;

import pl.fawizja.pixel.i18n.Messages;
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
        rows.add(new String[] {Messages.get("events_report.inwazja"), invasion == null ? Messages.get("events_report.tylko_w_grze_na") : invasion});
        if (expEvent != null && !expEvent.equals("-")) rows.add(new String[] {Messages.get("events_report.premia_exp"), expEvent});
        rows.add(new String[] {Messages.get("events_report.ranking_tygodnia"), weekly == null || weekly.equals("-") ? Messages.get("events_report.tylko_w_grze_na") : weekly});
        MonsterType boost = s.boostedCreature();
        rows.add(new String[] {Messages.get("game_screen.potwor_dnia_2"), boost == null ? Messages.get("server_world.brak") : pl.fawizja.pixel.daily.BoostedCreature.describe(boost)});
        MonsterType boss = s.weeklyBoss();
        if (boss != null) {
            long week = pl.fawizja.pixel.daily.WeeklyBoss.week(today);
            rows.add(new String[] {Messages.get("events_report.boss_tygodnia"), boss.name() + (pc.weeklyBossWeek() == week ? Messages.get("events_report.nagroda_odebrana_nowy_za")
                    + (7 - Math.floorMod(today, 7)) + Messages.get("server_world.dni") : ": " + String.format("%,d gp", pl.fawizja.pixel.daily.WeeklyBoss.rewardGold(boss))
                    + Messages.get("events_report.za_pierwsze_pokonanie"))});
        }
        rows.add(new String[] {Messages.get("hud.zadanie_dnia"), daily(pc.daily(), today, s)});
        pl.fawizja.pixel.quest.HuntTask hunt = pc.hunt();
        if (hunt.active()) {
            rows.add(new String[] {Messages.get("hud.zlecenie_lowcy"), s.monsterName(hunt.monster()) + " " + hunt.progress() + " / " + hunt.target()
                    + (hunt.done() ? Messages.get("events_report.oddaj_u_przewodnika") : "")});
        }
        int streak = pc.loginStreak().lastDay() == today ? pc.loginStreak().streak() : 0;
        rows.add(new String[] {Messages.get("events_report.seria_logowania"), streak > 0 ? Messages.get("events_report.dzien") + streak + Messages.get("common.z") + pl.fawizja.pixel.daily.LoginStreak.DAYS
                + (streak < pl.fawizja.pixel.daily.LoginStreak.DAYS ? Messages.get("events_report.jutro_wieksza_nagroda") : Messages.get("events_report.pelny_tydzien")) : Messages.get("events_report.nagroda_czeka_po_wejsciu")});
        int next = LevelRewards.next(Math.max(pc.level(), pc.rewardedLevel()), Integer.MAX_VALUE);
        if (next > 0) {
            int n = LevelRewards.scrolls(next);
            rows.add(new String[] {Messages.get("events_report.nagroda_za_poziom") + next, String.format("%,d gp", LevelRewards.gold(next))
                    + (n > 0 ? " i " + n + Messages.get("events_report.x_zwoj_nauki") : "")});
        }
        StringBuilder ench = new StringBuilder();
        for (Enchantments.Kind k : Enchantments.Kind.values()) {
            if (!pc.enchantments().active(k)) continue;
            if (ench.length() > 0) ench.append(", ");
            ench.append(k.label()).append(' ').append(Enchantments.describe(pc.enchantments().remainingMs(k)));
        }
        rows.add(new String[] {Messages.get("events_report.zaklecia"), ench.length() == 0 ? Messages.get("events_report.brak_kowal_zwoj_nauki") : ench.toString()});
        return rows;
    }

    static String daily(DailyTask d, long today, GameSession s) {
        if (!d.activeOn(today)) return Messages.get("events_report.wez_u_przewodnika_w");
        if (d.claimed()) return Messages.get("events_report.wykonane_nastepne_jutro");
        String name = d.monster();
        if (s.monsters() != null) {
            for (MonsterType t : s.monsters().spawnedTypes()) if (t.key().equals(d.monster())) name = t.name();
        }
        if (d.doneOn(today)) return name + Messages.get("events_report.gotowe_odbierz_nagrode_u");
        return name + ": " + d.progress() + " / " + d.target();
    }
}
