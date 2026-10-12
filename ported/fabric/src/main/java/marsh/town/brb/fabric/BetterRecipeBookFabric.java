package marsh.town.brb.fabric;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.util.RecipeUnlockUtil;
import marsh.town.brb.util.ClientRecipeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import marsh.town.brb.workstation.WorkstationRecipes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class BetterRecipeBookFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BetterRecipeBook.init();
        ServerLifecycleEvents.SERVER_STARTED.register(ClientRecipeRegistry::snapshot);
        ServerLifecycleEvents.SERVER_STOPPED.register(ClientRecipeRegistry::serverStopped);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) ClientRecipeRegistry.snapshot(server);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BetterRecipeBook.instantCraftingManager.tick();
            RecipeUnlockUtil.unlockRecipesIfRequired();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BetterRecipeBook.instantCraftingManager.cancelPending();
            RecipeUnlockUtil.clear();
            ClientRecipeRegistry.clear();
            WorkstationRecipes.clear();
        });
    }
}
