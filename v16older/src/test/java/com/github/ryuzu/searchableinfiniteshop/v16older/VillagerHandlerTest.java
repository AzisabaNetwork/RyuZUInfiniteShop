package com.github.ryuzu.searchableinfiniteshop.v16older;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VillagerHandlerTest {
    private VillagerHandlerV8Old handler;

    @BeforeEach
    void setUp() {
        handler = new VillagerHandlerV8Old();
    }

    @Test
    void resolveKnownNewStyleProfession() {
        assertEquals("BLACKSMITH", handler.resolveProfession("ARMORER"));
    }

    @Test
    void resolveUnknownProfessionReturnsInput() {
        assertEquals("FARMER", handler.resolveProfession("FARMER"));
    }

    @Test
    void resolveNullProfessionReturnsDefault() {
        assertEquals("FARMER", handler.resolveProfession(null));
    }

    @Test
    void getDefaultProfessionName() {
        assertEquals("NORMAL", handler.getDefaultProfessionName());
    }

    @Test
    void getValidProfessionNames() {
        List<String> names = handler.getValidProfessionNames();
        assertTrue(names.contains("FARMER"));
        assertTrue(names.contains("PRIEST"));
        assertTrue(names.contains("BLACKSMITH"));
        assertFalse(names.contains("NONE"));
    }

    @Test
    void hasBiome() {
        assertFalse(handler.hasBiome());
    }

    @Test
    void resolveBiomeAlwaysReturnsPlains() {
        assertEquals("PLAINS", handler.resolveBiome("DESERT"));
        assertEquals("PLAINS", handler.resolveBiome(null));
    }

    @Test
    void getDefaultBiomeName() {
        assertEquals("PLAINS", handler.getDefaultBiomeName());
    }

    @Test
    void getValidBiomeNames() {
        List<String> names = handler.getValidBiomeNames();
        assertEquals(1, names.size());
        assertTrue(names.contains("PLAINS"));
    }

    @Test
    void resolveNoneMapsToNormal() {
        assertEquals("NORMAL", handler.resolveProfession("NONE"));
    }

    @Test
    void resolveClericMapsToPriest() {
        assertEquals("PRIEST", handler.resolveProfession("CLERIC"));
    }

    @Test
    void resolveAllNewProfessionsMapToOldOnes() {
        assertEquals("NORMAL", handler.resolveProfession("CARTOGRAPHER"));
        assertEquals("NORMAL", handler.resolveProfession("FISHERMAN"));
        assertEquals("NORMAL", handler.resolveProfession("FLETCHER"));
        assertEquals("NORMAL", handler.resolveProfession("LEATHERWORKER"));
        assertEquals("NORMAL", handler.resolveProfession("MASON"));
        assertEquals("NORMAL", handler.resolveProfession("SHEPHERD"));
    }
}
