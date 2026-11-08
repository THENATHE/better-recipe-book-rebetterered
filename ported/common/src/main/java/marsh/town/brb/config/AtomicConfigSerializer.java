package marsh.town.brb.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.serializer.ConfigSerializer;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import me.shedaniel.autoconfig.util.Utils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Cloth Config's TOML format, saved through a temporary file so an interrupted save cannot truncate
 * the settings. Cloth falls back to defaults when the file cannot be read; that file is copied aside
 * before the first save replaces it, as the pins file is.
 */
public final class AtomicConfigSerializer<T extends ConfigData> implements ConfigSerializer<T> {
    private final Path path;
    private final Path savingPath;
    private final ConfigSerializer<T> reader;
    private final ConfigSerializer<T> writer;
    private boolean unreadableFile;

    public AtomicConfigSerializer(Config definition, Class<T> configClass) {
        this(Utils.getConfigFolder().resolve(definition.name() + ".toml"),
                Utils.getConfigFolder().resolve(definition.name() + ".saving.toml"),
                new Toml4jConfigSerializer<>(definition, configClass),
                new Toml4jConfigSerializer<>(named(definition.name() + ".saving"), configClass));
    }

    /** The writer must write {@code savingPath}; the reader reads {@code path}. */
    public AtomicConfigSerializer(Path path, Path savingPath, ConfigSerializer<T> reader, ConfigSerializer<T> writer) {
        this.path = path;
        this.savingPath = savingPath;
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public T deserialize() throws SerializationException {
        try {
            T config = reader.deserialize();
            unreadableFile = false;
            return config;
        } catch (SerializationException | RuntimeException failure) {
            // Mismatched value types escape Cloth's own parse handling and would leave the
            // settings unregistered. Report both the same way so Cloth uses defaults.
            unreadableFile = Files.exists(path);
            if (failure instanceof SerializationException serializationFailure) throw serializationFailure;
            throw new SerializationException(failure);
        }
    }

    @Override
    public void serialize(T config) throws SerializationException {
        try {
            if (unreadableFile && Files.exists(path)) {
                Files.copy(path, path.resolveSibling(path.getFileName() + ".unreadable-" + System.currentTimeMillis()),
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
            unreadableFile = false;
            writer.serialize(config);
            try {
                Files.move(savingPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(savingPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new SerializationException(failure);
        } finally {
            try {
                Files.deleteIfExists(savingPath);
            } catch (IOException ignored) {
                // A leftover temporary file is replaced by the next save.
            }
        }
    }

    @Override
    public T createDefault() {
        return reader.createDefault();
    }

    private static Config named(String name) {
        return new Config() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Config.class;
            }
        };
    }
}
