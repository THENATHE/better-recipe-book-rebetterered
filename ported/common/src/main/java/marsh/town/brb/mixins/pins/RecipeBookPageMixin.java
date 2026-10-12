package marsh.town.brb.mixins.pins;

import marsh.town.brb.mixins.accessors.RecipeBookComponentAccessor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookPage.class)
public class RecipeBookPageMixin {
    @Shadow @Final private RecipeBookComponent<?> parent;
    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void brb$unfocusSearch(MouseButtonEvent event, int x, int y, int width, int height, boolean doubleClick,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            var search = ((RecipeBookComponentAccessor) parent).getSearchBox();
            if (search != null) search.setFocused(false);
        }
    }
}
