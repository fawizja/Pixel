package pl.fawizja.pixel.game;

import java.util.ArrayList;
import java.util.List;

/** Okno /autorzy: kto narysowal grafiki (licencja CC BY 4.0 wymaga podania autorow). Pelna lista: docs/CREDITS.md. */
public final class Credits {
    private Credits() {}

    public static final String PACK = "OpenTibia Sprite Pack";
    public static final String LICENSE = "CC BY 4.0";
    public static final String URL = "github.com/peonso/opentibia_sprite_pack";

    /** Ramki okien, przyciski i pad joysticka (assets/ui/otc). */
    public static final String UI_PACK = "OTClientV8";
    public static final String UI_LICENSE = "MIT";
    public static final String UI_URL = "github.com/OTCv8/otclientv8";

    /** Autorzy paczki w kolejnosci alfabetycznej (z AUTHORS.md paczki). */
    public static final String AUTHORS = "2Rec, 3visan, Anevis, Andreéw, Arkan'., BeachboyX, BrenoNeto, Cat, Celix, Corvo, dami1310, Daniel, DawTorr, Despute, ElderDark, Erick Etchebeur, Frevius, Galiant, GhostX, HalfAway, Hunter Killer, Jacobs, Landera, Leshrot, lionkobin, Madarada, markindoot, maxmillerxd, Nechros, Nogard, Nordberg, Noxz, Nu77, Peonso, Ranzor, Sam Drost, Saphron, Sherice, Shiva Shadowsong, Slaake, Somni, Thorn, Tokume, Venesis, wesleyt10, Way20, Weto, Worr, Sajgon";

    public static List<String[]> rows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Grafiki", PACK + " (" + LICENSE + ")"});
        rows.add(new String[] {"Źródło", URL});
        rows.add(new String[] {"Zmiany", "wycięte, przycięte i pokolorowane do gry Pixel"});
        StringBuilder line = new StringBuilder();
        for (String name : AUTHORS.split(", ")) {
            if (line.length() > 0 && line.length() + name.length() > 48) {
                rows.add(new String[] {rows.size() == 3 ? "Autorzy" : "", line.toString()});
                line.setLength(0);
            }
            if (line.length() > 0) line.append(", ");
            line.append(name);
        }
        if (line.length() > 0) rows.add(new String[] {rows.size() == 3 ? "Autorzy" : "", line.toString()});
        rows.add(new String[] {"Interfejs", UI_PACK + " (" + UI_LICENSE + "): ramki okien, pola, przyciski"});
        rows.add(new String[] {"Źródło", UI_URL});
        rows.add(new String[] {"Reszta", "grafiki rysowane przez kod gry"});
        return rows;
    }
}
