package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import com.google.gson.Gson;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Stores a compressed shop payload outside the ItemStack NBT. */
public final class ShopItemStorage {
    private static final String DIRECTORY = "shop-items";
    private static final Gson GSON = new Gson();

    private ShopItemStorage() {
    }

    public static String save(Shop shop, List<ShopTrade> trades) {
        String id = UUID.randomUUID().toString();
        StoredShopFile data = new StoredShopFile(
                shop.convertShopToString(),
                shop.getShopType().name(),
                trades.stream().map(ShopItemStorage::serializeTrade).collect(Collectors.toList())
        );
        try {
            Files.write(getFile(id).toPath(), GSON.toJson(data).getBytes(StandardCharsets.UTF_8));
            return id;
        } catch (IOException e) {
            RyuZUInfiniteShop.getPlugin().getLogger().warning("Failed to save compressed shop " + id + ": " + e.getMessage());
            return null;
        }
    }

    public static StoredShop load(String id) {
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) return null;
        File file = getFile(id);
        if (!file.isFile()) return null;

        try {
            StoredShopFile data = GSON.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), StoredShopFile.class);
            if (data == null || data.shopData == null || data.trades == null) return null;
            List<ShopTrade> trades = new ArrayList<>();
            for (String serializedTrade : data.trades) {
                YamlConfiguration trade = new YamlConfiguration();
                trade.loadFromString(serializedTrade);
                trades.add(new ShopTrade(new HashMap<>(trade.getConfigurationSection("trade").getValues(false))));
            }
            return new StoredShop(data.shopData, ShopType.valueOf(data.shopType), trades);
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            RyuZUInfiniteShop.getPlugin().getLogger().warning("Failed to load compressed shop " + id + ": " + e.getMessage());
            return null;
        }
    }

    private static File getFile(String id) {
        File directory = new File(RyuZUInfiniteShop.getPlugin().getDataFolder(), DIRECTORY);
        if (!directory.exists()) directory.mkdirs();
        return new File(directory, id + ".json");
    }

    private static String serializeTrade(ShopTrade trade) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("trade", trade.serialize());
        return yaml.saveToString();
    }

    public static final class StoredShop {
        private final String shopData;
        private final ShopType shopType;
        private final List<ShopTrade> trades;

        private StoredShop(String shopData, ShopType shopType, List<ShopTrade> trades) {
            this.shopData = shopData;
            this.shopType = shopType;
            this.trades = trades;
        }

        public String getShopData() {
            return shopData;
        }

        public ShopType getShopType() {
            return shopType;
        }

        public List<ShopTrade> getTrades() {
            return trades;
        }
    }

    private static final class StoredShopFile {
        private final String shopData;
        private final String shopType;
        private final List<String> trades;

        private StoredShopFile(String shopData, String shopType, List<String> trades) {
            this.shopData = shopData;
            this.shopType = shopType;
            this.trades = trades;
        }
    }
}
