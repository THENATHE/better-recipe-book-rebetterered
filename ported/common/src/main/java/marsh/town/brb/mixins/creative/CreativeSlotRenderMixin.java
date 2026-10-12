package marsh.town.brb.mixins.creative;

import marsh.town.brb.creative.CreativeGroupScreen;
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
            graphics.fill(slot.x, slot.y + 8, slot.x + 8, slot.y + 16, 0xEE193829);
            graphics.fill(slot.x + 1, slot.y + 11, slot.x + 7, slot.y + 13, 0xFFF0FFF4);
            if (!entry.expanded()) {
                graphics.fill(slot.x + 3, slot.y + 9, slot.x + 5, slot.y + 15, 0xFFF0FFF4);
            }
        } else if (entry.groupKey() != null) {
            graphics.fill(slot.x - 1, slot.y, slot.x, slot.y + 16, 0xFF609879);
        }
    }
}
