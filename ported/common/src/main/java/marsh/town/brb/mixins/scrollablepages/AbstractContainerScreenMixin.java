package marsh.town.brb.mixins.scrollablepages;

import marsh.town.brb.workstation.WorkstationBookPanel;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void brb$scrollRecipeBook(double x, double y, double horizontal, double vertical,
                                     CallbackInfoReturnable<Boolean> cir) {
        var screen = (AbstractContainerScreen<?>) (Object) this;
        for (var child : screen.children()) {
            if ((child instanceof RecipeBookComponent<?> || child instanceof WorkstationBookPanel)
                    && child.mouseScrolled(x, y, horizontal, vertical)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
