package pl.fawizja.pixel.client.ui;

import pl.fawizja.pixel.inventory.ContainerGrid;

/**
 * Geometria i przewijanie jednego okna pojemnika (plecak, depozyt): siatka pol z widocznym obszarem
 * ({@link ContainerGrid}) i pionowy pasek przewijania po prawej, gdy pojemnik jest wiekszy niz widac. Bez rysowania -
 * HUD pyta o pola i prostokaty. Os y rosnie w gore (jak w calym HUD): wiersz 0 widocznego obszaru jest na gorze.
 */
final class ContainerView {

    /** Szerokosc paska przewijania i jego odstep od pol. */
    static final float BAR_W = 14f;
    static final float BAR_GAP = 3f;

    /** Czesc paska pod punktem. */
    enum Hit { NONE, UP, DOWN, THUMB, TRACK_UP, TRACK_DOWN }

    final ContainerGrid grid;
    /** Lewy dolny rog widocznego obszaru pol i bok jednego pola. */
    float x, y, cell;
    /** Odstep miedzy polami (pole ma bok cell, skok = cell + gap). */
    float gap;
    int size;
    int scroll;
    /** Przeciaganie suwaka: przesuniecie punktu chwytu wzgledem gornej krawedzi suwaka; &lt; 0 = nie przeciagamy. */
    private float grabOffset = -1;

    ContainerView(ContainerGrid grid) { this.grid = grid; }

    /** Ustawia polozenie i liczbe pol (obcina przewiniecie). */
    void place(float x, float y, float cell, float gap, int size) {
        this.x = x;
        this.y = y;
        this.cell = cell;
        this.gap = gap;
        this.size = size;
        scroll = grid.clamp(scroll, size);
    }

    float pitch() { return cell + gap; }

    boolean scrollable() { return grid.scrollable(size); }

    float gridW() { return grid.cols() * pitch() - gap; }

    float gridH() { return grid.visibleRows() * pitch() - gap; }

    /** Szerokosc paska razem z odstepem (0, gdy pojemnik sie miesci). */
    float barSpace() { return scrollable() ? BAR_GAP + BAR_W : 0; }

    float barX() { return x + gridW() + BAR_GAP; }

    /** Lewy dolny rog pola w widocznym wierszu row (0 = gora) i kolumnie col. */
    float cellX(int col) { return x + col * pitch(); }

    float cellY(int row) { return y + (grid.visibleRows() - 1 - row) * pitch(); }

    /** Numer pola (bezwzgledny) pod punktem albo -1 (poza siatka, w szczelinie, poza pojemnikiem). */
    int slotAt(float px, float py) {
        float dx = px - x, dy = (y + gridH()) - py;
        if (dx < 0 || dy < 0 || dx >= gridW() + gap / 2 || dy >= gridH() + gap / 2) return -1;
        int col = (int) (dx / pitch()), row = (int) (dy / pitch());
        if (col >= grid.cols() || row >= grid.visibleRows()) return -1;
        return grid.slotAt(col, row, scroll, size);
    }

    /** Czy punkt jest w obszarze pol albo paska (do przechwytywania dotyku). */
    boolean contains(float px, float py) {
        return px >= x - 2 && px <= x + gridW() + barSpace() + 2 && py >= y - 2 && py <= y + gridH() + 2;
    }

    // ------------------------------------------------------------------ pasek przewijania

    private float arrowH() { return Math.min(BAR_W, gridH() / 3f); }

    float trackY() { return y + arrowH(); }

    float trackH() { return Math.max(1f, gridH() - 2 * arrowH()); }

    float thumbH() { return Math.max(12f, trackH() * grid.thumbLength(size)); }

    /** Dolna krawedz suwaka. */
    float thumbY() {
        float free = trackH() - thumbH();
        return trackY() + free * (1f - grid.thumbPosition(scroll, size));
    }

    float upArrowY() { return y + gridH() - arrowH(); }

    float arrowHeight() { return arrowH(); }

    Hit hitBar(float px, float py) {
        if (!scrollable() || px < barX() - 1 || px > barX() + BAR_W + 1 || py < y || py > y + gridH()) return Hit.NONE;
        if (py >= upArrowY()) return Hit.UP;
        if (py <= y + arrowH()) return Hit.DOWN;
        float ty = thumbY();
        if (py >= ty && py <= ty + thumbH()) return Hit.THUMB;
        return py > ty + thumbH() ? Hit.TRACK_UP : Hit.TRACK_DOWN;
    }

    /** Dotkniecie paska: strzalki i tor przesuwaja o wiersz (tor o cale okno), suwak zaczyna przeciaganie. */
    boolean pressBar(float px, float py) {
        Hit h = hitBar(px, py);
        switch (h) {
            case UP: scrollRows(-1); return true;
            case DOWN: scrollRows(1); return true;
            case TRACK_UP: scrollRows(-grid.visibleRows()); return true;
            case TRACK_DOWN: scrollRows(grid.visibleRows()); return true;
            case THUMB:
                grabOffset = thumbY() + thumbH() - py;
                return true;
            default: return false;
        }
    }

    boolean draggingThumb() { return grabOffset >= 0; }

    /** Ruch palca przy przeciaganiu suwaka. */
    void dragThumb(float py) {
        if (grabOffset < 0) return;
        float free = trackH() - thumbH();
        if (free <= 0) return;
        float top = py + grabOffset;                       // gorna krawedz suwaka
        float pos = 1f - (top - thumbH() - trackY()) / free;
        scroll = grid.scrollForThumb(pos, size);
    }

    void releaseThumb() { grabOffset = -1; }

    void scrollRows(int delta) { scroll = grid.scrollBy(scroll, delta, size); }

    /** Pokazuje pole (np. po przeciagnieciu na krawedz albo wyborze). */
    void show(int slot) { scroll = grid.scrollToShow(slot, scroll, size); }
}
