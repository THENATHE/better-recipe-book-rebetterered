package marsh.town.brb.recipegroups;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.List;

/** A single display entry that retains its source's current selection and availability. */
public final class RecipeTileCollection extends RecipeCollection {
    private final RecipeCollection source;
    private final RecipeDisplayEntry entry;
    private final boolean groupHeader;
    private final boolean expanded;
    private final String groupKey;

    public RecipeTileCollection(RecipeCollection source, RecipeDisplayEntry entry,
                                boolean groupHeader, boolean expanded, String groupKey) {
        super(List.of(entry));
        this.source = source;
        this.entry = entry;
        this.groupHeader = groupHeader;
        this.expanded = expanded;
        this.groupKey = groupKey;
    }

    public boolean isGroupHeader() { return groupHeader; }
    public boolean isExpanded() { return expanded; }
    public String groupKey() { return groupKey; }

    @Override
    public boolean isCraftable(RecipeDisplayId id) {
        return !groupHeader && entry.id().equals(id) && source.isCraftable(id);
    }

    @Override
    public boolean hasCraftable() {
        return source.isCraftable(entry.id());
    }

    @Override
    public boolean hasAnySelected() {
        return !source.getSelectedRecipes(CraftableStatus.ANY).isEmpty();
    }

    @Override
    public List<RecipeDisplayEntry> getSelectedRecipes(CraftableStatus status) {
        if (status == CraftableStatus.CRAFTABLE && !source.isCraftable(entry.id())) return List.of();
        if (status == CraftableStatus.NOT_CRAFTABLE && source.isCraftable(entry.id())) return List.of();
        return List.of(entry);
    }
}
