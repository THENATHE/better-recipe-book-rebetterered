package marsh.town.brb.mixins;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.util.BRBTextures;
import marsh.town.brb.workstation.WorkstationBookPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BrewingStandScreen.class)
public abstract class BrewingStandScreenMixin extends AbstractContainerScreen<BrewingStandMenu> {
    @Unique private WorkstationBookPanel brb$book;
    @Unique private ImageButton brb$toggle;

    protected BrewingStandScreenMixin(BrewingStandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void brb$initializeBook(CallbackInfo ci) {
        brb$book = null;
        brb$toggle = null;
        if (!BetterRecipeBook.config.enableBook) return;
        brb$book = new WorkstationBookPanel(menu, ignored -> {});
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
                ? (width + WorkstationBookPanel.WIDTH) / 2 + 2 : leftPos + 135;
        brb$toggle.setPosition(buttonX, topPos + 32);
    }

    @ModifyVariable(method = "extractBackground", at = @At("STORE"), index = 5)
    private int brb$alignBrewingBackground(int vanillaLeft) {
        return leftPos;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (brb$book != null) brb$book.tick();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (brb$isNarrowBookOpen()) {
            hoveredSlot = null;
            extractBackground(graphics, mouseX, mouseY, delta);
            brb$toggle.extractRenderState(graphics, mouseX, mouseY, delta);
        } else {
            super.extractContents(graphics, mouseX, mouseY, delta);
        }
        if (brb$book != null) {
            if (!brb$isNarrowBookOpen()) brb$book.renderGhost(graphics, leftPos, topPos, mouseX, mouseY);
            graphics.nextStratum();
            brb$book.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        graphics.nextStratum();
        extractCarriedItem(graphics, mouseX, mouseY);
        extractTooltip(graphics, mouseX, mouseY);
    }

    @Unique
    private boolean brb$isNarrowBookOpen() {
        return width < 379 && brb$book != null && brb$book.isVisible();
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
        if (event.isEscape()) return super.keyPressed(event);
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
}
