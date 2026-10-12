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

}
