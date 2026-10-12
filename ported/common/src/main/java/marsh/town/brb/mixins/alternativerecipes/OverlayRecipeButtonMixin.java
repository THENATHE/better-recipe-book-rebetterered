package marsh.town.brb.mixins.alternativerecipes;

import marsh.town.brb.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
public abstract class OverlayRecipeButtonMixin extends AbstractWidget {
    @Shadow @Final private RecipeDisplayId recipe;
    @Shadow @Final private boolean isCraftable;
    @Shadow @Final private OverlayRecipeComponent this$0;
    @Shadow protected abstract Identifier getSprite(boolean craftable);

    protected OverlayRecipeButtonMixin(int x, int y, int width, int height, Component text) {
        super(x, y, width, height, text);
    }

    @Unique private RecipeDisplayEntry brb$entry() {
        return this$0.getRecipeCollection().getRecipes().stream().filter(entry -> entry.id().equals(recipe)).findFirst().orElse(null);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void brb$showResultUntilHover(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        RecipeDisplayEntry entry = brb$entry();
        var minecraft = Minecraft.getInstance();
        if (entry == null || minecraft.level == null) return;
        if (BetterRecipeBook.config.alternativeRecipes.onHover && !isHoveredOrFocused()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, getSprite(isCraftable), getX(), getY(), width, height);
            graphics.item(entry.display().result().resolveForFirstStack(SlotDisplayContext.fromLevel(minecraft.level)), getX() + 4, getY() + 4);
            if (BetterRecipeBook.config.enablePinning && BetterRecipeBook.pinnedRecipeManager.has(entry))
                graphics.text(minecraft.font, "★", getX() - 2, getY() - 2, 0xFFFFD66A, false);
            ci.cancel();
        }
    }

    @Inject(method = "extractWidgetRenderState", at = @At("RETURN"))
    private void brb$drawOverlayPin(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        RecipeDisplayEntry entry = brb$entry();
        if (entry != null && BetterRecipeBook.config.enablePinning && BetterRecipeBook.pinnedRecipeManager.has(entry))
            graphics.text(Minecraft.getInstance().font, "★", getX() - 2, getY() - 2, 0xFFFFD66A, false);
    }
}
