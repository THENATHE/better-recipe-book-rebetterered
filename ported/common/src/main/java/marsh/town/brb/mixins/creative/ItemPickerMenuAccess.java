package marsh.town.brb.mixins.creative;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public interface ItemPickerMenuAccess {
    @Invoker("getRowIndexForScroll")
    int brb$rowForScroll(float scroll);

    @Invoker("getScrollForRowIndex")
    float brb$scrollForRow(int row);
}
