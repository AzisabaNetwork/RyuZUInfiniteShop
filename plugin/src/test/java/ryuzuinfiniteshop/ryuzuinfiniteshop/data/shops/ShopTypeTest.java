package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ShopTypeTest {

    // ========== getNextShopType cycle ==========

    @Test
    void getNextShopTypeTwotoOneToFourtoFour() {
        assertEquals(ShopType.FourtoFour, ShopType.TwotoOne.getNextShopType());
    }

    @Test
    void getNextShopTypeFourtoFourToSixtoTwo() {
        assertEquals(ShopType.SixtoTwo, ShopType.FourtoFour.getNextShopType());
    }

    @Test
    void getNextShopTypeSixtoTwoToTwotoOne() {
        assertEquals(ShopType.TwotoOne, ShopType.SixtoTwo.getNextShopType());
    }

    @ParameterizedTest
    @MethodSource("cycleLengthProvider")
    void getNextShopTypeCycleReturnsToOriginalAfterThree(ShopType start) {
        ShopType current = start;
        for (int i = 0; i < 3; i++) {
            current = current.getNextShopType();
        }
        assertEquals(start, current, "3 steps should return to original type");
    }

    static Stream<Arguments> cycleLengthProvider() {
        return Stream.of(
                Arguments.of(ShopType.TwotoOne),
                Arguments.of(ShopType.FourtoFour),
                Arguments.of(ShopType.SixtoTwo)
        );
    }

    // ========== getShopTypeDisplay ==========

    @Test
    void getShopTypeDisplayTwotoOne() {
        String display = ShopType.TwotoOne.getShopTypeDisplay();
        assertTrue(display.contains("2") && display.contains("1"),
                "TwotoOne display should contain '2' and '1'");
    }

    @Test
    void getShopTypeDisplayFourtoFour() {
        String display = ShopType.FourtoFour.getShopTypeDisplay();
        assertTrue(display.contains("4") && display.contains("4"),
                "FourtoFour display should contain '4' and '4'");
    }

    @Test
    void getShopTypeDisplaySixtoTwo() {
        String display = ShopType.SixtoTwo.getShopTypeDisplay();
        assertTrue(display.contains("6") && display.contains("2"),
                "SixtoTwo display should contain '6' and '2'");
    }

    // ========== getLimitSize ==========

    @ParameterizedTest
    @MethodSource("limitSizeProvider")
    void getLimitSize(ShopType type, int expected) {
        assertEquals(expected, type.getLimitSize());
    }

    static Stream<Arguments> limitSizeProvider() {
        return Stream.of(
                Arguments.of(ShopType.TwotoOne, 10),
                Arguments.of(ShopType.FourtoFour, 5),
                Arguments.of(ShopType.SixtoTwo, 5)
        );
    }

    // ========== getAddSlot ==========

    @ParameterizedTest
    @MethodSource("addSlotProvider")
    void getAddSlot(ShopType type, int expected) {
        assertEquals(expected, type.getAddSlot());
    }

    static Stream<Arguments> addSlotProvider() {
        return Stream.of(
                Arguments.of(ShopType.TwotoOne, 4),
                Arguments.of(ShopType.FourtoFour, 9),
                Arguments.of(ShopType.SixtoTwo, 9)
        );
    }

    // ========== getSubtractSlot ==========

    @ParameterizedTest
    @MethodSource("subtractSlotProvider")
    void getSubtractSlot(ShopType type, int expected) {
        assertEquals(expected, type.getSubtractSlot());
    }

    static Stream<Arguments> subtractSlotProvider() {
        return Stream.of(
                Arguments.of(ShopType.TwotoOne, 2),
                Arguments.of(ShopType.FourtoFour, 4),
                Arguments.of(ShopType.SixtoTwo, 6)
        );
    }

    // ========== enum constants ==========

    @Test
    void enumHasThreeConstants() {
        assertEquals(3, ShopType.values().length);
    }

    @Test
    void enumConstantsOrder() {
        assertArrayEquals(
                new ShopType[]{ShopType.TwotoOne, ShopType.FourtoFour, ShopType.SixtoTwo},
                ShopType.values()
        );
    }

    @Test
    void valueOfAllConstants() {
        assertEquals(ShopType.TwotoOne, ShopType.valueOf("TwotoOne"));
        assertEquals(ShopType.FourtoFour, ShopType.valueOf("FourtoFour"));
        assertEquals(ShopType.SixtoTwo, ShopType.valueOf("SixtoTwo"));
    }
}
