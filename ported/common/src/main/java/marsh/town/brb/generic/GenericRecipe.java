package marsh.town.brb.generic;

import marsh.town.brb.api.BRBBookCategories;
import marsh.town.brb.generic.pins.Pinnable;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public interface GenericRecipe extends Pinnable {
    Identifier id();

    @Override
    default boolean has(Identifier identifier) {
        return (id().equals(identifier));
    }

    ItemStack getResult(RegistryAccess registryAccess, BRBBookCategories.Category category);

    String getSearchString(BRBBookCategories.Category category);
}
