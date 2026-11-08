package marsh.town.brb.clientqa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import marsh.town.brb.BetterRecipeBook;
import marsh.town.brb.config.Config;
import marsh.town.brb.config.CraftAllMethod;
import marsh.town.brb.config.CraftingOverflow;
import marsh.town.brb.config.CreativeDisplayMode;
import marsh.town.brb.mixins.accessors.KeyMappingAccessor;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import marsh.town.brb.workstation.WorkstationRecipes;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.SmithingMenu;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Release upgrade check. The seed suite runs on the previous release and saves non-default settings,
 * exact pins and a rebound pin key; the verify suite starts the candidate on copies of those files.
 */
public final class UpgradeChecks {
    private static final Path EXPECTED = Path.of("upgrade-expected.json");
    private static final Path SETTINGS = Path.of("config", "brb.toml");
    private static final Path PINS = Path.of("brb.pins");

    private UpgradeChecks() {}

    public static void seed(ClientGameTestContext context, List<String> evidence) {
        context.runOnClient(client -> {
            Config config = BetterRecipeBook.config;
            config.enableBook = true;
            config.recipeDisplayMode = RecipeDisplayMode.ALL;
            config.creativeDisplayMode = CreativeDisplayMode.VANILLA;
            config.enablePinning = true;
            config.keepCentered = true;
            config.settingsButton = false;
            config.newRecipes.unlockAll = false;
            config.newRecipes.enableBounce = true;
            config.instantCraft.showButton = false;
            config.instantCraft.enabled = false;
            config.instantCraft.craftAllMethod = CraftAllMethod.FILL_GRID;
            config.instantCraft.overflow = CraftingOverflow.STOP_AFTER_DROP;
            config.alternativeRecipes.onHover = false;
            config.scrolling.enableScrolling = false;
            config.scrolling.scrollAround = true;
            config.brewingBookOpen = true;
            config.smithingBookOpen = true;
            config.brewingBookCraftableOnly = true;
            config.smithingBookCraftableOnly = true;
            BetterRecipeBook.configHolder.save();

            var manager = BetterRecipeBook.pinnedRecipeManager;
            client.player.getRecipeBook().getCollections().stream().limit(3)
                    .forEach(collection -> manager.toggle(collection.getRecipes().getFirst()));
            manager.toggle(WorkstationRecipes.forMenu(new SmithingMenu(0, client.player.getInventory())).getFirst().id());
            manager.toggle(WorkstationRecipes.forMenu(new BrewingStandMenu(0, client.player.getInventory())).getFirst().id());

            BetterRecipeBook.PIN_MAPPING.setKey(InputConstants.getKey("key.keyboard.p"));
            KeyMapping.resetMapping();
            client.options.save();
        });
        JsonObject expected = context.computeOnClient(client -> snapshot());
        check(expected.getAsJsonArray("pins").size() == 5, "seed saved three recipe and two workstation pins", evidence);
        try {
            Files.writeString(EXPECTED, new GsonBuilder().setPrettyPrinting().create().toJson(expected) + "\n");
        } catch (Exception failure) {
            throw new AssertionError("Could not record upgrade expectations", failure);
        }
        evidence.add("SEED: " + expected);
    }

    public static void verify(ClientGameTestContext context, List<String> evidence) {
        JsonObject expected;
        try {
            expected = JsonParser.parseString(Files.readString(EXPECTED)).getAsJsonObject();
        } catch (Exception failure) {
            throw new AssertionError("Missing seeded upgrade expectations", failure);
        }
        JsonObject actual = context.computeOnClient(client -> snapshot());
        check(actual.get("settings").equals(expected.get("settings")),
                "all " + expected.getAsJsonObject("settings").size() + " saved settings loaded unchanged", evidence);
        check(actual.get("pins").equals(expected.get("pins")),
                "all " + expected.getAsJsonArray("pins").size() + " exact recipe and workstation pins loaded unchanged", evidence);
        check(actual.get("pinKey").equals(expected.get("pinKey")), "rebound pin key loaded unchanged", evidence);
        check(actual.get("settingsText").equals(expected.get("settingsText")),
                "settings file rewritten at startup with identical contents", evidence);
        check(context.computeOnClient(client -> !Files.exists(SETTINGS.resolveSibling("brb.saving.toml"))),
                "no temporary settings file remains", evidence);
        evidence.add("VERIFY: " + actual);
    }

