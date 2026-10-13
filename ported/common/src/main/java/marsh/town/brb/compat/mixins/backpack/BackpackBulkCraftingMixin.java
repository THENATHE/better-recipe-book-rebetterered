package marsh.town.brb.compat.mixins.backpack;

import marsh.town.brb.interfaces.BulkCraftingMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Pseudo
@Mixin(targets = "com.thenathe.backpackcrafting.menu.BackpackCraftingMenu", remap = false)
public abstract class BackpackBulkCraftingMixin implements BulkCraftingMenu {
    @Shadow public abstract int getStorageStart();
    @Shadow public abstract int getInventoryStart();
    @Shadow public abstract ItemStack getBackpack();
    @Unique private int brb$backpackSlot = -1;

    @Override
    public boolean brb$canPlanBulk() {
        var menu = (AbstractContainerMenu) (Object) this;
        ItemStack backpack = getBackpack();
        var matches = menu.slots.stream().filter(slot ->
                slot.index >= getInventoryStart()
                        && slot.getItem().getCount() == backpack.getCount()
                        && ItemStack.isSameItemSameComponents(slot.getItem(), backpack)).toList();
        // Opening data contains a decoded copy. With indistinguishable bags we cannot
        // identify which inventory stack will receive the server's storage update.
        if (matches.size() > 1) return false;
        brb$backpackSlot = matches.isEmpty() ? -1 : matches.getFirst().index;
        return true;
    }

    @Override
    public List<List<Slot>> brb$getOutputStorageGroups() {
        var menu = (AbstractContainerMenu) (Object) this;
        var inventory = new ArrayList<>(menu.slots.subList(getInventoryStart(), menu.slots.size()));
        Collections.reverse(inventory);
        return List.of(inventory, List.copyOf(menu.slots.subList(getStorageStart(), getInventoryStart())));
    }

    @Override
    public void brb$updateBulkSnapshot(List<ItemStack> items) {
        ItemStack backpack = getBackpack();
        var contents = new ArrayList<>(backpack.getOrDefault(DataComponents.CONTAINER,
                ItemContainerContents.EMPTY).itemCopies().toList());
        int size = getInventoryStart() - getStorageStart();
        while (contents.size() < size) contents.add(ItemStack.EMPTY);
        for (int index = 0; index < size; index++) contents.set(index, items.get(getStorageStart() + index).copy());
        ItemContainerContents updated = ItemContainerContents.fromItems(contents);
        if (brb$backpackSlot >= 0) items.get(brb$backpackSlot).set(DataComponents.CONTAINER, updated);
    }
}
