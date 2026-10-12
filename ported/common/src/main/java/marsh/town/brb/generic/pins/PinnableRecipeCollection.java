package marsh.town.brb.generic.pins;

import marsh.town.brb.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.Identifier;

public final class PinnableRecipeCollection implements Pinnable {
    private final RecipeCollection collection;

    private PinnableRecipeCollection(RecipeCollection collection) { this.collection = collection; }

    public static PinnableRecipeCollection of(RecipeCollection collection) {
        return new PinnableRecipeCollection(collection);
    }

    @Override
    public boolean has(Identifier identifier) {
        return collection.getRecipes().stream().anyMatch(entry -> identifier.equals(BetterRecipeBook.pinnedRecipeManager.identity(entry)));
    }
}
