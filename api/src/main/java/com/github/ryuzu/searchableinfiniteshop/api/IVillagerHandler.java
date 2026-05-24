package com.github.ryuzu.searchableinfiniteshop.api;

import java.util.List;

public interface IVillagerHandler {
    String resolveProfession(String configName);
    String getDefaultProfessionName();
    List<String> getValidProfessionNames();

    boolean hasBiome();
    String resolveBiome(String configName);
    String getDefaultBiomeName();
    List<String> getValidBiomeNames();
}
