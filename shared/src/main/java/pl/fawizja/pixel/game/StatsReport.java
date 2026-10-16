package pl.fawizja.pixel.game;

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
        rows.add(new String[] {"Czas gry", CharacterStats.describePlayTime(st.playMs())});
        rows.add(new String[] {"Poziom", pc.level() + (st.bestLevel() > pc.level() ? " (najwyższy " + st.bestLevel() + ")" : "")});
        rows.add(new String[] {"Pokonane potwory", String.format("%,d", b.total()) + " (gatunki: " + b.species() + ")"});
        rows.add(new String[] {"Elity i bossowie", b.eliteKills() + " elit, " + b.bossesDefeated() + " bossów"
                + (st.rareDrops() > 0 ? ", rzadkie łupy: " + st.rareDrops() : "")});
        rows.add(new String[] {"Śmierci", st.deaths() + (st.duelsWon() > 0 ? " (wygrane pojedynki: " + st.duelsWon() + ")" : "")});
        rows.add(new String[] {"Ukończone zadania", pc.quests().done().size() + " (zlecenia łowcy: " + pc.hunt().completed() + ", wyzwania: " + pc.stats().challenges() + ")"});
        rows.add(new String[] {"Otwarte skrzynie", String.valueOf(pc.openedChests().size())});
        rows.add(new String[] {"Osiągnięcia", pc.achievements().count() + " / " + Achievement.values().length});
        rows.add(new String[] {"Złoto w banku", String.format("%,d gp", pc.bank().balance())});
        return rows;
    }
}
