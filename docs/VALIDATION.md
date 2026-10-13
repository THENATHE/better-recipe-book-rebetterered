# Validation - Better Recipe Book: Rebetterered 1.11.5+26.3

Tested 2026-10-12. Installable JAR SHA-256: `3621bcb3f6eceb1c9031b4313f354d38a67acb6366f0e2c378a8c7d7f63ee544`.

## Passed

- Combined actual-client run completed successfully with complete Microsoft Java 25, Gradle 9.6.1 and Loom 1.17.20.
- A sanitized source export passed clean `check build clientqaJar` and standard regressions: 131,072 container-sequence comparisons, six ingredient-assignment cases, 11 pin persistence/identity checks and 1,155 creative selector checks. Both installable and sources JARs were reproduced byte-for-byte.
- **1,705 recorded actual-client checks**, covering all recipe and creative modes, normal/Shift-left placement, right/Shift-right crafting, exact pins, brewing/smithing, pagination, overflow preservation and dimension-change recipe cleanup. Recipe-mode assertions are grouped into one evidence entry.
- **1,005 creative checks** within that total: real-tab family headers and exact child order/count, all 16 wool/concrete color families, building materials, glass/panes, default shulkers, cushions, candles, bundles and harnesses. Actual mouse expansion/collapse preserves carried stacks; scroll indices remain aligned. Additional families cover native leaves/log tags with tab-specific grouping, ores, sherds, patterns, corals including dead variants, anvils, paintings, rods, horns, discs, heads, infested blocks and eight copper state groups. Spawn eggs use a creeper representative without changing child order. Every tested header resolves grey “Click to expand” / “Click to collapse” text.
- **243 settings checks** within that total: all 14 visible controls clicked through actual screen input at 320x240, 427x240 and 640x360 GUI sizes; non-overlapping labels; every recipe/creative mode and wraparound; keyboard activation; wheel scrolling; boolean/enum Reset; both craft-all methods and their default; Save; TOML reread; reopening; Cancel/Discard; unchanged live settings before Save.
- **58 native pinned-tab checks** within that total: actual tab clicks in inventory and crafting-table screens, all three recipe display modes, exact variant membership, 2x2 recipe exclusions, search, the actual craftable filter, empty pin lists, R-key unpin refresh, returning to the original native tab, narrow resize, alignment, and disabling/re-enabling pinning. Pinned entries remain individual and unshaded.
- **244 crafting checks** within that total: both craft-all methods in 2x2 and 3x3 menus, ordinary and Shift-left placement, single right-click batches, single-result pickups versus actual bulk quick-move, server-confirmed refill beyond 64 batches, partial output space, protected named ingredients, crafting remainders, output/ingredient conservation, empty cursor and cancellation.
- **146 workstation checks** within that total: native-style controls, category tabs, craftable filtering, search, paging, exact pins, crafting/brewing, narrow layouts with blocked background slots, focus release after clicking away, and actual settings Save disabling/re-enabling the same workstation screen.
- Brewing and smithing each reopen in their last open/closed state, preserve the other workstation preference. Escape now closes the entire menu without changing the remembered state, including focused search and narrow layouts. Separate TOML deserialization verifies disk persistence. Furnace, blast furnace and smoker retain native recipe-book state handling.
- Same-output grouping checks cover separate source collections, component isolation, output-count differences, connected recipe families, exact source IDs/pins, craftability and unchanged Vanilla/All behavior.
- **18 current screenshots visually inspected**, including expanded recipe/creative shading, native pinned tabs and the updated settings rows. No overlap or clipping found; child tint is lighter than the expanded-header shade.
- Actual-screen wheel routing is tested, including bounds, wrapping, disabled scrolling and workstation pagination. Shared badges and dark expanded headers remain available for collapse. Expanded children now share a subtle `0x40000000` tint in recipe, creative and workstation books, lighter than the `0x99000000` expanded-header shade; ordinary entries and pinned recipes retain their normal appearance.
- Native registry audit covers every stairs/slab/wall/shelf/cushion item except the deliberately separate legacy petrified oak slab. Rules retain unknown namespaces and unrelated ingredients as individual entries; explicitly requested Chalk IDs are supported without adding a dependency.

- **16 connected backpack scenarios**, eight native and eight with Polymer, passed with the exact 1.11.5 installable JAR and **Fill grid** selected. Both profiles cover leather/netherite capacities and both crafting grids, actual pinned-tab clicks/position, wide/narrow layout, settings, scrolling, normal right-click followed by Shift-right-click crafting, ingredient conservation, cursor/slot preservation and registered Chalk variants. The suite target is Vanilla++ Backpack Crafting Experiment 1.4.0-beta.1+26.3 (suite SHA-256 `23459d1ba1ffcb87790475e58a8c195f838d879360cbf17bd0295eded45cb40b`).

- **66 packaged multiplayer assertions across ten scenarios** passed against an unmodified Mojang 26.3 server on localhost using the exact 1.11.5 installable JAR, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 and optional Mod Menu 21.0.0. Both craft-all methods, learned/undiscovered recipes, 68-batch refill, authoritative output/ingredient counts and input return passed. Cloth Config opened; Architectury was absent. No server mods.

Public delivery checks are performed after upload; packaged evidence covers the candidate tested above. Previous releases retain their own validation records.

Evidence lives in `qa/release-evidence/` in source and `qa/` in the release package. Detailed local launch logs remain outside public source.

## Scope and limitations

This is targeted regression/gameplay QA, not exhaustive modpack certification or a frame-rate benchmark. Singleplayer omits Mod Menu; packaged multiplayer uses optional Mod Menu. The preserved Mouse Wheelie integration remains untested because no compatible 26.3 release was available.

Grouping joins distinct recipes producing the same item and components; it does not split alternatives inside a single tag-based ingredient into separate recipes. Brewing/smelting retain normal durations. Multiplayer previews cannot know undisclosed server-only recipe changes. Original brewing/smithing favorites using old potion/template aliases may need to be pinned again.

Workstation state persistence was verified by actual close/reopen and independent on-disk deserialization, without an additional full game restart. Stonecutters use an always-visible selector, not a collapsible recipe book. The original developer release and previous port releases are preserved; the original 1.21.1 startup check remains historical evidence rather than a fresh gameplay test.

Dependency provenance hashes now identify the runtime Fabric Loader/API JARs; earlier records accidentally selected their source JARs. The installed dependency versions are unchanged.

Backpack integration results are recorded separately against the exact Vanilla++ Backpack Crafting Experiment 1.4.0-beta.1+26.3, not the stable suite that lacks this crafting book.

Craft all defaults to **One at a time**. **Fill grid** maximizes ingredient placement and uses one bulk result transfer only for ordinary native inventory/crafting-table menus with a balanced loaded grid, no crafting remainders and enough room for every output batch. Other loads retain guarded individual transfers. Refill waits for the exact server-confirmed final inventory; changing the method cancels pending work. Normal right-click still crafts one batch.
