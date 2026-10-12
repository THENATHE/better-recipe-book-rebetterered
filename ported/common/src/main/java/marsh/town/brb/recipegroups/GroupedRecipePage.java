package marsh.town.brb.recipegroups;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

public interface GroupedRecipePage {
    boolean brb$isGroupHeaderAt(double x, double y);
    RecipeDisplayId brb$recipeAt(double x, double y);
    RecipeCollection brb$collectionAt(double x, double y);
}
