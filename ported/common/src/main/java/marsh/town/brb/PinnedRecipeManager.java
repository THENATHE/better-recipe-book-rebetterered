package marsh.town.brb;

import com.mojang.serialization.JsonOps;
import marsh.town.brb.generic.GenericRecipe;
import marsh.town.brb.generic.GenericRecipeBookCollection;
import marsh.town.brb.generic.pins.Pinnable;
import marsh.town.brb.mixins.accessors.RecipeBookComponentAccessor;
import marsh.town.brb.recipegroups.AtomicPinStore;
import marsh.town.brb.recipegroups.CanonicalJson;
import marsh.town.brb.util.ClientRecipeRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PinnedRecipeManager {
    public final Set<Identifier> pinned = new HashSet<>();
    private final Map<RecipeDisplayEntry, Identifier> identities = new IdentityHashMap<>();
    private java.lang.ref.WeakReference<ClientLevel> identityLevel = new java.lang.ref.WeakReference<>(null);
    private boolean unreadableFile;

    private Path pinsPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(BetterRecipeBook.MOD_ID + ".pins");
    }

    public synchronized void read() {
        pinned.clear();
        unreadableFile = false;
        clearIdentityCache();
        try {
            for (String value : new AtomicPinStore(pinsPath()).read()) {
                Identifier id = Identifier.tryParse(value);
                if (id == null) throw new IllegalArgumentException("Invalid pin identifier");
                pinned.add(id);
            }
        } catch (Exception exception) {
            pinned.clear();
            unreadableFile = true;
            BetterRecipeBook.LOGGER.error("Could not read BRB pins; the original file will be preserved", exception);
        }
    }

    private void store() {
        try {
            if (unreadableFile && Files.exists(pinsPath())) {
                Files.copy(pinsPath(), pinsPath().resolveSibling(pinsPath().getFileName() + ".unreadable-" + System.currentTimeMillis()),
                        StandardCopyOption.COPY_ATTRIBUTES);
                unreadableFile = false;
            }
            Set<String> values = new java.util.TreeSet<>();
            for (Identifier id : pinned) values.add(id.toString());
            new AtomicPinStore(pinsPath()).write(values);
        } catch (Exception exception) {
            BetterRecipeBook.LOGGER.error("Could not save BRB pins", exception);
        }
    }

    public void clearIdentityCache() {
        identities.clear();
        identityLevel.clear();
    }

    /** Network display numbers change on reload. The canonical display includes ingredients and components. */
    public Identifier identity(RecipeDisplayEntry entry) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        if (identityLevel.get() != level) {
            identities.clear();
            identityLevel = new java.lang.ref.WeakReference<>(level);
            migrateLegacyPins();
        }
        return identities.computeIfAbsent(entry, value -> {
            Identifier exact = displayIdentity(value.display(), level.registryAccess());
            return exact != null ? exact : displayIdentity(value.display(), ClientRecipeRegistry.provider());
        });
    }

    private Identifier displayIdentity(RecipeDisplay display, RegistryAccess registries) {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        return RecipeDisplay.CODEC.encodeStart(ops, display).result()
                .map(json -> Identifier.fromNamespaceAndPath("brb", "display/" + CanonicalJson.fingerprint(json)))
                .orElse(null);
    }

    private void migrateLegacyPins() {
        if (pinned.isEmpty()) return;
        var provider = ClientRecipeRegistry.provider();
        var registry = provider.lookup(Registries.RECIPE);
        if (registry.isEmpty()) return;
        boolean changed = false;
        for (Identifier legacy : List.copyOf(pinned)) {
            var recipe = registry.get().getOptional(legacy);
            if (recipe.isEmpty()) continue;
            for (RecipeDisplay display : recipe.get().display()) {
                Identifier exact = displayIdentity(display, provider);
                if (exact != null) changed |= pinned.add(exact);
            }
        }
        if (changed) store();
    }

    public boolean has(RecipeDisplayEntry entry) {
        Identifier id = identity(entry);
        return id != null && pinned.contains(id);
    }

    public boolean has(RecipeCollection target) {
        return target.getRecipes().stream().anyMatch(this::has);
    }

    public void addOrRemoveFavourite(RecipeCollection target) {
        if (!target.getRecipes().isEmpty()) toggle(target.getRecipes().getFirst());
    }

    public void toggle(RecipeDisplayEntry entry) {
        Identifier id = identity(entry);
        if (id != null) toggle(id);
    }

    public boolean has(Identifier id) {
        return pinned.contains(id);
    }

    public synchronized void toggle(Identifier id) {
        if (pinned.remove(id)) removeLegacyPinForDisplay(id);
        else pinned.add(id);
        store();
    }

    private void removeLegacyPinForDisplay(Identifier exact) {
        if (!exact.getNamespace().equals("brb") || !exact.getPath().startsWith("display/")) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        var provider = ClientRecipeRegistry.provider();
        var registry = provider.lookup(Registries.RECIPE);
        if (registry.isEmpty()) return;
        // Once a migrated variant is explicitly unpinned, its old broad ID must not repin it on reload.
        pinned.removeIf(legacy -> registry.get().getOptional(legacy)
                .map(recipe -> recipe.display().stream().anyMatch(display -> exact.equals(displayIdentity(display, provider))))
                .orElse(false));
    }

    public <R extends GenericRecipe, M extends AbstractContainerMenu> void addOrRemoveFavourite(GenericRecipeBookCollection<R, M> target) {
        if (!target.getRecipes().isEmpty()) toggle(target.getFirst().id());
    }

    public boolean has(Pinnable target) {
        return pinned.stream().anyMatch(target::has);
    }

    public static void handlePinRecipe(RecipeBookComponent<?> book, RecipeBookPage page, RecipeDisplayId recipe) {
        if (recipe == null) return;
        for (RecipeCollection collection : Minecraft.getInstance().player.getRecipeBook().getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (entry.id().equals(recipe)) {
                    BetterRecipeBook.pinnedRecipeManager.toggle(entry);
                    var accessor = (RecipeBookComponentAccessor) book;
                    accessor.updateCollectionsInvoker(false, accessor.brb$isFiltering());
                    return;
                }
            }
        }
    }
}
