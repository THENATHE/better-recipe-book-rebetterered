package marsh.town.brb.mixins.creative;

import marsh.town.brb.creative.CreativeGroupScreen;
import marsh.town.brb.util.GroupControlRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class CreativeSlotRenderMixin {
    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void brb$drawGroupControl(GuiGraphicsExtractor graphics, Slot slot,
                                      int mouseX, int mouseY, CallbackInfo ci) {
        if (!((Object) this instanceof CreativeGroupScreen screen)) {
            return;
        }
        var entry = screen.brb$creativeEntry(slot);
        if (entry == null) {
            return;
        }
        if (entry.header()) {
            GroupControlRenderer.renderHeader(graphics, slot.x, slot.y, 16, 16, 0, entry.expanded());
        } else if (entry.groupKey() != null) {
            GroupControlRenderer.renderChild(graphics, slot.x, slot.y, 16, 16);
            graphics.fill(slot.x - 1, slot.y, slot.x, slot.y + 16, 0xFF609879);
        }
    }
}
