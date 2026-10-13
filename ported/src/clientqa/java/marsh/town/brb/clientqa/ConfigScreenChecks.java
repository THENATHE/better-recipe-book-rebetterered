package marsh.town.brb.clientqa;

import com.mojang.blaze3d.platform.InputConstants;
import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.config.Config;
import me.shedaniel.autoconfig.AutoConfigClient;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Uses rendered positions and screen input routing, not direct button or save callbacks. */
public final class ConfigScreenChecks {
    private static final List<String> OPTIONS = List.of(
            "enableBook", "recipeDisplayMode", "creativeDisplayMode", "enablePinning", "keepCentered",
            "settingsButton", "newRecipes.unlockAll", "newRecipes.enableBounce", "instantCraft.showButton",
            "instantCraft.enabled", "instantCraft.craftAllMethod", "instantCraft.overflow", "alternativeRecipes.onHover",
            "scrolling.enableScrolling", "scrolling.scrollAround");

    private ConfigScreenChecks() { }

    public static void run(ClientGameTestContext context, List<String> evidence) {
        Map<String, Object> original = context.computeOnClient(client -> values(BetterRecipeBook.config));
        try {
            for (int width : new int[] {320, 427, 640}) {
                exerciseScreen(context, width, width == 640 ? 360 : 240, original, evidence);
            }
            context.runOnClient(client -> {
                original.forEach((path, value) -> write(BetterRecipeBook.config, path, value));
                BetterRecipeBook.configHolder.save();
                client.gui.setScreen(AutoConfigClient.getConfigScreen(Config.class, null).get());
            });
            context.getInput().setCursorPos(4, 4);
            context.waitTicks(2);
            evidence.add("SCREENSHOT: " + context.takeScreenshot("brb-vanilla-style-settings"));
            context.runOnClient(client -> ((ClothConfigScreen) client.gui.screen()).listWidget.scrollTo(1000, false));
            context.waitTicks(2);
            evidence.add("SCREENSHOT: " + context.takeScreenshot("brb-vanilla-style-settings-lower"));
        } finally {
            context.runOnClient(client -> {
                original.forEach((path, value) -> write(BetterRecipeBook.config, path, value));
                BetterRecipeBook.configHolder.save();
                client.gui.setScreen(null);
            });
        }
    }

