import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import marsh.town.brb.creative.CreativeGroupRules;

public class CreativeRuleChecks {
  static int checks;

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
    checks++;
    System.out.println("PASS: " + message);
  }

  public static void main(String[] args) throws Exception {
    var rules = new ArrayList<CreativeGroupRules.Rule>();
    for (var value : JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray()) {
      var r = value.getAsJsonObject();
      rules.add(
          new CreativeGroupRules.Rule(
              r.get("id").getAsString(),
              Pattern.compile(r.get("pattern").getAsString()),
              r.get("key").getAsString(),
              r.get("label").getAsString()));
    }
    var model = new CreativeGroupRules(rules);
    String[][] cases = {
      {"oak_planks", "wood:oak"},
      {"stripped_oak_log", "wood:oak"},
      {"dark_oak_stairs", "wood:dark_oak"},
      {"bamboo_mosaic_stairs", "wood:bamboo"},
      {"warped_hyphae", "wood:warped"},
      {"pale_oak_hanging_sign", "wood:pale_oak"},
      {"light_blue_wool", "color:wool"},
      {"red_wool", "color:wool"},
      {"blue_stained_glass_pane", "color:stained_glass_pane"},
      {"yellow_concrete_powder", "color:concrete_powder"},
      {"diamond_pickaxe", "equipment:pickaxe"},
      {"netherite_helmet", "equipment:helmet"},
      {"copper_sword", "equipment:sword"},
      {"potion", "potions:potion"},
      {"splash_potion", "potions:splash_potion"},
      {"tipped_arrow", "potions:tipped_arrow"},
      {"zombie_spawn_egg", "eggs:spawn_eggs"},
      {"sentry_armor_trim_smithing_template", "trim:armor_trims"},
      {"waxed_weathered_cut_copper_stairs", "copper:copper"}
    };
    for (var c : cases)
      check(model.match("minecraft:" + c[0]).key().equals(c[1]), "default selector " + c[0]);
    check(model.match("minecraft:stick") == null, "unmatched items stay visible individually");
    check(
        model.match("custom:oak_planks") == null,
        "custom namespace not silently grouped by vanilla rules");
    check(
        model.match("minecraft:potion").label().equals("potion variants"),
        "labels use stable captures");
    for (String invalid : List.of("$9", "$", "$x", "\\")) {
      try {
        new CreativeGroupRules.Rule("bad", Pattern.compile("(.*)"), invalid, "label");
        throw new AssertionError("Accepted invalid " + invalid);
      } catch (IllegalArgumentException expected) {
        checks++;
        System.out.println("PASS: invalid replacement " + invalid);
      }
    }
    var priority =
        new CreativeGroupRules(
            List.of(
                new CreativeGroupRules.Rule("first", Pattern.compile("(.*)"), "$1", "first"),
                new CreativeGroupRules.Rule("second", Pattern.compile("(.*)"), "$1", "second")));
    check(
        priority.match("minecraft:oak_planks").key().startsWith("first:"),
        "first resource rule deterministically owns overlapping matches");
    System.out.println("RESULT: PASS; " + checks + " assertions");
  }
}
