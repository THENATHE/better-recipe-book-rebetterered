package marsh.town.brb.mixins.creative;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.config.CreativeDisplayMode;
import marsh.town.brb.creative.CreativeGroupModel;
import marsh.town.brb.creative.CreativeGroupScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryMixin
        extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu>
        implements CreativeGroupScreen {
    @Shadow private static CreativeModeTab selectedTab;
    @Shadow private float scrollOffs;
    @Shadow private EditBox searchBox;

    @Unique private CreativeGroupModel brb$groups;
    @Unique private int brb$refreshDepth;
    @Unique private int brb$groupClickButton = -1;

    protected CreativeInventoryMixin(CreativeModeInventoryScreen.ItemPickerMenu menu,
                                     net.minecraft.world.entity.player.Inventory inventory,
                                     Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = {"selectTab", "refreshSearchResults", "refreshCurrentTabContents"}, at = @At("HEAD"))
    private void brb$beginVanillaRefresh(CallbackInfo ci) {
        brb$refreshDepth++;
    }

    @Inject(method = {"selectTab", "refreshSearchResults", "refreshCurrentTabContents"}, at = @At("RETURN"))
    private void brb$groupRefreshedItems(CallbackInfo ci) {
        if (--brb$refreshDepth != 0) {
            return;
        }
        if (brb$groups == null) {
            brb$groups = new CreativeGroupModel();
        }
        boolean enabled = BetterRecipeBook.config != null
                && BetterRecipeBook.config.creativeDisplayMode == CreativeDisplayMode.EXPANDABLE
                && selectedTab.getType() != CreativeModeTab.Type.INVENTORY
                && selectedTab.getType() != CreativeModeTab.Type.HOTBAR
                && (selectedTab.getType() != CreativeModeTab.Type.SEARCH
                    || searchBox == null || searchBox.getValue().isEmpty());
        int topRow = ((ItemPickerMenuAccess) menu).brb$rowForScroll(scrollOffs);
        brb$groups.setSource(new ArrayList<>(menu.items), enabled);
        brb$applyEntries(topRow);
    }

    @Unique
    private void brb$applyEntries(int topRow) {
        menu.items.clear();
        for (var entry : brb$groups.entries()) {
            menu.items.add(entry.stack());
        }
        int lastTopRow = Math.max(0, (menu.items.size() + 8) / 9 - 5);
        scrollOffs = lastTopRow == 0 ? 0.0F
                : ((ItemPickerMenuAccess) menu).brb$scrollForRow(Math.min(topRow, lastTopRow));
        menu.scrollTo(scrollOffs);
    }

    @Override
    public CreativeGroupModel.Entry brb$creativeEntry(Slot slot) {
        if (brb$groups == null || slot == null || slot.index < 0 || slot.index >= 45
                || selectedTab.getType() == CreativeModeTab.Type.INVENTORY
                || selectedTab.getType() == CreativeModeTab.Type.HOTBAR) {
            return null;
        }
        int topRow = ((ItemPickerMenuAccess) menu).brb$rowForScroll(scrollOffs);
        return brb$groups.entry(topRow * 9 + slot.index);
    }

    @Unique
    private boolean brb$toggleHeader(Slot slot) {
        var entry = brb$creativeEntry(slot);
        if (entry == null || !entry.header()) {
            return false;
        }
        int topRow = ((ItemPickerMenuAccess) menu).brb$rowForScroll(scrollOffs);
        brb$groups.toggle(topRow * 9 + slot.index);
        brb$applyEntries(topRow);
        return true;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brb$clickGroup(MouseButtonEvent event, boolean doubleClick,
                                CallbackInfoReturnable<Boolean> cir) {
        brb$groupClickButton = -1;
        for (Slot slot : menu.slots) {
            var entry = brb$creativeEntry(slot);
            if (entry == null || !entry.header()) {
                continue;
            }
            double x = event.x() - leftPos;
            double y = event.y() - topPos;
            if (x >= slot.x && x < slot.x + 16 && y >= slot.y && y < slot.y + 16) {
                brb$groupClickButton = event.button();
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    brb$toggleHeader(slot);
                }
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void brb$releaseGroup(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == brb$groupClickButton) {
            brb$groupClickButton = -1;
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void brb$dragGroup(MouseButtonEvent event, double deltaX, double deltaY,
                               CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == brb$groupClickButton) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void brb$protectGroupHeader(Slot slot, int slotId, int button,
                                       ContainerInput input, CallbackInfo ci) {
        var entry = brb$creativeEntry(slot);
        if (entry != null && entry.header()) {
            // Headers are UI controls, never a source of inventory or server actions.
            ci.cancel();
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void brb$keyboardGroup(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if ((searchBox == null || !searchBox.isFocused())
                && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE)
                && brb$toggleHeader(hoveredSlot)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("HEAD"), cancellable = true)
    private void brb$groupTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        var entry = brb$creativeEntry(hoveredSlot);
        if (entry != null && entry.header()) {
            cir.setReturnValue(List.of(Component.literal(entry.label()),
                    Component.translatable(entry.expanded() ? "brb.creative.collapse" : "brb.creative.expand",
                            entry.members())));
        }
    }
}
