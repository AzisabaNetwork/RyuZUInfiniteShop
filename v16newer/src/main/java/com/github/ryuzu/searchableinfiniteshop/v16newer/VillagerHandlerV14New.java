package com.github.ryuzu.searchableinfiniteshop.v16newer;

import com.github.ryuzu.searchableinfiniteshop.api.IVillagerHandler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class VillagerHandlerV14New implements IVillagerHandler {
    private static final Map<String, String> OLD_TO_NEW = Map.of(
        "NORMAL", "NONE",
        "PRIEST", "CLERIC",
        "BLACKSMITH", "ARMORER"
    );

    private static final List<String> VALID_PROFESSIONS = Arrays.asList(
        "NONE", "ARMORER", "BUTCHER", "CARTOGRAPHER", "CLERIC", "FARMER",
        "FISHERMAN", "FLETCHER", "LEATHERWORKER", "LIBRARIAN", "MASON",
        "NITWIT", "SHEPHERD", "TOOLSMITH", "WEAPONSMITH"
    );

    private static final List<String> VALID_BIOMES = Arrays.asList(
        "DESERT", "JUNGLE", "PLAINS", "SAVANNA", "SNOW", "SWAMP", "TAIGA"
    );

    @Override
    public String resolveProfession(String configName) {
        if (configName == null) return "NONE";
        String mapped = OLD_TO_NEW.get(configName);
        if (mapped != null) return mapped;
        return configName;
    }

    @Override
    public String getDefaultProfessionName() {
        return "NONE";
    }

    @Override
    public List<String> getValidProfessionNames() {
        return VALID_PROFESSIONS;
    }

    @Override
    public boolean hasBiome() {
        return true;
    }

    @Override
    public String resolveBiome(String configName) {
        if (configName == null) return "PLAINS";
        return configName;
    }

    @Override
    public String getDefaultBiomeName() {
        return "PLAINS";
    }

    @Override
    public List<String> getValidBiomeNames() {
        return VALID_BIOMES;
    }
}
