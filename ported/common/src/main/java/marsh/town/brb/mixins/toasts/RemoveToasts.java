package marsh.town.brb.mixins.toasts;

import marsh.town.brb.BetterRecipeBook;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeToast.class)
public class RemoveToasts {
    @Inject(method = "addOrUpdate", at = @At("HEAD"), cancellable = true)
    private static void brb$suppressUnlockToast(ToastManager manager, RecipeDisplay display, CallbackInfo ci) {
        if (BetterRecipeBook.config.newRecipes.unlockAll) ci.cancel();
    }
}
