package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JavaUtilTest {

    // ========== getSubList ==========

    @Test
    void getSubListReturnsCorrectRange() {
        List<String> list = List.of("a", "b", "c", "d", "e");
        assertEquals(List.of("b", "c"), JavaUtil.getSubList(list, 1, 3));
    }

    @Test
    void getSubListToIndexClampedAtSize() {
        List<String> list = List.of("a", "b", "c");
        assertEquals(List.of("a", "b", "c"), JavaUtil.getSubList(list, 0, 10));
    }

    @Test
    void getSubListFromIndexEqualsToReturnsEmpty() {
        List<String> list = List.of("a", "b", "c");
        assertTrue(JavaUtil.getSubList(list, 2, 2).isEmpty());
    }

    // ========== splitList ==========

    @Test
    void splitListEvenSplit() {
        List<String> list = List.of("a", "b", "c", "d");
        List<String>[] result = JavaUtil.splitList(list, 2);
        assertEquals(2, result.length);
        assertEquals(List.of("a", "b"), result[0]);
        assertEquals(List.of("c", "d"), result[1]);
    }

    @Test
    void splitListUnevenSplit() {
        List<String> list = List.of("a", "b", "c", "d", "e");
        List<String>[] result = JavaUtil.splitList(list, 2);
        assertEquals(3, result.length);
        assertEquals(List.of("a", "b"), result[0]);
        assertEquals(List.of("c", "d"), result[1]);
        assertEquals(List.of("e"), result[2]);
    }

    @Test
    void splitListChunkSizeLargerThanList() {
        List<String> list = List.of("a", "b");
        List<String>[] result = JavaUtil.splitList(list, 10);
        assertEquals(1, result.length);
        assertEquals(List.of("a", "b"), result[0]);
    }

    @Test
    void splitListEmptyList() {
        List<String> list = List.of();
        List<String>[] result = JavaUtil.splitList(list, 5);
        assertEquals(0, result.length);
    }

    @Test
    void splitListSingleElement() {
        List<String> list = List.of("only");
        List<String>[] result = JavaUtil.splitList(list, 1);
        assertEquals(1, result.length);
        assertEquals(List.of("only"), result[0]);
    }

    @Test
    void splitListsAreIndependent() {
        List<String> source = new java.util.ArrayList<>(List.of("a", "b", "c", "d"));
        List<String>[] result = JavaUtil.splitList(source, 2);
        source.add("e");
        assertEquals(2, result[0].size(), "sublists should be independent copies");
    }

    // ========== getOrDefault ==========

    @Test
    void getOrDefaultReturnsObjectWhenNotNull() {
        assertEquals("hello", JavaUtil.getOrDefault("hello", "default"));
    }

    @Test
    void getOrDefaultReturnsDefaultWhenNull() {
        assertEquals("default", JavaUtil.getOrDefault(null, "default"));
    }

    @Test
    void getOrDefaultReturnsDefaultForEmptyString() {
        assertEquals("default", JavaUtil.getOrDefault("", "default"));
    }

    @Test
    void getOrDefaultReturnsObjectForNonEmptyString() {
        assertEquals("text", JavaUtil.getOrDefault("text", "default"));
    }

    @Test
    void getOrDefaultWithInteger() {
        assertEquals(42, JavaUtil.getOrDefault(42, 0));
    }

    @Test
    void getOrDefaultNullIntegerReturnsDefault() {
        Integer val = null;
        assertEquals(0, JavaUtil.getOrDefault(val, 0));
    }

    // ========== getNonNull ==========

    @Test
    void getNonNullReturnsFirstWhenNotNull() {
        assertEquals("first", JavaUtil.getNonNull("first", "second"));
    }

    @Test
    void getNonNullReturnsSecondWhenFirstNull() {
        assertEquals("second", JavaUtil.getNonNull(null, "second"));
    }

    @Test
    void getNonNullBothNullReturnsNull() {
        assertNull(JavaUtil.getNonNull(null, null));
    }

    // ========== isEmptyString ==========

    @ParameterizedTest
    @MethodSource("isEmptyStringProvider")
    void isEmptyString(String input, boolean expected) {
        assertEquals(expected, JavaUtil.isEmptyString(input));
    }

    static Stream<Arguments> isEmptyStringProvider() {
        return Stream.of(
                Arguments.of(null, true),
                Arguments.of("", true),
                Arguments.of("   ", false),
                Arguments.of("text", false),
                Arguments.of(" §r ", false)
        );
    }

    @Test
    void isEmptyStringStripsColorCodes() {
        // "§c" is red color code, stripping it leaves empty
        assertTrue(JavaUtil.isEmptyString("§c"));
    }

    // ========== containsIgnoreCase(String, String) ==========

    @Test
    void containsIgnoreCaseBasicMatch() {
        assertTrue(JavaUtil.containsIgnoreCase("Hello World", "world"));
    }

    @Test
    void containsIgnoreCaseCaseInsensitive() {
        assertTrue(JavaUtil.containsIgnoreCase("HELLO", "hello"));
    }

    @Test
    void containsIgnoreCaseNoMatch() {
        assertFalse(JavaUtil.containsIgnoreCase("Hello", "xyz"));
    }

    @Test
    void containsIgnoreCaseNullHaystackReturnsFalse() {
        assertFalse(JavaUtil.containsIgnoreCase((String) null, "test"));
    }

    @Test
    void containsIgnoreCaseEmptyHaystackReturnsFalse() {
        assertFalse(JavaUtil.containsIgnoreCase("", "test"));
    }

    @Test
    void containsIgnoreCaseNullNeedleReturnsFalse() {
        assertFalse(JavaUtil.containsIgnoreCase("test", null));
    }

    @Test
    void containsIgnoreCaseEmptyNeedleReturnsFalse() {
        assertFalse(JavaUtil.containsIgnoreCase("test", ""));
    }

    @Test
    void containsIgnoreCaseStripsColorCodesInHaystack() {
        assertTrue(JavaUtil.containsIgnoreCase("§aHello§r", "hello"));
    }

    @Test
    void containsIgnoreCaseStripsColorCodesInNeedle() {
        assertTrue(JavaUtil.containsIgnoreCase("Hello", "§chel§r"));
    }

    @Test
    void containsIgnoreCaseSubstringNotAtStart() {
        assertTrue(JavaUtil.containsIgnoreCase("prefix-target-suffix", "target"));
    }

    @Test
    void containsIgnoreCaseFullMatch() {
        assertTrue(JavaUtil.containsIgnoreCase("Exact Match", "Exact Match"));
    }

    @Test
    void containsIgnoreCaseSpecialCharacters() {
        assertTrue(JavaUtil.containsIgnoreCase("test_item-123", "ITEM-123"));
    }
}
