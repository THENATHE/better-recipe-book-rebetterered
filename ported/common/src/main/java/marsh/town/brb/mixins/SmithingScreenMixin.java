package marsh.town.brb.mixins;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.util.BRBTextures;
import marsh.town.brb.workstation.WorkstationBookPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingScreen.class)
public abstract class SmithingScreenMixin extends ItemCombinerScreen<SmithingMenu> {
    @Shadow protected abstract void updateArmorStandPreview(ItemStack stack);
    @Unique private WorkstationBookPanel brb$book;
    @Unique private ImageButton brb$toggle;

    protected SmithingScreenMixin(SmithingMenu menu, Inventory inventory, Component title, Identifier resource) {
        super(menu, inventory, title, resource);
    }

    @Inject(method = "subInit", at = @At("RETURN"))
    private void brb$initializeBook(CallbackInfo ci) {
        brb$book = null;
        brb$toggle = null;
        if (!BetterRecipeBook.config.enableBook) return;
        brb$book = new WorkstationBookPanel(menu, this::updateArmorStandPreview);
        addWidget(brb$book);
        brb$toggle = addRenderableWidget(new ImageButton(0, 0, 20, 18,
                BRBTextures.RECIPE_BOOK_BUTTON_SPRITES, ignored -> {
                    brb$book.toggleVisibility();
                    brb$positionBook();
                }, Component.translatable("gui.recipebook.toggleRecipes.all")));
        brb$positionBook();
    }

    @Unique
    private void brb$positionBook() {
        leftPos = brb$book.findLeftEdge(width, imageWidth);
        brb$book.position(width, leftPos, topPos);
        int buttonX = width < 379 && brb$book.isVisible()
                ? (width + WorkstationBookPanel.WIDTH) / 2 + 2 : leftPos + 147;
        brb$toggle.setPosition(buttonX, topPos + 8);
    }

    @Inject(method = "containerTick", at = @At("RETURN"))
    private void brb$tickBook(CallbackInfo ci) {
        if (brb$book != null) brb$book.tick();
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void brb$renderBook(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (brb$book != null) {
            brb$book.renderGhost(graphics, leftPos, topPos, mouseX, mouseY);
            graphics.nextStratum();
            brb$book.extractRenderState(graphics, mouseX, mouseY, delta);
            graphics.nextStratum();
            extractCarriedItem(graphics, mouseX, mouseY);
        }
    }

    @Unique
    private boolean brb$isNarrowBookOpen() {
        return width < 379 && brb$book != null && brb$book.isVisible();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void brb$renderNarrowBook(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!brb$isNarrowBookOpen()) return;
        hoveredSlot = null;
        extractBackground(graphics, mouseX, mouseY, delta);
        brb$toggle.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.nextStratum();
        brb$book.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.nextStratum();
        extractCarriedItem(graphics, mouseX, mouseY);
        ci.cancel();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (brb$book != null && brb$book.mouseClicked(event, doubleClick)) {
            setFocused(brb$book);
            return true;
        }
        if (brb$toggle != null && brb$toggle.mouseClicked(event, doubleClick)) {
            setFocused(brb$toggle);
            return true;
        }
        return brb$isNarrowBookOpen() || super.mouseClicked(event, doubleClick);
    }

    @Override
    protected boolean isHovering(int left, int top, int slotWidth, int slotHeight, double mouseX, double mouseY) {
        return !brb$isNarrowBookOpen() && super.isHovering(left, top, slotWidth, slotHeight, mouseX, mouseY);
    }

    @Override
    public boolean isInputCaptured() {
        return super.isInputCaptured() || brb$book != null && brb$book.capturesInput();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (brb$book != null && brb$book.isVisible() && event.key() == InputConstants.KEY_ESCAPE) {
            brb$book.toggleVisibility();
            brb$positionBook();
            return true;
        }
        if (brb$book != null && brb$book.keyPressed(event)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (brb$book != null && brb$book.mouseReleased(event)) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (brb$book != null && brb$book.mouseDragged(event, deltaX, deltaY)) return true;
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return (brb$book == null || !brb$book.isMouseOver(mouseX, mouseY))
                && super.hasClickedOutside(mouseX, mouseY, left, top);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
        if (brb$book != null && slot != null && slot.index < 4) brb$book.clearGhost();
        super.slotClicked(slot, slotId, button, input);
    }

    @Inject(method = "extractOnboardingTooltips", at = @At("HEAD"), cancellable = true)
    private void brb$hideOnboardingForGhost(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (brb$book != null && brb$book.hasGhost()) ci.cancel();
    }

    @Inject(method = "slotChanged", at = @At("HEAD"))
    private void brb$clearChangedGhost(AbstractContainerMenu menu, int slot, ItemStack stack, CallbackInfo ci) {
        if (brb$book != null && slot < 4) brb$book.clearGhost();
    }
}
