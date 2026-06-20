package ryuzuinfiniteshop.ryuzuinfiniteshop.listener.editor.change;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.editor.ShopEditorGui;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopHolder;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.gui.holder.ShopMode;
import ryuzuinfiniteshop.ryuzuinfiniteshop.data.shops.*;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.effect.SoundUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.entity.EntityUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ItemUtil;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.inventory.ShopUtil;

//NPCのエンティティタイプごとの固有のNBTを変更
public class ChangeIndividualSettingsListener implements Listener {

    //年齢の変更
    @EventHandler
    public void changeSettings(InventoryClickEvent event) {
        //インベントリがショップなのかチェック
        ShopHolder holder = ShopUtil.getShopHolder(event);
        if (holder == null) return;
        if (!(holder.getGui() instanceof ShopEditorGui)) return;
        if (!holder.getMode().equals(ShopMode.EDIT)) return;
        if (event.getClickedInventory() == null) return;
        if (ItemUtil.getWhitePanel().equals(event.getCurrentItem())) return;

        //必要なデータを取得
        Player p = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        ShopEditorGui editor = (ShopEditorGui) holder.getGui();

        if (!editor.getSettingsMap().containsKey(slot)) return;
        changeAgeLook(holder, event.getSlot());
        changeSitting(holder, event.getSlot());
        changePowered(holder, event.getSlot());
        changeDerp(holder, event.getSlot());
        changeProfession(holder, event.getSlot());
        changeVisible(holder, event.getSlot());
        changeParrotColor(holder, event.getSlot());
        changeDyeColor(holder, event.getSlot());
        changeOptionalInfo(holder, event.getSlot());
        changeHorseColor(holder, event.getSlot());
        changeHorseStyle(holder, event.getSlot());
        changeSizeIncrease(holder, event.getSlot());
        changeSizeDecrease(holder, event.getSlot());
        changeBodyColor(holder, event.getSlot());
        changePatternColor(holder, event.getSlot());
        changePattern(holder, event.getSlot());
        changeCatType(holder, event.getSlot());
        changeRabbitType(holder, event.getSlot());
        changeAxolotlType(holder, event.getSlot());
        changeBiome(holder, event.getSlot());
        changeLevel(holder, event.getSlot());

        //音を出す
        SoundUtil.playClickShopSound(p);

        //GUIのアイテムを更新
        editor.setSettings(event.getView().getTopInventory());
    }

    //年齢の変更
    public void changeAgeLook(ShopHolder holder, int slot) {
        //必要なデータを取得
        ShopEditorGui editor = (ShopEditorGui) holder.getGui();

        if (!editor.getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.Age)) return;
        if (!(holder.getShop() instanceof AgeableShop)) return;

