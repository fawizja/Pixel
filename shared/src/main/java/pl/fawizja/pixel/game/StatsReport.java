package pl.fawizja.pixel.game;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.achievement.Achievement;
import pl.fawizja.pixel.achievement.Bestiary;
import pl.fawizja.pixel.character.CharacterStats;
import pl.fawizja.pixel.character.PlayerCharacter;

/** Okno "Statystyki" (/statystyki): podsumowanie calej gry postaci. Wiersze {etykieta, wartosc}. */
public final class StatsReport {
    private StatsReport() {}

    public static List<String[]> rows(PlayerCharacter pc) {
        CharacterStats st = pc.stats();
        Bestiary b = pc.bestiary();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {Messages.get("stats_report.czas_gry"), CharacterStats.describePlayTime(st.playMs())});
        rows.add(new String[] {Messages.get("hud.poziom"), pc.level() + (st.bestLevel() > pc.level() ? Messages.get("stats_report.najwyzszy") + st.bestLevel() + ")" : "")});
        rows.add(new String[] {Messages.get("stats_report.pokonane_potwory"), String.format("%,d", b.total()) + " (gatunki: " + b.species() + ")"});
        rows.add(new String[] {Messages.get("stats_report.elity_i_bossowie"), b.eliteKills() + " elit, " + b.bossesDefeated() + Messages.get("stats_report.bossow")
                + (st.rareDrops() > 0 ? Messages.get("stats_report.rzadkie_lupy") + st.rareDrops() : "")});
        rows.add(new String[] {Messages.get("stats_report.smierci"), st.deaths() + (st.duelsWon() > 0 ? Messages.get("stats_report.wygrane_pojedynki") + st.duelsWon() + ")" : "")});
        rows.add(new String[] {Messages.get("stats_report.ukonczone_zadania"), pc.quests().done().size() + Messages.get("stats_report.zlecenia_lowcy") + pc.hunt().completed() + Messages.get("stats_report.wyzwania") + pc.stats().challenges() + ")"});
        rows.add(new String[] {Messages.get("stats_report.otwarte_skrzynie"), pc.openedChests().size() + (st.herbs() > 0 ? Messages.get("stats_report.zebrane_ziola") + st.herbs() + ")" : "")});
        rows.add(new String[] {Messages.get("stats_report.osiagniecia"), pc.achievements().count() + " / " + Achievement.values().length});
        rows.add(new String[] {Messages.get("stats_report.zloto_w_banku"), String.format("%,d gp", pc.bank().balance())});
        return rows;
    }
}
