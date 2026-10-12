package marsh.town.brb.mixins;

import marsh.town.brb.BetterRecipeBook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ImageButton.class)
public abstract class RemoveBookButton {
    @Shadow @Final protected WidgetSprites sprites;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void brb$hideDisabledRecipeToggle(CallbackInfo ci) {
        if (BetterRecipeBook.config != null && sprites == RecipeBookComponent.RECIPE_BUTTON_SPRITES)
            ((ImageButton) (Object) this).visible = BetterRecipeBook.config.enableBook;
    }

    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void brb$hideToggleContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (BetterRecipeBook.config != null && !BetterRecipeBook.config.enableBook
                && sprites == RecipeBookComponent.RECIPE_BUTTON_SPRITES) {
            ((ImageButton) (Object) this).visible = false;
            ci.cancel();
        }
    }
}
