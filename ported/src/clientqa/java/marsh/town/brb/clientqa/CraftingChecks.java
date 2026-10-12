package marsh.town.brb.clientqa;

import com.mojang.blaze3d.platform.InputConstants;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.CraftRequestChecks;
import marsh.town.brb.mixins.accessors.RecipeBookComponentAccessor;
import marsh.town.brb.mixins.accessors.RecipeBookPageAccessor;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import marsh.town.brb.util.ClientInventoryUtil;
import marsh.town.brb.util.RecipePlacement;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderSet;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Field;
import java.util.List;

/** Exercises actual recipe widgets and server-authoritative inventories in the QA world. */
public final class CraftingChecks {
    private CraftingChecks() {
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, List<String> evidence) throws Exception {
        context.runOnClient(client -> {
            BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.ALL;
            BetterRecipeBook.config.instantCraft.enabled = true;
        });
        world.getServer().runCommand("recipe give @a *");
        world.getConnection().waitForClientboundPackets();
        for (boolean table : new boolean[] {false, true}) {
            String menu = table ? "3x3 table" : "2x2 inventory";
            prepare(context, world, table, 8, false);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_LEFT, false);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 0, menu + ": left-click places ingredients without crafting", evidence);
            require(gridCount(world) == 1, menu + ": left-click places one ingredient", evidence);

