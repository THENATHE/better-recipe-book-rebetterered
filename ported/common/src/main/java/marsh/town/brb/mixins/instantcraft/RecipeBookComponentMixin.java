package marsh.town.brb.mixins.instantcraft;

import com.mojang.blaze3d.platform.InputConstants;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.mixins.accessors.RecipeBookPageAccessor;
import marsh.town.brb.mixins.accessors.RecipeBookComponentAccessor;
import marsh.town.brb.recipegroups.GroupedRecipePage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.recipebook.FurnaceRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow @Final private RecipeBookPage recipeBookPage;
    @Shadow public abstract boolean isVisible();
    @Unique private Button betterRecipeBook$instantCraftButton;

    @Unique
    private boolean betterRecipeBook$showButton() {
        return BetterRecipeBook.config.instantCraft.showButton && !((Object) this instanceof FurnaceRecipeBookComponent);
    }

    @Unique
    private Component betterRecipeBook$toggleText() {
        return Component.translatable(BetterRecipeBook.instantCraftingManager.isEnabled() ? "brb.gui.instantCraft.on" : "brb.gui.instantCraft.off");
    }

    @Inject(method = "initVisuals", at = @At("RETURN"))
    private void initButton(CallbackInfo ci) {
        betterRecipeBook$instantCraftButton = null;
        if (!betterRecipeBook$showButton()) return;
        var book = (RecipeBookComponentAccessor) this;
        int x = book.brb$getXOrigin();
        int y = book.brb$getYOrigin();
        betterRecipeBook$instantCraftButton = Button.builder(Component.literal("R"), button -> {
            BetterRecipeBook.instantCraftingManager.toggleEnabled();
            ((RecipeBookPageAccessor) recipeBookPage).getOverlay().setVisible(false);
            button.setTooltip(Tooltip.create(betterRecipeBook$toggleText()));
        }).bounds(x + 110, y + 137, 26, 18).build();
        betterRecipeBook$instantCraftButton.setTooltip(Tooltip.create(betterRecipeBook$toggleText()));
        BetterRecipeBook.instantCraftingManager.lastInstantCraftButton = betterRecipeBook$instantCraftButton;
    }

    @Unique
    private void betterRecipeBook$positionButton() {
        if (betterRecipeBook$instantCraftButton == null) return;
        var book = (RecipeBookComponentAccessor) this;
        betterRecipeBook$instantCraftButton.setPosition(book.brb$getXOrigin() + 110, book.brb$getYOrigin() + 137);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderButton(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        betterRecipeBook$positionButton();
        if (isVisible() && betterRecipeBook$showButton() && betterRecipeBook$instantCraftButton != null) {
            betterRecipeBook$instantCraftButton.setMessage(Component.literal(BetterRecipeBook.instantCraftingManager.isEnabled() ? "R+" : "R-"));
            betterRecipeBook$instantCraftButton.extractRenderState(gui, mouseX, mouseY, delta);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!isVisible()) return;
        betterRecipeBook$positionButton();
        if (betterRecipeBook$showButton() && betterRecipeBook$instantCraftButton != null
                && betterRecipeBook$instantCraftButton.mouseClicked(event, doubleClick)) {
            cir.setReturnValue(true);
            return;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT || !BetterRecipeBook.instantCraftingManager.isEnabled()
                || (Object) this instanceof FurnaceRecipeBookComponent) return;
        if (((RecipeBookPageAccessor) recipeBookPage).getOverlay().isVisible()) return;
        GroupedRecipePage page = (GroupedRecipePage) recipeBookPage;
        if (page.brb$isGroupHeaderAt(event.x(), event.y())) return;
        RecipeDisplayId recipe = page.brb$recipeAt(event.x(), event.y());
        if (recipe == null) return;
        RecipeCollection collection = page.brb$collectionAt(event.x(), event.y());
        if (collection != null && (collection.isCraftable(recipe) || BetterRecipeBook.instantCraftingManager.canCraftPrepared(recipe))) {
            BetterRecipeBook.instantCraftingManager.requestCraft(recipe, event.hasShiftDown());
        }
        // An unavailable recipe's right-click never picks up the previous crafting result.
        cir.setReturnValue(true);
    }
}
