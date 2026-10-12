package marsh.town.brb.mixins.ungroup;

import marsh.town.brb.BetterRecipeBook;
import net.minecraft.client.ClientRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientRecipeBook.class)
public class ClientRecipeBookMixin {
    @Inject(method = {"clear", "rebuildCollections"}, at = @At("HEAD"))
    private void brb$clearPinIdentityCache(CallbackInfo ci) {
        if (BetterRecipeBook.pinnedRecipeManager != null) BetterRecipeBook.pinnedRecipeManager.clearIdentityCache();
    }
}
