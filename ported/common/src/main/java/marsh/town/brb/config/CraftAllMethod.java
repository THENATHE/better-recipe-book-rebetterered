package marsh.town.brb.config;

import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;

public enum CraftAllMethod implements SelectionListEntry.Translatable {
    SINGLE_BATCHES,
    FILL_GRID;

    @Override
    public String getKey() {
        return "brb.craftAllMethod." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
