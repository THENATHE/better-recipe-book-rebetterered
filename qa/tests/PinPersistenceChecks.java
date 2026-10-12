import com.google.gson.JsonParser;
import marsh.town.brb.recipegroups.AtomicPinStore;
import marsh.town.brb.recipegroups.CanonicalJson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class PinPersistenceChecks {
    public static void main(String[] arguments) throws Exception {
        Path folder = Files.createTempDirectory("brb-pins-regression-");
        Path file = folder.resolve("brb.pins");
        AtomicPinStore store = new AtomicPinStore(file);
        try {
            require(store.read().isEmpty(), "Missing file must load empty");
            Files.writeString(file, "[{\"namespace\":\"minecraft\",\"path\":\"oak_planks\"},\"minecraft:birch_planks\"]");
            require(store.read().equals(Set.of("minecraft:oak_planks", "minecraft:birch_planks")), "Legacy object and string migration failed");
            Set<String> migrated = store.read();
            store.write(migrated);
            require(store.read().equals(migrated), "Migration discarded original pins");
            store.write(Set.of("brb:display/first"));
            store.write(Set.of("brb:display/second"));
            require(store.read().equals(Set.of("brb:display/second")), "Latest ordered save was overwritten");
            try (var files = Files.list(folder)) { require(files.count() == 1, "Successful save left temporary files"); }
            String malformed = "{ broken pin data";
            Files.writeString(file, malformed);
            boolean rejected = false;
            try { store.read(); } catch (Exception exception) { rejected = true; }
            require(rejected, "Malformed JSON silently accepted");
            require(Files.readString(file).equals(malformed), "Reading malformed JSON modified original data");
            Files.writeString(file, "{\"version\":2,\"pins\":[42]}");
            rejected = false;
            try { store.read(); } catch (Exception exception) { rejected = true; }
            require(rejected, "Invalid pin entries silently accepted");
            var a = JsonParser.parseString("{\"result\":{\"id\":\"minecraft:potion\",\"components\":{\"potion\":\"minecraft:water\"}},\"ingredients\":[\"oak\",\"birch\"]}");
            var reordered = JsonParser.parseString("{\"ingredients\":[\"oak\",\"birch\"],\"result\":{\"components\":{\"potion\":\"minecraft:water\"},\"id\":\"minecraft:potion\"}}");
            var componentChange = JsonParser.parseString("{\"result\":{\"id\":\"minecraft:potion\",\"components\":{\"potion\":\"minecraft:awkward\"}},\"ingredients\":[\"oak\",\"birch\"]}");
            var ingredientChange = JsonParser.parseString("{\"result\":{\"id\":\"minecraft:potion\",\"components\":{\"potion\":\"minecraft:water\"}},\"ingredients\":[\"birch\",\"oak\"]}");
            require(CanonicalJson.fingerprint(a).equals(CanonicalJson.fingerprint(reordered)), "JSON object iteration order changed identity");
            require(!CanonicalJson.fingerprint(a).equals(CanonicalJson.fingerprint(componentChange)), "Component variants collapsed to one pin");
            require(!CanonicalJson.fingerprint(a).equals(CanonicalJson.fingerprint(ingredientChange)), "Positioned ingredient variants collapsed to one pin");
            System.out.println("PASS: pin migration, latest ordered save, temporary cleanup, malformed preservation, component and ingredient identity (11 checks)");
        } finally {
            try (var files = Files.list(folder)) { for (Path child : files.toList()) Files.deleteIfExists(child); }
            Files.deleteIfExists(folder);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
