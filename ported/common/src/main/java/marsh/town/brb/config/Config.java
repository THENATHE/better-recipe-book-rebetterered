package marsh.town.brb.config;

import me.shedaniel.autoconfig.ConfigData;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@me.shedaniel.autoconfig.annotation.Config(name = "brb")
@me.shedaniel.autoconfig.annotation.Config.Gui.Background("cloth-config2:transparent")
public class Config implements ConfigData {
    @ConfigEntry.Gui.Tooltip
    public boolean enableBook = true;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public RecipeDisplayMode recipeDisplayMode = RecipeDisplayMode.EXPANDABLE;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public CreativeDisplayMode creativeDisplayMode = CreativeDisplayMode.EXPANDABLE;

    @ConfigEntry.Gui.Tooltip
    public boolean enablePinning = true;

    @ConfigEntry.Gui.Tooltip
    public boolean keepCentered = false;

    @ConfigEntry.Gui.Tooltip
    public boolean settingsButton = true;

    @ConfigEntry.Gui.TransitiveObject()
    public NewRecipes newRecipes = new NewRecipes();

    @ConfigEntry.Gui.TransitiveObject()
    public InstantCraft instantCraft = new InstantCraft();

    @ConfigEntry.Gui.TransitiveObject()
    public AlternativeRecipes alternativeRecipes = new AlternativeRecipes();

    @ConfigEntry.Gui.TransitiveObject()
    public Scrolling scrolling = new Scrolling();

    @ConfigEntry.Gui.Excluded
    public boolean brewingBookOpen = false;

    @ConfigEntry.Gui.Excluded
    public boolean smithingBookOpen = false;
}
