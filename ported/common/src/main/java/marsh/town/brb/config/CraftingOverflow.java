package marsh.town.brb.config;

import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;

public enum CraftingOverflow implements SelectionListEntry.Translatable {
    STOP_BEFORE_DROP,
    DROP_AND_CONTINUE,
    STOP_AFTER_DROP;

    @Override
    public String getKey() {
        return "brb.craftingOverflow." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
