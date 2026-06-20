package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import com.github.ryuzu.searchableinfiniteshop.api.IMythicHandler;
import com.github.ryuzu.searchableinfiniteshop.v21newer.MythicHandlerV5_12_0;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

import java.util.function.Consumer;

public class MythicInstanceProvider {
    private static IMythicHandler instance;

    public static IMythicHandler getInstance() {
        if (instance == null || !instance.getPlugin().isEnabled()) setInstance();
        if (instance == null) throw new NullPointerException(LanguageKey.ERROR_INVALID_LOADED_MYTHICMOBS.getMessage());
        return instance;
    }

    public static boolean isLoaded() {
        return instance != null;
    }

    public static void setInstance() {
        if (Bukkit.getServer().getPluginManager().getPlugin("MythicMobs") == null) return;
        Consumer<Runnable> reloadConsumer = ShopUtil::reloadAllShopForMythicMobsReload;
        instance = new MythicHandlerV5_12_0(RyuZUInfiniteShop.getPlugin(), reloadConsumer);
    }
}
