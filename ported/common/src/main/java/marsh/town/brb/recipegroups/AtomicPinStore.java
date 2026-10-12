package marsh.town.brb.recipegroups;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

/** Synchronous, ordered saves: a failed write cannot truncate the previous pin file. */
public final class AtomicPinStore {
    private final Path path;

    public AtomicPinStore(Path path) { this.path = path; }

    public synchronized Set<String> read() throws IOException {
        Set<String> result = new LinkedHashSet<>();
        if (!Files.exists(path)) return result;
        JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
        JsonArray pins = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("pins");
        if (pins == null) throw new IOException("Pin document has no pins array");
        for (JsonElement pin : pins) {
            if (pin.isJsonPrimitive() && pin.getAsJsonPrimitive().isString()) {
                result.add(pin.getAsString());
            } else if (pin.isJsonObject()) {
                // Original BRB serialized ResourceLocation fields with Gson reflection.
                JsonObject object = pin.getAsJsonObject();
                if (object.has("namespace") && object.has("path"))
                    result.add(object.get("namespace").getAsString() + ":" + object.get("path").getAsString());
                else throw new IOException("Unrecognized legacy pin entry");
            } else throw new IOException("Invalid pin entry");
        }
        return result;
    }

    public synchronized void write(Set<String> pins) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 2);
            root.add("pins", new Gson().toJsonTree(pins));
            Files.writeString(temporary, new Gson().toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
