package ryuzuinfiniteshop.ryuzuinfiniteshop.util.effect;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundUtil {
    public static void playCloseShopSound(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 1, 1);
    }

    public static void playClickShopSound(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
    }

    public static void playFailSound(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }

    public static void playBreakSound(Player p) {
        p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BREAK, 1, 0);
    }

    public static void playSuccessSound(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
    }

    public static void playCautionSound(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1, 1.2f);
    }
}
