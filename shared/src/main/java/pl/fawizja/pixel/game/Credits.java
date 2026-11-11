package pl.fawizja.pixel.game;

import pl.fawizja.pixel.i18n.Messages;
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
            + Messages.get("credits.carlo_enrico_victoria_nemisys")
            + Messages.get("credits.evert_jaidynreiman_joe_white")
            + Messages.get("credits.lanea_zimmerman_sharm_luke")
            + Messages.get("credits.matthew_krohn_makrohn_michael")
            + Messages.get("credits.nila122_page_pierre_vigier")
            + Messages.get("credits.thane_brimhall_pennomi_thecile");

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
                rows.add(new String[] {first ? Messages.get("credits.autorzy") : "", line.toString()});
                first = false;
                line.setLength(0);
            }
            if (line.length() > 0) line.append(", ");
            line.append(name);
        }
        if (line.length() > 0) rows.add(new String[] {first ? Messages.get("credits.autorzy") : "", line.toString()});
    }

    public static List<String[]> rows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {Messages.get("credits.grafiki"), PACK + " (" + LICENSE + ")"});
        rows.add(new String[] {Messages.get("credits.zrodlo"), URL});
        rows.add(new String[] {Messages.get("credits.zmiany"), Messages.get("credits.wyciete_przyciete_i_pokolorowa")});
        authors(rows, AUTHORS);
        rows.add(new String[] {Messages.get("credits.przedmioty"), Messages.tr(HINAS)});
        rows.add(new String[] {"", Messages.tr(HINAS_NOTE)});
        rows.add(new String[] {"", Messages.get("credits.zestawy_i_ich_bronie")});
        rows.add(new String[] {Messages.get("credits.postacie_npc"), Messages.get("credits.universal_lpc_spritesheet") + LPC_LICENSE + ")"});
        rows.add(new String[] {Messages.get("credits.zrodlo"), LPC_URL});
        rows.add(new String[] {Messages.get("credits.zmiany"), Messages.get("credits.warstwy_zlozone_przebarwione_i")});
        authors(rows, LPC_AUTHORS);
        rows.add(new String[] {Messages.get("credits.szczegoly"), LPC_FILE});
        rows.add(new String[] {Messages.get("credits.interfejs"), UI_PACK + " (" + UI_LICENSE + Messages.get("credits.ramki_okien_pola_przyciski")});
        rows.add(new String[] {Messages.get("credits.zrodlo"), UI_URL});
        rows.add(new String[] {Messages.get("credits.wyglad_nowy"), "Pixel RPG UI Kit - Zidane42 (itch.io)"});
        rows.add(new String[] {"", Messages.get("credits.paski_okna_przyciski_pola")});
        rows.add(new String[] {Messages.get("credits.reszta"), Messages.get("credits.grafiki_rysowane_przez_kod")});
        return rows;
    }
}
