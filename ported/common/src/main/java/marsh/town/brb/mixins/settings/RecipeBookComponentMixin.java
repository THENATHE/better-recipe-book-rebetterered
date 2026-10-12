package marsh.town.brb.mixins.settings;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.interfaces.ISettingsButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements ISettingsButton {
    @Shadow private int height;
    @Shadow private int width;
    @Shadow private int xOffset;
    @Shadow public abstract boolean isVisible();
    @Unique private ImageButton brb$settingsButton;

    @Inject(method = "initVisuals", at = @At("RETURN"))
    private void brb$createSettings(CallbackInfo ci) {
        int x = (width - RecipeBookComponent.IMAGE_WIDTH) / 2 - xOffset;
        int y = (height - RecipeBookComponent.IMAGE_HEIGHT) / 2;
        brb$settingsButton = BetterRecipeBook.config.settingsButton ? createSettingsButton(x, y) : null;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brb$settingsClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (isVisible() && BetterRecipeBook.config.settingsButton && brb$settingsButton != null
                && brb$settingsButton.mouseClicked(event, doubleClick)) cir.setReturnValue(true);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void brb$renderSettings(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (isVisible() && BetterRecipeBook.config.settingsButton && brb$settingsButton != null)
            brb$settingsButton.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
