package marsh.town.brb.mixins.unlockrecipes;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.util.RecipePlacement;
import marsh.town.brb.util.RecipeUnlockUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void placeLocal(int container, RecipeDisplayId id, boolean maximum, CallbackInfo ci) {
        var manager = BetterRecipeBook.instantCraftingManager;
        if (!manager.isRequestingPlacement()) manager.cancelPending();
        if (id.index() >= 0) return;
        ci.cancel();
        var entry = RecipeUnlockUtil.find(id);
        if (entry == null) return;
        if (!RecipePlacement.place(entry, maximum)
                && Minecraft.getInstance().gui.screen() instanceof RecipeUpdateListener listener) {
            listener.fillGhostRecipe(entry.display());
        }
    }
}
