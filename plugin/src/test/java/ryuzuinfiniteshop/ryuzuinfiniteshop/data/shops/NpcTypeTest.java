package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcTypeTest {

    @Test
    void enumHasFourConstants() {
        assertEquals(4, NpcType.values().length);
    }

    @Test
    void enumConstantsPresent() {
        assertArrayEquals(
                new NpcType[]{NpcType.NORMAL, NpcType.BLOCK, NpcType.CITIZEN, NpcType.MYTHICMOB},
                NpcType.values()
        );
    }

    @Test
    void valueOfAllConstants() {
        assertEquals(NpcType.NORMAL, NpcType.valueOf("NORMAL"));
        assertEquals(NpcType.BLOCK, NpcType.valueOf("BLOCK"));
        assertEquals(NpcType.CITIZEN, NpcType.valueOf("CITIZEN"));
        assertEquals(NpcType.MYTHICMOB, NpcType.valueOf("MYTHICMOB"));
    }
}
