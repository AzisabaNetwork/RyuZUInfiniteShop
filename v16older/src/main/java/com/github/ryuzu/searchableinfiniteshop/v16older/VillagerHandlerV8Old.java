package com.github.ryuzu.searchableinfiniteshop.v16older;

import com.github.ryuzu.searchableinfiniteshop.api.IVillagerHandler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class VillagerHandlerV8Old implements IVillagerHandler {
    private static final Map<String, String> NEW_TO_OLD = Map.ofEntries(
        Map.entry("NONE", "NORMAL"),
        Map.entry("ARMORER", "BLACKSMITH"),
        Map.entry("WEAPONSMITH", "BLACKSMITH"),
        Map.entry("TOOLSMITH", "BLACKSMITH"),
        Map.entry("CARTOGRAPHER", "NORMAL"),
        Map.entry("CLERIC", "PRIEST"),
        Map.entry("FISHERMAN", "NORMAL"),
        Map.entry("FLETCHER", "NORMAL"),
        Map.entry("LEATHERWORKER", "NORMAL"),
        Map.entry("MASON", "NORMAL"),
        Map.entry("SHEPHERD", "NORMAL")
    );

    private static final List<String> VALID_PROFESSIONS = Arrays.asList(
        "FARMER", "LIBRARIAN", "PRIEST", "BLACKSMITH", "BUTCHER", "NITWIT"
    );

    @Override
    public String resolveProfession(String configName) {
        if (configName == null) return "FARMER";
        String mapped = NEW_TO_OLD.get(configName);
        if (mapped != null) return mapped;
        return configName;
    }

    @Override
    public String getDefaultProfessionName() {
        return "NORMAL";
    }

    @Override
    public List<String> getValidProfessionNames() {
        return VALID_PROFESSIONS;
    }

    @Override
    public boolean hasBiome() {
        return false;
    }

    @Override
    public String resolveBiome(String configName) {
        return "PLAINS";
    }

    @Override
    public String getDefaultBiomeName() {
        return "PLAINS";
    }

    @Override
    public List<String> getValidBiomeNames() {
        return List.of("PLAINS");
    }
}
