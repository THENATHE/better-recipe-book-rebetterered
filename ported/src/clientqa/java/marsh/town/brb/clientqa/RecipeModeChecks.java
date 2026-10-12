package marsh.town.brb.clientqa;

import marsh.town.brb.BetterRecipeBook;
import com.mojang.blaze3d.platform.InputConstants;
import marsh.town.brb.mixins.accessors.RecipeBookComponentAccessor;
import marsh.town.brb.mixins.accessors.RecipeBookPageAccessor;
import marsh.town.brb.recipegroups.GroupedRecipePage;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import marsh.town.brb.recipegroups.RecipeTileCollection;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;

import java.util.ArrayList;
import java.util.List;

/** Uses the actual mixed vanilla screen, page and buttons, rather than a simulated UI. */
public final class RecipeModeChecks {
    private RecipeModeChecks() { }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(minecraft -> {
            RecipeDisplayMode previousMode = BetterRecipeBook.config.recipeDisplayMode;
            boolean previousScrolling = BetterRecipeBook.config.scrolling.enableScrolling;
            boolean previousWrap = BetterRecipeBook.config.scrolling.scrollAround;
            minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
            var screen = minecraft.gui.screen();
            var book = screen.children().stream().filter(RecipeBookComponent.class::isInstance)
                    .map(child -> (RecipeBookComponent<?>) child).findFirst().orElseThrow();
            if (!book.isVisible()) book.toggleVisibility();
            var bookAccessor = (RecipeBookComponentAccessor) book;
            var page = bookAccessor.getRecipeBookPage();
            var pageAccessor = (RecipeBookPageAccessor) page;
            bookAccessor.getSearchBox().setValue("");
            List<RecipeDisplayEntry> candidates = minecraft.player.getRecipeBook().getCollections().stream()
                    .flatMap(collection -> collection.getRecipes().stream())
                    .filter(entry -> entry.display() instanceof ShapelessCraftingRecipeDisplay
                            || entry.display() instanceof ShapedCraftingRecipeDisplay shaped && shaped.width() <= 2 && shaped.height() <= 2)
                    .toList();
            require(candidates.size() >= 25, "Fixture must unlock at least 25 crafting displays");
            RecipeCollection source = new RecipeCollection(candidates.subList(0, 25));
            source.selectRecipes(new StackedItemContents(), display -> true);
            List<RecipeCollection> sources = List.of(source);
            try {
                BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.VANILLA;
                page.updateCollections(sources, true, false);
                require(pageAccessor.getCollections().size() == 1, "Vanilla mode changed collection grouping");
                require(pageAccessor.getButtons().getFirst().getCollection() == source, "Vanilla button lost its source");

                boolean previousInstant = BetterRecipeBook.config.instantCraft.enabled;
                try {
                    BetterRecipeBook.config.instantCraft.enabled = false;
                    RecipeButton vanillaButton = pageAccessor.getButtons().getFirst();
                    var rightClick = new MouseButtonEvent(vanillaButton.getX() + 8, vanillaButton.getY() + 8,
                            new MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0));
                    book.mouseClicked(rightClick, false);
                    require(pageAccessor.getOverlay().isVisible(), "Vanilla variant overlay did not open");
                    var toggle = BetterRecipeBook.instantCraftingManager.lastInstantCraftButton;
                    require(toggle != null, "Instant crafting toggle is unavailable");
                    book.mouseClicked(new MouseButtonEvent(toggle.getX() + 4, toggle.getY() + 4,
                            new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                    require(BetterRecipeBook.config.instantCraft.enabled, "Instant crafting toggle did not enable crafting");
                    require(!pageAccessor.getOverlay().isVisible(), "Enabling instant crafting left an interactive overlay above recipe buttons");
                    // A hotkey or external setting can enable crafting while the overlay is already open.
                    BetterRecipeBook.config.instantCraft.enabled = false;
                    book.mouseClicked(rightClick, false);
                    require(pageAccessor.getOverlay().isVisible(), "Variant overlay did not reopen");
                    BetterRecipeBook.config.instantCraft.enabled = true;
                    book.mouseClicked(rightClick, false);
                    require(page.getLastClickedRecipe() != null || !pageAccessor.getOverlay().isVisible(),
                            "Instant craft consumed a click through the variant overlay");
                } finally {
                    pageAccessor.getOverlay().setVisible(false);
                    BetterRecipeBook.config.instantCraft.enabled = previousInstant;
                }

                BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.EXPANDABLE;
                page.updateCollections(sources, true, false);
                require(pageAccessor.getCollections().size() == 1, "Collapsed group must occupy one tile");
                RecipeButton header = pageAccessor.getButtons().getFirst();
                var grouped = (GroupedRecipePage) page;
                require(grouped.brb$isGroupHeaderAt(header.getX() + 8, header.getY() + 8), "Header hit detection failed");
                require(grouped.brb$recipeAt(header.getX() + 8, header.getY() + 8) == null, "Header exposed a craftable selection");
                List<ItemStack> before = minecraft.player.containerMenu.slots.stream().map(slot -> slot.getItem().copy()).toList();
                ItemStack cursorBefore = minecraft.player.containerMenu.getCarried().copy();
                book.mouseClicked(new MouseButtonEvent(header.getX() + 8, header.getY() + 8, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                require(pageAccessor.getCollections().size() == 26, "Expansion failed to expose all fixed variants across pages");
                require(page.getLastClickedRecipe() == null && page.getLastClickedRecipeCollection() == null,
                        "Header retained a stale recipe selection");
                require(ItemStack.matches(cursorBefore, minecraft.player.containerMenu.getCarried()), "Header changed the cursor");
                for (int i = 0; i < before.size(); i++)
                    require(ItemStack.matches(before.get(i), minecraft.player.containerMenu.slots.get(i).getItem()), "Header changed inventory slot " + i);
                for (RecipeCollection tile : pageAccessor.getCollections()) {
                    require(tile instanceof RecipeTileCollection, "Expanded page contains a cycling collection");
                    require(tile.getRecipes().size() == 1, "Expanded child contains multiple recipe displays");
                }
                require(pageAccessor.getTotalPages() == 2, "Expanded group pagination did not include all children");
                var next = pageAccessor.getForwardButton();
                book.mouseClicked(new MouseButtonEvent(next.getX() + 4, next.getY() + 4, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                require(pageAccessor.getCurrentPage() == 1, "Expanded group next-page arrow did not work");
                BetterRecipeBook.config.scrolling.enableScrolling = true;
                BetterRecipeBook.config.scrolling.scrollAround = false;
                require(book.mouseScrolled(header.getX() + 8, header.getY() + 8, 0, 1), "Wheel over visible recipe book was not consumed");
                require(pageAccessor.getCurrentPage() == 0, "Wheel failed to move to previous recipe page");
                require(!book.mouseScrolled(header.getX() + 150, header.getY() + 8, 0, -1), "Recipe wheel handler intercepted inventory outside the book");
                require(pageAccessor.getCurrentPage() == 0, "Wheel outside book changed recipe page");
                require(book.mouseScrolled(header.getX() + 8, header.getY() + 8, 0, 1), "Clamped wheel was not handled");
                require(pageAccessor.getCurrentPage() == 0, "Non-wrapping wheel escaped page bounds");
                BetterRecipeBook.config.scrolling.scrollAround = true;
                require(book.mouseScrolled(header.getX() + 8, header.getY() + 8, 0, 1), "Wrapping wheel was not handled");
                require(pageAccessor.getCurrentPage() == 1, "Wrapping wheel did not reach the final page");
                BetterRecipeBook.config.scrolling.enableScrolling = false;
                require(!book.mouseScrolled(header.getX() + 8, header.getY() + 8, 0, 1), "Disabled wheel handler consumed inventory input");
                require(pageAccessor.getCurrentPage() == 1, "Disabled wheel handler changed the page");
                BetterRecipeBook.config.scrolling.enableScrolling = previousScrolling;
                BetterRecipeBook.config.scrolling.scrollAround = previousWrap;
                require(pageAccessor.getButtons().stream().filter(button -> button.visible).count() == 6,
                        "Expanded group final page lost children or retained obsolete tiles");
                var back = pageAccessor.getBackButton();
                book.mouseClicked(new MouseButtonEvent(back.getX() + 4, back.getY() + 4, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                header = pageAccessor.getButtons().getFirst();
                book.mouseClicked(new MouseButtonEvent(header.getX() + 8, header.getY() + 8, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0)), false);
                require(pageAccessor.getCollections().size() == 1, "Right-click failed to collapse header without crafting");
                bookAccessor.getSearchBox().setValue("minecraft:");
                page.updateCollections(sources, true, false);
                require(pageAccessor.getCollections().size() == 26, "Searching hid collapsed group members");
                bookAccessor.getSearchBox().setValue("");

                BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.ALL;
                page.updateCollections(sources, true, false);
                require(pageAccessor.getCollections().size() == 25, "ALL mode retained a header or lost variants");
                for (RecipeCollection tile : pageAccessor.getCollections())
                    require(tile instanceof RecipeTileCollection fixed && !fixed.isGroupHeader() && tile.getRecipes().size() == 1,
                            "ALL mode contains a cycling or header tile");

                var displayContext = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(minecraft.level);
                var searchFirst = candidates.getFirst();
                String firstName = searchFirst.resultItems(displayContext).getFirst().getHoverName().getString();
                var unrelated = candidates.stream().filter(entry -> entry.resultItems(displayContext).stream()
                        .noneMatch(stack -> stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT)
                                .contains(firstName.toLowerCase(java.util.Locale.ROOT)))).findFirst().orElseThrow();
                var searchSource = new RecipeCollection(List.of(searchFirst, unrelated));
                searchSource.selectRecipes(new StackedItemContents(), display -> true);
                bookAccessor.getSearchBox().setValue(firstName);
                page.updateCollections(List.of(searchSource), true, false);
                require(pageAccessor.getCollections().size() == 1, "Fixed recipe search retained an unrelated grouped sibling");
                require(pageAccessor.getCollections().getFirst().getRecipes().getFirst().id().equals(searchFirst.id()),
                        "Fixed recipe search selected the wrong sibling");
                bookAccessor.getSearchBox().setValue("");

                var manager = BetterRecipeBook.pinnedRecipeManager;
                RecipeDisplayEntry first = candidates.getFirst();
                RecipeDisplayEntry renumbered = new RecipeDisplayEntry(new RecipeDisplayId(1_000_000), first.display(), first.group(), first.category(), first.craftingRequirements());
                require(manager.identity(first) != null, "Canonical display identity cannot be encoded");
                require(manager.identity(first).equals(manager.identity(renumbered)), "Pin changed when server renumbered recipe display IDs");
                var distinct = candidates.stream().filter(entry -> !manager.identity(first).equals(manager.identity(entry))).findFirst().orElseThrow();
                var originalPins = new java.util.HashSet<>(manager.pinned);
                try {
                    manager.pinned.clear();
                    manager.pinned.add(manager.identity(first));
                    require(manager.has(renumbered), "Exact pin did not survive display ID renumbering");
                    require(!manager.has(distinct), "Pin spilled into a different recipe variant");
                    var registry = marsh.town.brb.util.ClientRecipeRegistry.provider().lookupOrThrow(net.minecraft.core.registries.Registries.RECIPE);
                    var legacy = registry.keySet().stream().filter(key -> registry.getOptional(key).orElseThrow().display().stream()
                            .anyMatch(display -> manager.identity(new RecipeDisplayEntry(new RecipeDisplayId(1_000_001), display,
                                    first.group(), first.category(), first.craftingRequirements())).equals(manager.identity(first))))
                            .findFirst().orElseThrow();
                    manager.pinned.clear();
                    manager.pinned.add(legacy);
                    manager.clearIdentityCache();
                    require(manager.has(first), "Legacy native recipe pin did not migrate to exact display");
                    require(manager.pinned.contains(legacy), "Migration discarded the original legacy ID");
                    manager.toggle(manager.identity(first));
                    require(!manager.has(first), "Migrated exact recipe variant failed to unpin");
                    manager.clearIdentityCache();
                    require(!manager.has(first), "Legacy broad pin repinned a removed exact variant after cache reload");
                    var unavailableLegacy = net.minecraft.resources.Identifier.fromNamespaceAndPath("missing_mod", "saved_recipe");
                    manager.pinned.add(unavailableLegacy);
                    manager.clearIdentityCache();
                    manager.identity(first);
                    require(manager.pinned.contains(unavailableLegacy), "Unresolved legacy native ID was discarded");
                } finally {
                    manager.pinned.clear();
                    manager.pinned.addAll(originalPins);
                }
                var savedPins = new java.util.HashSet<>(manager.pinned);
                try {
                    manager.pinned.clear();
                    var twoVariants = new RecipeCollection(List.of(first, distinct));
                    twoVariants.selectRecipes(new StackedItemContents(), display -> true);
                    page.updateCollections(List.of(twoVariants), true, false);
                    bookAccessor.getSearchBox().setFocused(false);
                    var focused = pageAccessor.getButtons().getFirst();
                    var hovered = pageAccessor.getButtons().get(1);
                    focused.setFocused(true);
                    for (var button : pageAccessor.getButtons()) setHovered(button, button == hovered);
                    screen.keyPressed(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_R, 0, 0));
                    require(manager.has(distinct) && !manager.has(first),
                            "Native recipe pin targeted the previously focused tile instead of the hovered variant");
                } finally {
                    for (var id : new java.util.HashSet<>(manager.pinned)) if (!savedPins.contains(id)) manager.toggle(id);
                    for (var id : savedPins) if (!manager.pinned.contains(id)) manager.toggle(id);
                }
                page.updateCollections(sources, true, true);
                require(pageAccessor.getCollections().isEmpty(), "Craftable filter exposed unavailable fixed tiles");
                BetterRecipeBook.LOGGER.info("BRB CLIENT QA: recipe display modes, headers, fixed variants, pin renumbering and filtering passed");
            } finally {
                BetterRecipeBook.config.recipeDisplayMode = previousMode;
                BetterRecipeBook.config.scrolling.enableScrolling = previousScrolling;
                BetterRecipeBook.config.scrolling.scrollAround = previousWrap;
                minecraft.gui.setScreen(null);
            }
        });
        RecipeDisplayMode originalMode = context.computeOnClient(minecraft -> BetterRecipeBook.config.recipeDisplayMode);
        List<RecipeButton> fixedButtons = new ArrayList<>();
        List<RecipeDisplayId> fixedIds = new ArrayList<>();
        List<ItemStack> fixedStacks = new ArrayList<>();
        try {
            context.runOnClient(minecraft -> {
                BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.ALL;
                minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
                var book = minecraft.gui.screen().children().stream().filter(RecipeBookComponent.class::isInstance)
                        .map(child -> (RecipeBookComponent<?>) child).findFirst().orElseThrow();
                if (!book.isVisible()) book.toggleVisibility();
                var page = ((RecipeBookComponentAccessor) book).getRecipeBookPage();
                ((RecipeBookComponentAccessor) book).updateCollectionsInvoker(true, false);
                for (RecipeButton button : ((RecipeBookPageAccessor) page).getButtons()) {
                    if (!button.visible) continue;
                    fixedButtons.add(button);
                    fixedIds.add(button.getCurrentRecipe());
                    fixedStacks.add(button.getDisplayStack().copy());
                }
                require(!fixedButtons.isEmpty(), "Fixed display timing test has no visible recipes");
            });
            context.waitTicks(45);
            context.runOnClient(minecraft -> {
                for (int i = 0; i < fixedButtons.size(); i++) {
                    require(fixedIds.get(i).equals(fixedButtons.get(i).getCurrentRecipe()), "Fixed recipe cycled after 45 ticks");
                    require(ItemStack.matches(fixedStacks.get(i), fixedButtons.get(i).getDisplayStack()), "Fixed result variant cycled after 45 ticks");
                }
                BetterRecipeBook.LOGGER.info("BRB CLIENT QA: ALL recipe tiles retained exact recipes and display components across 45 ticks");
            });
        } finally {
            context.runOnClient(minecraft -> {
                BetterRecipeBook.config.recipeDisplayMode = originalMode;
                minecraft.gui.setScreen(null);
            });
        }
        context.runOnClient(minecraft -> {
            boolean previousCentered = BetterRecipeBook.config.keepCentered;
            boolean previousBook = BetterRecipeBook.config.enableBook;
            boolean previousSettings = BetterRecipeBook.config.settingsButton;
            try {
                BetterRecipeBook.config.keepCentered = true;
                BetterRecipeBook.config.enableBook = true;
                BetterRecipeBook.config.settingsButton = true;
                minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
                var inventory = minecraft.gui.screen();
                var book = inventory.children().stream().filter(RecipeBookComponent.class::isInstance)
                        .map(child -> (RecipeBookComponent<?>) child).findFirst().orElseThrow();
                if (!book.isVisible()) book.toggleVisibility();
                var accessor = (RecipeBookComponentAccessor) book;
                require(accessor.getXOffset() == (inventory.width < 379 ? 0 : 162), "Centered book offset was overwritten by vanilla");
                require(book.updateScreenPosition(inventory.width, 176) == (inventory.width - 176) / 2,
                        "Centered book shifted its inventory container");
                int bookLeft = (inventory.width - RecipeBookComponent.IMAGE_WIDTH) / 2 - accessor.getXOffset();
                int bookTop = (inventory.height - RecipeBookComponent.IMAGE_HEIGHT) / 2;
                require(book.mouseClicked(new MouseButtonEvent(bookLeft + 20, bookTop + 146, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false),
                        "Settings gear did not consume its own click");
                require(minecraft.gui.screen() != inventory && minecraft.gui.screen() != null,
                        "Settings gear failed to open Cloth Config");

                BetterRecipeBook.config.enableBook = false;
                minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
                var disabledBook = minecraft.gui.screen().children().stream().filter(RecipeBookComponent.class::isInstance)
                        .map(child -> (RecipeBookComponent<?>) child).findFirst().orElseThrow();
                require(!disabledBook.isVisible(), "Disabled recipe book remains visible");
                require(minecraft.gui.screen().children().stream().filter(net.minecraft.client.gui.components.ImageButton.class::isInstance)
                        .map(child -> (net.minecraft.client.gui.components.ImageButton) child).noneMatch(button -> button.visible),
                        "Disabled recipe book toggle remains clickable");
                BetterRecipeBook.config.enableBook = true;
                minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
                require(minecraft.gui.screen().children().stream().filter(net.minecraft.client.gui.components.ImageButton.class::isInstance)
                        .map(child -> (net.minecraft.client.gui.components.ImageButton) child).anyMatch(button -> button.visible),
                        "Re-enabled recipe book did not restore its toggle");
                BetterRecipeBook.LOGGER.info("BRB CLIENT QA: centered inventory, Cloth Config gear, disable/re-enable recipe book passed");
            } finally {
                BetterRecipeBook.config.keepCentered = previousCentered;
                BetterRecipeBook.config.enableBook = previousBook;
                BetterRecipeBook.config.settingsButton = previousSettings;
                minecraft.gui.setScreen(null);
            }
        });
    }

    private static void setHovered(net.minecraft.client.gui.components.AbstractWidget widget, boolean hovered) {
        try {
            var field = net.minecraft.client.gui.components.AbstractWidget.class.getDeclaredField("isHovered");
            field.setAccessible(true);
            field.setBoolean(widget, hovered);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not set recipe hover state", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
