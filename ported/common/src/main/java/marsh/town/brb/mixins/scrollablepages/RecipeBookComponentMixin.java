package marsh.town.brb.mixins.scrollablepages;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.recipegroups.RecipePageScroll;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements GuiEventListener {
    @Shadow @Final private RecipeBookPage recipeBookPage;
    @Shadow public abstract boolean isVisible();
    @Shadow private int width;
    @Shadow private int height;
    @Shadow private int xOffset;

    @Unique private boolean brb$overBook(double x, double y) {
        int left = (width - RecipeBookComponent.IMAGE_WIDTH) / 2 - xOffset;
        int top = (height - RecipeBookComponent.IMAGE_HEIGHT) / 2;
        return isVisible() && x >= left && x < left + RecipeBookComponent.IMAGE_WIDTH
                && y >= top && y < top + RecipeBookComponent.IMAGE_HEIGHT;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!BetterRecipeBook.config.scrolling.enableScrolling || !brb$overBook(x, y) || vertical == 0) return false;
        return ((RecipePageScroll) recipeBookPage).brb$scrollPages(vertical > 0 ? -1 : 1);
    }

    @Inject(method = "isMouseOver", at = @At("RETURN"), cancellable = true)
    private void brb$bookHitbox(double x, double y, CallbackInfoReturnable<Boolean> cir) {
        if (brb$overBook(x, y)) cir.setReturnValue(true);
    }
}
