package marsh.town.brb.mixins.instantcraft;

import marsh.town.brb.BetterRecipeBook;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"))
    private void onPlaceRecipe(int containerId, RecipeDisplayId recipe, boolean maximum, CallbackInfo ci) {
        if (!BetterRecipeBook.instantCraftingManager.isRequestingPlacement()) BetterRecipeBook.instantCraftingManager.cancelPending();
    }

    @Inject(method = "handleContainerInput", at = @At("HEAD"))
    private void onContainerInput(int containerId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        BetterRecipeBook.instantCraftingManager.onUserContainerInput();
    }

    @Inject(method = "handleContainerInput", at = @At("TAIL"))
    private void afterContainerInput(int containerId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        BetterRecipeBook.instantCraftingManager.onContainerInputCompleted(slot);
    }
}
