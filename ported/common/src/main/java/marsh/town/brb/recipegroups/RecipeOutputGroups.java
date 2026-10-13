package marsh.town.brb.recipegroups;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

/** Keeps existing recipe families together while joining families that produce the same item. */
public final class RecipeOutputGroups {
    private RecipeOutputGroups() {}

    public static <T> List<List<T>> merge(List<List<T>> families, Function<T, List<ItemStack>> outputs) {
        int[] parent = new int[families.size()];
        var owners = new HashMap<OutputKey, Integer>();
        for (int index = 0; index < families.size(); index++) {
            parent[index] = index;
            for (T recipe : families.get(index)) {
                for (ItemStack output : outputs.apply(recipe)) {
                    if (output.isEmpty()) continue;
                    Integer previous = owners.putIfAbsent(new OutputKey(output), index);
                    if (previous != null) parent[root(parent, index)] = root(parent, previous);
                }
            }
        }
        // Iterate the original order after joining, so a bridge between two families cannot reorder recipes.
        var merged = new LinkedHashMap<Integer, List<T>>();
        for (int index = 0; index < families.size(); index++) {
            merged.computeIfAbsent(root(parent, index), ignored -> new ArrayList<>()).addAll(families.get(index));
        }
        return merged.values().stream().map(List::copyOf).toList();
    }

    private static int root(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }

    private static final class OutputKey {
        private final ItemStack stack;
        private final int hash;

        private OutputKey(ItemStack stack) {
            this.stack = stack.copyWithCount(1);
            hash = ItemStack.hashItemAndComponents(this.stack);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof OutputKey key && ItemStack.isSameItemSameComponents(stack, key.stack);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
