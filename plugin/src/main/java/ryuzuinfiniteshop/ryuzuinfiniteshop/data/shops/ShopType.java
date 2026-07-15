package ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops;

import org.bukkit.ChatColor;

public enum ShopType {
    TwotoOne,
    FourtoFour,
    SixtoTwo;

    public ShopType getNextShopType() {
        switch (this) {
            case TwotoOne:
                return ShopType.FourtoFour;
            case FourtoFour:
                return ShopType.SixtoTwo;
            case SixtoTwo:
                return ShopType.TwotoOne;
        }
        return ShopType.TwotoOne;
    }

    public String getShopTypeDisplay() {
        switch (this) {
            case TwotoOne:
                return ChatColor.GREEN + "2 -> 1";
            case FourtoFour:
                return ChatColor.GREEN + "4 -> 4";
            case SixtoTwo:
                return ChatColor.GREEN + "6 -> 2";
        }
        return "";
    }

    public int getLimitSize() {
        return this.equals(ShopType.TwotoOne) ? 10 : 5;
    }

    public int getAddSlot() {
        return this.equals(ShopType.TwotoOne) ? 4 : 9;
    }

    public int getSubtractSlot() {
        switch (this) {
            case TwotoOne:
                return 2;
            case FourtoFour:
                return 4;
            case SixtoTwo:
                return 6;
        }
        return 0;
    }
}
