package pl.fawizja.pixel.game;

import java.util.ArrayList;
import java.util.List;

/** Okno /autorzy: kto narysowal grafiki (licencje CC BY 4.0 i CC BY-SA 3.0 wymagaja podania autorow). Pelna lista: docs/CREDITS.md. */
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

    /** Postacie NPC (assets/art/lpc, tools/art/lpc_compose.py): warstwy LPC, zlozone postacie na CC BY-SA 3.0. */
    public static final String LPC_PACK = "Universal LPC Spritesheet Character Generator";
    public static final String LPC_LICENSE = "CC BY-SA 3.0";
    public static final String LPC_URL = "github.com/LiberatedPixelCup";
    public static final String LPC_FILE = "assets/art/lpc/CREDITS-LPC.csv";
    /** Autorzy uzytych warstw LPC (z CREDITS-LPC.csv, alfabetycznie). */
    public static final String LPC_AUTHORS = "Ahmad3366, Barbara Riviera, Benjamin K. Smith (BenCreating), bluecarrot16, "
            + "Carlo Enrico Victoria (Nemisys), Charles Sanchez (CharlesGabriel), DarkwallLKE, Durrani, Eliza Wyatt (ElizaWy), "
            + "Evert, JaidynReiman, Joe White, Johannes Sjölund (wulax), kcilds/Rocetti/Eredah, laetissima, "
            + "Lanea Zimmerman (Sharm), Luke Mehl, Manuel Riecke (MrBeast), Marcel van de Steeg (MadMarcel), "
            + "Matthew Krohn (makrohn), Michael Whitlock (bigbeargames), MuffinElZangano, Napsio (Vitruvian Studio), "
            + "Nila122, Page, Pierre Vigier (pvigier), Sander Frenken (castelonia), Stephen Challener (Redshrike), "
            + "Thane Brimhall (pennomi), thecilekli, TheraHedwig, Tracy, Tuomo Untinen (reemax)";

    /**
     * Przedmioty i zestawy (assets/art/items, tools/art/hinas_import.py): grafiki Hinasa udostepnione publicznie na
     * OTLand bez licencji, uzyte z podaniem autora (na zyczenie autora zostana usuniete). docs/CREDITS.md. Zwykly
     * myslnik, bo czcionka gry (lsans-15) nie ma polpauzy.
     */
    public static final String HINAS = "Hinas (OTLand) - przedmioty";
    public static final String HINAS_NOTE = "udostępnione publicznie, użyte z podaniem autora";

    private static void authors(List<String[]> rows, String names) {
        StringBuilder line = new StringBuilder();
        boolean first = true;
        for (String name : names.split(", ")) {
            if (line.length() > 0 && line.length() + name.length() > 48) {
                rows.add(new String[] {first ? "Autorzy" : "", line.toString()});
                first = false;
                line.setLength(0);
            }
            if (line.length() > 0) line.append(", ");
            line.append(name);
        }
        if (line.length() > 0) rows.add(new String[] {first ? "Autorzy" : "", line.toString()});
    }

    public static List<String[]> rows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Grafiki", PACK + " (" + LICENSE + ")"});
        rows.add(new String[] {"Źródło", URL});
        rows.add(new String[] {"Zmiany", "wycięte, przycięte i pokolorowane do gry Pixel"});
        authors(rows, AUTHORS);
        rows.add(new String[] {"Przedmioty", HINAS});
        rows.add(new String[] {"", HINAS_NOTE});
        rows.add(new String[] {"", "zestawy i ich bronie, księgi, amulety"});
        rows.add(new String[] {"Postacie NPC", "Universal LPC Spritesheet (" + LPC_LICENSE + ")"});
        rows.add(new String[] {"Źródło", LPC_URL});
        rows.add(new String[] {"Zmiany", "warstwy złożone, przebarwione i zmniejszone"});
        authors(rows, LPC_AUTHORS);
        rows.add(new String[] {"Szczegóły", LPC_FILE});
        rows.add(new String[] {"Interfejs", UI_PACK + " (" + UI_LICENSE + "): ramki okien, pola, przyciski"});
        rows.add(new String[] {"Źródło", UI_URL});
        rows.add(new String[] {"Wygląd nowy", "Pixel RPG UI Kit - Zidane42 (itch.io)"});
        rows.add(new String[] {"", "paski, okna, przyciski, pola; kupiony do gry"});
        rows.add(new String[] {"Reszta", "grafiki rysowane przez kod gry"});
        return rows;
    }
}
