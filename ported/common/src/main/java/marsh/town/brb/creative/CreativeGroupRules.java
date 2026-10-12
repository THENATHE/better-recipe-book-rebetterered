package marsh.town.brb.creative;

import java.util.List;
import java.util.regex.Pattern;

/** Ordered resource-pack rules. The first matching rule owns an item. */
public final class CreativeGroupRules {
    private final List<Rule> rules;

    public CreativeGroupRules(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public Match match(String itemId) {
        for (Rule rule : rules) {
            var matcher = rule.pattern().matcher(itemId);
            if (matcher.matches()) {
                return new Match(rule.id() + ":" + matcher.replaceFirst(rule.key()),
                        matcher.replaceFirst(rule.label()));
            }
        }
        return null;
    }

    public record Rule(String id, Pattern pattern, String key, String label) {
        public Rule {
            validateReplacement(key, pattern.matcher("").groupCount());
            validateReplacement(label, pattern.matcher("").groupCount());
        }

        private static void validateReplacement(String replacement, int groups) {
            for (int index = 0; index < replacement.length(); index++) {
                char character = replacement.charAt(index);
                if (character == '\\') {
                    if (++index == replacement.length()) {
                        throw new IllegalArgumentException("Trailing escape in group replacement");
                    }
                } else if (character == '$') {
                    if (++index == replacement.length() || replacement.charAt(index) < '0'
                            || replacement.charAt(index) > '9') {
                        throw new IllegalArgumentException("Group replacements require numeric references such as $1");
                    }
                    int group = replacement.charAt(index) - '0';
                    if (group > groups) {
                        throw new IllegalArgumentException("Group replacement refers to a missing capture group");
                    }
                    while (index + 1 < replacement.length()) {
                        char next = replacement.charAt(index + 1);
                        if (next < '0' || next > '9' || group * 10 + next - '0' > groups) {
                            break;
                        }
                        group = group * 10 + next - '0';
                        index++;
                    }
                }
            }
        }
    }

    public record Match(String key, String label) {}
}
