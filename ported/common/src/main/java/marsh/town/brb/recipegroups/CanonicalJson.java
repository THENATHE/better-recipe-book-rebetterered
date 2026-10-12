package marsh.town.brb.recipegroups;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

public final class CanonicalJson {
    private CanonicalJson() { }

    public static JsonElement sorted(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject result = new JsonObject();
            JsonObject object = element.getAsJsonObject();
            for (String key : new TreeSet<>(object.keySet())) result.add(key, sorted(object.get(key)));
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) result.add(sorted(child));
            return result;
        }
        return element.deepCopy();
    }

    public static String fingerprint(JsonElement element) {
        try {
            byte[] bytes = sorted(element).toString().getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }
}
