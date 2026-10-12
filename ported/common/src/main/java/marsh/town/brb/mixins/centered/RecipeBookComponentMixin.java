package marsh.town.brb.mixins.centered;

import marsh.town.brb.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow private int xOffset;
    @Shadow private boolean widthTooNarrow;

    @Inject(method = "initVisuals", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;xOffset:I",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void brb$centerBook(CallbackInfo ci) {
        if (BetterRecipeBook.config.keepCentered && !widthTooNarrow) xOffset = 162;
    }

    @Inject(method = "updateScreenPosition", at = @At("RETURN"), cancellable = true)
    private void brb$centerContainer(int width, int backgroundWidth, CallbackInfoReturnable<Integer> cir) {
        if (BetterRecipeBook.config.keepCentered) cir.setReturnValue((width - backgroundWidth) / 2);
    }

    @Inject(method = "isOffsetNextToMainGUI", at = @At("RETURN"), cancellable = true)
    private void brb$wideCenteredBook(CallbackInfoReturnable<Boolean> cir) {
        if (xOffset == 162) cir.setReturnValue(true);
    }
}
