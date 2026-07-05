package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OptionTypeTest {

    @Test
    void enumHasThreeConstants() {
        assertEquals(3, OptionType.values().length);
    }

    @Test
    void enumConstantsPresent() {
        assertArrayEquals(
                new OptionType[]{OptionType.RATE, OptionType.LIMIT, OptionType.MONEY},
                OptionType.values()
        );
    }

    @Test
    void valueOfAllConstants() {
        assertEquals(OptionType.RATE, OptionType.valueOf("RATE"));
        assertEquals(OptionType.LIMIT, OptionType.valueOf("LIMIT"));
        assertEquals(OptionType.MONEY, OptionType.valueOf("MONEY"));
    }
}
