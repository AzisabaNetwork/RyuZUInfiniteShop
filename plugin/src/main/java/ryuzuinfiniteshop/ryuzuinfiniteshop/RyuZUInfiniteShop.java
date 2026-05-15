package ryuzuinfiniteshop.ryuzuinfiniteshop;

import com.github.ryuzu.ryuzucommandsgenerator.RyuZUCommandsGenerator;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
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
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.*;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public final class RyuZUInfiniteShop extends JavaPlugin {
    @Getter
    private static RyuZUInfiniteShop plugin;
    private static Logger logger;
    public static final String prefixCommand = ChatColor.GOLD + "[SIS]";
    public static final String prefixPersistent = "RyuZU.Infinite.Shop.";
    public static final int VERSION = Integer.parseInt(Bukkit.getServer().getVersion().split("\\.")[1]);

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
        FileUtil.loadAll();
        RyuZUCommandsGenerator.initialize(this, LanguageKey.COMMAND_ERROR_PERMISSION.getMessage());
    }

    @Override
    public void onDisable() {
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
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeNpcDirecationListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeLockListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ChangeSearchableListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ConvartListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new RemoveShopListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new ReloadShopListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new SearchTradeListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new TeleportShopListener(), getPlugin());
        getPlugin().getServer().getPluginManager().registerEvents(new SchedulerListener(), getPlugin());
    }

    public static void registerAllListeners() {
        Plugin plugin = RyuZUInfiniteShop.getPlugin();
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        String listenersPackageName = plugin.getClass().getPackage().getName() + ".listener";

        try {
            URL pluginUrl = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            File pluginFile = new File(pluginUrl.toURI());
            if (pluginFile.isFile()) {
                JarFile jarFile = new JarFile(pluginFile);
                Enumeration<JarEntry> entries = jarFile.entries();
                URL[] urls = {pluginUrl};
                URLClassLoader classLoader = new URLClassLoader(urls);

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entryName.startsWith(listenersPackageName.replace('.', '/')) && entryName.endsWith(".class")) {
                        String className = entryName.replace('/', '.').substring(0, entryName.length() - ".class".length());
                        Class<?> clazz = classLoader.loadClass(className);
                        if (Listener.class.isAssignableFrom(clazz)) {
                            Listener listener = (Listener) clazz.getDeclaredConstructor().newInstance();
                            pluginManager.registerEvents(listener, plugin);
                        }
                    }
                }
                jarFile.close();
                classLoader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String extractClassName(Path basePath, Path classFile) {
        String packageName = RyuZUInfiniteShop.getPlugin().getClass().getPackage().getName();
        String relativePath = basePath.relativize(classFile).toString();
        String className = relativePath.replace(File.separator, ".");
        className = className.substring(0, className.length() - ".class".length());
        return packageName + ".listeners." + className;
    }
}
