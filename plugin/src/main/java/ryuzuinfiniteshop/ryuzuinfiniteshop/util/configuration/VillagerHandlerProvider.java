package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import com.github.ryuzu.searchableinfiniteshop.api.IVillagerHandler;
import com.github.ryuzu.searchableinfiniteshop.v21newer.VillagerHandlerV14New;

public class VillagerHandlerProvider {
    private static IVillagerHandler handler;

    public static IVillagerHandler getHandler() {
        if (handler != null) return handler;
        handler = new VillagerHandlerV14New();
        return handler;
    }
}
