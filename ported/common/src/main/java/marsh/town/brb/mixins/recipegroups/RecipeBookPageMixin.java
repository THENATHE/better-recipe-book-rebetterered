package marsh.town.brb.mixins.recipegroups;

import marsh.town.brb.BetterRecipeBook;
import com.mojang.blaze3d.platform.InputConstants;
import marsh.town.brb.mixins.accessors.RecipeBookComponentAccessor;
import marsh.town.brb.recipegroups.GroupedRecipePage;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import marsh.town.brb.recipegroups.RecipeDisplaySearch;
import marsh.town.brb.recipegroups.RecipeOutputGroups;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import marsh.town.brb.recipegroups.RecipeTileCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageMixin implements GroupedRecipePage {
    @Shadow @Final private List<RecipeButton> buttons;
    @Shadow @Final private RecipeBookComponent<?> parent;
    @Shadow private RecipeDisplayId lastClickedRecipe;
    @Shadow private RecipeCollection lastClickedRecipeCollection;
    @Shadow public abstract void updateCollections(List<RecipeCollection> collections, boolean resetPage, boolean filtering);
    @Unique private List<RecipeCollection> brb$sources = List.of();
    @Unique private final Set<String> brb$expanded = new HashSet<>();
    @Unique private final Set<String> brb$searchCollapsed = new HashSet<>();
    @Unique private String brb$lastSearch = "";
    @Unique private boolean brb$filtering;

    @ModifyVariable(method = "updateCollections", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private List<RecipeCollection> brb$flattenCollections(List<RecipeCollection> sources,
                                                          List<RecipeCollection> ignored, boolean resetPage, boolean filtering) {
        brb$sources = sources;
        brb$filtering = filtering;
        RecipeDisplayMode mode = BetterRecipeBook.config.recipeDisplayMode;
        if (mode == RecipeDisplayMode.VANILLA) return sources;
        List<RecipeCollection> tiles = new ArrayList<>();
        var searchBox = ((RecipeBookComponentAccessor) parent).getSearchBox();
        String search = searchBox == null ? "" : searchBox.getValue();
        boolean searching = !search.isBlank();
        if (!search.equals(brb$lastSearch)) {
            brb$searchCollapsed.clear();
            brb$lastSearch = search;
        }
        var client = Minecraft.getInstance();
        var context = client.level == null ? null : SlotDisplayContext.fromLevel(client.level);
        List<List<RecipeCollection>> groups = sources.stream().map(List::of).toList();
        if (mode == RecipeDisplayMode.EXPANDABLE && context != null) {
            groups = RecipeOutputGroups.merge(groups, source -> source.getRecipes().stream()
                    .flatMap(entry -> entry.resultItems(context).stream()).toList());
        }
        for (List<RecipeCollection> group : groups) {
            var sourcesByEntry = new java.util.IdentityHashMap<RecipeDisplayEntry, RecipeCollection>();
            List<RecipeDisplayEntry> entries = new ArrayList<>();
            for (RecipeCollection source : group) {
                for (RecipeDisplayEntry entry : source.getSelectedRecipes(filtering
                        ? RecipeCollection.CraftableStatus.CRAFTABLE : RecipeCollection.CraftableStatus.ANY)) {
                    if (searching && context != null && !RecipeDisplaySearch.matches(entry, search, context)) continue;
                    entries.add(entry);
                    sourcesByEntry.put(entry, source);
                }
            }
            if (entries.isEmpty()) continue;
            String key = group.stream().flatMap(source -> source.getRecipes().stream())
                    .map(entry -> String.valueOf(BetterRecipeBook.pinnedRecipeManager.identity(entry)))
                    .sorted().collect(java.util.stream.Collectors.joining("|"));
            boolean expanded = searching ? !brb$searchCollapsed.contains(key) : brb$expanded.contains(key);
            if (mode == RecipeDisplayMode.EXPANDABLE && entries.size() > 1) {
                RecipeDisplayEntry representative = entries.stream()
                        .filter(entry -> sourcesByEntry.get(entry).isCraftable(entry.id())).findFirst().orElse(entries.getFirst());
                tiles.add(new RecipeTileCollection(sourcesByEntry.get(representative), representative, true, expanded, key));
                if (!expanded) continue;
            }
            for (RecipeDisplayEntry entry : entries) {
                tiles.add(new RecipeTileCollection(sourcesByEntry.get(entry), entry, false, false, key));
            }
        }
        if (BetterRecipeBook.config.enablePinning) {
            // Keep group contents adjacent in expandable mode. ALL can order exact variants independently.
            if (mode == RecipeDisplayMode.ALL)
                tiles.sort(java.util.Comparator.comparing(tile -> !BetterRecipeBook.pinnedRecipeManager.has(tile)));
        }
        return tiles;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, order = 900)
    private void brb$toggleGroup(MouseButtonEvent event, int x, int y, int width, int height, boolean doubleClick,
                                CallbackInfoReturnable<Boolean> cir) {
        RecipeCollection collection = brb$collectionAt(event.x(), event.y());
        if (!(collection instanceof RecipeTileCollection tile) || !tile.isGroupHeader()) return;
        // A consumed header must clear any previous recipe before the component reads the page selection.
        lastClickedRecipe = null;
        lastClickedRecipeCollection = null;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (!brb$lastSearch.isBlank()) {
                if (tile.isExpanded()) brb$searchCollapsed.add(tile.groupKey());
                else brb$searchCollapsed.remove(tile.groupKey());
            } else {
                if (!brb$expanded.remove(tile.groupKey())) brb$expanded.add(tile.groupKey());
            }
            updateCollections(brb$sources, false, brb$filtering);
        }
        cir.setReturnValue(true);
    }

    @Override
    public boolean brb$isGroupHeaderAt(double x, double y) {
        return brb$collectionAt(x, y) instanceof RecipeTileCollection tile && tile.isGroupHeader();
    }

    @Override
    public RecipeDisplayId brb$recipeAt(double x, double y) {
        for (RecipeButton button : buttons) {
            if (button.visible && button.isMouseOver(x, y)) {
                if (button.getCollection() instanceof RecipeTileCollection tile && tile.isGroupHeader()) return null;
                return button.getCurrentRecipe();
            }
        }
        return null;
    }

    @Override
    public RecipeCollection brb$collectionAt(double x, double y) {
        for (RecipeButton button : buttons) {
            if (button.visible && button.isMouseOver(x, y)) return button.getCollection();
        }
        return null;
    }
}
