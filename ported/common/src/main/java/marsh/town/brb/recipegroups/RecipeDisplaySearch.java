package marsh.town.brb.recipegroups;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.util.context.ContextMap;

import java.util.Locale;

/** Refines vanilla's group-level search so fixed tiles do not reveal unrelated siblings. */
public final class RecipeDisplaySearch {
    private RecipeDisplaySearch() { }

    public static boolean matches(RecipeDisplayEntry entry, String search, ContextMap context) {
        String query = search.strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return true;
        for (ItemStack stack : entry.resultItems(context)) {
            if (query.startsWith("#")) {
                String tag = query.substring(1);
                if (stack.typeHolder().tags().anyMatch(key -> key.location().toString().contains(tag))) return true;
                continue;
            }
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            int separator = query.indexOf(':');
            if (separator >= 0) {
                if (id.getNamespace().contains(query.substring(0, separator))
                        && (name.contains(query.substring(separator + 1)) || id.getPath().contains(query.substring(separator + 1)))) return true;
            } else if (name.contains(query) || id.getPath().contains(query)) {
                return true;
            }
        }
        return false;
    }
}
