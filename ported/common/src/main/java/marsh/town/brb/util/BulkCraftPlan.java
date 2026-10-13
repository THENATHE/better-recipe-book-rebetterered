package marsh.town.brb.util;

import marsh.town.brb.interfaces.BulkCraftingMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Detached simulation of vanilla result transfers and crafting remainders. */
public final class BulkCraftPlan {
    public record Input(Slot slot, ItemStack remainder) { }
    public record State(List<ItemStack> items, boolean dropped) { }
    public record Result(List<State> states, int batches, boolean exhausted, boolean dropped) { }

    private BulkCraftPlan() { }

    public static Result plan(AbstractContainerMenu menu, List<Input> inputs, List<List<Slot>> outputGroups,
                              ItemStack output, long budget, boolean throwOutputs) {
        if (inputs.isEmpty() || output.isEmpty() || output.getCount() > output.getMaxStackSize()) return null;
        int batches = Integer.MAX_VALUE;
        for (Input input : inputs) {
            ItemStack ingredient = input.slot().getItem();
            if (ingredient.isEmpty()) return null;
            if (input.remainder().isEmpty() || !ItemStack.isSameItemSameComponents(ingredient, input.remainder())) {
                // Unequal depleting inputs can leave another recipe after this one ends.
                // Vanilla keeps shifting while that recipe produces the same item type.
                if (batches != Integer.MAX_VALUE && batches != ingredient.getCount()) return null;
                batches = ingredient.getCount();
            }
        }
        // A fully reusable grid would also make vanilla's shift-result loop unbounded.
        if (batches == Integer.MAX_VALUE || batches > 64 || batches > budget) return null;
        List<ItemStack> items = copy(menu.slots.stream().map(Slot::getItem).toList());
        List<State> states = new ArrayList<>();
        boolean dropped = false;
        for (int batch = 0; batch < batches; batch++) {
            boolean batchDropped = throwOutputs;
            if (!throwOutputs) {
                int remaining = output.getCount();
                for (List<Slot> group : outputGroups) remaining = moveOutput(items, group, output, remaining);
                if (remaining == output.getCount()) return new Result(List.copyOf(states), batch, false, dropped);
                batchDropped = remaining > 0;
            }
            for (Input input : inputs) {
                int index = input.slot().index;
                ItemStack ingredient = items.get(index);
                ingredient.shrink(1);
                ItemStack remainder = input.remainder().copy();
                if (remainder.isEmpty()) continue;
                if (ingredient.isEmpty()) items.set(index, remainder);
                else if (ItemStack.isSameItemSameComponents(ingredient, remainder)) {
                    remainder.grow(ingredient.getCount());
                    items.set(index, remainder);
                } else {
                    int stored = addRemainder(menu, items, remainder);
                    if (stored < 0) return null;
                    if (stored == 0) batchDropped = true;
                }
            }
            if (menu instanceof BulkCraftingMenu bulk) bulk.brb$updateBulkSnapshot(items);
            dropped |= batchDropped;
            states.add(new State(copy(items), batchDropped));
        }
        return new Result(List.copyOf(states), batches, true, dropped);
    }

    private static int moveOutput(List<ItemStack> items, List<Slot> slots, ItemStack output, int remaining) {
        if (output.isStackable()) {
            for (Slot slot : slots) {
                ItemStack stored = items.get(slot.index);
                if (stored.isEmpty() || !ItemStack.isSameItemSameComponents(stored, output)) continue;
                int moved = Math.min(remaining, Math.max(0, slot.getMaxStackSize(stored) - stored.getCount()));
                stored.grow(moved);
                remaining -= moved;
                if (remaining == 0) return 0;
            }
        }
        // One moveItemStackTo call places into at most one empty slot.
        for (Slot slot : slots) {
            if (!items.get(slot.index).isEmpty() || !slot.mayPlace(output)) continue;
            int moved = Math.min(remaining, slot.getMaxStackSize(output));
            if (moved > 0) items.set(slot.index, output.copyWithCount(moved));
            return remaining - moved;
        }
        return remaining;
    }

