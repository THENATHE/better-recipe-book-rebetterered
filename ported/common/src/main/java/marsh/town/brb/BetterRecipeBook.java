package marsh.town.brb;

import com.mojang.blaze3d.platform.InputConstants;
import marsh.town.brb.config.Config;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BetterRecipeBook {
    public static final String MOD_ID = "brb";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static Config config;
    public static ConfigHolder<Config> configHolder;
    public static PinnedRecipeManager pinnedRecipeManager;
    public static InstantCraftingManager instantCraftingManager;
    public static int queuedScroll;
    public static boolean isFilteringNone;

    public static final KeyMapping PIN_MAPPING = new KeyMapping(
            "key.brb.pin", InputConstants.Type.KEYBOARD, InputConstants.KEY_R,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "keys")));

    public static void init() {
        try {
            AutoConfig.register(Config.class, Toml4jConfigSerializer::new);
            configHolder = AutoConfig.getConfigHolder(Config.class);
            config = configHolder.getConfig();
            if (config.alternativeRecipes.noGrouped) {
                config.recipeDisplayMode = RecipeDisplayMode.ALL;
                config.alternativeRecipes.noGrouped = false;
                configHolder.save();
            }
        } catch (RuntimeException failure) {
            LOGGER.error("Could not load BRB settings; using defaults without overwriting the file", failure);
            config = new Config();
        }
        pinnedRecipeManager = new PinnedRecipeManager();
        pinnedRecipeManager.read();
        instantCraftingManager = new InstantCraftingManager();
        KeyMappingHelper.registerKeyMapping(PIN_MAPPING);
    }
}
