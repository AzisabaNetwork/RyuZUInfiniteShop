package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

/**
 * Pure-logic calculator for Shop page-related computations.
 * Extracted from Shop.java for testability and separation of concerns.
 */
public final class ShopPageCalculator {

    private ShopPageCalculator() {}

    /**
     * Calculates how many trade pages are needed for the given number of trades.
     *
     * @param tradeCount total number of trades
     * @param limitSize  max trades per page (depends on ShopType)
     * @return number of trade pages
     */
    public static int calculateTradePageCount(int tradeCount, int limitSize) {
        int size = tradeCount / limitSize;
        if (tradeCount % limitSize != 0) size++;
        return size;
    }

    /**
     * Calculates how many editor pages are needed for the given number of trade pages.
     *
     * @param tradePageCount number of trade pages
     * @return number of editor pages
     */
    public static int calculateEditorPageCount(int tradePageCount) {
        return tradePageCount / 18 + 1;
    }

    /**
     * Checks if a page has reached its maximum trade capacity.
     *
     * @param tradesOnPage number of trades currently on the page
     * @param limitSize    max trades per page
     * @return true if the page is full
     */
    public static boolean isPageFull(int tradesOnPage, int limitSize) {
        return tradesOnPage == limitSize;
    }

    /**
     * Checks whether a new trade page can be created.
     *
     * @param tradesEmpty          whether the shop has no trades
     * @param lastPageTradeCount   number of trades on the last page
     * @param currentPageCount     current number of pages
     * @param limitSize            max trades per page
     * @return true if a new page can be created
     */
    public static boolean canCreateNewTradePage(boolean tradesEmpty, int lastPageTradeCount, int currentPageCount, int limitSize) {
        if (tradesEmpty) return true;
        return isPageFull(lastPageTradeCount, limitSize);
    }

    /**
     * Checks whether a new editor page can be created.
     *
     * @param editorsEmpty      whether the editors list is empty
     * @param editorsSize       current number of editors
     * @param editorPageCount   calculated editor page count
     * @return true if a new editor page can be created
     */
    public static boolean canCreateNewEditorPage(boolean editorsEmpty, int editorsSize, int editorPageCount) {
        if (editorsEmpty) return true;
        return editorsSize < editorPageCount;
    }
}
