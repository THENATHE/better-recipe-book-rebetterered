# Validation - Better Recipe Book: Rebetterered 1.11.4+26.3

Tested 2026-10-12. Installable JAR SHA-256: `475088b5e8f4c6b30b1f3f648f103a847a1ad8fab7d410d6f3fd6dabc608864c`.

## Passed

- Clean `check build clientqaJar` with complete Microsoft Java 25, Gradle 9.6.1 and Loom 1.17.20; combined client run also completed successfully.
- Standard regressions: 131,072 container-sequence comparisons, six ingredient-assignment cases, 11 pin persistence/identity checks and 1,155 creative selector checks.
- **1,477 recorded actual-client checks**, covering all recipe and creative modes, normal/Shift-left placement, right/Shift-right crafting, exact pins, brewing/smithing, pagination, overflow preservation and dimension-change recipe cleanup. Recipe-mode assertions are grouped into one evidence entry.
- **1,005 creative checks** within that total: real-tab family headers and exact child order/count, all 16 wool/concrete color families, building materials, glass/panes, default shulkers, cushions, candles, bundles and harnesses. Actual mouse expansion/collapse preserves carried stacks; scroll indices remain aligned. Additional families cover native leaves/log tags with tab-specific grouping, ores, sherds, patterns, corals including dead variants, anvils, paintings, rods, horns, discs, heads, infested blocks and eight copper state groups. Spawn eggs use a creeper representative without changing child order. Every tested header resolves grey “Click to expand” / “Click to collapse” text.
- **225 settings checks** within that total: all 13 visible controls clicked through actual screen input at 320x240, 427x240 and 640x360 GUI sizes; non-overlapping labels; every recipe/creative mode and wraparound; keyboard activation; wheel scrolling; boolean/enum Reset; Save; TOML reread; reopening; Cancel/Discard; unchanged live settings before Save.
- **146 workstation checks** within that total: native-style controls, category tabs, craftable filtering, search, paging, exact pins, crafting/brewing, narrow layouts with blocked background slots, focus release after clicking away, and actual settings Save disabling/re-enabling the same workstation screen.
- Brewing and smithing each reopen in their last open/closed state, preserve the other workstation preference. Escape now closes the entire menu without changing the remembered state, including focused search and narrow layouts. Separate TOML deserialization verifies disk persistence. Furnace, blast furnace and smoker retain native recipe-book state handling.
- Same-output grouping checks cover separate source collections, component isolation, output-count differences, connected recipe families, exact source IDs/pins, craftability and unchanged Vanilla/All behavior.
- Screenshots inspected for creative material families, expanded poplar/cobblestone, bottom-right badges, vanilla-style workstation layouts and config rows.
- Actual-screen wheel routing is tested, including bounds, wrapping, disabled scrolling and workstation pagination. Shared badges and dark expanded headers were visually inspected; child variants retain their normal appearance.
- Native registry audit covers every stairs/slab/wall/shelf/cushion item except the deliberately separate legacy petrified oak slab. Rules retain unknown namespaces and unrelated ingredients as individual entries; explicitly requested Chalk IDs are supported without adding a dependency.
- **42 packaged multiplayer assertions** against an unmodified Mojang 26.3 server on localhost using the exact installable JAR, Fabric API 0.161.0+26.3 and optional Mod Menu 21.0.0. No server mods. Learned/undiscovered recipe controls and authoritative item counts passed; Cloth Config opened and Architectury was absent.
- **16 connected backpack scenarios**, eight each with and without Polymer, using native suite clients and the exact final BRB JAR with Vanilla++ Backpack Crafting Experiment 1.4.0-beta.1+26.3 (suite SHA-256 `23459d1ba1ffcb87790475e58a8c195f838d879360cbf17bd0295eded45cb40b`). Leather/netherite capacities and both crafting grids pass settings Save/Cancel, instant toggles, wide/narrow positions, real-screen scrolling, ordinary/maximum crafting, slot/cursor preservation and authoritative ingredient conservation. Actual registered Chalk items group as 32 exact variants in both profiles.
- An independent sanitized source export passed clean build and standard checks, reproducing both installable and sources JARs byte-for-byte.
- Production ZIP integrity, metadata, nested Cloth Config, icon equality and exclusion of QA classes checked. Original developer JAR checksum rechecked unchanged.

Evidence lives in `qa/release-evidence/` in source and `qa/` in the release package. Detailed local launch logs remain outside public source.

## Scope and limitations

This is targeted regression/gameplay QA, not exhaustive modpack certification or a frame-rate benchmark. Singleplayer omits Mod Menu; packaged multiplayer includes it. The preserved Mouse Wheelie integration remains untested because no compatible 26.3 release was available.

Grouping joins distinct recipes producing the same item and components; it does not split alternatives inside a single tag-based ingredient into separate recipes. Brewing/smelting retain normal durations. Multiplayer previews cannot know undisclosed server-only recipe changes. Original brewing/smithing favorites using old potion/template aliases may need to be pinned again.

Workstation state persistence was verified by actual close/reopen and independent on-disk deserialization, without an additional full game restart. Stonecutters use an always-visible selector, not a collapsible recipe book. The original developer release and previous port releases are preserved; the original 1.21.1 startup check remains historical evidence rather than a fresh gameplay test.

Dependency provenance hashes now identify the runtime Fabric Loader/API JARs; earlier records accidentally selected their source JARs. The installed dependency versions are unchanged.

Backpack integration results are recorded separately against the exact Vanilla++ Backpack Crafting Experiment 1.4.0-beta.1+26.3, not the stable suite that lacks this crafting book.
