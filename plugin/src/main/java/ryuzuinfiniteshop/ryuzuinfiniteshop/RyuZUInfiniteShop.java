package ryuzuinfiniteshop.ryuzuinfiniteshop;

import com.github.ryuzu.ryuzucommandsgenerator.RyuZUCommandsGenerator;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;
import ryuzuinfiniteshop.ryuzuinfiniteshop.command.CommandChain;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.TradeOption;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.MythicItem;
import ryuzuinfiniteshop.ryuzuinfiniteshop.listener.canceller.CancelAffectNpc;
import ryuzuinfiniteshop.ryuzuinfiniteshop.listener.canceller.CancelItemMoveListener;
import ryuzuinfiniteshop.ryuzuinfiniteshop.listener.editor.change.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.listener.editor.edit.EditMainPageListener;
import ryuzuinfiniteshop.ryuzuinfiniteshop.listener.editor.edit.EditTradePageListener;
import ryuzuinfiniteshop.ryuzuinfiniteshop.listener.editor.system.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.listener.player.OpenShopListener;
import ryuzuinfiniteshop.ryuzuinfiniteshop.listener.player.SearchTradeListener;
import ryuzuinfiniteshop.ryuzuinfiniteshop.listener.player.ShopListListener;
import ryuzuinfiniteshop.ryuzuinfiniteshop.migration.MigrationResult;
import ryuzuinfiniteshop.ryuzuinfiniteshop.migration.ShopMigrationService;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.CitizensHandler;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.MythicInstanceProvider;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.VaultHandler;

import java.util.logging.Logger;

public final class RyuZUInfiniteShop extends JavaPlugin {
    @Getter
    private static RyuZUInfiniteShop plugin;
    private static Logger logger;
    public static final String prefixCommand = ChatColor.GOLD + "[SIS]";
    public static final String prefixPersistent = "RyuZU.Infinite.Shop.";

    @Override
    public void onEnable() {
        // Plugin startup logic
        plugin = this;
        logger = getLogger();
        MythicInstanceProvider.setInstance();
        VaultHandler.setInstance();
        CitizensHandler.setInstance();
        CommandChain.registerCommand();
        registerEvents();
        ConfigurationSerialization.registerClass(MythicItem.class);
        ConfigurationSerialization.registerClass(TradeOption.class);
        MigrationResult migrationResult = ShopMigrationService.migrateAll(null);
        if (migrationResult.getMigrated() > 0 || migrationResult.getFailed() > 0) {
            getLogger().info("Shop migration complete: " + migrationResult.getMigrated() + " migrated, " + migrationResult.getSkipped() + " skipped, " + migrationResult.getFailed() + " failed.");
        }
        FileUtil.loadAll();
        RyuZUCommandsGenerator.initialize(this, LanguageKey.COMMAND_ERROR_PERMISSION.getMessage());
    }

    @Override
    public void onDisable() {
        FileUtil.shutdownSaver();
        FileUtil.saveAllSync();
    }

    public static void registerEvents() {
        getPlugin().getServer().getPluginManager().registerEvents(new EditMainPageListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new EditTradePageListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new OpenShopListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ShopListListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new CancelAffectNpc(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new CancelItemMoveListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeEquipmentListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeDisplayNameListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeNpcTypeListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeIndividualSettingsListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeShopTypeListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeMythicMobTypeListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeCitizenNpcTypeListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeNpcDirectionListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeLockListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeSearchableListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ConvertListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new RemoveShopListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ReloadShopListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new SearchTradeListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new TeleportShopListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new SchedulerListener(), getPlugin());
    }


}
