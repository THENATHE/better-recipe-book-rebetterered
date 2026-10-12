package marsh.town.brb.recipegroups;

import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;

public enum RecipeDisplayMode implements SelectionListEntry.Translatable {
    VANILLA,
    EXPANDABLE,
    ALL;

    @Override
    public String getKey() {
        return "brb.displayMode." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
