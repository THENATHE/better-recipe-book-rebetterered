package marsh.town.brb.mixins.unlockrecipes;

import marsh.town.brb.util.RecipeUnlockUtil;
import marsh.town.brb.workstation.WorkstationRecipes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void clearLocalCatalogue(CallbackInfo ci) {
        marsh.town.brb.BetterRecipeBook.instantCraftingManager.cancelPending();
        marsh.town.brb.util.ClientRecipeRegistry.clear();
    }

    @Inject(method = {"handleRecipeBookAdd", "handleRecipeBookRemove", "handleUpdateRecipes"}, at = @At("TAIL"))
    private void updateLocalRecipes(CallbackInfo ci) {
        RecipeUnlockUtil.invalidate();
        WorkstationRecipes.clear();
    }
}
