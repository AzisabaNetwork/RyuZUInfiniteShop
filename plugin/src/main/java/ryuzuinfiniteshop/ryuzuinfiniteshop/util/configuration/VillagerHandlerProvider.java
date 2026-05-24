package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import com.github.ryuzu.searchableinfiniteshop.api.IVillagerHandler;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;

public class VillagerHandlerProvider {
    private static IVillagerHandler handler;

    public static IVillagerHandler getHandler() {
        if (handler != null) return handler;
        try {
            if (RyuZUInfiniteShop.VERSION < 14) {
                handler = (IVillagerHandler) Class.forName("com.github.ryuzu.searchableinfiniteshop.v16older.VillagerHandlerV8Old").getDeclaredConstructor().newInstance();
            } else if (RyuZUInfiniteShop.VERSION < 21) {
                handler = (IVillagerHandler) Class.forName("com.github.ryuzu.searchableinfiniteshop.v16newer.VillagerHandlerV14New").getDeclaredConstructor().newInstance();
            } else {
                handler = (IVillagerHandler) Class.forName("com.github.ryuzu.searchableinfiniteshop.v21newer.VillagerHandlerV14New").getDeclaredConstructor().newInstance();
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to load VillagerHandler for version " + RyuZUInfiniteShop.VERSION, e);
        }
        return handler;
    }
}
