package marsh.town.brb.mixins.pins;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.recipegroups.RecipeTileCollection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin extends AbstractWidget {
    protected RecipeButtonMixin(int x, int y, int width, int height, Component text) { super(x, y, width, height, text); }
    @Shadow public abstract RecipeCollection getCollection();
    @Shadow public abstract RecipeDisplayId getCurrentRecipe();

    @Unique private boolean brb$pinnedCurrent() {
        return getCollection().getRecipes().stream().anyMatch(entry -> entry.id().equals(getCurrentRecipe()) && BetterRecipeBook.pinnedRecipeManager.has(entry));
    }

    @Inject(method = "getTooltipText", at = @At("RETURN"), cancellable = true)
    private void brb$pinTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        if (!BetterRecipeBook.config.enablePinning) return;
        if (getCollection() instanceof RecipeTileCollection tile && tile.isGroupHeader()) return;
        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(brb$pinnedCurrent() ? "brb.gui.pin.remove" : "brb.gui.pin.add", BetterRecipeBook.PIN_MAPPING.getTranslatedKeyMessage()));
        cir.setReturnValue(tooltip);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("RETURN"))
    private void brb$drawPin(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
        if (BetterRecipeBook.config.enablePinning && brb$pinnedCurrent())
            graphics.text(Minecraft.getInstance().font, "★", getX() - 2, getY() - 2, 0xFFFFD66A, false);
    }
}
