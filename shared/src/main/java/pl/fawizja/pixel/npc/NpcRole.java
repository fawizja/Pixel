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
    HOUSING,
    /** Wladca miasta (krol Aurelian w zamku Varden, w innych miastach krol, krolowa albo lord): konto premium
     *  (rejsy statkami) i promocja profesji - wszedzie tak samo. */
    KING,
    /** Straznik przy bramie miasta: tylko powitanie (bez uslug), stoi w strefie ochronnej. */
    GUARD,
    /** Zaklinacz (klucz *_imbuer, w kazdym miescie): nasycenia (imbuementy) jak u kowala, bez sklepu. */
    IMBUER,
    /** Mnich z kaplicy poza miastem: sprzedaje jedno blogoslawienstwo (zob. {@code Blessing#sellerKey()}), jak mnisi z oddalonych swiatyn w Tibii. */
    BLESSER
}
