package marsh.town.brb.clientqa;

import com.mojang.blaze3d.platform.InputConstants;
import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.interfaces.PinnedRecipeBook;
import marsh.town.brb.mixins.accessors.RecipeBookComponentAccessor;
import marsh.town.brb.mixins.accessors.RecipeBookPageAccessor;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import marsh.town.brb.recipegroups.RecipeTileCollection;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;

/** Interacts with the added tab through the actual inventory and crafting table screens. */
public final class PinnedTabChecks {
    private PinnedTabChecks() { }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, List<String> evidence) throws Exception {
        var savedPins = context.computeOnClient(client -> new HashSet<>(BetterRecipeBook.pinnedRecipeManager.pinned));
        boolean oldPinning = BetterRecipeBook.config.enablePinning;
        RecipeDisplayMode oldMode = BetterRecipeBook.config.recipeDisplayMode;
        try {
            context.runOnClient(client -> {
                BetterRecipeBook.config.enablePinning = true;
                BetterRecipeBook.pinnedRecipeManager.pinned.clear();
            });
            for (boolean table : new boolean[] {false, true}) {
                prepare(context, world, table);
                String menu = table ? "3x3 crafting table" : "2x2 inventory";
                context.runOnClient(client -> {
                    var book = component(client);
                    var accessor = (RecipeBookComponentAccessor) book;
                    var pins = (PinnedRecipeBook) book;
                    var oak = recipe(client, Items.OAK_PLANKS);
                    var birch = recipe(client, Items.BIRCH_PLANKS);
                    var chest = recipe(client, Items.CHEST);
                    var manager = BetterRecipeBook.pinnedRecipeManager;
                    manager.pinned.clear();
                    manager.pinned.add(manager.identity(oak));
                    manager.pinned.add(manager.identity(birch));
                    manager.pinned.add(manager.identity(chest));
                    accessor.getSearchBox().setValue("");
                    var nativeTabs = nativeTabs(book);
                    var tab = pins.brb$getPinnedTab();
                    require(tab != null && tab.visible, menu + ": pinned tab is visible", evidence);
                    require(tab.getX() == accessor.brb$getXOrigin() - 30 && tab.getY() == accessor.brb$getYOrigin() + 3
                            + 27 * nativeTabs.stream().filter(button -> button.visible).count(),
                            menu + ": pinned tab follows the native category column", evidence);
                    require(tab.getY() + tab.getHeight() <= accessor.brb$getYOrigin() + 166,
                            menu + ": pinned tab fits the native book background height", evidence);
                    require(!book.hasClickedOutside(tab.getX() + 12, tab.getY() + 12, 0, 0, 1, 1),
                            menu + ": pinned tab is treated as part of the book", evidence);
                    for (RecipeDisplayMode mode : RecipeDisplayMode.values()) {
                        BetterRecipeBook.config.recipeDisplayMode = mode;
                        click(client, tab);
                        require(pins.brb$isPinnedTabSelected(), menu + ": click selects pinned tab in " + mode, evidence);
                        var collections = page(book).getCollections();
                        require(collections.size() == (table ? 3 : 2), menu + ": pinned tab includes exact compatible recipes in " + mode, evidence);
                        require(collections.stream().allMatch(collection -> collection instanceof RecipeTileCollection tile
                                        && !tile.isGroupHeader() && collection.getRecipes().size() == 1),
                                menu + ": pinned recipes stay fixed and ungrouped in " + mode, evidence);
                        require(collections.stream().flatMap(collection -> collection.getRecipes().stream()).allMatch(manager::has),
                                menu + ": pinned tab never includes an unpinned sibling in " + mode, evidence);
                    }
                    accessor.getSearchBox().setValue("oak planks");
                    accessor.updateCollectionsInvoker(true, false);
                    require(page(book).getCollections().size() == 1 && page(book).getCollections().getFirst().getRecipes().getFirst().id().equals(oak.id()),
                            menu + ": pinned search preserves only the exact matching variant", evidence);
                    accessor.getSearchBox().setValue("no such recipe 82941");
                    accessor.updateCollectionsInvoker(true, false);
                    require(page(book).getCollections().isEmpty() && tab.visible,
                            menu + ": empty pinned search retains a clickable pinned tab", evidence);
                    accessor.getSearchBox().setValue("");
                    accessor.updateCollectionsInvoker(true, false);
                    var filter = filter(book);
                    click(client, filter);
                    require(accessor.brb$isFiltering(), menu + ": actual filter button selects craftable pins", evidence);
                    require(page(book).getCollections().size() == 1 && page(book).getCollections().getFirst().getRecipes().getFirst().id().equals(oak.id()),
                            menu + ": craftable pins exclude unavailable ingredients", evidence);
                    click(client, filter);
                    require(!accessor.brb$isFiltering(), menu + ": actual filter button restores all pins", evidence);
                    var originalNativeTab = nativeTabs.getFirst();
                    click(client, originalNativeTab);
                    require(!pins.brb$isPinnedTabSelected() && page(book).getCollections().size() > 3,
                            menu + ": clicking the previous vanilla category restores its recipe list", evidence);
                    click(client, tab);
                    int width = client.gui.screen().width;
                    int height = client.gui.screen().height;
                    client.gui.screen().resize(340, 240);
                    var resizedTab = pins.brb$getPinnedTab();
                    require(pins.brb$isPinnedTabSelected() && resizedTab.getX() == accessor.brb$getXOrigin() - 30,
                            menu + ": narrow resize preserves pin selection and tab position", evidence);
                    require(page(book).getCollections().size() == (table ? 3 : 2),
                            menu + ": narrow resize preserves exact pinned contents", evidence);
                    click(client, nativeTabs(book).getFirst());
                    require(!pins.brb$isPinnedTabSelected(), menu + ": narrow native tab remains clickable", evidence);
                    click(client, resizedTab);
                    client.gui.screen().resize(width, height);
                    BetterRecipeBook.config.enablePinning = false;
                    book.tick();
                    require(!pins.brb$getPinnedTab().visible && !pins.brb$isPinnedTabSelected() && page(book).getCollections().size() > 3,
                            menu + ": disabling pins hides the tab and restores the native category", evidence);
                    BetterRecipeBook.config.enablePinning = true;
                    book.tick();
                    click(client, pins.brb$getPinnedTab());
                    var tile = page(book).getButtons().stream().filter(button -> button.visible
                            && button.getCurrentRecipe().equals(oak.id())).findFirst().orElseThrow();
                    for (var button : page(book).getButtons()) {
                        button.setFocused(false);
                        setHovered(button, false);
                    }
                    accessor.getSearchBox().setFocused(false);
                    tile.setFocused(true);
                    require(client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_R, 0, 0)),
                            menu + ": pin key is handled on the selected pinned recipe", evidence);
                    require(!manager.has(oak) && page(book).getCollections().size() == (table ? 2 : 1),
                            menu + ": unpin refresh removes only the exact pinned variant", evidence);
                    manager.pinned.clear();
                    accessor.updateCollectionsInvoker(true, false);
                    require(page(book).getCollections().isEmpty() && pins.brb$getPinnedTab().visible,
                            menu + ": empty pinned tab remains visible", evidence);
                    manager.pinned.add(manager.identity(oak));
                    accessor.updateCollectionsInvoker(true, false);
                });
                context.waitTicks(2);
                context.takeScreenshot(table ? "brb-crafting-table-pinned-tab" : "brb-inventory-pinned-tab");
            }
        } finally {
            context.runOnClient(client -> {
                var manager = BetterRecipeBook.pinnedRecipeManager;
                for (var id : new HashSet<>(manager.pinned)) if (!savedPins.contains(id)) manager.toggle(id);
                for (var id : savedPins) if (!manager.pinned.contains(id)) manager.toggle(id);
                BetterRecipeBook.config.enablePinning = oldPinning;
                BetterRecipeBook.config.recipeDisplayMode = oldMode;
                client.player.closeContainer();
                client.gui.setScreen(null);
            });
            world.getConnection().waitForClientboundPackets();
        }
    }

    private static void prepare(ClientGameTestContext context, TestSingleplayerContext world, boolean table) {
        context.runOnClient(client -> {
            client.player.closeContainer();
            client.gui.setScreen(null);
            client.player.getRecipeBook().setFiltering(net.minecraft.world.inventory.RecipeBookType.CRAFTING, false);
        });
        world.getConnection().waitForClientboundPackets();
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 8));
            player.awardRecipes(server.getRecipeManager().getRecipes());
            player.inventoryMenu.broadcastFullState();
            if (table) {
                var position = player.blockPosition().offset(1, 0, 0);
                player.level().setBlock(position, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                player.openMenu(new SimpleMenuProvider((id, inventory, user) -> new CraftingMenu(id, inventory,
                        ContainerLevelAccess.create(user.level(), position)), Component.literal("BRB pinned tab QA")));
            }
        });
        world.getConnection().waitForClientboundPackets();
        if (!table) context.runOnClient(client -> client.gui.setScreen(new InventoryScreen(client.player)));
        context.waitTicks(5);
        context.runOnClient(client -> {
            var book = component(client);
            if (!book.isVisible()) book.toggleVisibility();
            client.player.getRecipeBook().setFiltering(net.minecraft.world.inventory.RecipeBookType.CRAFTING, false);
            book.recipesUpdated();
        });
    }

    private static RecipeDisplayEntry recipe(Minecraft client, Item result) {
        var displayContext = SlotDisplayContext.fromLevel(client.level);
        return client.player.getRecipeBook().getCollections().stream().flatMap(collection -> collection.getRecipes().stream())
                .filter(entry -> entry.resultItems(displayContext).stream().anyMatch(stack -> stack.is(result))).findFirst().orElseThrow();
    }

    private static RecipeBookComponent<?> component(Minecraft client) {
        return client.gui.screen().children().stream().filter(RecipeBookComponent.class::isInstance)
                .map(child -> (RecipeBookComponent<?>) child).findFirst().orElseThrow();
    }

    private static RecipeBookPageAccessor page(RecipeBookComponent<?> book) {
        return (RecipeBookPageAccessor) ((RecipeBookComponentAccessor) book).getRecipeBookPage();
    }

    @SuppressWarnings("unchecked")
    private static List<RecipeBookTabButton> nativeTabs(RecipeBookComponent<?> book) {
        return (List<RecipeBookTabButton>) field(book, "tabButtons");
    }

    private static AbstractWidget filter(RecipeBookComponent<?> book) {
        return (AbstractWidget) field(book, "filterButton");
    }

    private static Object field(RecipeBookComponent<?> book, String name) {
        try {
            Field field = RecipeBookComponent.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(book);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void click(Minecraft client, AbstractWidget widget) {
        require(client.gui.screen().mouseClicked(new MouseButtonEvent(widget.getX() + 12, widget.getY() + 8,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false), "UI click was not consumed: " + widget.getMessage(), null);
    }

    private static void setHovered(AbstractWidget widget, boolean hovered) {
        try {
            Field field = AbstractWidget.class.getDeclaredField("isHovered");
            field.setAccessible(true);
            field.setBoolean(widget, hovered);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void require(boolean condition, String message, List<String> evidence) {
        if (!condition) throw new AssertionError(message);
        if (evidence != null) evidence.add("PASS: " + message);
    }
}
