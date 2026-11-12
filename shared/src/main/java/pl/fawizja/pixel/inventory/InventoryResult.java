package pl.fawizja.pixel.inventory;

public enum InventoryResult {
    OK,
    /** Brak miejsca w pojemniku - nic nie zostalo zmienione. */
    NO_SPACE,
    /** Brak wymaganych przedmiotow lub ilosci. */
    NOT_ENOUGH_ITEMS,
    INVALID_SLOT,
    INVALID_AMOUNT,
    WRONG_SLOT_TYPE,
    WRONG_VOCATION,
    LEVEL_TOO_LOW,
    EMPTY_SLOT,
    INSUFFICIENT_FUNDS,
    /** Udzwig (cap) postaci nie pozwala unies tego przedmiotu razem z zawartoscia plecaka. */
    TOO_HEAVY,
    /** Plecaka nie mozna wlozyc do samego siebie (ani do wlasnej zawartosci). */
    CYCLE;

    public boolean ok() { return this == OK; }
}
