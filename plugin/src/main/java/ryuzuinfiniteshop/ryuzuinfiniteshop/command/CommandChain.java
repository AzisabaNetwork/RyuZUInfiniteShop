package ryuzuinfiniteshop.ryuzuinfiniteshop.command;

import com.github.ryuzu.ryuzucommandsgenerator.CommandData;
import com.github.ryuzu.ryuzucommandsgenerator.CommandsGenerator;
import com.google.common.collect.Sets;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.LanguageKey;
import ryuzuinfiniteshop.ryuzuinfiniteshop.config.UnderstandSystemConfig;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.SelectSearchItemGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.common.ShopListGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.Shop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.ShopTrade;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.effect.SoundUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.TradeUtil;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CommandChain {
    public static void registerCommand() {
        Set<String> args1 = Sets.newHashSet("list", "search", "spawn", "open", "searchability", "list", "reload", "reload-single", "load", "save", "limit");

        CommandsGenerator.registerCommand(
                        "sis",
                        data -> {
                            data.sendMessage("§a§l-------§e§l=====§b§lSearchable Infinite Shop§e§l=====§a§l-------");
                            if (data.getSender().hasPermission("sis.list") || data.getSender().hasPermission("sis.op")) {
                                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.BLUE + "/" + data.getLabel() + " list " + ChatColor.GOLD + LanguageKey.COMMAND_LIST_SHOPS.getMessage());
                            }
                            if (data.getSender().hasPermission("sis.search") || data.getSender().hasPermission("sis.op")) {
                                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.BLUE + "/" + data.getLabel() + " search " + ChatColor.GOLD + LanguageKey.COMMAND_SEARCH_TRADES.getMessage());
                            }
                            if (data.getSender().hasPermission("sis.op")) {
                                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.BLUE + "/" + data.getLabel() + " spawn <world,x,y,z/Block/EntityType/MythicMobID> " + ChatColor.GOLD + LanguageKey.COMMAND_SPAWN_SHOP.getMessage());
                                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.BLUE + "/" + data.getLabel() + " open [world,x,y,z] <player> " + ChatColor.GOLD + LanguageKey.COMMAND_OPEN_TRADE_GUI.getMessage());
                                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.BLUE + "/" + data.getLabel() + " list " + ChatColor.GOLD + LanguageKey.COMMAND_LIST_SHOPS.getMessage());
                                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.BLUE + "/" + data.getLabel() + " reload " + ChatColor.GOLD + LanguageKey.COMMAND_RELOAD_ALL_DATA.getMessage());
                                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.BLUE + "/" + data.getLabel() + " reload-single [shopId] " + ChatColor.GOLD + LanguageKey.COMMAND_RELOAD_SHOP.getMessage());
                                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.BLUE + "/" + data.getLabel() + " save " + ChatColor.GOLD + LanguageKey.COMMAND_SAVE_ALL_DATA.getMessage());
                                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.BLUE + "/" + data.getLabel() + " searchability [true/false] [world] " + ChatColor.GOLD + LanguageKey.COMMAND_SET_SEARCHABLE.getMessage());
                                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.BLUE + "/" + data.getLabel() + " limit [increase/decrease/set] [player] [value] " + ChatColor.GOLD + LanguageKey.COMMAND_CHANGE_TRADE_LIMIT.getMessage());
                                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.BLUE + "[arg] " + ChatColor.GOLD + LanguageKey.COMMAND_ARGUMENT_REQUIRED.getMessage() + ", " + ChatColor.BLUE + "<arg> " + ChatColor.GOLD + LanguageKey.COMMAND_ARGUMENT_OPTIONAL.getMessage());
                            }
                            data.sendMessage("§a§l-------§e§l==============================§a§l-------");
                        }
                )
                .permissions("sis.player")
                .tabCompleteConditon(data -> data.getArgs().length == 0 || (data.getArgs().length == 1 && !args1.contains(data.getArgs()[0])));


        CommandsGenerator.registerCommand(
                "sis.reload",
                data -> FileUtil.reloadAllWithMessage()
        ).permissions("sis.op").tabCompleteConditon(data -> !FileUtil.isSaveBlock(data));

        CommandsGenerator.registerCommand(
                "sis.reload-single",
                data -> {
                    if (data.getArgs().length == 0) {
                        data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + "/" + data.getLabel() + "reload single [shopID]");
                        return;
                    }
                    if (!ShopUtil.getShops().containsKey(data.getArgs()[0])) {
                        data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_SHOP_NOT_EXIST.getMessage());
                        return;
                    }
                    Shop shop = ShopUtil.reloadShop(ShopUtil.getShop(data.getArgs()[0]));
                    shop.respawnNPC();
                    data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_SHOP_UPDATED.getMessage());
                }
        ).permissions("sis.op").tabCompleteConditon(data -> !FileUtil.isSaveBlock(data));

        CommandsGenerator.registerCommand(
                "sis.save",
                data -> FileUtil.saveAll()
        ).permissions("sis.op").tabCompleteConditon(data -> !FileUtil.isSaveBlock(data));

        CommandsGenerator.registerCommand(
                        "sis.understand",
                        data -> {
                            Player p = (Player) data.getSender();
                            UnderstandSystemConfig.addPlayer(p);
                            SoundUtil.playClickShopSound(p);
                        }
                )
                .permissions("sis.player")
                .condition(data -> isPlayer(data.getSender()) && !UnderstandSystemConfig.contains(((Player) data.getSender())))
                .tabCompleteConditon(data -> !FileUtil.isSaveBlock(data));

        CommandsGenerator.registerCommand(
                        "sis.spawn",
                        data -> {
                            Player player = (Player) data.getSender();
                            Location location = player.getLocation();
                            if (ShopUtil.getShops().containsKey(LocationUtil.toStringFromLocation(location))) {
                                Shop shop = ShopUtil.reloadShop(ShopUtil.getShop(LocationUtil.toStringFromLocation(location)));
                                shop.respawnNPC();
                                player.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_SHOP_UPDATED.getMessage());
                                SoundUtil.playSuccessSound(player);
                                return;
                            }
                            Shop shop = ShopUtil.createNewShop(location, EntityType.VILLAGER.name(), null);
                            shop.respawnNPC();
                            data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_SHOP_CREATED.getMessage());
                            SoundUtil.playSuccessSound(player);
                            LogUtil.log(LogUtil.LogType.CREATESHOP, data.getSender().getName(), LocationUtil.toStringFromLocation(location));
                        }
                )
                .permissions("sis.op")
                .tabCompleteConditon(data -> data.getArgs().length == 1 && isPlayer(data.getSender()) && !FileUtil.isSaveBlock(data));


        CommandsGenerator.registerCommand(
                        "sis.spawn",
                        data -> {
                            Location loc;
                            if (LocationUtil.isLocationString(data.getArgs()[1])) {
                                loc = LocationUtil.toLocationFromString(data.getArgs()[1]);
                                if (ShopUtil.getShops().containsKey(data.getArgs()[1])) {
                                    Shop shop = ShopUtil.reloadShop(ShopUtil.getShop(data.getArgs()[1]));
                                    shop.respawnNPC();
                                    data.sendMessage(ChatColor.GREEN + LanguageKey.MESSAGE_SHOP_UPDATED.getMessage());
                                    return;
                                }
                            } else {
                                Player p = (Player) data.getSender();
                                loc = p.getLocation();
                            }
                            if (data.getArgs()[1].equalsIgnoreCase("CITIZEN"))
                                new Shop(loc, ((Player) data.getSender()).getUniqueId(), false);
                            else if (MythicInstanceProvider.getInstance().exsistsMythicMob(data.getArgs()[1]))
                                new Shop(loc, data.getArgs()[1]);
                            else
                                ShopUtil.createNewShop(loc, data.getArgs()[1], null);
                            ShopUtil.getShop(LocationUtil.toStringFromLocation(loc)).respawnNPC();
                            data.sendMessage(ChatColor.GREEN + LanguageKey.MESSAGE_SHOP_CREATED.getMessage());
                            if (data.getSender() instanceof Player) SoundUtil.playSuccessSound((Player) data.getSender());
                            LogUtil.log(LogUtil.LogType.CREATESHOP, data.getSender().getName(), LocationUtil.toStringFromLocation(loc));
                        }
                )
                .permissions("sis.op")
                .condition(data -> {
                    if (LocationUtil.isLocationString(data.getArgs()[1]))
                        return true;
                    if (data.getArgs()[1].equalsIgnoreCase("BLOCK"))
                        return true;
                    if (CitizensHandler.isLoaded() && isPlayer(data.getSender()) && data.getArgs()[1].equalsIgnoreCase("CITIZEN"))
                        return true;
                    if (!isPlayer(data.getSender()))
                        return false;
                    try {
                        EntityType.valueOf(data.getArgs()[1].toUpperCase());
                        return true;
                    } catch (IllegalArgumentException e) {
                        if (MythicInstanceProvider.isLoaded() && !MythicInstanceProvider.getInstance().exsistsMythicMob(data.getArgs()[1])) {
                            data.sendMessage(ChatColor.RED + LanguageKey.MESSAGE_ERROR_INVALID_ENTITY.getMessage());
                            return false;
                        }
                        return true;
                    }
                })
                .tabCompleteConditon(data -> data.getArgs().length >= 2 && !FileUtil.isSaveBlock(data))
                .complete(1, "BLOCK")
                .complete(1, Arrays.stream(EntityType.values()).map(Enum::name).collect(Collectors.toList()))
                .complete(1, MythicInstanceProvider.isLoaded() ? new ArrayList<>(MythicInstanceProvider.getInstance().getMythicMobs()) : new ArrayList<>())
                .complete(1, CitizensHandler.isLoaded() ? Collections.singletonList("CITIZEN") : new ArrayList<>());

        CommandsGenerator.registerCommand(
                        "sis.open",
                        data -> {
                            Player p = data.getArgs().length == 2 ? (Player) data.getSender() : Bukkit.getServer().getPlayer(data.getArgs()[2]);
                            p.openInventory(ShopUtil.getShop(data.getArgs()[1]).getPage(1).getInventory(ShopMode.TRADE, p));
                        }
                )
                .permissions("sis.op")
                .condition(data -> {
                    if (!ShopUtil.getShops().containsKey(data.getArgs()[1])) {
                        data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_SHOP_NOT_EXIST.getMessage());
                        return false;
                    }
                    if (ShopUtil.getShops().get(data.getArgs()[1]).getPageCount() == 0) {
                        data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_SHOP_NO_TRADES.getMessage());
                        return false;
                    }
                    if (data.getArgs().length == 2)
                        return isPlayer(data.getSender());
                    else {
                        if (Bukkit.getServer().getPlayer(data.getArgs()[2]) == null) {
                            data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_NOT_EXIST_PLAYER.getMessage());
                            return false;
                        }
                    }
                    return true;
                })
                .tabCompleteConditon(data -> !FileUtil.isSaveBlock(data))
                .completePlayer(2);

        CommandsGenerator.registerCommand(
                        "sis.searchability",
                        data -> {
                            ShopUtil.getShops().values().stream().filter(shop -> shop.getLocation().getWorld().getName().equalsIgnoreCase(data.getArgs()[2])).forEach(shop -> shop.setSearchable(Boolean.parseBoolean(data.getArgs()[1])));
                            data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_SET_SEARCHABLE.getMessage());
                        }
                )
                .permissions("sis.op")
                .condition(data -> {
                    if (data.getArgs().length < 3) {
                        data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + "/" + data.getLabel() + " searchability [true/false] [world]");
                        return false;
                    }
                    try {
                        Boolean.parseBoolean(data.getArgs()[1]);
                    } catch (IllegalArgumentException e) {
                        data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_INVALID_ARGUMENTS.getMessage());
                        return false;
                    }
                    if (Bukkit.getWorld(data.getArgs()[2]) == null) {
                        data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_NOT_EXIST_WORLD.getMessage());
                        return false;
                    }
                    return true;
                })
                .tabCompleteConditon(data -> !FileUtil.isSaveBlock(data))
                .complete(1, Arrays.asList("true", "false"))
                .complete(2, Bukkit.getWorlds().stream().map(world -> world.getName().toLowerCase()).collect(Collectors.toList()));

        CommandsGenerator.registerCommand(
                        "sis.list",
                        data -> {
                            Player p = (Player) data.getSender();
                            ShopMode mode = p.hasPermission("sis.op") ? ShopMode.EDIT : ShopMode.TRADE;
                            p.openInventory(new ShopListGui(1, ShopUtil.getSortedShops(mode, null)).getInventory(mode));
                            SoundUtil.playClickShopSound(p);
                        }
                )
                .permissions("sis.list", "sis.op")
                .tabCompleteConditon(data -> isPlayer(data.getSender()) && !FileUtil.isSaveBlock(data));

        CommandsGenerator.registerCommand(
                        "sis.search",
                        data -> {
                            Player p = (Player) data.getSender();
                            p.openInventory(new SelectSearchItemGui().getInventory(ShopMode.SEARCH));
                            SoundUtil.playClickShopSound(p);
                        },
                        Arrays.asList("sis.search", "sis.op"),
                        data -> true,
                        data -> {
                            if (!(data.getSender() instanceof Player)) {
                                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.COMMAND_PLAYER_ONLY.getMessage());
                                return false;
                            }
                            return !FileUtil.isSaveBlock(data);
                        }
                )
                .permissions("sis.search", "sis.op")
                .tabCompleteConditon(data -> isPlayer(data.getSender()) && !FileUtil.isSaveBlock(data));
        HashMap<String, BiFunction<Integer, Integer, Integer>> limitargs2 = new HashMap<>() {{
            put("increase", (a, b) -> a + b);
            put("decrease", (a, b) -> a - b);
            put("set", (a, b) -> b);
        }};

        CommandsGenerator.registerCommand(
                        "sis.limit",
                        data -> {
                            data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + "/" + data.getLabel() + "limit [increase/decrease/set] [player] [limit]");
                            data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_TRADE_REQUIRED_GEM.getMessage());
                        }
                )
                .permissions("sis.op")
                .tabCompleteConditon(data -> data.getArgs().length < 4 && isPlayer(data.getSender()) && !FileUtil.isSaveBlock(data));

        Predicate<CommandData> limitCondition = data -> {
            if (data.getArgs().length < 4) return false;
            if (Bukkit.getServer().getPlayer(data.getArgs()[2]) == null) {
                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_NOT_EXIST_PLAYER.getMessage());
                return false;
            }
            Player p = (Player) data.getSender();
            ShopTrade trade = TradeUtil.getFirstTrade(p.getInventory().getItemInMainHand());
            if (trade == null) {
                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.MESSAGE_ERROR_TRADE_REQUIRED_GEM.getMessage());
                return false;
            }
            try {
                Integer.parseInt(data.getArgs()[3]);
                return true;
            } catch (IllegalArgumentException e) {
                data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.COMMAND_INVALID_INTEGER.getMessage());
                return false;
            }
        };

        limitargs2.keySet().forEach(
                key -> CommandsGenerator.registerCommand(
                                "sis.limit." + key,
                                data -> {
                                    Player p = (Player) data.getSender();
                                    Player target = Bukkit.getServer().getPlayer(data.getArgs()[2]);
                                    ShopTrade trade = TradeUtil.getFirstTrade(p.getInventory().getItemInMainHand());
                                    trade.setTradeCount(target, limitargs2.get(key).apply(trade.getTradeCount(target), Integer.parseInt(data.getArgs()[3])));
                                    data.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.GREEN + LanguageKey.MESSAGE_SUCCESS_LIMIT_CHANGE.getMessage(target.getName()));
                                    SoundUtil.playClickShopSound(p);
                                }
                        )
                        .permissions("sis.op")
                        .condition(limitCondition)
                        .tabCompleteConditon(data -> isPlayer(data.getSender()) && !FileUtil.isSaveBlock(data))
                        .completePlayer(2)
        );
    }

    private static boolean isPlayer(CommandSender p) {
        if (!(p instanceof Player)) {
            p.sendMessage(RyuZUInfiniteShop.prefixCommand + ChatColor.RED + LanguageKey.COMMAND_PLAYER_ONLY.getMessage());
            return false;
        }
        return true;
    }
}
