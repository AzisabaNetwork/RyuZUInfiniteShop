package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for page calculation logic extracted from Shop.java.
 */
class ShopPageCalculatorTest {

    // ========== Trade page count calculation ==========

    @Test
    void tradePageCountZeroTrades() {
        // TwotoOne limitSize=12: 0/12 = 0
        assertEquals(0, calculateTradePages(0, 12));
    }

    @Test
    void tradePageCountOneTradeUnderLimit() {
        // TwotoOne limitSize=12: 1/12 = 0, remainder → 1
        assertEquals(1, calculateTradePages(1, 12));
    }

    @Test
    void tradePageCountExactLimit() {
        // TwotoOne limitSize=12: 12/12 = 1
        assertEquals(1, calculateTradePages(12, 12));
    }

    @Test
    void tradePageCountOneOverLimit() {
        // TwotoOne limitSize=12: 13/12 = 1, remainder → 2
        assertEquals(2, calculateTradePages(13, 12));
    }

    @Test
    void tradePageCountFourtoFourLimit() {
        // FourtoFour limitSize=6: 7/6 = 1, remainder → 2
        assertEquals(2, calculateTradePages(7, 6));
    }

    @Test
    void tradePageCountLargeNumber() {
        // TwotoOne limitSize=12: 100/12 = 8, remainder 4 → 9
        assertEquals(9, calculateTradePages(100, 12));
    }

    // ========== Editor page count calculation ==========

    @Test
    void editorPageCountNoTradePages() {
        // tradePageCount=0: 0/18 = 0, +1 = 1
        assertEquals(1, calculateEditorPages(0));
    }

    @Test
    void editorPageCountOneTradePage() {
        // tradePageCount=1: 1/18 = 0, +1 = 1
        assertEquals(1, calculateEditorPages(1));
    }

    @Test
    void editorPageCountEighteenTradePages() {
        // tradePageCount=18: 18/18 = 1, +1 = 2
        assertEquals(2, calculateEditorPages(18));
    }

    @Test
    void editorPageCountNineteenTradePages() {
        // tradePageCount=19: 19/18 = 1, +1 = 2
        assertEquals(2, calculateEditorPages(19));
    }

    @Test
    void editorPageCountThirtySixTradePages() {
        // tradePageCount=36: 36/18 = 2, +1 = 3
        assertEquals(3, calculateEditorPages(36));
    }

    // ========== Limit page check ==========

    @Test
    void isLimitPageFull() {
        assertTrue(isPageFull(12, 12));
    }

    @Test
    void isLimitPageNotFull() {
        assertFalse(isPageFull(5, 12));
    }

    @Test
    void isLimitPageEmpty() {
        assertFalse(isPageFull(0, 12));
    }

    // ========== Able to create new trade page ==========

    @Test
    void ableCreateNewPageWhenTradesEmpty() {
        assertTrue(canCreateNewPage(true, 0, 0, 12));
    }

    @Test
    void ableCreateNewPageWhenCurrentPageFull() {
        assertTrue(canCreateNewPage(false, 12, 1, 12));
    }

    @Test
    void ableCreateNewPageWhenCurrentPageNotFull() {
        assertFalse(canCreateNewPage(false, 5, 1, 12));
    }

    @Test
    void ableCreateNewPageWhenCurrentPageFullAndMultiplePages() {
        assertTrue(canCreateNewPage(false, 12, 3, 12));
    }

    // ========== Able to create new editor page ==========

    @Test
    void ableCreateEditorPageWhenEditorsEmpty() {
        assertTrue(canCreateEditorPage(true, 0, 1));
    }

    @Test
    void ableCreateEditorPageWhenEditorsLessThanNeeded() {
        // editors=1 < editorPageCount=2 → true
        assertTrue(canCreateEditorPage(false, 1, 2));
    }

    @Test
    void ableCreateEditorPageWhenEditorsEqualNeeded() {
        // editors=2, editorPageCount=2 → 2 < 2 = false
        assertFalse(canCreateEditorPage(false, 2, 2));
    }

    @Test
    void ableCreateEditorPageWhenEditorsExceedNeeded() {
        // editors=3, editorPageCount=2 → 3 < 2 = false
        assertFalse(canCreateEditorPage(false, 3, 2));
    }

    // ========== Helper methods matching Shop's logic ==========

    private static int calculateTradePages(int tradeCount, int limitSize) {
        int size = tradeCount / limitSize;
        if (tradeCount % limitSize != 0) size++;
        return size;
    }

    private static int calculateEditorPages(int tradePageCount) {
        return tradePageCount / 18 + 1;
    }

    private static boolean isPageFull(int tradesOnPage, int limitSize) {
        return tradesOnPage == limitSize;
    }

    private static boolean canCreateNewPage(boolean tradesEmpty, int lastPageTradeCount, int pagesSize, int limitSize) {
        if (tradesEmpty) return true;
        return isPageFull(lastPageTradeCount, limitSize);
    }

    private static boolean canCreateEditorPage(boolean editorsEmpty, int editorsSize, int editorPageCount) {
        if (editorsEmpty) return true;
        return editorsSize < editorPageCount;
    }
}