    private static int addRemainder(AbstractContainerMenu menu, List<ItemStack> items, ItemStack remainder) {
        Minecraft client = Minecraft.getInstance();
        Inventory inventory = client.player.getInventory();
        int selected = inventory.getSelectedSlot();
        List<Slot> playerSlots = menu.slots.stream().filter(slot -> slot.container == inventory
                && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 36).toList();
        Slot selectedSlot = playerSlots.stream().filter(slot -> slot.getContainerSlot() == selected).findFirst().orElse(null);
        Slot offhand = menu.slots.stream().filter(slot -> slot.container == inventory && slot.getContainerSlot() == 40).findFirst().orElse(null);
        if (selectedSlot != null) {
            mergeRemainder(inventory, items, selectedSlot, remainder);
            if (remainder.isEmpty()) return 1;
        }
        List<Slot> mergeOrder = new ArrayList<>();
        if (offhand != null) mergeOrder.add(offhand);
        mergeOrder.addAll(playerSlots.stream().sorted(java.util.Comparator.comparingInt(Slot::getContainerSlot)).toList());
        // A menu without its offhand slot cannot correlate changes to that stack. Refuse
        // a plan that would merge there rather than silently ignoring an authoritative change.
        ItemStack unseenOffhand = inventory.getItem(40);
        if (offhand == null && !unseenOffhand.isEmpty() && unseenOffhand.isStackable()
                && ItemStack.isSameItemSameComponents(unseenOffhand, remainder)
                && unseenOffhand.getCount() < unseenOffhand.getMaxStackSize()) return -1;
        for (Slot slot : mergeOrder) {
            mergeRemainder(inventory, items, slot, remainder);
            if (remainder.isEmpty()) return 1;
        }
        for (Slot slot : playerSlots.stream().sorted(java.util.Comparator.comparingInt(Slot::getContainerSlot)).toList()) {
            if (!items.get(slot.index).isEmpty()) continue;
            int moved = Math.min(remainder.getCount(), inventory.getMaxStackSize(remainder));
            if (moved > 0) items.set(slot.index, remainder.copyWithCount(moved));
            remainder.shrink(moved);
            if (remainder.isEmpty()) return 1;
        }
        // Creative inventory insertion discards an unstoreable remainder rather than dropping it.
        if (client.player.hasInfiniteMaterials()) {
            remainder.setCount(0);
            return 1;
        }
        return 0;
    }

    private static void mergeRemainder(Inventory inventory, List<ItemStack> items, Slot slot, ItemStack remainder) {
        ItemStack stored = items.get(slot.index);
        if (stored.isEmpty() || !stored.isStackable() || !ItemStack.isSameItemSameComponents(stored, remainder)) return;
        int moved = Math.min(remainder.getCount(), Math.max(0, inventory.getMaxStackSize(stored) - stored.getCount()));
        stored.grow(moved);
        remainder.shrink(moved);
    }

    public static Boolean pickupWouldDrop(AbstractContainerMenu menu, List<Input> inputs, List<Slot> storage, ItemStack output) {
        List<ItemStack> items = copy(menu.slots.stream().map(Slot::getItem).toList());
        boolean dropped = false;
        for (Input input : inputs) {
            ItemStack ingredient = items.get(input.slot().index);
            ingredient.shrink(1);
            ItemStack remainder = input.remainder().copy();
            if (remainder.isEmpty()) continue;
            if (ingredient.isEmpty()) items.set(input.slot().index, remainder);
            else if (ItemStack.isSameItemSameComponents(ingredient, remainder)) {
                remainder.grow(ingredient.getCount());
                items.set(input.slot().index, remainder);
            } else {
                int stored = addRemainder(menu, items, remainder);
                if (stored < 0) return null;
                dropped |= stored == 0;
            }
        }
        int remaining = output.getCount();
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (Slot slot : storage) {
                ItemStack stored = items.get(slot.index);
                if (!slot.isActive() || !slot.mayPlace(output) || (pass == 0) == stored.isEmpty()
                        || !stored.isEmpty() && !ItemStack.isSameItemSameComponents(stored, output)) continue;
                int moved = Math.min(remaining, Math.max(0, slot.getMaxStackSize(output) - stored.getCount()));
                if (stored.isEmpty()) items.set(slot.index, output.copyWithCount(moved));
                else stored.grow(moved);
                remaining -= moved;
                if (remaining == 0) break;
            }
        }
        return dropped || remaining > 0;
    }

    private static List<ItemStack> copy(List<ItemStack> items) {
        return items.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
}
