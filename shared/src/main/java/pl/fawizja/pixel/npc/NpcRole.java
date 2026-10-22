package pl.fawizja.pixel.npc;

/** Rola NPC decyduje o tym, co mozna u niego zalatwic. */
public enum NpcRole {
    /** Kupiec: sprzedaje zapasy i skupuje lupy (sklep z data/shops.csv). */
    MERCHANT,
    /** Bankier: wplaty i wyplaty z konta postaci. */
    BANKER,
    /** Kaplan: leczenie i zamieszkanie w miescie (miejsce odrodzenia). */
    PRIEST,
    /** Przewodnik: zadania z data/quests.csv. */
    GUIDE,
    /** Woznica: platny przejazd do innych miast (cena z odleglosci, {@link Travel}). */
    CARRIER,
    /** Kapitan statku: rejsy do innych portow i na wyspy (trasy z maps/&lt;mapa&gt;/ships.csv, {@link Travel}). */
    CAPTAIN,
    /** Zarzadca domow: lista domow miasta, kupno za zloto z banku i oddanie domu. */
    HOUSING
}
