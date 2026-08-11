package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.inventory.ItemStack;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.MythicInstanceProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MythicMobsItemMigration implements MigrationStep {
    @Override
    public int fromVersion() {
        return 1;
    }

    @Override
    public int toVersion() {
        return 2;
    }

    @Override
    public String description() {
        return "Regenerate saved MythicMobs v4 item stacks with MythicMobs v5";
    }

    @Override
    public void migrate(org.bukkit.configuration.file.YamlConfiguration yaml) {
        if (!MythicInstanceProvider.isLoaded()) return;

        boolean changed = false;
        for (String key : yaml.getKeys(false)) {
            Conversion conversion = convert(yaml.get(key), this::regenerate);
            if (conversion.changed()) {
                yaml.set(key, conversion.value());
                changed = true;
            }
        }
        if (changed || MythicInstanceProvider.isLoaded()) {
            yaml.set("data-version", toVersion());
        }
    }

    private static Conversion convert(Object value, Function<ItemStack, ItemStack> regenerator) {
        if (value instanceof ItemStack item) {
            ItemStack updated = regenerator.apply(item);
            return updated == null ? new Conversion(value, false) : new Conversion(updated, true);
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            boolean changed = false;
            for (Object entry : list) {
                Conversion conversion = convert(entry, regenerator);
                converted.add(conversion.value());
                changed |= conversion.changed();
            }
            return new Conversion(changed ? converted : value, changed);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> converted = new LinkedHashMap<>();
            boolean changed = false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Conversion conversion = convert(entry.getValue(), regenerator);
                converted.put(entry.getKey(), conversion.value());
                changed |= conversion.changed();
            }
            return new Conversion(changed ? converted : value, changed);
        }
        return new Conversion(value, false);
    }

    private ItemStack regenerate(ItemStack item) {
        String id = MythicInstanceProvider.getInstance().getID(item);
        if (id == null || id.isBlank()) return null;
        return MythicInstanceProvider.getInstance().getMythicItem(id, item.getAmount());
    }

    static Object regenerateSavedItems(Object value, Function<ItemStack, ItemStack> regenerator) {
        return convert(value, regenerator).value();
    }

    private record Conversion(Object value, boolean changed) {}
}
