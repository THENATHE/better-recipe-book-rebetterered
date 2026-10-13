package marsh.town.brb.mixins.settings;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.interfaces.ISettingsButton;
import marsh.town.brb.mixins.accessors.RecipeBookComponentAccessor;
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
    @Shadow public abstract boolean isVisible();
    @Unique private ImageButton brb$settingsButton;

    @Inject(method = "initVisuals", at = @At("RETURN"))
    private void brb$createSettings(CallbackInfo ci) {
        var book = (RecipeBookComponentAccessor) this;
        int x = book.brb$getXOrigin();
        int y = book.brb$getYOrigin();
        brb$settingsButton = BetterRecipeBook.config.settingsButton ? createSettingsButton(x, y) : null;
    }

    @Unique
    private void brb$positionSettings() {
        if (brb$settingsButton == null) return;
        var book = (RecipeBookComponentAccessor) this;
        brb$settingsButton.setPosition(book.brb$getXOrigin() + 11, book.brb$getYOrigin() + 137);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brb$settingsClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        brb$positionSettings();
        if (isVisible() && BetterRecipeBook.config.settingsButton && brb$settingsButton != null
                && brb$settingsButton.mouseClicked(event, doubleClick)) cir.setReturnValue(true);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void brb$renderSettings(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        brb$positionSettings();
        if (isVisible() && BetterRecipeBook.config.settingsButton && brb$settingsButton != null)
            brb$settingsButton.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