        //年齢の変更
        ((AgeableShop) holder.getShop()).setAgeLook(!((AgeableShop) holder.getShop()).isAdult());
    }

    public void changeSitting(ShopHolder holder, int slot) {
        //必要なデータを取得
        ShopEditorGui editor = (ShopEditorGui) holder.getGui();

        if (!editor.getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.Sitting)) return;
        if (!(holder.getShop() instanceof SittableShop)) return;

        //年齢の変更
        ((SittableShop) holder.getShop()).setSitting(!((SittableShop) holder.getShop()).isSitting());
    }

    //クリーパーを帯電させるか変更
    public void changePowered(ShopHolder holder, int slot) {
        //必要なデータを取得
        if (!((ShopEditorGui) holder.getGui()).getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.Power))
            return;
        if (!(holder.getShop() instanceof PoweredableShop)) return;

        //帯電させるか変更
        ((PoweredableShop) holder.getShop()).setPowered(!((PoweredableShop) holder.getShop()).isPowered());
    }

    public void changeDerp(ShopHolder holder, int slot) {
        //必要なデータを取得
        if (!((ShopEditorGui) holder.getGui()).getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.Derp))
            return;
        if (!(holder.getShop() instanceof SnowmanShop)) return;

        //帯電させるか変更
        ((SnowmanShop) holder.getShop()).setDerp(!((SnowmanShop) holder.getShop()).isDerp());
    }

    public void changeSizeIncrease(ShopHolder holder, int slot) {
        //必要なデータを取得
        if (!((ShopEditorGui) holder.getGui()).getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.SizeIncrease))
            return;
        if (!(holder.getShop() instanceof SlimeShop)) return;

        //サイズを大きくする
        ((SlimeShop) holder.getShop()).setSize(((SlimeShop) holder.getShop()).getSize() + 1);
    }

    public void changeSizeDecrease(ShopHolder holder, int slot) {
        //必要なデータを取得
        if (!((ShopEditorGui) holder.getGui()).getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.SizeDecrease))
            return;
        if (!(holder.getShop() instanceof SlimeShop)) return;

        //サイズを小さくする
        ((SlimeShop) holder.getShop()).setSize(((SlimeShop) holder.getShop()).getSize() - 1);
    }

    public void changeBodyColor(ShopHolder holder, int slot) {
        //必要なデータを取得
        if (!((ShopEditorGui) holder.getGui()).getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.BodyColor))
            return;
        if (!(holder.getShop() instanceof TropicalFishShop)) return;

        //体の色の変更
        ((TropicalFishShop) holder.getShop()).setBodyColor(EntityUtil.getNextColor(((TropicalFishShop) holder.getShop()).getBodyColor()));
    }

    public void changePatternColor(ShopHolder holder, int slot) {
        //必要なデータを取得
        if (!((ShopEditorGui) holder.getGui()).getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.BodyColor))
            return;
        if (!(holder.getShop() instanceof TropicalFishShop)) return;

        //模様の色の変更
        ((TropicalFishShop) holder.getShop()).setPatternColor(EntityUtil.getNextColor(((TropicalFishShop) holder.getShop()).getPatternColor()));
    }

    public void changePattern(ShopHolder holder, int slot) {
        //必要なデータを取得
        if (!((ShopEditorGui) holder.getGui()).getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.BodyColor))
            return;
        if (!(holder.getShop() instanceof TropicalFishShop)) return;

        //模様の変更
        ((TropicalFishShop) holder.getShop()).setPattern(((TropicalFishShop) holder.getShop()).getNextPattern());
    }

    public void changeCatType(ShopHolder holder, int slot) {
        //必要なデータを取得
        if (!((ShopEditorGui) holder.getGui()).getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.CatType))
            return;
        if (!(holder.getShop() instanceof CatShop)) return;

        //模様の変更
        ((CatShop) holder.getShop()).setCatType(((CatShop) holder.getShop()).getNextType());
    }

    public void changeAxolotlType(ShopHolder holder, int slot) {
        //必要なデータを取得
        if (!((ShopEditorGui) holder.getGui()).getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.AxolotlType))
            return;
        if (!(holder.getShop() instanceof AxolotlShop)) return;

        //模様の変更
        ((AxolotlShop) holder.getShop()).setCatType(((AxolotlShop) holder.getShop()).getNextType());
    }

    public void changeRabbitType(ShopHolder holder, int slot) {
        //必要なデータを取得
        if (!((ShopEditorGui) holder.getGui()).getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.RabbitType))
            return;
        if (!(holder.getShop() instanceof RabbitShop)) return;

        //模様の変更
        ((RabbitShop) holder.getShop()).setRabbitType(((RabbitShop) holder.getShop()).getNextType());
    }

    //村人の職業を変更
    public void changeProfession(ShopHolder holder, int slot) {
        //必要なデータを取得
        ShopEditorGui editor = (ShopEditorGui) holder.getGui();

        if (!editor.getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.Profession)) return;
        if (!(holder.getShop() instanceof VillagerableShop)) return;

        //職業を変更
        ((VillagerableShop) holder.getShop()).setProfession(((VillagerableShop) holder.getShop()).getNextProfession());
    }

    //村人のバイオームを変更
    public void changeBiome(ShopHolder holder, int slot) {
        //必要なデータを取得
        ShopEditorGui editor = (ShopEditorGui) holder.getGui();

        if (!editor.getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.Biome)) return;
        if (!(holder.getShop() instanceof VillagerableShop)) return;

        //バイオームを変更
        ((VillagerableShop) holder.getShop()).setBiome(((VillagerableShop) holder.getShop()).getNextBiome());
    }

    //村人のレベル
    public void changeLevel(ShopHolder holder, int slot) {
        //必要なデータを取得
        ShopEditorGui editor = (ShopEditorGui) holder.getGui();

        if (!editor.getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.Level)) return;
        if (!(holder.getShop() instanceof VillagerableShop)) return;

        //レベルを変更
        int level = ((VillagerableShop) holder.getShop()).getLevel();
        ((VillagerableShop) holder.getShop()).setLevel(level >= 5 ? 1 : level + 1);
    }

    //透明か変更
    public void changeVisible(ShopHolder holder, int slot) {
        //必要なデータを取得
        ShopEditorGui editor = (ShopEditorGui) holder.getGui();

        if (!editor.getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.Visible)) return;
        if (!(holder.getShop().getEntity() instanceof LivingEntity)) return;

        //透明か変更
        holder.getShop().changeInvisible();
    }

    //オウムの色を変更
    public void changeParrotColor(ShopHolder holder, int slot) {
        //必要なデータを取得
        ShopEditorGui editor = (ShopEditorGui) holder.getGui();

        if (!editor.getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.ParrotColor)) return;
        if (!(holder.getShop() instanceof ParrotShop)) return;

        //色を変更
        ((ParrotShop) holder.getShop()).setColor(((ParrotShop) holder.getShop()).getNextColor());
    }

    //色を変更
    public void changeDyeColor(ShopHolder holder, int slot) {
        //必要なデータを取得
        ShopEditorGui editor = (ShopEditorGui) holder.getGui();

        if (!editor.getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.DyeColor)) return;
        if (!(holder.getShop() instanceof DyeableShop)) return;

        //色を変更
        ((DyeableShop) holder.getShop()).setColor(((DyeableShop) holder.getShop()).getNextColor());
    }

    //追加情報の変更
    public void changeOptionalInfo(ShopHolder holder, int slot) {
        //必要なデータを取得
        ShopEditorGui editor = (ShopEditorGui) holder.getGui();

        if (!editor.getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.OptionalInfo)) return;
        if (!(holder.getShop() instanceof DyeableShop)) return;

        //色を変更
        ((DyeableShop) holder.getShop()).setOptionalInfo(!((DyeableShop) holder.getShop()).getOptionalInfo());
    }

    //馬の色を変更
    public void changeHorseColor(ShopHolder holder, int slot) {
        //必要なデータを取得
        ShopEditorGui editor = (ShopEditorGui) holder.getGui();

        if (!editor.getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.HorseColor)) return;
        if (!(holder.getShop() instanceof HorseShop)) return;

        //色を変更
        ((HorseShop) holder.getShop()).setColor(((HorseShop) holder.getShop()).getNextColor());
    }

    //馬の模様を変更
    public void changeHorseStyle(ShopHolder holder, int slot) {
        //必要なデータを取得
        ShopEditorGui editor = (ShopEditorGui) holder.getGui();

        if (!editor.getSettingsMap().get(slot).equals(ShopEditorGui.ShopSettings.HorseStyle)) return;
        if (!(holder.getShop() instanceof HorseShop)) return;

        //色を変更
        ((HorseShop) holder.getShop()).setStyle(((HorseShop) holder.getShop()).getNextStyle());
    }
}
