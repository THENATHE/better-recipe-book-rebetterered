package marsh.town.brb.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Inventory transfers through ordinary server-validated container clicks. */
public class ClientInventoryUtil {

    public static boolean canStore(ItemStack stack, AbstractContainerMenu menu, Predicate<Integer> indexCheck) {
        return canStoreAll(List.of(stack), menu, indexCheck);
    }

    public static boolean canStoreAll(List<ItemStack> incoming, AbstractContainerMenu menu, Predicate<Integer> indexCheck) {
        List<Slot> slots = menu.slots.stream().filter(slot -> indexCheck.test(slot.index) && slot.isActive()).toList();
        List<ItemStack> stored = slots.stream().map(slot -> slot.getItem().copy()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (ItemStack stack : incoming) {
            int remaining = stack.getCount();
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                for (int i = 0; i < slots.size() && remaining > 0; i++) {
                    Slot slot = slots.get(i);
                    ItemStack existing = stored.get(i);
                    if (!slot.mayPlace(stack) || (pass == 0) == existing.isEmpty()) continue;
                    if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(stack, existing)) continue;
                    int capacity = Math.max(0, Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack)) - existing.getCount());
                    int transferred = Math.min(remaining, capacity);
                    if (transferred <= 0) continue;
                    if (existing.isEmpty()) stored.set(i, stack.copyWithCount(transferred));
                    else existing.grow(transferred);
                    remaining -= transferred;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    /** Returns false without dropping items when there is insufficient valid storage. */
    public static boolean storeItem(int fromSlot, Predicate<Integer> indexCheck) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) return false;
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (fromSlot >= 0) {
            if (!menu.isValidSlotIndex(fromSlot)) return false;
            Slot source = menu.getSlot(fromSlot);
            if (source.getItem().isEmpty()) return true;
            if (!menu.getCarried().isEmpty() || !source.mayPickup(minecraft.player)) return false;
            if (!canStore(source.getItem(), menu, i -> i != fromSlot && indexCheck.test(i))) return false;
            minecraft.gameMode.handleContainerInput(menu.containerId, fromSlot, 0, ContainerInput.PICKUP, minecraft.player);
        }
        if (menu.getCarried().isEmpty()) return true;
        if (!canStore(menu.getCarried(), menu, index -> index != fromSlot && indexCheck.test(index))) return false;

        List<Slot> slots = new ArrayList<>(menu.slots);
        slots.sort((a, b) -> Boolean.compare(a.getItem().isEmpty(), b.getItem().isEmpty()));
        for (Slot slot : slots) {
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) return true;
            if (slot.index == fromSlot || !indexCheck.test(slot.index) || !slot.isActive() || !slot.mayPlace(carried)) continue;
            ItemStack existing = slot.getItem();
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(carried, existing)) continue;
            if (existing.getCount() >= Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried))) continue;
            minecraft.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, minecraft.player);
        }
        return menu.getCarried().isEmpty();
    }

    /** Explicit user-requested drops only; storage never calls this method. */
    public static void dropItem(int slot, boolean wholeStack, boolean force) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) return;
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        ContainerInput input = ContainerInput.THROW;
        if (slot < 0) {
            if (!force && menu.getCarried().isEmpty()) return;
            slot = AbstractContainerMenu.SLOT_CLICKED_OUTSIDE;
            input = ContainerInput.PICKUP;
        } else if (!menu.isValidSlotIndex(slot) || (!force && menu.getSlot(slot).getItem().isEmpty())) {
            return;
        }
        int button = input == ContainerInput.THROW ? (wholeStack ? 1 : 0) : (wholeStack ? 0 : 1);
        minecraft.gameMode.handleContainerInput(menu.containerId, slot, button, input, minecraft.player);
    }
}
