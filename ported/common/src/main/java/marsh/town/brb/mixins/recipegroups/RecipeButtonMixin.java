package marsh.town.brb.mixins.recipegroups;

import marsh.town.brb.recipegroups.RecipeTileCollection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Inject(method = "getDisplayStack", at = @At("HEAD"), cancellable = true)
    private void brb$fixedResult(CallbackInfoReturnable<ItemStack> cir) {
        if (!(getCollection() instanceof RecipeTileCollection tile)) return;
        var level = Minecraft.getInstance().level;
        if (level != null)
            cir.setReturnValue(tile.getRecipes().getFirst().display().result().resolveForFirstStack(SlotDisplayContext.fromLevel(level)));
    }

    @Inject(method = "extractWidgetRenderState", at = @At("RETURN"))
    private void brb$groupIndicator(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (getCollection() instanceof RecipeTileCollection tile && tile.isGroupHeader()) {
            var font = Minecraft.getInstance().font;
            String label = tile.isExpanded() ? "−" : "+";
            int size = 9;
            int left = getRight() - size - 3;
            int top = getBottom() - size - 3;
            graphics.fill(left, top, left + size, top + size, 0xEE172331);
            graphics.text(font, label, left + (size - font.width(label) + 1) / 2,
                    top + (size - font.lineHeight) / 2, 0xFFFFFFFF, false);
        }
    }

    @Inject(method = "getTooltipText", at = @At("RETURN"), cancellable = true)
    private void brb$groupTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        if (getCollection() instanceof RecipeTileCollection tile && tile.isGroupHeader()) {
            List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
            tooltip.add(Component.translatable(tile.isExpanded() ? "brb.gui.group.collapse" : "brb.gui.group.expand"));
            cir.setReturnValue(tooltip);
        }
    }
}
