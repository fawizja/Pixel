package pl.fawizja.pixel.inventory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Siatka plecaka: 20 pol na jednej stronie, wieksze kontenery przewijane w pionie (komputer 5 x 4, telefon 10 x 2). */
class ContainerGridTest {

    @Test
    void defaultBackpackFitsOnOneScreenOnDesktopAndPhone() {
        for (ContainerGrid g : new ContainerGrid[] {ContainerGrid.DESKTOP, ContainerGrid.PHONE}) {
            assertEquals(g.cols() * g.visibleRows(), 20, "20 slots exactly fill the window");
            assertFalse(g.scrollable(20));
            assertEquals(0, g.maxScroll(20));
            assertEquals(0, g.scrollBy(0, 5, 20), "nothing to scroll");
            assertEquals(g.visibleRows(), g.shownRows(20));
        }
    }

    @Test
    void biggerContainersScrollVerticallyNotInPages() {
        ContainerGrid d = ContainerGrid.DESKTOP;
        assertEquals(5, d.rows(24));
        assertEquals(1, d.maxScroll(24), "24 slots: 5 rows, 4 visible");
        assertTrue(d.scrollable(24));
        assertEquals(8, d.rows(36));
        assertEquals(4, d.maxScroll(36), "36 slots: 8 rows, 4 visible");
        assertEquals(4, d.shownRows(36), "the window keeps its height");
    }

    @Test
    void scrollIsClampedAndSlotMappingFollowsIt() {
        ContainerGrid d = ContainerGrid.DESKTOP;
        int size = 36;
        assertEquals(0, d.scrollBy(0, -3, size));
        assertEquals(1, d.scrollBy(0, 1, size));
        assertEquals(d.maxScroll(size), d.scrollBy(d.maxScroll(size), 9, size));
        assertEquals(0, d.slotAt(0, 0, 0, size));
        assertEquals(5, d.slotAt(0, 1, 0, size));
        assertEquals(5, d.slotAt(0, 0, 1, size), "after one row of scrolling the top row shows slots 5..9");
        assertEquals(35, d.slotAt(0, 3, d.maxScroll(size), size), "bottom-left cell when scrolled to the end");
        assertEquals(-1, d.slotAt(5, 0, 0, size), "outside the columns");
        assertEquals(-1, d.slotAt(0, 4, 0, size), "outside the visible rows");
        assertEquals(10, d.firstVisibleSlot(2, size));
    }

    @Test
    void lastPartialRowOnlyShowsExistingSlots() {
        ContainerGrid d = ContainerGrid.DESKTOP;
        int size = 24;   // 5 columns: rows 0..4, last row has 4 slots
        int s = d.maxScroll(size);
        assertEquals(1, s);
        assertEquals(20, d.slotAt(0, 3, s, size));
        assertEquals(23, d.slotAt(3, 3, s, size));
        assertEquals(-1, d.slotAt(4, 3, s, size), "slot 24 does not exist");
    }

    @Test
    void scrollToShowKeepsAPickedSlotVisible() {
        ContainerGrid d = ContainerGrid.DESKTOP;
        assertEquals(0, d.scrollToShow(3, 0, 36));
        assertEquals(4, d.scrollToShow(35, 0, 36));
        assertEquals(1, d.scrollToShow(5, 3, 36), "scrolling back up to row 1");
        assertEquals(2, d.scrollToShow(12, 2, 36), "already visible: unchanged");
    }

    @Test
    void phoneGridShowsTwoRowsOfTen() {
        ContainerGrid p = ContainerGrid.PHONE;
        assertEquals(10, p.cols());
        assertEquals(2, p.visibleRows());
        assertEquals(1, p.maxScroll(24), "24 slots: 3 rows, 2 visible");
        assertEquals(2, p.maxScroll(36), "36 slots: 4 rows");
        assertEquals(30, p.slotAt(0, 1, 2, 36));
    }

    @Test
    void scrollbarThumbRoundTrips() {
        ContainerGrid d = ContainerGrid.DESKTOP;
        int size = 36;
        assertEquals(0f, d.thumbPosition(0, size));
        assertEquals(1f, d.thumbPosition(d.maxScroll(size), size));
        assertEquals(0.5f, d.thumbLength(size), 1e-6, "4 of 8 rows visible");
        assertEquals(1f, d.thumbLength(20));
        for (int s = 0; s <= d.maxScroll(size); s++) assertEquals(s, d.scrollForThumb(d.thumbPosition(s, size), size));
        assertEquals(0, d.scrollForThumb(-1f, size));
        assertEquals(d.maxScroll(size), d.scrollForThumb(2f, size));
    }
}
