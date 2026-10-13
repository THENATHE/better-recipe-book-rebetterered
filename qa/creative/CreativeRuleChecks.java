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

  static void expect(CreativeGroupRules model, String item, String key) {
    var match = model.match("minecraft:" + item);
    check(match != null && match.key().equals(key), item + " belongs to " + key);
  }

  static void family(CreativeGroupRules model, String key, String members) {
    for (String item : members.split(" ")) expect(model, item, key);
  }

  static void checkFamilies(CreativeGroupRules model) {
    for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
        "mangrove", "cherry", "pale_oak", "poplar", "bamboo", "crimson", "warped")) {
      for (String shape : List.of("planks", "stairs", "slab", "fence", "fence_gate", "door",
          "trapdoor", "pressure_plate", "button", "sign", "hanging_sign", "shelf")) {
        expect(model, wood + "_" + shape, "wood:" + wood);
      }
      String trunk = wood.equals("bamboo") ? "block"
          : List.of("crimson", "warped").contains(wood) ? "stem" : "log";
      expect(model, wood + "_" + trunk, "wood:" + wood);
      expect(model, "stripped_" + wood + "_" + trunk, "wood:" + wood);
    }
    for (String color : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
        "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
      for (String shape : List.of("wool", "wool_stairs", "wool_slab", "carpet")) {
        expect(model, color + "_" + shape, "wool:" + color);
      }
      for (String shape : List.of("concrete", "concrete_stairs", "concrete_slab", "concrete_powder")) {
        expect(model, color + "_" + shape, "concrete:" + color);
      }
      for (String shape : List.of("terracotta", "glazed_terracotta", "stained_glass",
          "stained_glass_pane", "shulker_box", "cushion", "harness", "candle", "bundle",
          "bed", "banner", "dye")) {
        expect(model, color + "_" + shape, "color:" + shape);
      }
    }
    for (String base : List.of("terracotta", "shulker_box", "candle", "bundle")) {
      expect(model, base, "color:" + base);
    }
    expect(model, "glass", "color:stained_glass");
    expect(model, "tinted_glass", "color:stained_glass");
    expect(model, "glass_pane", "color:stained_glass_pane");
    family(model, "building:stone", "stone stone_stairs stone_slab smooth_stone smooth_stone_slab "
        + "stone_bricks stone_brick_stairs stone_brick_slab stone_brick_wall mossy_stone_bricks "
        + "mossy_stone_brick_stairs mossy_stone_brick_slab mossy_stone_brick_wall "
        + "chiseled_stone_bricks cracked_stone_bricks stone_button stone_pressure_plate");
    family(model, "building:cobblestone", "cobblestone cobblestone_stairs cobblestone_slab "
        + "cobblestone_wall mossy_cobblestone mossy_cobblestone_stairs mossy_cobblestone_slab mossy_cobblestone_wall");
    family(model, "building:deepslate", "deepslate cobbled_deepslate polished_deepslate "
        + "chiseled_deepslate deepslate_bricks deepslate_tiles cracked_deepslate_bricks "
        + "cracked_deepslate_tiles deepslate_brick_wall deepslate_tile_stairs reinforced_deepslate");
    family(model, "building:blackstone", "blackstone blackstone_wall gilded_blackstone "
        + "polished_blackstone polished_blackstone_button polished_blackstone_pressure_plate "
        + "polished_blackstone_bricks chiseled_polished_blackstone cracked_polished_blackstone_bricks "
        + "polished_blackstone_brick_stairs");
    for (String base : List.of("sandstone", "red_sandstone")) {
      family(model, "building:" + base, base + " " + base + "_stairs " + base + "_slab "
          + base + "_wall cut_" + base + " cut_" + base + "_slab smooth_" + base
          + " smooth_" + base + "_stairs smooth_" + base + "_slab chiseled_" + base);
    }
    for (String base : List.of("tuff", "cinnabar", "sulfur")) {
      family(model, "building:" + base, base + " " + base + "_stairs " + base + "_slab "
          + base + "_wall polished_" + base + " polished_" + base + "_stairs polished_" + base
          + "_slab polished_" + base + "_wall chiseled_" + base + " " + base + "_bricks "
          + base + "_brick_stairs " + base + "_brick_slab " + base + "_brick_wall");
    }
    expect(model, "chiseled_tuff_bricks", "building:tuff");
    for (String base : List.of("granite", "diorite", "andesite")) {
      family(model, "building:" + base, base + " " + base + "_stairs " + base + "_slab "
          + base + "_wall polished_" + base + " polished_" + base + "_stairs polished_" + base + "_slab");
    }
    family(model, "building:quartz", "quartz_block quartz_pillar quartz_bricks quartz_stairs "
        + "quartz_slab chiseled_quartz_block smooth_quartz smooth_quartz_stairs smooth_quartz_slab");
    family(model, "building:prismarine", "prismarine prismarine_wall prismarine_stairs prismarine_slab "
        + "prismarine_bricks prismarine_brick_stairs prismarine_brick_slab dark_prismarine "
        + "dark_prismarine_stairs dark_prismarine_slab");
    family(model, "building:basalt", "basalt polished_basalt smooth_basalt");
    family(model, "building:purpur", "purpur_block purpur_pillar purpur_stairs purpur_slab");
    family(model, "building:end_stone", "end_stone end_stone_bricks end_stone_brick_stairs "
        + "end_stone_brick_slab end_stone_brick_wall");
    family(model, "building:bricks", "bricks brick_stairs brick_slab brick_wall");
    family(model, "building:nether_bricks", "nether_bricks nether_brick_stairs nether_brick_slab "
        + "nether_brick_wall nether_brick_fence red_nether_bricks red_nether_brick_stairs "
        + "red_nether_brick_slab red_nether_brick_wall chiseled_nether_bricks cracked_nether_bricks");
    family(model, "building:mud", "mud packed_mud mud_bricks mud_brick_stairs mud_brick_slab mud_brick_wall");
    family(model, "building:resin", "resin_block resin_bricks resin_brick_stairs resin_brick_slab resin_brick_wall chiseled_resin_bricks");
    for (String wax : List.of("", "waxed_")) {
      expect(model, wax + "copper_block", "copper:copper");
      for (String age : List.of("", "exposed_", "weathered_", "oxidized_")) {
        if (!age.isEmpty()) expect(model, wax + age + "copper", "copper:copper");
        for (String shape : List.of("chiseled_copper", "cut_copper", "cut_copper_stairs", "cut_copper_slab",
            "copper_grate", "copper_bulb", "copper_door", "copper_trapdoor", "copper_bars",
            "copper_chain", "copper_chest", "copper_lantern", "copper_golem_statue")) {
          expect(model, wax + age + shape, "copper:copper");
        }
      }
    }
    for (String tier : List.of("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite")) {
      expect(model, tier + "_spear", "equipment:spear");
    }
    expect(model, "copper_nautilus_armor", "equipment:nautilus_armor");
    for (String excluded : List.of("stonecutter", "redstone", "redstone_block", "deepslate_diamond_ore",
        "infested_stone", "infested_mossy_stone_bricks", "infested_deepslate", "nether_quartz_ore",
        "quartz", "brick", "nether_brick", "resin_brick", "prismarine_shard", "prismarine_crystals",
        "copper_ingot", "copper_ore", "raw_copper_block", "copper_torch", "potent_sulfur", "sulfur_spike",
        "sulfur_cube_bucket", "white_tulip", "shelf_mushroom", "poplar_sapling", "red_poplar_leaves",
        "muddy_mangrove_roots", "bookshelf", "chiseled_bookshelf")) {
      check(model.match("minecraft:" + excluded) == null, "unrelated item remains individual: " + excluded);
    }
    for (String item : List.of("poplar_planks", "stone", "mossy_cobblestone", "white_wool_slab",
        "glass", "copper_block")) {
      check(model.match("custom:" + item) == null, "custom namespace stays independent: " + item);
    }
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
      {"light_blue_wool", "wool:light_blue"},
      {"red_wool", "wool:red"},
      {"blue_stained_glass_pane", "color:stained_glass_pane"},
      {"yellow_concrete_powder", "concrete:yellow"},
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
    checkFamilies(model);
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
