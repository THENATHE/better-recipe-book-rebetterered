package marsh.town.brb.config;

import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;

public enum CreativeDisplayMode implements SelectionListEntry.Translatable {
    VANILLA,
    EXPANDABLE;

    @Override
    public String getKey() {
        return "brb.displayMode." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