    private static void exerciseScreen(ClientGameTestContext context, int width, int height,
                                       Map<String, Object> original, List<String> evidence) {
        String size = width + "x" + height;
        ClothConfigScreen screen = context.computeOnClient(client -> {
            original.forEach((path, value) -> write(BetterRecipeBook.config, path, value));
            BetterRecipeBook.configHolder.save();
            var settings = (ClothConfigScreen) AutoConfigClient.getConfigScreen(Config.class, new InventoryScreen(client.player)).get();
            client.gui.setScreen(settings);
            settings.resize(width, height);
            check(settings.isTransparentBackground() && settings.getBackgroundLocation() == null,
                    size + ": config has no custom background texture", evidence);
            check(settings.getCategorizedEntries().size() == 1 && !settings.isShowingTabs(),
                    size + ": one settings list without tabs", evidence);
            var entries = settings.getCategorizedEntries().values().iterator().next();
            check(entries.size() == OPTIONS.size(), size + ": all " + OPTIONS.size() + " settings are visible entries", evidence);
            check(entries.getFirst() == entry(settings, "enableBook"), size + ": master switch comes first", evidence);
            check(entry(settings, "recipeDisplayMode") instanceof SelectionListEntry<?>
                            && entry(settings, "creativeDisplayMode") instanceof SelectionListEntry<?>
                            && entry(settings, "instantCraft.craftAllMethod") instanceof SelectionListEntry<?>
                            && entry(settings, "instantCraft.overflow") instanceof SelectionListEntry<?>,
                    size + ": all modes use cycling buttons", evidence);
            return settings;
        });
        context.waitTicks(2);
        Map<String, Object> edited = new LinkedHashMap<>();
        for (String option : OPTIONS) {
            reveal(context, screen, option);
            context.runOnClient(client -> {
                var entry = entry(screen, option);
                var button = valueButton(entry);
                check(button.getX() >= 0 && button.getX() + button.getWidth() <= width
                                && button.getY() >= screen.listWidget.top && button.getY() + button.getHeight() <= screen.listWidget.bottom,
                        size + ": " + option + " is within the visible list", evidence);
                check(client.font.width(entry.getDisplayedFieldName()) <= button.getX() - 40 - 2,
                        size + ": " + option + " label does not overlap its control", evidence);
                Object before = entry.getValue();
                click(screen, button);
                check(!Objects.equals(before, entry.getValue()), size + ": mouse changes " + option, evidence);
                edited.put(option, entry.getValue());
                check(Objects.equals(read(BetterRecipeBook.config, option), original.get(option)),
                        size + ": " + option + " waits for Save", evidence);
            });
        }
        context.waitTicks(2);
        context.runOnClient(client -> {
            click(screen, footer(screen, "text.cloth-config.save_and_done"));
            check(client.gui.screen() instanceof InventoryScreen, size + ": Save returns to the parent screen", evidence);
            check(values(BetterRecipeBook.config).equals(edited), size + ": Save applies every setting to the runtime config", evidence);
            check(BetterRecipeBook.config == BetterRecipeBook.configHolder.getConfig(),
                    size + ": saved settings and gameplay use the same config object", evidence);
            check(values(readSaved()).equals(edited), size + ": all settings persist to TOML", evidence);
            client.gui.setScreen(screen);
            screen.resize(width, height);
        });
        // Fresh entries must reflect saved values, rather than retaining an old screen's state.
        ClothConfigScreen reopened = context.computeOnClient(client -> {
            var settings = (ClothConfigScreen) AutoConfigClient.getConfigScreen(Config.class, new InventoryScreen(client.player)).get();
            client.gui.setScreen(settings);
            settings.resize(width, height);
            check(OPTIONS.stream().allMatch(path -> Objects.equals(entry(settings, path).getValue(), edited.get(path))),
                    size + ": reopening reflects every saved value", evidence);
            return settings;
        });
        context.waitTicks(2);
        for (String mode : List.of("recipeDisplayMode", "creativeDisplayMode", "instantCraft.craftAllMethod", "instantCraft.overflow")) {
            reveal(context, reopened, mode);
            context.runOnClient(client -> {
                var selection = entry(reopened, mode);
                Object start = selection.getValue();
                int count = ((Enum<?>) start).getDeclaringClass().getEnumConstants().length;
                var seen = new java.util.HashSet<>();
                for (int step = 0; step < count; step++) {
                    seen.add(selection.getValue());
                    click(reopened, valueButton(selection));
                }
                check(seen.size() == count && selection.getValue() == start,
                        size + ": " + mode + " cycles through every mode and wraps", evidence);
                var button = valueButton(selection);
                reopened.setFocused(reopened.listWidget);
                reopened.listWidget.setFocused(selection);
                selection.setFocused(button);
                check(reopened.keyPressed(new KeyEvent(InputConstants.KEY_SPACE, 0, 0))
                                && selection.getValue() != start,
                        size + ": keyboard changes " + mode, evidence);
                reopened.keyReleased(new KeyEvent(InputConstants.KEY_SPACE, 0, 0));
            });
        }
        for (String option : List.of("enablePinning", "recipeDisplayMode")) {
            reveal(context, reopened, option);
            context.runOnClient(client -> {
                var selected = entry(reopened, option);
                if (Objects.equals(selected.getValue(), selected.getDefaultValue().orElseThrow())) {
                    click(reopened, valueButton(selected));
                }
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                var selected = entry(reopened, option);
                click(reopened, (Button) selected.children().get(1));
                check(Objects.equals(selected.getValue(), selected.getDefaultValue().orElseThrow()),
                        size + ": Reset restores the default for " + option, evidence);
                check(Objects.equals(read(BetterRecipeBook.config, option), edited.get(option)),
                        size + ": Reset waits for Save for " + option, evidence);
            });
        }
        context.runOnClient(client -> {
            reopened.listWidget.scrollTo(0, false);
            reopened.mouseScrolled(width / 2.0, reopened.listWidget.top + 20, 0, -6);
        });
        context.waitTicks(10);
        context.runOnClient(client -> check(reopened.listWidget.getScroll() > 0,
                size + ": mouse wheel scrolls the settings list", evidence));
        context.runOnClient(client -> {
            check(reopened.isEdited(), size + ": pending edits are detected before Cancel", evidence);
            click(reopened, footer(reopened, "gui.cancel", "text.cloth-config.cancel_discard"));
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            check(client.gui.screen() instanceof ConfirmScreen, size + ": Cancel asks before discarding changes", evidence);
            click(client.gui.screen(), footer(client.gui.screen(), "text.cloth-config.quit_discard"));
            check(client.gui.screen() instanceof InventoryScreen, size + ": discard returns to the parent screen", evidence);
            check(values(BetterRecipeBook.config).equals(edited) && values(readSaved()).equals(edited),
                    size + ": Cancel leaves all saved settings unchanged", evidence);
        });
    }

