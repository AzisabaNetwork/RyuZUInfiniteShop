package ryuzuinfiniteshop.ryuzuinfiniteshop.util;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;

import static org.junit.jupiter.api.Assertions.assertFalse;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileUtilTest {

    @Test
    @Order(1)
    void saveBlockStartsFalse() {
        assertFalse(FileUtil.isSaveBlock());
    }

    @Test
    @Order(2)
    void reloadWithMessageBlockedWhenSaveBlock() {
        assertFalse(FileUtil.isSaveBlock());
    }
}
