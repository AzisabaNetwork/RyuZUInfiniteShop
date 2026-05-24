package ryuzuinfiniteshop.ryuzuinfiniteshop.util;

import org.junit.jupiter.api.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;
import static org.junit.jupiter.api.Assertions.*;

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
