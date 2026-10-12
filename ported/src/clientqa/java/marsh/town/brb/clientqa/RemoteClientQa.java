package marsh.town.brb.clientqa;

import com.mojang.blaze3d.platform.InputConstants;
import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.mixins.accessors.RecipeBookComponentAccessor;
import marsh.town.brb.mixins.accessors.RecipeBookPageAccessor;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import marsh.town.brb.util.ClientRecipeRegistry;
import marsh.town.brb.workstation.WorkstationRecipes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** An opt-in packaged client fixture for an owned, operator-enabled localhost vanilla server. */
public final class RemoteClientQa implements ClientModInitializer {
    private final List<String> evidence = new ArrayList<>();
    private final String nonce = Long.toUnsignedString(System.nanoTime(), 36);
    private int ticks;
    private int stage;
    private int nextTick;
    private int caseIndex;
    private int actionStarted;
    private boolean finished;

    private record Scenario(String name, int button, boolean shift, boolean undiscovered,
                            int expectedGrid, int expectedInventoryLogs, int expectedPlanks) {}

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("left-click normal placement", InputConstants.MOUSE_BUTTON_LEFT, false, false, 1, 7, 0),
            new Scenario("Shift-left-click maximum placement", InputConstants.MOUSE_BUTTON_LEFT, true, false, 8, 0, 0),
            new Scenario("right-click one batch", InputConstants.MOUSE_BUTTON_RIGHT, false, false, 0, 7, 4),
            new Scenario("Shift-right-click maximum crafting", InputConstants.MOUSE_BUTTON_RIGHT, true, false, 0, 0, 32),
            new Scenario("undiscovered local placement", InputConstants.MOUSE_BUTTON_LEFT, false, true, 1, 7, 0),
            new Scenario("undiscovered local right-click crafting", InputConstants.MOUSE_BUTTON_RIGHT, false, true, 0, 7, 4));

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("brb.remoteQa")) return;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (finished) return;
        try {
            if (++ticks > 2500) throw new AssertionError("Remote QA timeout at stage " + stage + ", scenario " + caseIndex);
            if (client.player == null || client.level == null || client.gameMode == null || ticks < nextTick) return;
            if (stage == 0 && client.player.isDeadOrDying()) {
                client.player.respawn();
                nextTick = ticks + 10;
                return;
            }
            switch (stage) {
                case 0 -> {
                    check(client.getSingleplayerServer() == null && !client.hasSingleplayerServer(), "connected without an integrated server");
                    var server = client.getCurrentServer();
                    check(server != null && (server.ip.startsWith("127.0.0.1:") || server.ip.startsWith("localhost:")), "fixture is restricted to the owned loopback server");
                    client.options.pauseOnLostFocus = false;
                    if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) {
                        check(com.terraformersmc.modmenu.ModMenu.hasConfigScreen("brb"), "optional Mod Menu registers BRB settings");
                        var settings = com.terraformersmc.modmenu.ModMenu.getConfigScreen("brb", client.gui.screen());
                        check(settings != null, "Mod Menu creates the retained Cloth Config screen");
                        client.gui.setScreen(settings);
                    }
                    BetterRecipeBook.config.recipeDisplayMode = RecipeDisplayMode.ALL;
                    BetterRecipeBook.config.instantCraft.enabled = true;
                    BetterRecipeBook.config.newRecipes.unlockAll = true;
                    long localRecipes = ClientRecipeRegistry.provider().lookupOrThrow(Registries.RECIPE).listElements().count();
                    check(localRecipes > 1000, "packaged vanilla recipe catalogue loads remotely: " + localRecipes + " recipes");
                    int brews = WorkstationRecipes.forMenu(new BrewingStandMenu(42, client.player.getInventory())).size();
                    int smiths = WorkstationRecipes.forMenu(new SmithingMenu(43, client.player.getInventory())).size();
                    check(brews > 0 && smiths > 0, "remote brewing and smithing catalogues are populated: " + brews + "/" + smiths);
                    command(client, "gamemode survival @s");
                    command(client, "scoreboard objectives add brbqa dummy");
                    marker(client, "READY");
                    advance(1, 5);
                }
                case 1 -> {
                    if (!seen(client, "READY")) return;
                    setupScenario(client);
                    advance(2, 8);
                }
                case 2 -> {
                    if (!seen(client, "SETUP-" + caseIndex) || count(client, Items.OAK_LOG) != 8 || count(client, Items.OAK_PLANKS) != 0) return;
                    check(client.gameMode.getPlayerMode() == net.minecraft.world.level.GameType.SURVIVAL, "scenario runs in survival: " + scenario().name());
                    client.gui.setScreen(new InventoryScreen(client.player));
                    RecipeBookComponent<?> component = component(client);
                    if (!component.isVisible()) component.toggleVisibility();
                    ((RecipeBookComponentAccessor) component).getSearchBox().setValue("oak planks");
                    component.recipesUpdated();
                    advance(3, 5);
                }
                case 3 -> {
                    RecipeButton tile = plankTile(client);
                    if (tile == null || (tile.getCurrentRecipe().index() < 0) != scenario().undiscovered()) return;
                    check((tile.getCurrentRecipe().index() < 0) == scenario().undiscovered(),
                            "recipe display provenance matches scenario: " + scenario().name());
                    var event = new MouseButtonEvent(tile.getX() + 12, tile.getY() + 12,
                            new MouseButtonInfo(scenario().button(), scenario().shift() ? InputConstants.MOD_SHIFT : 0));
                    check(component(client).mouseClicked(event, false), "real recipe widget consumes " + scenario().name());
                    actionStarted = ticks;
                    advance(4, 4);
                }
                case 4 -> {
                    Scenario test = scenario();
                    if (gridCount(client) != test.expectedGrid() || count(client, Items.OAK_LOG) != test.expectedInventoryLogs()
                            || count(client, Items.OAK_PLANKS) != test.expectedPlanks()) {
                        if (ticks - actionStarted > 250) throw new AssertionError("Recipe action did not settle: " + test.name()
                                + "; grid=" + gridCount(client) + "; logs=" + count(client, Items.OAK_LOG) + "; planks=" + count(client, Items.OAK_PLANKS));
                        return;
                    }
                    check(client.player.containerMenu.getCarried().isEmpty(), "cursor is empty after " + test.name());
                    // Vanilla clear ... 0 counts player inventory, crafting inputs and the carried item.
                    queryCount(client, "oak_log", test.expectedInventoryLogs() + test.expectedGrid(), "LOGS-" + caseIndex);
                    queryCount(client, "oak_planks", test.expectedPlanks(), "PLANKS-" + caseIndex);
                    advance(5, 3);
                }
                case 5 -> {
                    if (!seen(client, "LOGS-" + caseIndex) || !seen(client, "PLANKS-" + caseIndex)) {
                        // Native container actions are queued separately from commands. Retry after server settlement.
                        queryCount(client, "oak_log", scenario().expectedInventoryLogs() + scenario().expectedGrid(), "LOGS-" + caseIndex);
                        queryCount(client, "oak_planks", scenario().expectedPlanks(), "PLANKS-" + caseIndex);
                        nextTick = ticks + 20;
                        return;
                    }
                    check(true, "vanilla server confirms authoritative item counts for " + scenario().name());
                    BetterRecipeBook.instantCraftingManager.cancelPending();
                    client.player.closeContainer();
                    client.gui.setScreen(null);
                    int totalLogs = scenario().expectedInventoryLogs() + scenario().expectedGrid();
                    queryCount(client, "oak_log", totalLogs, "RETURNED-" + caseIndex);
                    advance(6, 5);
                }
                case 6 -> {
                    if (!seen(client, "RETURNED-" + caseIndex)) return;
                    check(true, "server menu closure preserves every unconsumed input for " + scenario().name());
                    caseIndex++;
                    if (caseIndex == SCENARIOS.size()) {
                        evidence.add("RESULT: PASS; " + evidence.stream().filter(line -> line.startsWith("PASS:")).count() + " assertions");
                        finish(client);
                    } else {
                        setupScenario(client);
                        advance(2, 8);
                    }
                }
                default -> throw new AssertionError("Unknown remote stage " + stage);
            }
        } catch (Throwable failure) {
            evidence.add("RESULT: FAIL; " + failure);
            failure.printStackTrace();
            finish(client);
        }
    }

    private Scenario scenario() {
        return SCENARIOS.get(caseIndex);
    }

    private void setupScenario(Minecraft client) {
        BetterRecipeBook.instantCraftingManager.cancelPending();
        client.player.closeContainer();
        client.gui.setScreen(null);
        command(client, "clear @s");
        command(client, "give @s minecraft:oak_log 8");
        command(client, "recipe " + (scenario().undiscovered() ? "take" : "give") + " @s minecraft:oak_planks");
        marker(client, "SETUP-" + caseIndex);
    }

    private void queryCount(Minecraft client, String itemId, int expected, String check) {
        command(client, "execute store result score @s brbqa run clear @s minecraft:" + itemId + " 0");
        command(client, "execute if score @s brbqa matches " + expected + " run tellraw @s {\"text\":\"" + token(check) + "\"}");
    }

    private static void command(Minecraft client, String command) {
        client.player.connection.sendCommand(command);
    }

    private void marker(Minecraft client, String marker) {
        command(client, "tellraw @s {\"text\":\"" + token(marker) + "\"}");
    }

    private String token(String marker) {
        return "BRBREMOTE-" + nonce + "-" + marker;
    }

    @SuppressWarnings("unchecked")
    private boolean seen(Minecraft client, String marker) throws ReflectiveOperationException {
        Field field = ChatComponent.class.getDeclaredField("allMessages");
        field.setAccessible(true);
        List<GuiMessage> messages = (List<GuiMessage>) field.get(client.gui.hud.getChat());
        return messages.stream().anyMatch(message -> message.content().getString().equals(token(marker)));
    }

    private static RecipeBookComponent<?> component(Minecraft client) throws ReflectiveOperationException {
        Field field = AbstractRecipeBookScreen.class.getDeclaredField("recipeBookComponent");
        field.setAccessible(true);
        return (RecipeBookComponent<?>) field.get(client.gui.screen());
    }

    private static RecipeButton plankTile(Minecraft client) throws ReflectiveOperationException {
        var page = ((RecipeBookComponentAccessor) component(client)).getRecipeBookPage();
        return ((RecipeBookPageAccessor) page).getButtons().stream()
                .filter(button -> button.visible && !button.getDisplayStack().isEmpty() && button.getDisplayStack().getItem() == Items.OAK_PLANKS)
                .findFirst().orElse(null);
    }

    private static int count(Minecraft client, Item item) {
        return client.player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.getItem() == item).mapToInt(ItemStack::getCount).sum();
    }

    private static int gridCount(Minecraft client) {
        return client.player.inventoryMenu.getInputGridSlots().stream().mapToInt(slot -> slot.getItem().getCount()).sum();
    }

    private void advance(int nextStage, int delay) {
        stage = nextStage;
        nextTick = ticks + delay;
    }

    private void check(boolean success, String description) {
        if (!success) throw new AssertionError(description);
        evidence.add("PASS: " + description);
    }

    private void finish(Minecraft client) {
        finished = true;
        client.options.keyShift.setDown(false);
        try {
            Files.write(client.gameDirectory.toPath().resolve("remoteqa-result.txt"), evidence);
        } catch (Exception failure) {
            failure.printStackTrace();
        }
        client.stop();
    }
}
