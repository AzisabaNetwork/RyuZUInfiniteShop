package ryuzuinfiniteshop.ryuzuinfiniteshop.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.DisplayPanel;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.XMaterial;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class DisplayPanelConfig {
    private static final HashMap<ShopTrade.TradeResult, String> defaultResultMaterial = new HashMap<ShopTrade.TradeResult, String>() {{
        put(ShopTrade.TradeResult.Success, "GREEN_STAINED_GLASS_PANE");
        put(ShopTrade.TradeResult.NotEnoughItems, "RED_STAINED_GLASS_PANE");
        put(ShopTrade.TradeResult.NotEnoughMoney, "RED_STAINED_GLASS_PANE");
        put(ShopTrade.TradeResult.Error, "RED_STAINED_GLASS_PANE");
        put(ShopTrade.TradeResult.Limited, "RED_STAINED_GLASS_PANE");
        put(ShopTrade.TradeResult.Full, "YELLOW_STAINED_GLASS_PANE");
        put(ShopTrade.TradeResult.Normal, "WHITE_STAINED_GLASS_PANE");
        put(ShopTrade.TradeResult.Locked, "ORANGE_STAINED_GLASS_PANE");
    }};
    private static HashMap<ShopTrade.TradeResult, DisplayPanel> panels = new HashMap<>();

    public static DisplayPanel getPanel(ShopTrade.TradeResult result) {
        return panels.get(result);
    }

    public static void load() {
        File file = FileUtil.initializeFile("panel.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }
        panels = new HashMap<ShopTrade.TradeResult, DisplayPanel>() {{
            for (ShopTrade.TradeResult result : defaultResultMaterial.keySet()) {
                put(
                        result,
                        new DisplayPanel(
                                result,
                                XMaterial.matchXMaterial(yaml.getString(getResultConfig(result) + ".Material", defaultResultMaterial.get(result))).get().parseItem(),
                                yaml.getInt(getResultConfig(result) + ".CustomModelData", -1)
                        )
                );
            }
        }};
    }

    public static void save() {
        File file = FileUtil.initializeFile("panel.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }
        for (ShopTrade.TradeResult result : defaultResultMaterial.keySet()) {
            if (!yaml.contains(getResultConfig(result) + ".Material"))
                yaml.set(getResultConfig(result) + ".Material", panels.get(result).getItem().getType().name());
            if (!yaml.contains(getResultConfig(result) + ".CustomModelData"))
                yaml.set(getResultConfig(result) + ".CustomModelData", panels.get(result).getData());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            if (!Config.readOnlyIgnoreIOException) e.printStackTrace();
        }
    }

    private static String getResultConfig(ShopTrade.TradeResult result) {
        return "Display.Panel." + result.name();
    }
}
