package marsh.town.brb.mixins.scrollablepages;

import marsh.town.brb.BetterRecipeBook;
import com.mojang.blaze3d.platform.InputConstants;
import marsh.town.brb.recipegroups.RecipePageScroll;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageMixin implements RecipePageScroll {
    @Shadow private int currentPage;
    @Shadow private int totalPages;
    @Shadow private ImageButton forwardButton;
    @Shadow private ImageButton backButton;
    @Shadow @Final private OverlayRecipeComponent overlay;
    @Shadow private RecipeDisplayId lastClickedRecipe;
    @Shadow private RecipeCollection lastClickedRecipeCollection;
    @Shadow protected abstract void updateButtonsForPage();

    @Override
    public boolean brb$scrollPages(int steps) {
        if (totalPages <= 1 || steps == 0 || overlay.isVisible()) return false;
        int requested = currentPage + steps;
        int destination = BetterRecipeBook.config.scrolling.scrollAround
                ? Math.floorMod(requested, totalPages) : Math.clamp(requested, 0, totalPages - 1);
        if (destination != currentPage) {
            currentPage = destination;
            updateButtonsForPage();
        }
        return true;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brb$handlePageArrows(MouseButtonEvent event, int x, int y, int width, int height, boolean doubleClick,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (overlay.isVisible() || event.button() != InputConstants.MOUSE_BUTTON_LEFT || totalPages <= 1) return;
        int steps = forwardButton.visible && forwardButton.isMouseOver(event.x(), event.y()) ? 1
                : backButton.visible && backButton.isMouseOver(event.x(), event.y()) ? -1 : 0;
        if (steps == 0) return;
        lastClickedRecipe = null;
        lastClickedRecipeCollection = null;
        net.minecraft.client.gui.components.AbstractWidget.playButtonClickSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
        brb$scrollPages(steps);
        cir.setReturnValue(true);
    }

    @Inject(method = "updateArrowButtons", at = @At("RETURN"))
    private void brb$wrapArrowVisibility(CallbackInfo ci) {
        if (forwardButton != null && backButton != null && BetterRecipeBook.config.scrolling.scrollAround && totalPages > 1) {
            forwardButton.visible = true;
            backButton.visible = true;
        }
    }
}