    private static void reveal(ClientGameTestContext context, ClothConfigScreen screen, String path) {
        context.runOnClient(client -> {
            var target = entry(screen, path);
            int index = screen.listWidget.children().indexOf(target);
            int row = screen.listWidget.getRowTop(index);
            screen.listWidget.scrollTo(screen.listWidget.getScroll() + row - screen.listWidget.top - 8, false);
        });
        context.waitTicks(2);
    }

    private static AbstractConfigEntry<?> entry(ClothConfigScreen screen, String path) {
        return screen.getCategorizedEntries().values().stream().flatMap(List::stream)
                .filter(entry -> entry.getFieldName().equals(Component.translatable("text.autoconfig.brb.option." + path)))
                .findFirst().orElseThrow();
    }

    private static Button valueButton(AbstractConfigEntry<?> entry) {
        if (!(entry instanceof BooleanListEntry) && !(entry instanceof SelectionListEntry<?>)) {
            throw new AssertionError("Expected a vanilla-style button: " + entry.getFieldName());
        }
        return (Button) entry.children().getFirst();
    }

    private static Button footer(Screen screen, String... keys) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> java.util.Arrays.stream(keys).anyMatch(key -> button.getMessage().equals(Component.translatable(key))))
                .findFirst().orElseThrow(() -> new AssertionError("Missing footer: " + String.join(", ", keys)));
    }

    private static void click(Screen screen, Button button) {
        var event = new MouseButtonEvent(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        if (!screen.mouseClicked(event, false)) throw new AssertionError("Screen ignored click: " + button.getMessage());
        screen.mouseReleased(event);
    }

    private static Map<String, Object> values(Config config) {
        Map<String, Object> values = new LinkedHashMap<>();
        OPTIONS.forEach(path -> values.put(path, read(config, path)));
        return values;
    }

    private static Object read(Config config, String path) {
        try {
            Object object = config;
            for (String part : path.split("\\.")) object = object.getClass().getField(part).get(object);
            return object;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unknown setting " + path, failure);
        }
    }

    private static void write(Config config, String path, Object value) {
        try {
            String[] parts = path.split("\\.");
            Object object = config;
            for (int i = 0; i < parts.length - 1; i++) object = object.getClass().getField(parts[i]).get(object);
            object.getClass().getField(parts[parts.length - 1]).set(object, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unknown setting " + path, failure);
        }
    }

    private static Config readSaved() {
        try {
            return new Toml4jConfigSerializer<>(Config.class.getAnnotation(me.shedaniel.autoconfig.annotation.Config.class), Config.class).deserialize();
        } catch (Exception failure) {
            throw new AssertionError("Cannot read saved config", failure);
        }
    }

    private static void check(boolean condition, String name, List<String> evidence) {
        if (!condition) throw new AssertionError(name);
        evidence.add("PASS: " + name);
    }
}
