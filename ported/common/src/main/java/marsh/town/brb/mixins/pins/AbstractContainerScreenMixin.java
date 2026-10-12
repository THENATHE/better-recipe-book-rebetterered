package marsh.town.brb.mixins.pins;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.PinnedRecipeManager;
import marsh.town.brb.mixins.accessors.OverlayRecipeButtonAccessor;
import marsh.town.brb.mixins.accessors.OverlayRecipeComponentAccessor;
import marsh.town.brb.mixins.accessors.RecipeBookComponentAccessor;
import marsh.town.brb.mixins.accessors.RecipeBookPageAccessor;
import marsh.town.brb.recipegroups.RecipeTileCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen")
public abstract class AbstractContainerScreenMixin {
    @Shadow @Final private RecipeBookComponent<?> recipeBookComponent;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void brb$pinHoveredRecipe(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!BetterRecipeBook.config.enablePinning || !recipeBookComponent.isVisible()
                || !BetterRecipeBook.PIN_MAPPING.matches(event)) return;
        var accessor = (RecipeBookComponentAccessor) recipeBookComponent;
        if (accessor.getSearchBox() != null && accessor.getSearchBox().canConsumeInput()) return;
        var page = accessor.getRecipeBookPage();
        var pageAccessor = (RecipeBookPageAccessor) page;
        var overlay = pageAccessor.getOverlay();
        if (overlay.isVisible()) {
            var buttons = ((OverlayRecipeComponentAccessor) overlay).getRecipeButtons();
            net.minecraft.client.gui.components.AbstractWidget target = buttons.stream()
                    .filter(button -> button.isHovered()).findFirst().orElse(null);
            if (target == null) target = buttons.stream().filter(button -> button.isFocused()).findFirst().orElse(null);
            if (target != null) {
                PinnedRecipeManager.handlePinRecipe(recipeBookComponent, page, ((OverlayRecipeButtonAccessor) target).getRecipe());
                cir.setReturnValue(true);
            }
            return;
        }
        var buttons = pageAccessor.getButtons();
        RecipeButton target = buttons.stream().filter(button -> button.visible && button.isHovered()).findFirst()
                .orElseGet(() -> buttons.stream().filter(button -> button.visible && button.isFocused()).findFirst().orElse(null));
        if (target != null) {
            if (target.getCollection() instanceof RecipeTileCollection tile && tile.isGroupHeader()) return;
            PinnedRecipeManager.handlePinRecipe(recipeBookComponent, page, target.getCurrentRecipe());
            cir.setReturnValue(true);
        }
    }
}