    /**
     * Starts on a seeded settings file that Cloth cannot use as written. "unreadable" files must load
     * defaults and be preserved before the first save; "repaired" files keep every valid value.
     */
    public static void recover(ClientGameTestContext context, List<String> evidence) {
        JsonObject expected;
        try {
            expected = JsonParser.parseString(Files.readString(EXPECTED)).getAsJsonObject();
        } catch (Exception failure) {
            throw new AssertionError("Missing seeded recovery expectations", failure);
        }
        String mode = expected.get("recovery").getAsString();
        String original = expected.get("settingsText").getAsString();
        check(context.computeOnClient(client -> BetterRecipeBook.configHolder != null),
                mode + ": settings stay registered with Cloth Config", evidence);
        if (mode.equals("unreadable")) {
            check(context.computeOnClient(client -> values(BetterRecipeBook.config, "").equals(values(new Config(), ""))),
                    "unreadable: defaults are used", evidence);
            check(context.computeOnClient(client -> fileHash(SETTINGS).equals(original)),
                    "unreadable: startup leaves the original file untouched", evidence);
            context.runOnClient(client -> {
                client.gui.setScreen(me.shedaniel.autoconfig.AutoConfigClient.getConfigScreen(Config.class, null).get());
                client.gui.setScreen(null);
                BetterRecipeBook.instantCraftingManager.toggleEnabled();
                BetterRecipeBook.instantCraftingManager.toggleEnabled();
            });
            evidence.add("PASS: unreadable: settings screen opens and instant-craft toggles save");
            List<Path> copies = context.computeOnClient(client -> unreadableCopies());
            check(copies.size() == 1, "unreadable: exactly one preserved copy after two saves", evidence);
            check(context.computeOnClient(client -> fileHash(copies.getFirst()).equals(original)),
                    "unreadable: preserved copy keeps the original bytes", evidence);
            check(context.computeOnClient(client -> {
                try {
                    return Files.readString(SETTINGS).contains("[instantCraft]");
                } catch (Exception failure) {
                    return false;
                }
            }), "unreadable: settings file is replaced with a complete saved file", evidence);
        } else {
            Map<String, String> loaded = context.computeOnClient(client -> values(BetterRecipeBook.config, ""));
            JsonObject settings = expected.getAsJsonObject("settings");
            for (String key : settings.keySet()) {
                check(settings.get(key).getAsString().equals(loaded.get(key)),
                        "repaired: " + key + " = " + settings.get(key).getAsString(), evidence);
            }
            check(context.computeOnClient(client -> unreadableCopies().isEmpty()),
                    "repaired: a loadable file is not copied aside", evidence);
        }
    }

    private static List<Path> unreadableCopies() {
        try (var files = Files.list(SETTINGS.getParent())) {
            return files.filter(path -> path.getFileName().toString().startsWith("brb.toml.unreadable-")).toList();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static String fileHash(Path path) {
        try {
            return sha256(Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static JsonObject snapshot() {
        JsonObject result = new JsonObject();
        JsonObject settings = new JsonObject();
        values(BetterRecipeBook.config, "").forEach(settings::addProperty);
        result.add("settings", settings);
        var pins = new com.google.gson.JsonArray();
        new TreeSet<>(BetterRecipeBook.pinnedRecipeManager.pinned.stream().map(Object::toString).toList()).forEach(pins::add);
        result.add("pins", pins);
        result.addProperty("pinKey", ((KeyMappingAccessor) BetterRecipeBook.PIN_MAPPING).getKey().getName());
        try {
            result.addProperty("settingsText", sha256(Files.readString(SETTINGS, StandardCharsets.UTF_8)));
            result.addProperty("pinsFile", sha256(Files.readString(PINS, StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new AssertionError("Could not read saved settings or pins", failure);
        }
        return result;
    }

    private static Map<String, String> values(Object owner, String prefix) {
        Map<String, String> values = new TreeMap<>();
        for (Field field : owner.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                Object value = field.get(owner);
                if (value == null || value instanceof Boolean || value instanceof Enum<?>) {
                    values.put(prefix + field.getName(), String.valueOf(value));
                } else {
                    values.putAll(values(value, prefix + field.getName() + "."));
                }
            } catch (IllegalAccessException failure) {
                throw new AssertionError(failure);
            }
        }
        return values;
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static void check(boolean condition, String message, List<String> evidence) {
        if (!condition) throw new AssertionError(message);
        evidence.add("PASS: " + message);
    }
}
