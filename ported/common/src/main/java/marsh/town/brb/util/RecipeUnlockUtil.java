package marsh.town.brb.util;

import marsh.town.brb.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

public final class RecipeUnlockUtil {
    private static ClientLevel loadedLevel;
    private static ClientRecipeBook loadedBook;
    private static boolean dirty = true;
    private static boolean previousEnabled;
    private static final Set<RecipeDisplayId> localIds = new HashSet<>();
    private static int nextLocalId = -1;

    private RecipeUnlockUtil() {}

    public static void clear() {
        loadedLevel = null;
        loadedBook = null;
        localIds.clear();
        nextLocalId = -1;
        dirty = true;
    }

    public static void invalidate() { dirty = true; }

    public static RecipeDisplayEntry find(RecipeDisplayId id) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return null;
        for (var collection : client.player.getRecipeBook().getCollections()) {
            for (var entry : collection.getRecipes()) if (entry.id().equals(id)) return entry;
        }
        return null;
    }

    public static void unlockRecipesIfRequired() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || BetterRecipeBook.config == null) return;
        var book = client.player.getRecipeBook();
        if (loadedBook != book) {
            clear();
            loadedBook = book;
        }
        // Dimension changes retain the recipe book, including our local entries.
        if (loadedLevel != client.level) {
            loadedLevel = client.level;
            dirty = true;
        }
        boolean enabled = BetterRecipeBook.config.newRecipes.unlockAll;
        if (previousEnabled != enabled) { dirty = true; previousEnabled = enabled; }
        if (!dirty) return;
        var registry = ClientRecipeRegistry.provider().lookup(Registries.RECIPE);
        if (registry.isEmpty()) return;
        for (var id : localIds) book.remove(id);
        localIds.clear();
        book.rebuildCollections();
        if (enabled) {
            Map<net.minecraft.resources.Identifier, RecipeDisplayEntry> known = new HashMap<>();
            for (var collection : book.getCollections()) {
                for (var entry : collection.getRecipes()) {
                    var identity = BetterRecipeBook.pinnedRecipeManager.identity(entry);
                    if (identity != null) known.put(identity, entry);
                }
            }
            RecipeManager recipes = new RecipeManager(ClientRecipeRegistry.provider());
            recipes.finalizeRecipeLoading(client.level.enabledFeatures());
            for (var holder : recipes.getLearnableRecipes()) {
                recipes.listDisplaysForRecipe(holder.id(), original -> {
                    var identity = BetterRecipeBook.pinnedRecipeManager.identity(original);
                    if (identity == null || known.containsKey(identity)) return;
                    RecipeDisplayId id = new RecipeDisplayId(nextLocalId--);
                    OptionalInt group = original.group().isPresent()
                            ? OptionalInt.of(-1 - original.group().getAsInt()) : OptionalInt.empty();
                    RecipeDisplayEntry local = new RecipeDisplayEntry(id, original.display(), group,
                            original.category(), original.craftingRequirements());
                    book.add(local);
                    localIds.add(id);
                    known.put(identity, local);
                });
            }
        }
        book.rebuildCollections();
        if (client.getConnection() != null) client.getConnection().searchTrees().updateRecipes(book, client.level);
        dirty = false;
        if (client.gui.screen() instanceof RecipeUpdateListener listener) listener.recipesUpdated();
    }
}
