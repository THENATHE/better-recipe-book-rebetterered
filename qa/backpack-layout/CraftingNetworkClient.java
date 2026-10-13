import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thenathe.backpackcrafting.menu.BackpackCraftingMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Drives actual screen mouse callbacks and normal container packets. */
public final class CraftingNetworkClient implements ClientModInitializer {
    private int ticks;
    private final JsonArray actions = new JsonArray();
    private Screen settingsParent;
    private JsonObject settingsAction;
    private List<ItemStack> settingsItems;
    private ItemStack settingsCarried;
    private Object settingsMenu;
    private int settingsPhase;
    private boolean originalPinning;
    private int chalkMembers = -1;
    private boolean craftAllMethodConfigured;
    private boolean packetTraceInstalled;
    private final java.util.concurrent.ConcurrentLinkedQueue<ContainerInput> resultInputs = new java.util.concurrent.ConcurrentLinkedQueue<>();
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || ++ticks % 5 != 0) return;
            try {
                Path control = Path.of(System.getProperty("crafting.qa.control"));
                String name = client.player.getGameProfile().name();
                if (settingsAction != null) {
                    Screen settings = client.gui.screen();
                    boolean save = settingsAction.get("type").getAsString().equals("settings-save");
                    if (save && (settingsPhase == 0 || settingsPhase == 2)) {
                        ClothConfigScreen configScreen = (ClothConfigScreen) settings;
                        AbstractConfigEntry<?> pinning = configScreen.getCategorizedEntries().values().stream()
                                .flatMap(List::stream)
                                .filter(entry -> entry.getFieldName().equals(Component.translatable("text.autoconfig.brb.option.enablePinning")))
                                .findFirst().orElseThrow(() -> new AssertionError("Pinning setting missing"));
                        Button value = (Button) pinning.children().getFirst();
                        click(settings, value);
                        settingsPhase++;
                        return;
                    }
                    String footerKey = settingsAction.get("type").getAsString().equals("settings-save")
                            ? "text.cloth-config.save_and_done" : "gui.cancel";
                    Button footer = settings.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                            .filter(button -> button.getMessage().equals(Component.translatable(footerKey))
                                    || footerKey.equals("gui.cancel") && button.getMessage().equals(Component.translatable("text.cloth-config.cancel_discard")))
                            .findFirst().orElseThrow(() -> new AssertionError("Settings footer missing: " + footerKey));
                    click(settings, footer);
                    if (client.gui.screen() != settingsParent || client.player.containerMenu != settingsMenu) {
                        throw new AssertionError("Settings did not return to the same backpack menu");
                    }
                    assertContents(client.player.containerMenu, settingsItems, settingsCarried);
                    if (save) {
                        Object config = Class.forName("marsh.town.brb.BetterRecipeBook").getField("config").get(null);
                        boolean actualPinning = config.getClass().getField("enablePinning").getBoolean(config);
                        boolean expectedPinning = settingsPhase == 1 ? !originalPinning : originalPinning;
                        if (actualPinning != expectedPinning) throw new AssertionError("Settings Save did not apply pinning value");
                        if (settingsPhase == 1) {
                            var returnedBook = (RecipeBookComponent<?>) field(settingsParent, "recipeBookComponent");
                            click(settingsParent, (AbstractWidget) field(returnedBook, "brb$settingsButton"));
                            settingsPhase = 2;
                            return;
                        }
                        settingsAction.addProperty("save_applies_and_restores_edit", true);
                    }
                    settingsAction.addProperty("returned_to_same_backpack", true);
                    actions.add(settingsAction);
                    settingsAction = null;
                    settingsParent = null;
                }
                if (!packetTraceInstalled && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("brb")) {
                    var connectionField = net.minecraft.network.Connection.class.getDeclaredField("channel");
                    connectionField.setAccessible(true);
                    var channel = (io.netty.channel.Channel) connectionField.get(client.getConnection().getConnection());
                    channel.pipeline().addLast("brb_backpack_result_trace", new io.netty.channel.ChannelDuplexHandler() {
                        @Override
                        public void write(io.netty.channel.ChannelHandlerContext context, Object message, io.netty.channel.ChannelPromise promise) throws Exception {
                            if (message instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket packet && packet.slotNum() == 0) {
                                resultInputs.add(packet.containerInput());
                            }
                            super.write(context, message, promise);
                        }
                    });
                    packetTraceInstalled = true;
                }
                var row = new JsonObject();
                row.addProperty("result_quick_moves", resultInputs.stream().filter(input -> input == ContainerInput.QUICK_MOVE).count());
                row.addProperty("result_pickups", resultInputs.stream().filter(input -> input == ContainerInput.PICKUP).count());
                row.addProperty("result_throws", resultInputs.stream().filter(input -> input == ContainerInput.THROW).count());
                row.addProperty("ticks", ticks);
                row.addProperty("screen", client.gui.screen() == null ? "none" : client.gui.screen().getClass().getName());
                row.add("actions", actions.deepCopy());
                if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("brb")) {
                    if (!craftAllMethodConfigured) {
                        configureCraftAllMethod();
                        craftAllMethodConfigured = true;
                    }
                    Object config = Class.forName("marsh.town.brb.BetterRecipeBook").getField("config").get(null);
                    Object instant = config.getClass().getField("instantCraft").get(config);
                    row.addProperty("craft_all_method", String.valueOf(instant.getClass().getField("craftAllMethod").get(instant)));
                    if (chalkMembers < 0) chalkMembers = checkChalkGrouping();
                    row.addProperty("chalk_grouping", true);
                    row.addProperty("chalk_grouping_members", chalkMembers);
                    Object manager = Class.forName("marsh.town.brb.BetterRecipeBook").getField("instantCraftingManager").get(null);
                    Object pending = field(manager, "pending");
                    row.addProperty("brbr_pending", pending != null);
                    if (pending != null) {
                        var pendingState = new JsonObject();
                        for (String flag : List.of("receivedResult", "awaitingNextBatch", "awaitingPlacement", "lastState")) {
                            pendingState.addProperty(flag, String.valueOf(field(pending, flag)));
                        }
                        row.add("brbr_state", pendingState);
                    }
                }
                if (client.player.containerMenu instanceof net.minecraft.world.inventory.AbstractCraftingMenu menu && client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen) {
                    int capacity = menu instanceof BackpackCraftingMenu backpack ? backpack.getStorage().getContainerSize() : 0;
                    int menuWidth = menu instanceof BackpackCraftingMenu backpack ? backpack.getWidth() : 176;
                    int menuHeight = menu instanceof BackpackCraftingMenu backpack ? backpack.getHeight() : 166;
                    row.addProperty("size", menu.getGridWidth());
                    row.addProperty("menu_id", menu.containerId);
                    row.addProperty("capacity", capacity);
                    int left = ((Number) field(client.gui.screen(), "leftPos")).intValue();
                    int top = ((Number) field(client.gui.screen(), "topPos")).intValue();
                    row.addProperty("screen_width", client.gui.screen().width);
                    row.addProperty("screen_height", client.gui.screen().height);
                    row.addProperty("menu_left", left);
                    row.addProperty("menu_top", top);
                    row.addProperty("menu_width", menuWidth);
                    row.addProperty("menu_height", menuHeight);
                    if (left < 0 || top < 0 || left + menuWidth > client.gui.screen().width
                            || top + menuHeight > client.gui.screen().height) {
                        throw new AssertionError("crafting menu exceeds viewport");
                    }
                    row.addProperty("output", menu.getSlot(0).getItem().getCount());
                    row.addProperty("cursor_empty", menu.getCarried().isEmpty());
                    var book = (RecipeBookComponent<?>) field(client.gui.screen(), "recipeBookComponent");
                    row.addProperty("book_visible", book.isVisible());
                    row.addProperty("ghost_slots", ((java.util.Map<?, ?>) field(field(book, "ghostSlots"), "ingredients")).size());
                    if (book.isVisible()) {
                        var origin = RecipeBookComponent.class.getDeclaredMethod("getXOrigin");
                        origin.setAccessible(true);
                        int bookLeft = (Integer) origin.invoke(book);
                        row.addProperty("book_left", bookLeft);
                        @SuppressWarnings("unchecked") var tabs = (List<net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton>) field(book, "tabButtons");
                        for (var tab : tabs) {
                            if (tab.visible && (tab.getX() < 0 || tab.getX() + tab.getWidth() > bookLeft + 8)) {
                                throw new AssertionError("recipe tab overlaps recipe content or leaves viewport");
                            }
                        }
                        boolean narrow = (Boolean) field(book, "widthTooNarrow");
                        if (bookLeft < 0 || !narrow && bookLeft + RecipeBookComponent.IMAGE_WIDTH > left + 2) {
                            throw new AssertionError("recipe book overlaps backpack at wide viewport");
                        }
                        int bookTop = (client.gui.screen().height - RecipeBookComponent.IMAGE_HEIGHT) / 2;
                        assertButton(book, "brb$settingsButton", bookLeft + 11, bookTop + 137, 18, 18);
                        assertButton(book, "betterRecipeBook$instantCraftButton", bookLeft + 110, bookTop + 137, 26, 18);
                        row.addProperty("button_layout_verified", true);
                        assertPinnedTab(book, bookLeft, bookTop, tabs.stream().filter(tab -> tab.visible).count());
                        row.addProperty("pinned_tab_layout_verified", true);
                        row.addProperty("narrow", narrow);
                    }
                    Path request = control.resolve(name + "-action.json");
                    if (Files.exists(request)) {
                        var action = JsonParser.parseString(Files.readString(request)).getAsJsonObject();
                        Files.delete(request);
                        String type = action.get("type").getAsString();
                        if (type.equals("book")) {
                            if (!book.isVisible()) {
                                var screen = client.gui.screen();
                                var opener = screen.children().stream()
                                        .filter(child -> child instanceof net.minecraft.client.gui.components.ImageButton)
                                        .map(child -> (net.minecraft.client.gui.components.ImageButton) child)
                                        .findFirst().orElseThrow(() -> new AssertionError("recipe book button missing"));
                                System.out.println("CRAFTING_BOOK_BUTTON x=" + opener.getX() + " y=" + opener.getY());
                                if (!opener.mouseClicked(new MouseButtonEvent(opener.getX() + 8, opener.getY() + 8, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false)
                                        || !book.isVisible()) {
                                    throw new AssertionError("recipe book opener callback did not open book");
                                }
                            }
                            ((EditBox) field(book, "searchBox")).setValue("crafting table");
                            var updateSearch = RecipeBookComponent.class.getDeclaredMethod("checkSearchStringUpdate");
                            updateSearch.setAccessible(true);
                            updateSearch.invoke(book);
                            book.tick();
                        } else if (type.equals("controls")) {
                            var before = menu.slots.stream().map(slot -> slot.getItem().copy()).toList();
                            var carried = menu.getCarried().copy();
                            var instant = (AbstractWidget) field(book, "betterRecipeBook$instantCraftButton");
                            Object manager = Class.forName("marsh.town.brb.BetterRecipeBook").getField("instantCraftingManager").get(null);
                            var enabled = manager.getClass().getMethod("isEnabled");
                            boolean original = (Boolean) enabled.invoke(manager);
                            click(client.gui.screen(), instant);
                            if ((Boolean) enabled.invoke(manager) == original) throw new AssertionError("Instant button did not toggle");
                            assertContents(menu, before, carried);
                            click(client.gui.screen(), instant);
                            if ((Boolean) enabled.invoke(manager) != original) throw new AssertionError("Instant button did not toggle back");
                            assertContents(menu, before, carried);
                            action.addProperty("instant_toggle_preserves_storage", true);
                            var pinned = (AbstractWidget) book.getClass().getMethod("brb$getPinnedTab").invoke(book);
                            var nativeTab = (AbstractWidget) field(book, "selectedTab");
                            String searchBeforePins = ((EditBox) field(book, "searchBox")).getValue();
                            click(client.gui.screen(), pinned);
                            if (!Boolean.TRUE.equals(book.getClass().getMethod("brb$isPinnedTabSelected").invoke(book))) {
                                throw new AssertionError("Pinned tab mouse click did not select pins");
                            }
                            assertContents(menu, before, carried);
                            click(client.gui.screen(), nativeTab);
                            if (Boolean.TRUE.equals(book.getClass().getMethod("brb$isPinnedTabSelected").invoke(book))
                                    || field(book, "selectedTab") != nativeTab
                                    || !((EditBox) field(book, "searchBox")).getValue().equals(searchBeforePins)) {
                                throw new AssertionError("Native tab did not restore selected category and search after pins");
                            }
                            assertContents(menu, before, carried);
                            action.addProperty("pinned_tab_mouse_selection_preserves_storage", true);
                            EditBox search = (EditBox) field(book, "searchBox");
                            String oldSearch = search.getValue();
                            search.setValue("");
                            var updateSearch = RecipeBookComponent.class.getDeclaredMethod("checkSearchStringUpdate");
                            updateSearch.setAccessible(true);
                            updateSearch.invoke(book);
                            book.tick();
                            Object page = field(book, "recipeBookPage");
                            if (((Number) field(page, "totalPages")).intValue() <= 1) throw new AssertionError("Scroll fixture needs multiple recipe pages");
                            int oldPage = ((Number) field(page, "currentPage")).intValue();
                            var origin = RecipeBookComponent.class.getDeclaredMethod("getXOrigin");
                            origin.setAccessible(true);
                            int scrollX = (Integer) origin.invoke(book) + 10;
                            int scrollY = (client.gui.screen().height - RecipeBookComponent.IMAGE_HEIGHT) / 2 + 90;
                            if (!client.gui.screen().mouseScrolled(scrollX, scrollY, 0, -1)
                                    || ((Number) field(page, "currentPage")).intValue() == oldPage) {
                                throw new AssertionError("Scroll over the actual left side of the book did not change pages");
                            }
                            search.setValue(oldSearch);
                            updateSearch.invoke(book);
                            book.tick();
                            assertContents(menu, before, carried);
                            action.addProperty("actual_book_scroll_changes_page", true);
                            net.minecraft.client.Screenshot.grab(client.gameDirectory, "backpack-buttons-" + menu.getGridWidth() + "-" + capacity + "-" + client.gui.screen().width + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
                        } else if (type.equals("settings-save") || type.equals("settings-cancel")) {
                            settingsParent = client.gui.screen();
                            settingsPhase = 0;
                            Object config = Class.forName("marsh.town.brb.BetterRecipeBook").getField("config").get(null);
                            originalPinning = config.getClass().getField("enablePinning").getBoolean(config);
                            settingsMenu = menu;
                            settingsItems = menu.slots.stream().map(slot -> slot.getItem().copy()).toList();
                            settingsCarried = menu.getCarried().copy();
                            var gear = (AbstractWidget) field(book, "brb$settingsButton");
                            click(settingsParent, gear);
                            if (client.gui.screen() == settingsParent || !client.gui.screen().getClass().getName().contains("ClothConfigScreen")) {
                                throw new AssertionError("Settings button did not open config");
                            }
                            assertContents(menu, settingsItems, settingsCarried);
                            settingsAction = action;
                        } else if (type.equals("resize-narrow") || type.equals("resize-wide")) {
                            client.gui.screen().resize(type.equals("resize-narrow") ? 480 : 640, 400);
                        } else if (type.equals("recipe")) {
                            Object page = field(book, "recipeBookPage");
                            @SuppressWarnings("unchecked") var buttons = (List<RecipeButton>) field(page, "buttons");
                            System.out.println("CRAFTING_RECIPE_BUTTONS " + buttons.stream().filter(candidate -> candidate.visible).map(candidate -> candidate.getDisplayStack().toString()).toList());
                            var button = buttons.stream().filter(candidate -> candidate.visible && candidate.getDisplayStack().is(Items.CRAFTING_TABLE)).findFirst().orElseThrow(() -> new AssertionError("crafting table recipe button missing"));
                            net.minecraft.client.Screenshot.grab(client.gameDirectory, "backpack-crafting-" + menu.getGridWidth() + "-" + capacity + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
                            resultInputs.clear();
                            int mouseButton = action.has("right") && action.get("right").getAsBoolean() ? InputConstants.MOUSE_BUTTON_RIGHT : InputConstants.MOUSE_BUTTON_LEFT;
                            boolean clicked = client.gui.screen().mouseClicked(new MouseButtonEvent(button.getX() + 8, button.getY() + 8, new MouseButtonInfo(mouseButton, action.has("maximum") && action.get("maximum").getAsBoolean() ? InputConstants.MOD_SHIFT : 0)), false);
                            if (!clicked) throw new AssertionError("recipe click unhandled");
                            action.addProperty("recipe_id", button.getCurrentRecipe().toString());
                            action.addProperty("recipe_craftable", button.getCollection().isCraftable(button.getCurrentRecipe()));
                            action.addProperty("last_placed", String.valueOf(field(book, "lastPlacedRecipe")));
                            System.out.println("CRAFTING_RECIPE_CLICK " + action);
                        } else if (type.equals("bucket-cake")) {
                            setInstantEnum("craftAllMethod", action.get("method").getAsString());
                            setInstantEnum("overflow", action.get("overflow").getAsString());
                            ((EditBox) field(book, "searchBox")).setValue("cake");
                            var updateSearch = RecipeBookComponent.class.getDeclaredMethod("checkSearchStringUpdate");
                            updateSearch.setAccessible(true);
                            updateSearch.invoke(book);
                            book.tick();
                            @SuppressWarnings("unchecked") var buttons = (List<RecipeButton>) field(field(book, "recipeBookPage"), "buttons");
                            var cake = buttons.stream().filter(candidate -> candidate.visible && candidate.getDisplayStack().is(Items.CAKE))
                                    .findFirst().orElseThrow(() -> new AssertionError("Prepared cake recipe widget missing"));
                            resultInputs.clear();
                            var event = new MouseButtonEvent(cake.getX() + 8, cake.getY() + 8,
                                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT,
                                            action.get("maximum").getAsBoolean() ? InputConstants.MOD_SHIFT : 0));
                            if (!client.gui.screen().mouseClicked(event, false)) throw new AssertionError("Prepared cake recipe click ignored");
                            client.gui.screen().mouseReleased(event);
                            action.addProperty("prepared_cake_clicked", true);
                        } else if (type.equals("cake")) {
                            ((EditBox) field(book, "searchBox")).setValue("cake");
                            var updateSearch = RecipeBookComponent.class.getDeclaredMethod("checkSearchStringUpdate");
                            updateSearch.setAccessible(true);
                            updateSearch.invoke(book);
                            @SuppressWarnings("unchecked") var buttons = (List<RecipeButton>) field(field(book, "recipeBookPage"), "buttons");
                            var cake = buttons.stream().filter(candidate -> candidate.visible && candidate.getDisplayStack().is(Items.CAKE)).findFirst();
                            if (menu.getGridWidth() == 2) {
                                if (cake.isPresent()) throw new AssertionError("three by three cake visible for two by two grid");
                                action.addProperty("larger_recipe_filtered", true);
                            } else {
                                var button = cake.orElseThrow(() -> new AssertionError("uncraftable cake absent from recipe book"));
                                if (button.getCollection().isCraftable(button.getCurrentRecipe())) throw new AssertionError("cake incorrectly marked craftable");
                                client.gui.screen().mouseClicked(new MouseButtonEvent(button.getX() + 8, button.getY() + 8, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                                action.addProperty("uncraftable_recipe_clicked", true);
                            }
                        } else if (type.equals("snapshot")) {
                            net.minecraft.client.Screenshot.grab(client.gameDirectory, "backpack-crafting-ghost-" + menu.getGridWidth() + "-" + capacity + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
                        } else if (type.equals("take")) {
                            net.minecraft.client.Screenshot.grab(client.gameDirectory, "backpack-crafting-result-" + menu.getGridWidth() + "-" + capacity + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
                            client.gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.QUICK_MOVE, client.player);
                        } else if (type.equals("close")) {
                            client.player.closeContainer();
                        }
                        if (settingsAction != action) actions.add(action);
                    }
                }
                Path observation = control.resolve(name + "-client.json");
                    Path temporary = control.resolve(name + "-client.json.tmp");
                    Files.writeString(temporary, new GsonBuilder().create().toJson(row));
                    Files.move(temporary, observation, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(Path.of(System.getProperty("crafting.qa.control"), "failure"), failure.toString()); }
                catch (Exception ignored) {}
            }
        });
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setInstantEnum(String field, String value) throws Exception {
        Object config = Class.forName("marsh.town.brb.BetterRecipeBook").getField("config").get(null);
        Object instant = config.getClass().getField("instantCraft").get(config);
        var setting = instant.getClass().getField(field);
        setting.set(instant, Enum.valueOf((Class<? extends Enum>) setting.getType(), value.toUpperCase(java.util.Locale.ROOT)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void configureCraftAllMethod() throws Exception {
        Object config = Class.forName("marsh.town.brb.BetterRecipeBook").getField("config").get(null);
        Object instant = config.getClass().getField("instantCraft").get(config);
        var method = instant.getClass().getField("craftAllMethod");
        String selected = System.getProperty("crafting.qa.craftAllMethod", "single_batches").toUpperCase(java.util.Locale.ROOT);
        method.set(instant, Enum.valueOf((Class<? extends Enum>) method.getType(), selected));
    }

    private static void assertPinnedTab(Object book, int left, int top, long visibleTabs) throws Exception {
        AbstractWidget pinned = (AbstractWidget) book.getClass().getMethod("brb$getPinnedTab").invoke(book);
        if (pinned == null || !pinned.visible || pinned.getX() != left - 30 || pinned.getY() != top + 3 + 27 * visibleTabs
                || pinned.getWidth() != 35 || pinned.getHeight() != 27) {
            throw new AssertionError("Pinned tab does not follow the actual recipe book origin and visible category count");
        }
    }

    private static int checkChalkGrouping() throws Exception {
        var source = new java.util.ArrayList<ItemStack>();
        String pattern = "^(?:white|orange|magenta|light_blue|yellow|lime|pink|gray|light_gray|cyan|purple|blue|brown|green|red|black)_(?:glow_)?chalk$";
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals("chalk") && id.getPath().matches(pattern)) {
                source.add(new ItemStack(item));
            }
        }
        if (source.size() != 32) throw new AssertionError("Suite Chalk fixture expected 32 registered variants, found " + source.size());
        Class<?> type = Class.forName("marsh.town.brb.creative.CreativeGroupModel");
        Object model = type.getConstructor().newInstance();
        try {
            type.getMethod("setSource", List.class, boolean.class, String.class)
                    .invoke(model, source, true, "minecraft:tools_and_utilities");
        } catch (NoSuchMethodException olderApi) {
            type.getMethod("setSource", List.class, boolean.class).invoke(model, source, true);
        }
        var entriesMethod = type.getMethod("entries");
        var entries = (List<?>) entriesMethod.invoke(model);
        if (entries.size() != 1) throw new AssertionError("Chalk colors did not collapse under a single header");
        Object header = entries.getFirst();
        Class<?> entryType = header.getClass();
        if (!Boolean.TRUE.equals(entryType.getMethod("header").invoke(header))
                || !"chalk:chalk".equals(entryType.getMethod("groupKey").invoke(header))
                || ((Integer) entryType.getMethod("members").invoke(header)) != 32) {
            throw new AssertionError("Chalk header did not contain all 32 colored and glowing tools");
        }
        type.getMethod("toggle", int.class).invoke(model, 0);
        entries = (List<?>) entriesMethod.invoke(model);
        if (entries.size() != 33) throw new AssertionError("Expanded Chalk group omitted a registered tool");
        for (int index = 0; index < source.size(); index++) {
            Object child = entries.get(index + 1);
            if (Boolean.TRUE.equals(entryType.getMethod("header").invoke(child))
                    || entryType.getMethod("stack").invoke(child) != source.get(index)) {
                throw new AssertionError("Expanded Chalk group changed exact source stack identity or order at " + index);
            }
        }
        type.getMethod("toggle", int.class).invoke(model, 0);
        entries = (List<?>) entriesMethod.invoke(model);
        if (entries.size() != 1 || !entryType.getMethod("stack").invoke(entries.getFirst()).equals(source.getFirst())) {
            throw new AssertionError("Chalk collapse failed to restore the original representative");
        }
        return source.size();
    }

    private static void assertButton(Object book, String name, int x, int y, int width, int height) throws Exception {
        AbstractWidget button = (AbstractWidget) field(book, name);
        if (button == null || button.getX() != x || button.getY() != y
                || button.getWidth() != width || button.getHeight() != height) {
            throw new AssertionError(name + " does not follow the actual recipe book origin: expected " + x + "," + y
                    + ", actual " + (button == null ? "null" : button.getX() + "," + button.getY()));
        }
    }

    private static void click(Screen screen, AbstractWidget button) {
        var event = new MouseButtonEvent(button.getX() + button.getWidth() / 2.0,
                button.getY() + button.getHeight() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        if (!screen.mouseClicked(event, false)) throw new AssertionError("Button click ignored: " + button.getMessage());
        screen.mouseReleased(event);
    }

    private static void assertContents(net.minecraft.world.inventory.AbstractContainerMenu menu,
                                       List<ItemStack> expected, ItemStack carried) {
        if (!ItemStack.matches(carried, menu.getCarried()) || menu.slots.size() != expected.size()) {
            throw new AssertionError("BRB control changed the backpack cursor or slot count");
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!ItemStack.matches(expected.get(index), menu.getSlot(index).getItem())) {
                throw new AssertionError("BRB control changed backpack slot " + index);
            }
        }
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
