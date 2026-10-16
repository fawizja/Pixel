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
    CARRIER
}
