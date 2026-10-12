package marsh.town.brb.mixins.unlockrecipes;

import marsh.town.brb.BetterRecipeBook;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {
    @Shadow public abstract RecipeDisplayId getCurrentRecipe();

    @Inject(method = "getTooltipText", at = @At("RETURN"), cancellable = true)
    private void brb$lockedTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        var recipe = getCurrentRecipe();
        if (!BetterRecipeBook.config.newRecipes.unlockAll || recipe == null || recipe.index() >= 0) return;
        var tooltip = new ArrayList<>(cir.getReturnValue());
        var player = net.minecraft.client.Minecraft.getInstance().player;
        String key = player != null && player.containerMenu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu
                ? "brb.gui.furnace.lockedRecipe" : "brb.gui.crafting.lockedRecipe";
        tooltip.add(0, Component.translatable(key)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        cir.setReturnValue(tooltip);
    }
}
