package marsh.town.brb.workstation;

import marsh.town.brb.util.ClientInventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Plans inventory movements before any packet is sent; result slots are never cleared. */
public final class WorkstationTransfer {
    private WorkstationTransfer() {}

    private record Plan(List<WorkstationRecipe.Input> inputs, List<ItemStack> chosen,
                        int batches, List<Integer> clearSlots) {}

    public static boolean canPlace(AbstractContainerMenu menu, WorkstationRecipe recipe) {
        return plan(menu, recipe.inputs(), 1) != null;
    }

    public static ItemStack expectedResult(AbstractContainerMenu menu, WorkstationRecipe recipe) {
        Plan plan = plan(menu, recipe.inputs(), 1);
        return plan == null ? ItemStack.EMPTY : recipe.resultFor(plan.chosen());
    }

    public static boolean place(AbstractContainerMenu menu, WorkstationRecipe recipe, boolean maximum) {
        List<WorkstationRecipe.Input> inputs = recipe.inputs();
        if (menu instanceof BrewingStandMenu) {
            // One reagent serves all bottles. Shift fills compatible empty bottle positions.
            List<WorkstationRecipe.Input> bottles = new ArrayList<>(inputs);
            if (maximum) {
                WorkstationRecipe.Input bottle = inputs.getFirst();
                for (int slot = 1; slot < 3; slot++) {
                    if (menu.getSlot(slot).getItem().isEmpty() || bottle.accepts().test(menu.getSlot(slot).getItem())) {
                        var next = new WorkstationRecipe.Input(slot, bottle.accepts(), bottle.examples());
                        var candidate = new ArrayList<>(bottles);
                        candidate.add(next);
                        if (plan(menu, candidate, 1) != null) bottles.add(next);
                    }
                }
            }
            return placeInputs(menu, bottles, false);
        }
        return placeInputs(menu, inputs, maximum);
    }

