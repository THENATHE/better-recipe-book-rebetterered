package marsh.town.brb.mixins.pins;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.interfaces.PinnedRecipeBook;
import marsh.town.brb.mixins.accessors.RecipeBookComponentAccessor;
import marsh.town.brb.recipegroups.RecipeDisplaySearch;
import marsh.town.brb.recipegroups.RecipeTileCollection;
import marsh.town.brb.util.BRBTextures;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements PinnedRecipeBook {
    @Shadow @Final private List<RecipeBookTabButton> tabButtons;
    @Shadow @Final private List<RecipeBookComponent.TabInfo> tabInfos;
    @Shadow @Final protected RecipeBookMenu menu;
    @Shadow private RecipeBookTabButton selectedTab;
    @Shadow public abstract boolean isVisible();
    @Unique private ImageButton brb$pinnedTab;
    @Unique private boolean brb$pinnedSelected;

    @Inject(method = "initVisuals", at = @At("RETURN"))
    private void brb$createPinnedTab(CallbackInfo ci) {
        if (menu.getRecipeBookType() != RecipeBookType.CRAFTING) return;
        brb$pinnedTab = new ImageButton(0, 0, 35, 27, BRBTextures.RECIPE_BOOK_TAB_SPRITES,
                ignored -> brb$selectPinnedTab(), Component.translatable("brb.gui.workstation.pins")) {
            @Override
            public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                int left = getX() - (brb$pinnedSelected ? 2 : 0);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprites.get(true, brb$pinnedSelected), left, getY(), width, height);
                graphics.fakeItem(new ItemStack(Items.KNOWLEDGE_BOOK), left + 9, getY() + 5);
                graphics.text(Minecraft.getInstance().font, "★", left + 6, getY() + 3, 0xFFFFD66A, false);
                if (isFocused()) graphics.outline(left + 2, getY() + 2, width - 4, height - 4, 0xFFFFFFFF);
            }
        };
        brb$pinnedTab.setTooltip(Tooltip.create(brb$pinnedTab.getMessage()));
        if (brb$isPinnedTabSelected() && selectedTab != null) selectedTab.unselect();
        brb$positionPinnedTab();
    }

    @Unique
    private void brb$selectPinnedTab() {
        brb$pinnedSelected = true;
        if (selectedTab != null) selectedTab.unselect();
        var accessor = (RecipeBookComponentAccessor) this;
        accessor.getSearchBox().setFocused(false);
        accessor.updateCollectionsInvoker(true, accessor.brb$isFiltering());
    }

    @Unique
    private void brb$positionPinnedTab() {
        if (brb$pinnedTab == null) return;
        var accessor = (RecipeBookComponentAccessor) this;
        long visibleTabs = tabButtons.stream().filter(tab -> tab.visible).count();
        brb$pinnedTab.setPosition(accessor.brb$getXOrigin() - 30,
                accessor.brb$getYOrigin() + 3 + 27 * (int) visibleTabs);
        brb$pinnedTab.visible = isVisible() && BetterRecipeBook.config.enablePinning;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void brb$checkPinningEnabled(CallbackInfo ci) {
        if (brb$pinnedSelected && !BetterRecipeBook.config.enablePinning) {
            brb$pinnedSelected = false;
            if (selectedTab != null) selectedTab.select();
            if (isVisible()) {
                var accessor = (RecipeBookComponentAccessor) this;
                accessor.updateCollectionsInvoker(true, accessor.brb$isFiltering());
            }
        }
        brb$positionPinnedTab();
    }

    @Inject(method = "onTabButtonPress", at = @At("HEAD"), cancellable = true)
    private void brb$returnToVanillaTab(Button button, CallbackInfo ci) {
        if (!brb$pinnedSelected) return;
        brb$pinnedSelected = false;
        if (button == selectedTab) {
            selectedTab.select();
            var accessor = (RecipeBookComponentAccessor) this;
            accessor.updateCollectionsInvoker(true, accessor.brb$isFiltering());
            ci.cancel();
        }
    }

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/ClientRecipeBook;getCollection(Lnet/minecraft/world/item/crafting/ExtendedRecipeBookCategory;)Ljava/util/List;"))
    private List<RecipeCollection> brb$allCraftingCollections(ClientRecipeBook book, ExtendedRecipeBookCategory category) {
        if (!brb$isPinnedTabSelected()) return book.getCollection(category);
        var collections = new LinkedHashSet<RecipeCollection>();
        for (var tab : tabInfos) collections.addAll(book.getCollection(tab.category()));
        return List.copyOf(collections);
    }

    @ModifyArg(method = "updateCollections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;ZZ)V"), index = 0)
    private List<RecipeCollection> brb$sortPins(List<RecipeCollection> collections) {
        if (brb$isPinnedTabSelected()) {
            var client = Minecraft.getInstance();
            var context = SlotDisplayContext.fromLevel(client.level);
            var accessor = (RecipeBookComponentAccessor) this;
            String search = accessor.getSearchBox().getValue();
            var status = accessor.brb$isFiltering() ? RecipeCollection.CraftableStatus.CRAFTABLE : RecipeCollection.CraftableStatus.ANY;
            List<RecipeCollection> pins = new ArrayList<>();
            for (RecipeCollection source : collections) {
                for (var entry : source.getSelectedRecipes(status)) {
                    if (BetterRecipeBook.pinnedRecipeManager.has(entry) && RecipeDisplaySearch.matches(entry, search, context))
                        pins.add(new RecipeTileCollection(source, entry, false, false, ""));
                }
            }
            return pins;
        }
        List<RecipeCollection> sorted = new ArrayList<>(collections);
        if (BetterRecipeBook.config.enablePinning)
            sorted.sort(Comparator.comparing(collection -> !BetterRecipeBook.pinnedRecipeManager.has(collection)));
        return sorted;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brb$clickPinnedTab(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        brb$positionPinnedTab();
        if (brb$pinnedTab != null && isVisible() && !Minecraft.getInstance().player.isSpectator()) {
            if (brb$pinnedTab.mouseClicked(event, doubleClick)) cir.setReturnValue(true);
        }
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void brb$keepTabInside(double mouseX, double mouseY, int left, int top, int width, int height,
                                  CallbackInfoReturnable<Boolean> cir) {
        brb$positionPinnedTab();
        if (brb$pinnedTab != null && brb$pinnedTab.visible && brb$pinnedTab.isMouseOver(mouseX, mouseY))
            cir.setReturnValue(false);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void brb$renderPinnedTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        brb$positionPinnedTab();
        if (brb$pinnedTab != null && brb$pinnedTab.visible) brb$pinnedTab.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Inject(method = "updateNarration", at = @At("TAIL"))
    private void brb$narratePinnedTab(NarrationElementOutput output, CallbackInfo ci) {
        brb$positionPinnedTab();
        if (brb$pinnedTab != null && brb$pinnedTab.visible && brb$pinnedTab.isHoveredOrFocused())
            brb$pinnedTab.updateNarration(output.nest());
    }

    @Override
    public boolean brb$isPinnedTabSelected() {
        return brb$pinnedSelected && BetterRecipeBook.config.enablePinning;
    }

    @Override
    public ImageButton brb$getPinnedTab() {
        brb$positionPinnedTab();
        return brb$pinnedTab;
    }
}
