package com.github.ryuzu.searchableinfiniteshop.v21newer;

import com.github.ryuzu.searchableinfiniteshop.api.IVillagerHandler;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern KEY_PATTERN = Pattern.compile("minecraft:([a-z0-9_]+)");

    @Override
    public String resolveProfession(String configName) {
        if (configName == null || configName.isBlank()) return getDefaultProfessionName();

        if (configName.contains("minecraft:")) {
            Matcher matcher = KEY_PATTERN.matcher(configName);
            String foundKey = null;
            while (matcher.find()) {
                String candidate = matcher.group(1);
                if (!candidate.equalsIgnoreCase("villager_profession")) {
                    foundKey = candidate;
                }
            }
            if (foundKey != null) {
                configName = foundKey;
            }
        }

        String mapped = OLD_TO_NEW.get(configName);
        if (mapped != null) return mapped;

        String upper = configName.toUpperCase(Locale.ROOT);
        if (VALID_PROFESSIONS.contains(upper)) {
            return upper;
        }

        return getDefaultProfessionName();
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
        if (configName == null || configName.isBlank()) return getDefaultBiomeName();

        if (configName.contains("minecraft:")) {
            Matcher matcher = KEY_PATTERN.matcher(configName);
            String foundKey = null;
            while (matcher.find()) {
                String candidate = matcher.group(1);
                if (!candidate.equalsIgnoreCase("villager_type")) {
                    foundKey = candidate;
                }
            }
            if (foundKey != null) {
                configName = foundKey;
            }
        }

        String upper = configName.toUpperCase(Locale.ROOT);
        if (VALID_BIOMES.contains(upper)) {
            return upper;
        }

        return getDefaultBiomeName();
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
