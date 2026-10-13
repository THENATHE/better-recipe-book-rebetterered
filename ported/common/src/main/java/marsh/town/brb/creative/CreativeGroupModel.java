package marsh.town.brb.creative;

import com.google.gson.JsonParser;
import marsh.town.brb.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class CreativeGroupModel {
    private final CreativeGroupRules rules = loadRules();
    private final Set<String> expanded = new HashSet<>();
    private List<ItemStack> source = List.of();
    private List<Entry> entries = List.of();
    private String tabId;

    public void setSource(List<ItemStack> source, boolean enabled) {
        setSource(source, enabled, null);
    }

    public void setSource(List<ItemStack> source, boolean enabled, String tabId) {
        // Keep the vanilla stacks, including their components, order and duplicates.
        this.source = List.copyOf(source);
        this.tabId = tabId;
        rebuild(enabled);
    }

    public List<Entry> entries() {
        return entries;
    }

    public Entry entry(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    public boolean toggle(int index) {
        Entry entry = entry(index);
        if (entry == null || !entry.header()) {
            return false;
        }
        if (!expanded.add(entry.groupKey())) {
            expanded.remove(entry.groupKey());
        }
        rebuild(true);
        return true;
    }

    private void rebuild(boolean enabled) {
        var result = new ArrayList<Entry>();
        if (!enabled) {
            for (ItemStack stack : source) {
                result.add(new Entry(stack, null, null, 0, false, false));
            }
            entries = List.copyOf(result);
            return;
        }

        Map<String, List<ItemStack>> members = new LinkedHashMap<>();
        var matches = new ArrayList<CreativeGroupRules.Match>(source.size());
        for (ItemStack stack : source) {
            var match = rules.match(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), tabId);
            matches.add(match);
            if (match != null) {
                members.computeIfAbsent(match.key(), ignored -> new ArrayList<>()).add(stack);
            }
        }

        Set<String> emitted = new HashSet<>();
        for (int index = 0; index < source.size(); index++) {
            ItemStack stack = source.get(index);
            var match = matches.get(index);
            List<ItemStack> group = match == null ? null : members.get(match.key());
            if (group == null || group.size() < 2) {
                result.add(new Entry(stack, null, null, 0, false, false));
            } else if (emitted.add(match.key())) {
                boolean open = expanded.contains(match.key());
                ItemStack representative = stack;
                if (match.icon() != null) {
                    for (ItemStack member : group) {
                        if (BuiltInRegistries.ITEM.getKey(member.getItem()).toString().equals(match.icon())) {
                            representative = member;
                            break;
                        }
                    }
                }
                result.add(new Entry(representative, match.key(), humanize(match.label()), group.size(), true, open));
                if (open) {
                    for (ItemStack child : group) {
                        result.add(new Entry(child, match.key(), null, 0, false, true));
                    }
                }
            }
        }
        entries = List.copyOf(result);
    }

    private static String humanize(String label) {
        String text = label.replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static CreativeGroupRules loadRules() {
        var rules = new ArrayList<CreativeGroupRules.Rule>();
        Identifier resourceId = Identifier.fromNamespaceAndPath("brb", "creative-groups.json");
        var resource = Minecraft.getInstance().getResourceManager().getResource(resourceId);
        if (resource.isEmpty()) {
            return new CreativeGroupRules(rules);
        }
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            for (var element : JsonParser.parseReader(reader).getAsJsonArray()) {
                try {
                    var rule = element.getAsJsonObject();
                    Set<String> tabs = new HashSet<>();
                    if (rule.has("tabs")) {
                        for (var tab : rule.getAsJsonArray("tabs")) {
                            if (!tab.isJsonPrimitive() || !tab.getAsJsonPrimitive().isString()) {
                                throw new IllegalArgumentException("Creative grouping tab IDs must be strings");
                            }
                            tabs.add(tab.getAsString());
                        }
                    }
                    rules.add(new CreativeGroupRules.Rule(rule.get("id").getAsString(),
                            Pattern.compile(rule.get("pattern").getAsString()),
                            rule.get("key").getAsString(), rule.get("label").getAsString(), tabs,
                            rule.has("icon") ? rule.get("icon").getAsString() : null));
                } catch (RuntimeException exception) {
                    BetterRecipeBook.LOGGER.warn("Skipping an invalid creative grouping rule", exception);
                }
            }
        } catch (Exception exception) {
            BetterRecipeBook.LOGGER.warn("Could not load creative grouping rules; retaining vanilla items", exception);
            rules.clear();
        }
        return new CreativeGroupRules(rules);
    }

    public record Entry(ItemStack stack, String groupKey, String label, int members,
                        boolean header, boolean expanded) {}
}