            RecipeDisplayEntry original = context.computeOnClient(client -> {
                var tile = plankTile(client);
                return tile.getCollection().getRecipes().stream()
                        .filter(recipe -> recipe.id().equals(tile.getCurrentRecipe())).findFirst().orElseThrow();
            });
            RecipeDisplayId localId = new RecipeDisplayId(-987654);
            context.runOnClient(client -> {
                var book = client.player.getRecipeBook();
                book.remove(original.id());
                book.add(new RecipeDisplayEntry(localId, original.display(), original.group(), original.category(), original.craftingRequirements()));
                book.rebuildCollections();
                client.getConnection().searchTrees().updateRecipes(book, client.level);
                component(client).recipesUpdated();
            });
            context.waitTicks(3);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, false);
            world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == 4, 150);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 4 && count(world, Items.OAK_LOG) + gridCount(world) == 7,
                    menu + ": right-click on a prepared local recipe crafts once without a new placement packet", evidence);
            context.runOnClient(client -> {
                var book = client.player.getRecipeBook();
                book.remove(localId);
                book.add(original);
                book.rebuildCollections();
                client.getConnection().searchTrees().updateRecipes(book, client.level);
                component(client).recipesUpdated();
            });

            for (int available : new int[] {1, 8}) {
                localInventoryCraft(context, world, table, original, localId, available, false, evidence);
            }
            localInventoryCraft(context, world, table, original, localId, 8, true, evidence);
            localGridRedistribution(context, world, table, evidence);

            prepare(context, world, table, 8, false);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ItemStack renamed = new ItemStack(Items.OAK_LOG, 8);
                renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Saved timber"));
                player.getInventory().setItem(0, renamed.copyWithCount(7));
                ((AbstractCraftingMenu) player.containerMenu).getInputGridSlots().getFirst().set(renamed.copyWithCount(1));
                player.containerMenu.broadcastFullState();
            });
            synchronize(context, world);
            context.runOnClient(client -> {
                var book = client.player.getRecipeBook();
                book.remove(original.id());
                book.add(new RecipeDisplayEntry(localId, original.display(), original.group(), original.category(), original.craftingRequirements()));
                book.rebuildCollections();
                client.getConnection().searchTrees().updateRecipes(book, client.level);
                component(client).recipesUpdated();
            });
            context.waitTicks(3);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, false);
            world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == 4, 150);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 4 && count(world, Items.OAK_LOG) + gridCount(world) == 7,
                    menu + ": local item ingredients accept a manually prepared renamed log and consume one batch", evidence);
            require(world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems().stream()
                            .filter(stack -> stack.getItem() == Items.OAK_LOG).allMatch(stack -> stack.getHoverName().getString().equals("Saved timber"))),
                    menu + ": unconsumed renamed logs retain their custom name", evidence);
            context.runOnClient(client -> {
                var local = client.player.getRecipeBook().getCollections().stream().flatMap(collection -> collection.getRecipes().stream())
                        .filter(entry -> entry.id().equals(localId)).findFirst().orElseThrow();
                require(!RecipePlacement.place(local, true), menu + ": automatic local placement protects renamed inventory ingredients", evidence);
            });
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 4 && count(world, Items.OAK_LOG) == 7 && gridCount(world) == 0,
                    menu + ": protected inventory ingredients remain unconsumed after an automatic refill attempt", evidence);
            context.runOnClient(client -> {
                var book = client.player.getRecipeBook();
                book.remove(localId);
                book.add(original);
                book.rebuildCollections();
                client.getConnection().searchTrees().updateRecipes(book, client.level);
                component(client).recipesUpdated();
            });

            prepare(context, world, table, 8, false);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_LEFT, false);
            synchronize(context, world);
            context.runOnClient(client -> {
                var invalidDisplay = new ShapedCraftingRecipeDisplay(2, 1,
                        List.of(new SlotDisplay.ItemSlotDisplay(Items.OAK_LOG), new SlotDisplay.TagSlotDisplay(HolderSet.direct(List.of()))),
                        new SlotDisplay.ItemSlotDisplay(Items.OAK_PLANKS), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE));
                var unresolved = new RecipeDisplayEntry(localId, invalidDisplay, original.group(), original.category(), original.craftingRequirements());
                require(!RecipePlacement.place(unresolved, false), menu + ": an unresolved required ingredient refuses placement", evidence);
            });
            synchronize(context, world);
            require(count(world, Items.OAK_LOG) == 7 && gridCount(world) == 1 && count(world, Items.OAK_PLANKS) == 0,
                    menu + ": unresolved ingredient placement preserves the prepared grid and all inventory items", evidence);

            prepare(context, world, table, 8, false);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_LEFT, true);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 0 && gridCount(world) == 8,
                    menu + ": Shift-left-click retains vanilla maximum grid fill", evidence);

            prepare(context, world, table, 8, false);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, false);
            world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == 4, 150);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 4 && count(world, Items.OAK_LOG) + gridCount(world) == 7,
                    menu + ": right-click creates precisely one four-plank batch", evidence);
            require(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()), menu + ": single batch leaves cursor empty", evidence);

            prepare(context, world, table, 8, false);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, true);
            world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == 32, 250);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 32 && gridCount(world) == 0,
                    menu + ": Shift-right-click crafts all eight batches", evidence);
            require(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()), menu + ": maximum craft leaves cursor empty", evidence);

            prepare(context, world, table, 8, true);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 63));
                player.containerMenu.broadcastFullState();
            });
            synchronize(context, world);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, true);
            world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == 95, 250);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 95 && gridCount(world) == 0,
                    menu + ": maximum crafting survives a batch split between a partial stack and an empty slot", evidence);
            require(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()),
                    menu + ": split storage confirmations leave no output on the cursor", evidence);

            prepare(context, world, table, 64, false);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(1, new ItemStack(Items.OAK_LOG, 4));
                player.inventoryMenu.broadcastFullState();
            });
            synchronize(context, world);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, true);
            world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == 272, 500);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 272 && gridCount(world) == 0,
                    menu + ": maximum crafting refills beyond the first 64-batch grid placement", evidence);

            prepare(context, world, table, 8, true);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, false);
            context.waitTicks(15);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 0 && count(world, Items.OAK_LOG) + gridCount(world) == 8,
                    menu + ": full inventory refuses a batch without consuming ingredients", evidence);
            require(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()), menu + ": full inventory leaves cursor empty", evidence);

            prepare(context, world, table, 64, true);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, true);
            world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == 64, 300);
            context.waitTicks(15);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 64 && gridCount(world) == 48,
                    menu + ": maximum craft stops at storage capacity and preserves 48 unconsumed ingredients", evidence);
            require(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()),
                    menu + ": capacity-limited maximum craft leaves cursor empty", evidence);

            prepare(context, world, table, 8, false);
            context.runOnClient(client -> BetterRecipeBook.config.instantCraft.enabled = false);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, false);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 0, menu + ": disabled instant crafting never crafts on right-click", evidence);
            context.runOnClient(client -> BetterRecipeBook.config.instantCraft.enabled = true);

            prepare(context, world, table, 8, false);
            context.runOnClient(client -> client.player.containerMenu.setCarried(new ItemStack(Items.STONE, 3)));
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, false);
            context.waitTicks(10);
            require(count(world, Items.OAK_PLANKS) == 0, menu + ": occupied cursor refuses automatic crafting", evidence);
            context.runOnClient(client -> client.player.containerMenu.setCarried(ItemStack.EMPTY));

            prepare(context, world, table, 8, false);
            context.runOnClient(client -> {
                click(client, InputConstants.MOUSE_BUTTON_RIGHT, false);
                client.player.closeContainer();
                client.gui.setScreen(null);
            });
            synchronize(context, world);
            context.waitTicks(10);
            require(count(world, Items.OAK_PLANKS) == 0, menu + ": closing the menu cancels a pending craft", evidence);
        }
        prepare(context, world, false, 8, false);
        context.runOnClient(client -> {
            ItemStack output = new ItemStack(Items.OAK_PLANKS, 4);
            require(!ClientInventoryUtil.canStore(output, client.player.containerMenu, index -> index >= 5 && index <= 8),
                    "Armor slots reject ordinary crafting output during storage preflight", evidence);
        });
        require(CraftRequestChecks.newerState(0, 32767) && !CraftRequestChecks.newerState(32767, 0)
                        && !CraftRequestChecks.newerState(5, 5),
                "Container sequence comparison handles rollover, stale packets, and duplicates", evidence);
        context.runOnClient(client -> {
            BetterRecipeBook.instantCraftingManager.cancelPending();
            client.player.closeContainer();
            client.gui.setScreen(null);
        });
    }

    private static void localGridRedistribution(ClientGameTestContext context, TestSingleplayerContext world,
                                               boolean table, List<String> evidence) throws Exception {
        RecipeDisplayEntry original = context.computeOnClient(client -> client.player.getRecipeBook().getCollections().stream()
                .flatMap(collection -> collection.getRecipes().stream())
                .filter(entry -> entry.resultItems(net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(client.level))
                        .stream().anyMatch(stack -> stack.is(Items.CRAFTING_TABLE))).findFirst().orElseThrow());
        RecipeDisplayEntry local = new RecipeDisplayEntry(new RecipeDisplayId(-987655), original.display(), original.group(),
                original.category(), original.craftingRequirements());
        String name = table ? "3x3 grid redistribution" : "2x2 grid redistribution";
        for (int scenario = 0; scenario < 6; scenario++) {
            int selected = scenario;
            int planks = scenario == 1 || scenario == 5 ? 8 : 4;
            prepare(context, world, table, 0, false);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ItemStack stack = new ItemStack(Items.OAK_PLANKS, planks);
                if (selected == 3) stack.set(DataComponents.CUSTOM_NAME, Component.literal("Saved planks"));
                if (selected == 4) {
                    for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
                }
                ((AbstractCraftingMenu) player.containerMenu).getInputGridSlots().getFirst().set(stack);
                player.containerMenu.broadcastFullState();
            });
            synchronize(context, world);
            context.runOnClient(client -> {
                var book = client.player.getRecipeBook();
                book.remove(original.id());
                book.add(local);
                book.rebuildCollections();
                client.getConnection().searchTrees().updateRecipes(book, client.level);
                component(client).recipesUpdated();
                var accessor = (RecipeBookComponentAccessor) component(client);
                accessor.getSearchBox().setValue("crafting table");
                accessor.updateCollectionsInvoker(true, false);
            });
            context.waitTicks(3);
            if (scenario < 3 || scenario == 5) {
                context.runOnClient(client -> click(client, selected == 2 || selected == 5 ? InputConstants.MOUSE_BUTTON_RIGHT
                        : InputConstants.MOUSE_BUTTON_LEFT, selected == 1 || selected == 5, Items.CRAFTING_TABLE));
                int expectedTables = scenario == 5 ? 2 : 1;
                if (scenario == 2 || scenario == 5) {
                    world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst()
                            .getInventory().getNonEquipmentItems(), Items.CRAFTING_TABLE) == expectedTables, 150);
                }
                synchronize(context, world);
                if (scenario == 2 || scenario == 5) {
                    require(count(world, Items.CRAFTING_TABLE) == expectedTables && gridCount(world) == 0,
                            name + ": local " + (scenario == 5 ? "Shift-right" : "right")
                                    + " redistributes grid planks and crafts " + expectedTables + " table batches", evidence);
                } else {
                    int perSlot = scenario == 1 ? 2 : 1;
                    require(world.getServer().computeOnServer(server -> ((AbstractCraftingMenu) server.getPlayerList().getPlayers()
                                    .getFirst().containerMenu).getInputGridSlots().stream()
                                    .filter(slot -> slot.getItem().is(Items.OAK_PLANKS) && slot.getItem().getCount() == perSlot).count() == 4)
                                    && gridCount(world) == planks && count(world, Items.CRAFTING_TABLE) == 0,
                            name + ": local " + (scenario == 1 ? "Shift-left" : "left") + " redistributes without crafting", evidence);
                }
            } else {
                context.runOnClient(client -> require(!RecipePlacement.place(local, false),
                        name + ": redistribution refuses " + (selected == 3 ? "valuable ingredients" : "insufficient temporary storage"), evidence));
                synchronize(context, world);
                require(gridCount(world) == 4 && count(world, Items.CRAFTING_TABLE) == 0 && count(world, Items.OAK_PLANKS) == 0,
                        name + ": refused redistribution preserves all grid ingredients", evidence);
                if (scenario == 3) {
                    require(world.getServer().computeOnServer(server -> ((AbstractCraftingMenu) server.getPlayerList().getPlayers()
                                    .getFirst().containerMenu).getInputGridSlots().getFirst().getItem().getHoverName().getString().equals("Saved planks")),
                            name + ": refused redistribution preserves the custom name", evidence);
                }
            }
            require(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()),
                    name + ": scenario " + scenario + " leaves the cursor empty", evidence);
            context.runOnClient(client -> {
                var book = client.player.getRecipeBook();
                book.remove(local.id());
                book.add(original);
                book.rebuildCollections();
                client.getConnection().searchTrees().updateRecipes(book, client.level);
                component(client).recipesUpdated();
            });
        }
    }

    private static void localInventoryCraft(ClientGameTestContext context, TestSingleplayerContext world, boolean table,
                                            RecipeDisplayEntry original, RecipeDisplayId localId, int available, boolean maximum,
                                            List<String> evidence) throws Exception {
        prepare(context, world, table, available, false);
        context.runOnClient(client -> {
            var book = client.player.getRecipeBook();
            book.remove(original.id());
            book.add(new RecipeDisplayEntry(localId, original.display(), original.group(), original.category(), original.craftingRequirements()));
            book.rebuildCollections();
            client.getConnection().searchTrees().updateRecipes(book, client.level);
            component(client).recipesUpdated();
        });
        context.waitTicks(3);
        clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, maximum);
        int expected = maximum ? available * 4 : 4;
        world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == expected, 200);
        synchronize(context, world);
        String name = (table ? "3x3" : "2x2") + " fresh local recipe, inventory source=" + available + ", maximum=" + maximum;
        require(count(world, Items.OAK_PLANKS) == expected && count(world, Items.OAK_LOG) + gridCount(world) == available - expected / 4,
                name + ": ordinary placement and crafting conserve server ingredients", evidence);
        require(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()), name + ": cursor is empty", evidence);
        context.runOnClient(client -> {
            var book = client.player.getRecipeBook();
            book.remove(localId);
            book.add(original);
            book.rebuildCollections();
            client.getConnection().searchTrees().updateRecipes(book, client.level);
            component(client).recipesUpdated();
        });
    }

    private static void prepare(ClientGameTestContext context, TestSingleplayerContext world, boolean table, int logs, boolean full) throws Exception {
        context.runOnClient(client -> {
            BetterRecipeBook.instantCraftingManager.cancelPending();
            client.player.closeContainer();
            client.gui.setScreen(null);
        });
        synchronize(context, world);
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, logs));
            if (full) {
                for (int i = 1; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            }
            player.inventoryMenu.broadcastFullState();
            if (table) {
                var position = player.blockPosition().offset(1, 0, 0);
                player.level().setBlock(position, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                player.openMenu(new SimpleMenuProvider((id, inventory, user) -> new CraftingMenu(id, inventory,
                        ContainerLevelAccess.create(user.level(), position)), Component.literal("BRB crafting QA")));
            }
        });
        world.getConnection().waitForClientboundPackets();
        if (!table) context.runOnClient(client -> client.gui.setScreen(new InventoryScreen(client.player)));
        context.waitTicks(5);
        context.runOnClient(client -> {
            RecipeBookComponent<?> component = component(client);
            if (!component.isVisible()) component.toggleVisibility();
            var accessor = (RecipeBookComponentAccessor) component;
            accessor.getSearchBox().setValue("oak planks");
            accessor.updateCollectionsInvoker(true, false);
        });
        context.waitTicks(3);
    }

    private static RecipeBookComponent<?> component(Minecraft client) throws ReflectiveOperationException {
        Field field = AbstractRecipeBookScreen.class.getDeclaredField("recipeBookComponent");
        field.setAccessible(true);
        return (RecipeBookComponent<?>) field.get(client.gui.screen());
    }

    private static void clickRecipe(ClientGameTestContext context, int button, boolean shift) throws Exception {
        context.runOnClient(client -> click(client, button, shift));
    }

    private static void click(Minecraft client, int button, boolean shift) throws ReflectiveOperationException {
        click(client, button, shift, Items.OAK_PLANKS);
    }

    private static void click(Minecraft client, int button, boolean shift, Item output) throws ReflectiveOperationException {
        RecipeBookComponent<?> component = component(client);
        var page = ((RecipeBookComponentAccessor) component).getRecipeBookPage();
        RecipeButton tile = recipeTile(client, output);
        MouseButtonEvent event = new MouseButtonEvent(tile.getX() + 12, tile.getY() + 12, new MouseButtonInfo(button, shift ? InputConstants.MOD_SHIFT : 0));
        if (!component.mouseClicked(event, false) && (button == InputConstants.MOUSE_BUTTON_LEFT || BetterRecipeBook.config.instantCraft.enabled)) throw new AssertionError("Recipe widget did not consume click");
    }

    private static RecipeButton plankTile(Minecraft client) throws ReflectiveOperationException {
        return recipeTile(client, Items.OAK_PLANKS);
    }

    private static RecipeButton recipeTile(Minecraft client, Item output) throws ReflectiveOperationException {
        var page = ((RecipeBookComponentAccessor) component(client)).getRecipeBookPage();
        return ((RecipeBookPageAccessor) page).getButtons().stream()
                .filter(widget -> widget.visible && !widget.getDisplayStack().isEmpty() && widget.getDisplayStack().getItem() == output)
                .findFirst().orElseThrow(() -> new AssertionError("No " + output + " recipe widget in the visible page"));
    }

    private static void synchronize(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getConnection().waitForServerboundPackets();
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(3);
    }

    private static int count(TestSingleplayerContext world, Item item) {
        return world.getServer().computeOnServer(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), item));
    }

    private static int countServer(List<ItemStack> inventory, Item item) {
        return inventory.stream().filter(stack -> stack.getItem() == item).mapToInt(ItemStack::getCount).sum();
    }

    private static int gridCount(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> ((AbstractCraftingMenu) server.getPlayerList().getPlayers().getFirst().containerMenu)
                .getInputGridSlots().stream().mapToInt(slot -> slot.getItem().getCount()).sum());
    }

    private static void require(boolean passed, String description, List<String> evidence) {
        if (!passed) throw new AssertionError(description);
        evidence.add("PASS: " + description);
    }
}
