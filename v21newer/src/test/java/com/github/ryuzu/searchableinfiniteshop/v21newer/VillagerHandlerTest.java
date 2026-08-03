package com.github.ryuzu.searchableinfiniteshop.v21newer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VillagerHandlerTest {
    private VillagerHandlerV14New handler;

    @BeforeEach
    void setUp() {
        handler = new VillagerHandlerV14New();
    }

    @Test
    void resolveKnownProfession() {
        assertEquals("ARMORER", handler.resolveProfession("BLACKSMITH"));
    }

    @Test
    void resolveUnknownProfessionReturnsInput() {
        assertEquals("FARMER", handler.resolveProfession("FARMER"));
    }

    @Test
    void resolveNullProfessionReturnsDefault() {
        assertEquals("NONE", handler.resolveProfession(null));
    }

    @Test
    void getDefaultProfessionName() {
        assertEquals("NONE", handler.getDefaultProfessionName());
    }

    @Test
    void getValidProfessionNames() {
        List<String> names = handler.getValidProfessionNames();
        assertTrue(names.contains("NONE"));
        assertTrue(names.contains("CLERIC"));
        assertTrue(names.contains("ARMORER"));
        assertFalse(names.contains("NORMAL"));
    }

    @Test
    void hasBiome() {
        assertTrue(handler.hasBiome());
    }

    @Test
    void resolveKnownBiome() {
        assertEquals("DESERT", handler.resolveBiome("DESERT"));
    }

    @Test
    void resolveNullBiomeReturnsDefault() {
        assertEquals("PLAINS", handler.resolveBiome(null));
    }

    @Test
    void getDefaultBiomeName() {
        assertEquals("PLAINS", handler.getDefaultBiomeName());
    }

    @Test
    void getValidBiomeNames() {
        List<String> names = handler.getValidBiomeNames();
        assertTrue(names.contains("DESERT"));
        assertTrue(names.contains("TAIGA"));
        assertEquals(7, names.size());
    }

    @Test
    void resolveNormalMapsToNone() {
        assertEquals("NONE", handler.resolveProfession("NORMAL"));
    }

    @Test
    void resolvePriestMapsToCleric() {
        assertEquals("CLERIC", handler.resolveProfession("PRIEST"));
    }

    @Test
    void resolveCraftProfessionCorruptedString() {
        String corruptedString = "CraftProfession{holder=Reference{ResourceKey[minecraft:villager_profession / minecraft:none]=VillagerProfession[name=translation{key='entity.minecraft.villager.none', args=[]}]}}";
        assertEquals("NONE", handler.resolveProfession(corruptedString));

        String corruptedFarmer = "CraftProfession{holder=Reference{ResourceKey[minecraft:villager_profession / minecraft:farmer]=...}}";
        assertEquals("FARMER", handler.resolveProfession(corruptedFarmer));
    }

    @Test
    void resolveCraftTypeCorruptedString() {
        String corruptedString = "CraftType{holder=Reference{ResourceKey[minecraft:villager_type / minecraft:plains]=VillagerType[...]}}";
        assertEquals("PLAINS", handler.resolveBiome(corruptedString));

        String corruptedDesert = "CraftType{holder=Reference{ResourceKey[minecraft:villager_type / minecraft:desert]=...}}";
        assertEquals("DESERT", handler.resolveBiome(corruptedDesert));
    }

    @Test
    void resolveInvalidProfessionReturnsDefault() {
        assertEquals("NONE", handler.resolveProfession("INVALID_PROFESSION_NAME"));
    }

    @Test
    void resolveInvalidBiomeReturnsDefault() {
        assertEquals("PLAINS", handler.resolveBiome("INVALID_BIOME_NAME"));
    }
}
