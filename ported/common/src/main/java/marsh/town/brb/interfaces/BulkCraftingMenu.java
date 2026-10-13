package marsh.town.brb.interfaces;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Describes a known menu's result quick-move destinations without changing its behavior. */
public interface BulkCraftingMenu {
    default boolean brb$canPlanBulk() { return true; }

    /** Each group runs its merge pass and empty-slot pass before the next group. */
    List<List<Slot>> brb$getOutputStorageGroups();

    /** Update detached snapshots when storage also changes an item holding that storage. */
    default void brb$updateBulkSnapshot(List<ItemStack> items) { }
}
