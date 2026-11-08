import marsh.town.brb.config.AtomicConfigSerializer;
import marsh.town.brb.config.Config;
import marsh.town.brb.config.CraftAllMethod;
import marsh.town.brb.config.CraftingOverflow;
import marsh.town.brb.config.CreativeDisplayMode;
import marsh.town.brb.recipegroups.RecipeDisplayMode;
import me.shedaniel.autoconfig.serializer.ConfigSerializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** File handling of the settings serializer; the TOML format itself is Cloth Config's and is checked in-game. */
public final class ConfigPersistenceChecks {
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        Path folder = Files.createTempDirectory("brb-config-regression-");
        Path file = folder.resolve("brb.toml");
        Path saving = folder.resolve("brb.saving.toml");
        try {
            var serializer = new AtomicConfigSerializer<>(file, saving, new TextReader(file), new TextWriter(saving));
            Files.writeString(file, "saved settings");
            require(serializer.deserialize().enableBook, "Readable settings loaded");
            serializer.serialize(new Config());
            require(Files.readString(file).equals("written settings"), "Save replaced the settings file");
            require(listing(folder).equals(List.of("brb.toml")), "Successful save left only the settings file");

            Files.writeString(file, "broken");
            require(throwsSerialization(serializer), "Unreadable settings reported to Cloth");
            require(Files.readString(file).equals("broken"), "Reading unreadable settings left them unchanged");
            serializer.serialize(new Config());
            List<String> files = listing(folder);
            require(files.size() == 2 && files.get(1).startsWith("brb.toml.unreadable-"), "Unreadable settings preserved before the first save");
            require(Files.readString(folder.resolve(files.get(1))).equals("broken"), "Preserved copy kept the original bytes");
            serializer.serialize(new Config());
            require(listing(folder).size() == 2, "Later saves made no further copies");

            Files.writeString(file, "wrong type");
            require(throwsSerialization(serializer), "Mismatched value types reported as unreadable settings");

            Files.writeString(file, "saved settings");
            serializer.deserialize();
            var failing = new AtomicConfigSerializer<>(file, saving, new TextReader(file), (SaveOnly) config -> {
                try {
                    Files.writeString(saving, "partial");
                } catch (java.io.IOException failure) {
                    throw new ConfigSerializer.SerializationException(failure);
                }
                throw new ConfigSerializer.SerializationException(new IllegalStateException("disk full"));
            });
            require(throwsOnSave(failing), "Failed save reported");
            require(Files.readString(file).equals("saved settings"), "Failed save kept the previous settings");
            require(!Files.exists(saving), "Failed save removed its temporary file");

            Config edited = new Config();
            edited.recipeDisplayMode = null;
            edited.creativeDisplayMode = null;
            edited.instantCraft.craftAllMethod = null;
            edited.instantCraft.overflow = null;
            edited.instantCraft.enabled = false;
            edited.keepCentered = true;
            edited.scrolling = null;
            edited.validatePostLoad();
            require(edited.recipeDisplayMode == RecipeDisplayMode.EXPANDABLE
                    && edited.creativeDisplayMode == CreativeDisplayMode.EXPANDABLE, "Unknown display modes use their defaults");
            require(edited.instantCraft.craftAllMethod == CraftAllMethod.SINGLE_BATCHES
                    && edited.instantCraft.overflow == CraftingOverflow.STOP_BEFORE_DROP, "Unknown Craft all options never allow drops");
            require(!edited.instantCraft.enabled && edited.keepCentered && edited.scrolling != null,
                    "Repair keeps every other saved value");
            System.out.println("PASS: settings save replacement, temporary cleanup, unreadable preservation, failed saves and value repair ("
                    + checks + " checks)");
        } finally {
            for (String name : listing(folder)) Files.deleteIfExists(folder.resolve(name));
            Files.deleteIfExists(folder);
        }
    }

    private static boolean throwsSerialization(AtomicConfigSerializer<Config> serializer) {
        try {
            serializer.deserialize();
            return false;
        } catch (ConfigSerializer.SerializationException expected) {
            return true;
        }
    }

    private static boolean throwsOnSave(AtomicConfigSerializer<Config> serializer) {
        try {
            serializer.serialize(new Config());
            return false;
        } catch (ConfigSerializer.SerializationException expected) {
            return true;
        }
    }

    private static List<String> listing(Path folder) throws Exception {
        try (var files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    /** Stands in for Cloth's reader: "broken" fails to parse and "wrong type" fails conversion. */
    private record TextReader(Path file) implements ConfigSerializer<Config> {
        @Override
        public void serialize(Config config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Config deserialize() throws SerializationException {
            try {
                String text = Files.readString(file);
                if (text.equals("broken")) throw new SerializationException(new IllegalStateException("parse error"));
                if (text.equals("wrong type")) throw new ClassCastException("String cannot be converted to boolean");
                return new Config();
            } catch (java.io.IOException failure) {
                throw new SerializationException(failure);
            }
        }

        @Override
        public Config createDefault() {
            return new Config();
        }
    }

    private interface SaveOnly extends ConfigSerializer<Config> {
        @Override
        default Config deserialize() {
            throw new UnsupportedOperationException();
        }

        @Override
        default Config createDefault() {
            return new Config();
        }
    }

    private record TextWriter(Path file) implements SaveOnly {
        @Override
        public void serialize(Config config) throws SerializationException {
            try {
                Files.writeString(file, "written settings");
            } catch (java.io.IOException failure) {
                throw new SerializationException(failure);
            }
        }
    }
}