    public static boolean placeInputs(AbstractContainerMenu menu, List<WorkstationRecipe.Input> inputs, boolean maximum) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null || client.player.containerMenu != menu) return false;
        Plan chosen = plan(menu, inputs, 1);
        if (chosen == null) return false;
        if (maximum) {
            for (int batches = 2; batches <= 64; batches++) {
                Plan candidate = plan(menu, inputs, batches);
                if (candidate == null) break;
                chosen = candidate;
            }
        }
        for (int slot : chosen.clearSlots()) {
            if (!ClientInventoryUtil.storeItem(slot, index -> isInventory(menu.getSlot(index)))) return false;
        }
        for (int index = 0; index < chosen.inputs().size(); index++) {
            WorkstationRecipe.Input input = chosen.inputs().get(index);
            Slot target = menu.getSlot(input.slot());
            ItemStack selected = chosen.chosen().get(index);
            int needed = chosen.batches() - target.getItem().getCount();
            for (Slot source : menu.slots) {
                if (needed <= 0) break;
                if (!isInventory(source) || !mayUseInventoryIngredient(menu, source.getItem())
                        || !ItemStack.isSameItemSameComponents(selected, source.getItem())) continue;
                int amount = Math.min(needed, source.getItem().getCount());
                client.gameMode.handleContainerInput(menu.containerId, source.index, 0, ContainerInput.PICKUP, client.player);
                for (int count = 0; count < amount; count++) {
                    client.gameMode.handleContainerInput(menu.containerId, target.index, 1, ContainerInput.PICKUP, client.player);
                }
                if (!menu.getCarried().isEmpty()) {
                    client.gameMode.handleContainerInput(menu.containerId, source.index, 0, ContainerInput.PICKUP, client.player);
                }
                if (!menu.getCarried().isEmpty()) return false;
                needed = chosen.batches() - target.getItem().getCount();
            }
            if (needed > 0) return false;
        }
        return true;
    }

    public static boolean isInventory(Slot slot) {
        return slot.container instanceof Inventory && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 36;
    }

    private static boolean mayUseInventoryIngredient(AbstractContainerMenu menu, ItemStack stack) {
        return !(menu instanceof AbstractCraftingMenu || menu instanceof AbstractFurnaceMenu)
                || Inventory.isUsableForCrafting(stack);
    }

    private static Plan plan(AbstractContainerMenu menu, List<WorkstationRecipe.Input> inputs, int batches) {
        Plan result = plan(menu, inputs, batches, false);
        if (result == null && menu instanceof AbstractCraftingMenu) {
            // A stack already in an accepted grid slot may need to supply several recipe inputs.
            // Preflight its return to inventory before redistributing it through ordinary clicks.
            result = plan(menu, inputs, batches, true);
        }
        return result;
    }

    private static Plan plan(AbstractContainerMenu menu, List<WorkstationRecipe.Input> inputs, int batches,
                             boolean redistributeGrid) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !menu.getCarried().isEmpty() || inputs.isEmpty()) return null;
        java.util.Set<Integer> sourceInputs = new java.util.HashSet<>();
        for (var input : inputs) sourceInputs.add(input.slot());
        if (menu instanceof net.minecraft.world.inventory.SmithingMenu) {
            sourceInputs.add(0);
            sourceInputs.add(1);
            sourceInputs.add(2);
        } else if (menu instanceof net.minecraft.world.inventory.AbstractCraftingMenu crafting) {
            for (Slot slot : crafting.getInputGridSlots()) sourceInputs.add(slot.index);
        }
        // Most catalogue variants have a missing ingredient. Reject those before copying inventory stacks.
        for (WorkstationRecipe.Input input : inputs) {
            Slot target = menu.getSlot(input.slot());
            int possible = 0;
            for (Slot slot : menu.slots) {
                boolean candidate = isInventory(slot) || sourceInputs.contains(slot.index);
                if (candidate && input.accepts().test(slot.getItem()) && target.mayPlace(slot.getItem())) {
                    possible += slot.getItem().getCount();
                    if (possible >= batches) break;
                }
            }
            if (possible < batches) return null;
        }
        List<Slot> inventorySlots = menu.slots.stream().filter(WorkstationTransfer::isInventory).toList();
        List<ItemStack> inventory = inventorySlots.stream().map(slot -> slot.getItem().copy()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<Integer> clear = new ArrayList<>();
        for (WorkstationRecipe.Input input : inputs) {
            Slot slot = menu.getSlot(input.slot());
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && (!input.accepts().test(stack)
                    || redistributeGrid && mayUseInventoryIngredient(menu, stack))) {
                if (!slot.mayPickup(client.player) || !simulateStore(inventorySlots, inventory, stack)) return null;
                clear.add(slot.index);
            }
        }
        // Clear other native inputs too, but never touch an output, fuel or unrelated player slot.
        if (menu instanceof net.minecraft.world.inventory.AbstractCraftingMenu crafting) {
            for (Slot slot : crafting.getInputGridSlots()) {
                if (inputs.stream().anyMatch(input -> input.slot() == slot.index) || slot.getItem().isEmpty()) continue;
                if (!slot.mayPickup(client.player) || !simulateStore(inventorySlots, inventory, slot.getItem())) return null;
                clear.add(slot.index);
            }
        }
        if (menu instanceof net.minecraft.world.inventory.SmithingMenu) {
            for (int index = 0; index < 3; index++) {
                Slot slot = menu.getSlot(index);
                if (inputs.stream().anyMatch(input -> input.slot() == slot.index) || slot.getItem().isEmpty()) continue;
                if (!slot.mayPickup(client.player) || !simulateStore(inventorySlots, inventory, slot.getItem())) return null;
                clear.add(slot.index);
            }
        }
        List<ItemStack> selected = new ArrayList<>(java.util.Collections.nCopies(inputs.size(), ItemStack.EMPTY));
        if (!allocate(menu, inputs, inventory, selected, clear, 0, batches)) return null;
        return new Plan(List.copyOf(inputs), List.copyOf(selected), batches, List.copyOf(clear));
    }

    private static boolean allocate(AbstractContainerMenu menu, List<WorkstationRecipe.Input> inputs,
                                    List<ItemStack> inventory, List<ItemStack> selected,
                                    List<Integer> clear, int index, int batches) {
        if (index == inputs.size()) return true;
        WorkstationRecipe.Input input = inputs.get(index);
        Slot target = menu.getSlot(input.slot());
        ItemStack existing = clear.contains(target.index) ? ItemStack.EMPTY : target.getItem();
        List<ItemStack> candidates = existing.isEmpty()
                ? inventory.stream().filter(stack -> mayUseInventoryIngredient(menu, stack)).toList() : List.of(existing);
        List<ItemStack> considered = new ArrayList<>();
        for (ItemStack candidate : candidates) {
            if (candidate.isEmpty() || !input.accepts().test(candidate) || !target.mayPlace(candidate)
                    || batches > target.getMaxStackSize(candidate)) continue;
            if (considered.stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, candidate))) continue;
            considered.add(candidate.copyWithCount(1));
            ItemStack choice = candidate.copyWithCount(1);
            int needed = Math.max(0, batches - existing.getCount());
            int available = 0;
            for (ItemStack stack : inventory) {
                if (mayUseInventoryIngredient(menu, stack) && ItemStack.isSameItemSameComponents(choice, stack)) available += stack.getCount();
            }
            if (available < needed) continue;
            List<ItemStack> before = inventory.stream().map(ItemStack::copy).toList();
            int remaining = needed;
            for (ItemStack stack : inventory) {
                if (!mayUseInventoryIngredient(menu, stack) || !ItemStack.isSameItemSameComponents(choice, stack)) continue;
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
            selected.set(index, choice);
            if (allocate(menu, inputs, inventory, selected, clear, index + 1, batches)) return true;
            for (int i = 0; i < inventory.size(); i++) inventory.set(i, before.get(i));
        }
        return false;
    }

    private static boolean simulateStore(List<Slot> slots, List<ItemStack> stacks, ItemStack offered) {
        ItemStack remaining = offered.copy();
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 0; index < slots.size(); index++) {
                Slot slot = slots.get(index);
                ItemStack current = stacks.get(index);
                if (!slot.mayPlace(remaining) || (pass == 0 && current.isEmpty()) || (pass == 1 && !current.isEmpty())) continue;
                if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, remaining)) continue;
                int amount = Math.min(remaining.getCount(), slot.getMaxStackSize(remaining) - current.getCount());
                if (amount <= 0) continue;
                if (current.isEmpty()) stacks.set(index, remaining.copyWithCount(amount));
                else current.grow(amount);
                remaining.shrink(amount);
                if (remaining.isEmpty()) return true;
            }
        }
        return false;
    }
}
