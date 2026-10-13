package marsh.town.brb.clientqa;

import com.mojang.blaze3d.platform.InputConstants;

import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.CraftRequestChecks;
import marsh.town.brb.config.CraftAllMethod;
import marsh.town.brb.config.CraftingOverflow;
import marsh.town.brb.config.Config;
import me.shedaniel.autoconfig.AutoConfigClient;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.item.ItemEntity;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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
        CraftAllMethod originalMethod = context.computeOnClient(client -> BetterRecipeBook.config.instantCraft.craftAllMethod);
        CraftingOverflow originalOverflow = context.computeOnClient(client -> BetterRecipeBook.config.instantCraft.overflow);
        CraftPacketTrace trace = context.computeOnClient(CraftPacketTrace::new);
        try {
            for (CraftAllMethod method : CraftAllMethod.values()) {
                selectCraftSettings(context, method, CraftingOverflow.STOP_BEFORE_DROP, evidence);
                runMethodChecks(context, world, evidence, method, trace);
                if (method == CraftAllMethod.FILL_GRID) unequalGridRefusal(context, world, trace, evidence);
                cakeRemainders(context, world, method, trace, evidence);
                overflowChecks(context, world, method, evidence);
            }
        } finally {
            context.runOnClient(client -> {
                trace.close();
                BetterRecipeBook.config.instantCraft.craftAllMethod = originalMethod;
                BetterRecipeBook.config.instantCraft.overflow = originalOverflow;
                BetterRecipeBook.configHolder.save();
                BetterRecipeBook.instantCraftingManager.cancelPending();
            });
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

    static void selectCraftSettings(ClientGameTestContext context, CraftAllMethod method,
                                    CraftingOverflow overflow, List<String> evidence) {
        // Cloth disables Save when the requested values already match the saved defaults.
        // Save the other strategy first so the requested selection always proves the full UI flow.
        if (context.computeOnClient(client -> BetterRecipeBook.config.instantCraft.craftAllMethod == method
                && BetterRecipeBook.config.instantCraft.overflow == overflow)) {
            CraftAllMethod other = method == CraftAllMethod.SINGLE_BATCHES ? CraftAllMethod.FILL_GRID : CraftAllMethod.SINGLE_BATCHES;
            selectCraftSettings(context, other, overflow, evidence);
        }
        var settings = context.computeOnClient(client -> {
            Screen parent = client.gui.screen() == null ? new InventoryScreen(client.player) : client.gui.screen();
            var screen = (ClothConfigScreen) AutoConfigClient.getConfigScreen(Config.class, parent).get();
            client.gui.setScreen(screen);
            return screen;
        });
        context.waitTicks(2);
        chooseSetting(context, settings, "instantCraft.craftAllMethod", method);
        chooseSetting(context, settings, "instantCraft.overflow", overflow);
        context.waitFor(client -> settings.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .anyMatch(button -> button.active && button.visible && button.getY() > 0
                        && button.getMessage().equals(Component.translatable("text.cloth-config.save_and_done"))), 100);
        context.runOnClient(client -> {
            var save = settings.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.active && button.visible
                            && button.getMessage().equals(Component.translatable("text.cloth-config.save_and_done")))
                    .findFirst().orElseThrow();
            clickSetting(settings, save);
            require(BetterRecipeBook.config.instantCraft.craftAllMethod == method
                            && BetterRecipeBook.config.instantCraft.overflow == overflow && client.gui.screen() != settings,
                    "Actual config clicks and Save select " + method + " with " + overflow + " before crafting", evidence);
        });
    }

    private static void chooseSetting(ClientGameTestContext context, ClothConfigScreen settings, String path, Object value) {
        var entry = context.computeOnClient(client -> settings.getCategorizedEntries().values().stream().flatMap(List::stream)
                .filter(candidate -> candidate.getFieldName().equals(Component.translatable("text.autoconfig.brb.option." + path)))
                .findFirst().orElseThrow());
        context.runOnClient(client -> {
            int row = settings.listWidget.getRowTop(settings.listWidget.children().indexOf(entry));
            settings.listWidget.scrollTo(settings.listWidget.getScroll() + row - settings.listWidget.top - 8, false);
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            for (int attempts = 0; !entry.getValue().equals(value) && attempts < 4; attempts++) {
                clickSetting(settings, (Button) entry.children().getFirst());
            }
            if (!entry.getValue().equals(value)) throw new AssertionError("Cannot select " + path + " through the rendered control");
        });
        context.waitTicks(2);
    }

    private static void clickSetting(Screen screen, Button button) {
        var event = new MouseButtonEvent(button.getX() + button.getWidth() / 2.0,
                button.getY() + button.getHeight() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        if (!screen.mouseClicked(event, false)) throw new AssertionError("Settings screen ignored " + button.getMessage());
        screen.mouseReleased(event);
    }

    private static void overflowChecks(ClientGameTestContext context, TestSingleplayerContext world,
                                       CraftAllMethod method, List<String> evidence) throws Exception {
        for (CraftingOverflow overflow : CraftingOverflow.values()) {
            selectCraftSettings(context, method, overflow, evidence);
            for (boolean table : new boolean[] {false, true}) {
                for (boolean partial : new boolean[] {false, true}) {
                    prepare(context, world, table, 0, false);
                    world.getServer().runOnServer(server -> {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32)).forEach(ItemEntity::discard);
                        for (int index = 0; index < 36; index++) player.getInventory().setItem(index, new ItemStack(Items.STONE, 64));
                        if (partial) player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 63));
                        ((AbstractCraftingMenu) player.containerMenu).getInputGridSlots().getFirst().set(new ItemStack(Items.OAK_LOG, 8));
                        player.containerMenu.broadcastFullState();
                    });
                    synchronize(context, world);
                    int beforeDropped = droppedOutput(world, Items.OAK_PLANKS);
                    int expectedBatches = switch (overflow) {
                        case STOP_BEFORE_DROP -> 0;
                        case DROP_AND_CONTINUE -> 8;
                        case STOP_AFTER_DROP -> 1;
                    };
                    int basePlanks = partial ? 63 : 0;
                    clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, true);
                    if (expectedBatches > 0) world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst()
                            .getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) + droppedOutputServer(server, Items.OAK_PLANKS)
                            == basePlanks + beforeDropped + expectedBatches * 4, 300);
                    context.waitTicks(20);
                    synchronize(context, world);
                    String name = method + "/" + overflow + "/" + (table ? "3x3" : "2x2") + (partial ? "/one free output space" : "/full storage");
                    int actualDropped = droppedOutput(world, Items.OAK_PLANKS) - beforeDropped;
                    int expectedDropped = expectedBatches * 4 - (partial && expectedBatches > 0 ? 1 : 0);
                    require(count(world, Items.OAK_PLANKS) + actualDropped == basePlanks + expectedBatches * 4,
                            name + ": inventory and dropped output conserve every crafted batch", evidence);
                    require(actualDropped == expectedDropped && gridCount(world) == 8 - expectedBatches,
                            name + ": overflow stops at the selected batch and preserves all uncrafted inputs", evidence);
                    require(cursorEmpty(context, world), name + ": overflow leaves both authoritative and client cursors empty", evidence);
                    require(world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                                    .getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(Items.STONE))
                                    .mapToInt(ItemStack::getCount).sum()) == (partial ? 35 : 36) * 64,
                            name + ": overflow never replaces unrelated inventory stacks", evidence);
                }
            }
        }
        selectCraftSettings(context, method, CraftingOverflow.STOP_BEFORE_DROP, evidence);
    }

    private static int droppedOutput(TestSingleplayerContext world, Item item) {
        return world.getServer().computeOnServer(server -> droppedOutputServer(server, item));
    }

    private static int droppedOutputServer(net.minecraft.server.MinecraftServer server, Item item) {
        var player = server.getPlayerList().getPlayers().getFirst();
        return player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32)).stream()
                .map(ItemEntity::getItem).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void runMethodChecks(ClientGameTestContext context, TestSingleplayerContext world,
                                        List<String> evidence, CraftAllMethod method, CraftPacketTrace trace) throws Exception {
        for (boolean table : new boolean[] {false, true}) {
            String menu = method + ": " + (table ? "3x3 table" : "2x2 inventory");
            int droppedBefore;
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
            localInventoryCraft(context, world, table, original, localId, 68, true, evidence);
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
            require(cursorEmpty(context, world), menu + ": single batch leaves cursor empty", evidence);

            prepare(context, world, table, 8, false);
            trace.resultActions.clear();
            droppedBefore = droppedItems(world);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, true);
            world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == 32, 250);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 32 && gridCount(world) == 0,
                    menu + ": Shift-right-click crafts all eight batches", evidence);
            require(method == CraftAllMethod.FILL_GRID
                            ? trace.resultActions.size() == 1 && trace.resultActions.peek() == ContainerInput.QUICK_MOVE
                            : !trace.resultActions.contains(ContainerInput.QUICK_MOVE)
                                    && trace.resultActions.stream().filter(action -> action == ContainerInput.PICKUP).count() == 8,
                    menu + ": selected craft-all method uses " + (method == CraftAllMethod.FILL_GRID ? "one true Shift-click result transfer with zero result pickups" : "eight single result pickups"), evidence);
            require(cursorEmpty(context, world), menu + ": maximum craft leaves cursor empty", evidence);
            require(droppedItems(world) == droppedBefore, menu + ": maximum craft never overflows output or ingredients into the world", evidence);

            prepare(context, world, table, 8, true);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 63));
                player.containerMenu.broadcastFullState();
            });
            synchronize(context, world);
            droppedBefore = droppedItems(world);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, true);
            world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == 95, 250);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 95 && gridCount(world) == 0,
                    menu + ": maximum crafting survives a batch split between a partial stack and an empty slot", evidence);
            require(cursorEmpty(context, world),
                    menu + ": split storage confirmations leave no output on the cursor", evidence);
            require(droppedItems(world) == droppedBefore, menu + ": maximum craft never overflows output or ingredients into the world", evidence);

            prepare(context, world, table, 64, false);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(1, new ItemStack(Items.OAK_LOG, 4));
                player.inventoryMenu.broadcastFullState();
            });
            synchronize(context, world);
            droppedBefore = droppedItems(world);
            trace.resultActions.clear();
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, true);
            world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == 272, 500);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 272 && gridCount(world) == 0,
                    menu + ": maximum crafting refills beyond the first 64-batch grid placement", evidence);
            require(method == CraftAllMethod.FILL_GRID
                            ? trace.resultActions.size() == 2 && trace.resultActions.stream().allMatch(action -> action == ContainerInput.QUICK_MOVE)
                            : trace.resultActions.size() == 68 && trace.resultActions.stream().allMatch(action -> action == ContainerInput.PICKUP),
                    menu + ": 68 batches use " + (method == CraftAllMethod.FILL_GRID ? "two true Shift-click loads with zero result pickups" : "68 individual result pickups"), evidence);
            require(droppedItems(world) == droppedBefore, menu + ": maximum craft never overflows output or ingredients into the world", evidence);

            prepare(context, world, table, 8, true);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, false);
            context.waitTicks(15);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 0 && count(world, Items.OAK_LOG) + gridCount(world) == 8,
                    menu + ": full inventory refuses a batch without consuming ingredients", evidence);
            require(cursorEmpty(context, world), menu + ": full inventory leaves cursor empty", evidence);

            prepare(context, world, table, 64, true);
            droppedBefore = droppedItems(world);
            clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, true);
            world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == 64, 300);
            context.waitTicks(15);
            synchronize(context, world);
            require(count(world, Items.OAK_PLANKS) == 64 && gridCount(world) == 48,
                    menu + ": maximum craft stops at storage capacity and preserves 48 unconsumed ingredients", evidence);
            require(cursorEmpty(context, world),
                    menu + ": capacity-limited maximum craft leaves cursor empty", evidence);
            require(droppedItems(world) == droppedBefore, menu + ": maximum craft never overflows output or ingredients into the world", evidence);

            partialBatchRefusal(context, world, table, menu, evidence);

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
    }

    private static void unequalGridRefusal(ClientGameTestContext context, TestSingleplayerContext world,
                                            CraftPacketTrace trace, List<String> evidence) throws Exception {
        for (boolean table : new boolean[] {false, true}) {
            prepare(context, world, table, 0, false);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var menu = (AbstractCraftingMenu) player.containerMenu;
                var inputs = menu.getInputGridSlots();
                int width = menu.getGridWidth();
                inputs.get(0).set(new ItemStack(Items.OAK_PLANKS, 1));
                inputs.get(1).set(new ItemStack(Items.OAK_PLANKS, 2));
                inputs.get(width).set(new ItemStack(Items.OAK_PLANKS, 2));
                inputs.get(width + 1).set(new ItemStack(Items.OAK_PLANKS, 2));
                player.containerMenu.broadcastFullState();
            });
            synchronize(context, world);
            context.runOnClient(client -> {
                var accessor = (RecipeBookComponentAccessor) component(client);
                accessor.getSearchBox().setValue("crafting table");
                accessor.updateCollectionsInvoker(true, false);
            });
            context.waitTicks(3);
            trace.resultActions.clear();
            int droppedBefore = droppedItems(world);
            context.runOnClient(client -> click(client, InputConstants.MOUSE_BUTTON_RIGHT, true, Items.CRAFTING_TABLE));
            context.waitTicks(20);
            synchronize(context, world);
            String name = "FILL_GRID: " + (table ? "3x3 table" : "2x2 inventory");
            require(trace.resultActions.isEmpty() && count(world, Items.CRAFTING_TABLE) == 0,
                    name + ": unequal prepared inputs refuse bulk crafting before any result click", evidence);
            require(world.getServer().computeOnServer(server -> {
                var menu = (AbstractCraftingMenu) server.getPlayerList().getPlayers().getFirst().containerMenu;
                var inputs = menu.getInputGridSlots();
                int width = menu.getGridWidth();
                return inputs.get(0).getItem().getCount() == 1 && inputs.get(1).getItem().getCount() == 2
                        && inputs.get(width).getItem().getCount() == 2 && inputs.get(width + 1).getItem().getCount() == 2
                        && inputs.stream().filter(slot -> slot.getItem().is(Items.OAK_PLANKS)).mapToInt(slot -> slot.getItem().getCount()).sum() == 7;
            }), name + ": refused unequal grid preserves every prepared ingredient", evidence);
            require(cursorEmpty(context, world) && droppedItems(world) == droppedBefore,
                    name + ": unequal-grid refusal leaves empty cursors and drops nothing", evidence);
        }
    }

    private static void partialBatchRefusal(ClientGameTestContext context, TestSingleplayerContext world,
                                            boolean table, String menu, List<String> evidence) throws Exception {
        prepare(context, world, table, 64, true);
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            ((AbstractCraftingMenu) player.containerMenu).getInputGridSlots().getFirst().set(new ItemStack(Items.OAK_LOG, 64));
            player.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
            player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 63));
            player.containerMenu.broadcastFullState();
        });
        synchronize(context, world);
        int droppedBefore = droppedItems(world);
        clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, true);
        context.waitTicks(20);
        synchronize(context, world);
        require(count(world, Items.OAK_PLANKS) == 63 && gridCount(world) == 64 && count(world, Items.OAK_LOG) == 0,
                menu + ": one free output space refuses a four-item batch and conserves all prepared ingredients", evidence);
        require(cursorEmpty(context, world), menu + ": partial-batch refusal leaves both client and server cursors empty", evidence);
        require(droppedItems(world) == droppedBefore, menu + ": partial-batch refusal drops no output or ingredients", evidence);
    }

    private static void cakeRemainders(ClientGameTestContext context, TestSingleplayerContext world,
                                       CraftAllMethod method, CraftPacketTrace trace, List<String> evidence) throws Exception {
        prepare(context, world, true, 0, false);
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            for (int slot = 0; slot < 9; slot++) player.getInventory().setItem(slot, new ItemStack(Items.MILK_BUCKET));
            player.getInventory().setItem(9, new ItemStack(Items.SUGAR, 6));
            player.getInventory().setItem(10, new ItemStack(Items.WHEAT, 9));
            player.getInventory().setItem(11, new ItemStack(Items.EGG, 3));
            player.containerMenu.broadcastFullState();
        });
        synchronize(context, world);
        int droppedBefore = droppedItems(world);
        context.runOnClient(client -> {
            var accessor = (RecipeBookComponentAccessor) component(client);
            accessor.getSearchBox().setValue("cake");
            accessor.updateCollectionsInvoker(true, false);
        });
        context.waitTicks(3);
        trace.resultActions.clear();
        context.runOnClient(client -> click(client, InputConstants.MOUSE_BUTTON_RIGHT, true, Items.CAKE));
        world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst()
                .getInventory().getNonEquipmentItems(), Items.CAKE) == 3, 300);
        synchronize(context, world);
        require(trace.resultActions.size() == 3 && trace.resultActions.stream().allMatch(action -> action ==
                        (method == CraftAllMethod.FILL_GRID ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP)),
                method + ": cake remainder loads use three " + (method == CraftAllMethod.FILL_GRID ? "true Shift-clicks and zero result pickups" : "individual result pickups"), evidence);
        int gridBuckets = world.getServer().computeOnServer(server -> ((AbstractCraftingMenu) server.getPlayerList().getPlayers()
                .getFirst().containerMenu).getInputGridSlots().stream()
                .filter(slot -> slot.getItem().is(Items.BUCKET)).mapToInt(slot -> slot.getItem().getCount()).sum());
        String actual = world.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            return "; inventory=" + player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).toList()
                    + "; grid=" + ((AbstractCraftingMenu) player.containerMenu).getInputGridSlots().stream().map(slot -> slot.getItem()).toList();
        });
        require(count(world, Items.CAKE) == 3 && count(world, Items.BUCKET) == 6 && gridBuckets == 3 && count(world, Items.MILK_BUCKET) == 0,
                method + ": three cakes conserve all nine buckets, six returned and three vanilla grid remainders" + actual, evidence);
        require(count(world, Items.SUGAR) == 0 && count(world, Items.WHEAT) == 0 && count(world, Items.EGG) == 0 && gridCount(world) == 3,
                method + ": cake crafting consumes exactly three batches and leaves only the final bucket remainders" + actual, evidence);
        require(cursorEmpty(context, world) && droppedItems(world) == droppedBefore,
                method + ": cake outputs and remainders leave no cursor items or dropped items", evidence);
    }

    private static boolean cursorEmpty(ClientGameTestContext context, TestSingleplayerContext world) {
        return context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty())
                && world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().containerMenu.getCarried().isEmpty());
    }

    private static int droppedItems(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            return player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32)).stream()
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
        });
    }

    /** Observes vanilla result-slot packets so the two selected strategies cannot silently behave identically. */
    static final class CraftPacketTrace implements AutoCloseable {
        private final Channel channel;
        final Queue<ContainerInput> resultActions = new ConcurrentLinkedQueue<>();

        CraftPacketTrace(Minecraft client) throws ReflectiveOperationException {
            this(client, 0);
        }

        CraftPacketTrace(Minecraft client, int resultSlot) throws ReflectiveOperationException {
            Field field = net.minecraft.network.Connection.class.getDeclaredField("channel");
            field.setAccessible(true);
            channel = (Channel) field.get(client.getConnection().getConnection());
            channel.pipeline().addLast("brb_craft_qa", new ChannelDuplexHandler() {
                @Override
                public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                    if (message instanceof ServerboundContainerClickPacket click && click.slotNum() == resultSlot) {
                        resultActions.add(click.containerInput());
                    }
                    super.write(context, message, promise);
                }
            });
        }

        @Override
        public void close() {
            channel.pipeline().remove("brb_craft_qa");
        }
    }

    private static void localGridRedistribution(ClientGameTestContext context, TestSingleplayerContext world,
                                               boolean table, List<String> evidence) throws Exception {
        RecipeDisplayEntry original = context.computeOnClient(client -> client.player.getRecipeBook().getCollections().stream()
                .flatMap(collection -> collection.getRecipes().stream())
                .filter(entry -> entry.resultItems(net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(client.level))
                        .stream().anyMatch(stack -> stack.is(Items.CRAFTING_TABLE))).findFirst().orElseThrow());
        RecipeDisplayEntry local = new RecipeDisplayEntry(new RecipeDisplayId(-987655), original.display(), original.group(),
                original.category(), original.craftingRequirements());
        String name = context.computeOnClient(client -> BetterRecipeBook.config.instantCraft.craftAllMethod)
                + ": " + (table ? "3x3 grid redistribution" : "2x2 grid redistribution");
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
        prepare(context, world, table, Math.min(available, 64), false);
        if (available > 64) {
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(1, new ItemStack(Items.OAK_LOG, available - 64));
                player.containerMenu.broadcastFullState();
            });
            synchronize(context, world);
        }
        context.runOnClient(client -> {
            var book = client.player.getRecipeBook();
            book.remove(original.id());
            book.add(new RecipeDisplayEntry(localId, original.display(), original.group(), original.category(), original.craftingRequirements()));
            book.rebuildCollections();
            client.getConnection().searchTrees().updateRecipes(book, client.level);
            component(client).recipesUpdated();
        });
        context.waitTicks(3);
        int droppedBefore = droppedItems(world);
        clickRecipe(context, InputConstants.MOUSE_BUTTON_RIGHT, maximum);
        int expected = maximum ? available * 4 : 4;
        world.getServer().waitFor(server -> countServer(server.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems(), Items.OAK_PLANKS) == expected, available > 64 ? 600 : 200);
        synchronize(context, world);
        String name = context.computeOnClient(client -> BetterRecipeBook.config.instantCraft.craftAllMethod)
                + ": " + (table ? "3x3" : "2x2") + " fresh local recipe, inventory source=" + available + ", maximum=" + maximum;
        require(count(world, Items.OAK_PLANKS) == expected && count(world, Items.OAK_LOG) + gridCount(world) == available - expected / 4,
                name + ": ordinary placement and crafting conserve server ingredients", evidence);
        require(cursorEmpty(context, world), name + ": both client and server cursors are empty", evidence);
        require(droppedItems(world) == droppedBefore, name + ": crafting drops no output or ingredients", evidence);
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
            // Closing the preceding full-inventory fixture can spill its leftover grid inputs.
            player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32)).forEach(ItemEntity::discard);
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
