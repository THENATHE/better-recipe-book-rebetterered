package marsh.town.brb.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "instantCraft")
public class InstantCraft implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public boolean showButton = true;
    @ConfigEntry.Gui.Tooltip
    public boolean enabled = true;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public CraftAllMethod craftAllMethod = CraftAllMethod.SINGLE_BATCHES;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public CraftingOverflow overflow = CraftingOverflow.STOP_BEFORE_DROP;

}
