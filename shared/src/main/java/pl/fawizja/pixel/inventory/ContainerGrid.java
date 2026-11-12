package pl.fawizja.pixel.inventory;

/**
 * Uklad okna pojemnika: wszystkie sloty na jednej siatce (bez stron), a pojemnik wiekszy niz widoczny obszar
 * przewija sie w pionie wierszami (jak w Tibii). Czysta logika bez grafiki - wspolna dla HUD-u na komputer
 * (5 kolumn x 4 wiersze = 20 pol widocznych) i telefon (10 x 2), testowana osobno.
 */
public final class ContainerGrid {

    /** Komputer: 5 kolumn, 4 widoczne wiersze - standardowy plecak (20 pol) miesci sie bez przewijania. */
    public static final ContainerGrid DESKTOP = new ContainerGrid(5, 4);
    /** Telefon: 10 kolumn, 2 widoczne wiersze - te same 20 pol. */
    public static final ContainerGrid PHONE = new ContainerGrid(10, 2);

    private final int cols;
    private final int visibleRows;

    public ContainerGrid(int cols, int visibleRows) {
        if (cols <= 0 || visibleRows <= 0) throw new IllegalArgumentException("grid");
        this.cols = cols;
        this.visibleRows = visibleRows;
    }

    public int cols() { return cols; }

    public int visibleRows() { return visibleRows; }

    /** Liczba wszystkich wierszy pojemnika o size polach. */
    public int rows(int size) { return Math.max(1, (size + cols - 1) / cols); }

    /** Ile wierszy okno pokazuje naraz (nigdy wiecej niz ma pojemnik). */
    public int shownRows(int size) { return Math.min(visibleRows, rows(size)); }

    /** Najwiekszy mozliwy przesuniecie (w wierszach); 0 gdy wszystko sie miesci. */
    public int maxScroll(int size) { return Math.max(0, rows(size) - visibleRows); }

    public boolean scrollable(int size) { return maxScroll(size) > 0; }

    public int clamp(int scroll, int size) { return Math.max(0, Math.min(maxScroll(size), scroll)); }

    /** Przesuniecie o delta wierszy (ujemne = w gore), obciete do zakresu. */
    public int scrollBy(int scroll, int deltaRows, int size) { return clamp(scroll + deltaRows, size); }

    /** Numer pola dla komorki (col, row) widocznego obszaru przy danym przesunieciu; -1 poza pojemnikiem. */
    public int slotAt(int col, int row, int scroll, int size) {
        if (col < 0 || col >= cols || row < 0 || row >= visibleRows) return -1;
        int slot = (clamp(scroll, size) + row) * cols + col;
        return slot < size ? slot : -1;
    }

    /** Kolumna pola. */
    public int colOf(int slot) { return slot % cols; }

    /** Wiersz pola w calej siatce (nie w widocznym obszarze). */
    public int rowOf(int slot) { return slot / cols; }

    /** Najmniejsze przesuniecie, przy ktorym pole jest widoczne (zostaje bez zmian, gdy juz je widac). */
    public int scrollToShow(int slot, int scroll, int size) {
        int r = rowOf(slot);
        int s = clamp(scroll, size);
        if (r < s) s = r;
        else if (r >= s + visibleRows) s = r - visibleRows + 1;
        return clamp(s, size);
    }

    /** Pierwszy widoczny numer pola (lewy gorny rog). */
    public int firstVisibleSlot(int scroll, int size) { return clamp(scroll, size) * cols; }

    /** Pozycja suwaka przewijania 0..1 (0 = gora). */
    public float thumbPosition(int scroll, int size) {
        int max = maxScroll(size);
        return max == 0 ? 0f : clamp(scroll, size) / (float) max;
    }

    /** Wzgledna dlugosc suwaka 0..1 (widoczne wiersze / wszystkie). */
    public float thumbLength(int size) { return Math.min(1f, visibleRows / (float) rows(size)); }

    /** Przesuniecie wynikajace z polozenia suwaka (0 = gora, 1 = dol). */
    public int scrollForThumb(float position, int size) {
        return clamp(Math.round(Math.max(0f, Math.min(1f, position)) * maxScroll(size)), size);
    }
}
