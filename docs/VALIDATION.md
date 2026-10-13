# Validation - Better Recipe Book: Rebetterered 1.11.6+26.3

Tested 2026-10-13. Installable JAR SHA-256: `c129d74ee023c0de60b17fc960e14abe475d0f8035482d58ce98810eb704ea36`.

Sources JAR SHA-256: `33c80adc1b9ad46ed738a3bc343db5015b7f5d192b64be6038096384fffb54c2`.

## Passed

- An independent sanitized source export passed clean `check build clientqaJar` with Microsoft OpenJDK 25.0.4.1. Standard regressions passed: 131,072 container-sequence comparisons, six ingredient-assignment cases, 11 pin persistence/identity checks and 1,155 creative selector checks. Installable and sources JARs were reproduced byte-for-byte against the final hashes above. The source-rebuild evidence records 161 build-input files and their manifest checksum.

- **1,893 recorded singleplayer checks** passed in the combined final client run against the source producing the exact packaged hashes above. This includes 1,005 creative checks, 365 crafting checks, 58 native pin-tab checks, 195 workstation checks, eight recipe-lifecycle checks and 261 settings checks; the recipe-mode assertions share one evidence entry.
- **261 settings checks** cover all 15 controls at 320x240, 427x240 and 640x360 GUI sizes: actual mouse/keyboard mode selection, overflow-policy selection, scrolling, non-overlapping labels, defaults/Reset, Save, runtime object agreement, TOML reread, reopening, Cancel and Discard.
- **58 native pinned-tab checks** cover both inventory and crafting-table books: visible/clickable tabs, exact non-cycling variants, 2x2 exclusions, all recipe modes, search, craftable filtering, empty lists, unpin refresh, return to native categories, narrow resize and disabling/re-enabling pinning.

- **365 actual-client crafting checks** passed for both craft-all methods in inventory and crafting-table menus. Tests select the strategy and overflow policy through the actual Cloth controls and Save before crafting.
- **Fill grid** sends exactly one result `QUICK_MOVE` for eight loaded plank batches and two for a 68-batch load/refill, with zero result `PICKUP` packets. **One at a time** sends eight and 68 individual result pickups respectively. Server counts verify every output batch and ingredient consumed.
- Cake crafting uses three actual result `QUICK_MOVE` loads in Fill grid, including the milk-bucket remainders, with zero result pickups. Both methods conserve all nine buckets, preserve the final three vanilla grid remainders, and leave both cursors empty without dropping items.
- **Six unequal-grid refusal checks** cover both 2x2 and 3x3 prepared crafting grids. Fill grid sends no result click, preserves every prepared ingredient, leaves both cursors empty and drops nothing when consumed ingredient counts differ.
- **All 24 overflow scenarios** passed: both methods, all three policies, both 2x2/3x3 menus, and completely full storage versus one free output space. Inventory output plus authoritative dropped-item entities equals the expected crafted total; uncrafted grid inputs and unrelated storage stacks remain intact.
- **195 actual-client workstation checks** passed, including both smithing strategies with exact components, one true result Shift-click crafting all three valid stackable trim bases, and two true Shift-click loads for distinct unstackable armor bases. Both cursors remain empty and authoritative output/input counts match. Native-style controls, recipe modes, pins, remembered open/closed states, Escape behavior, settings Save, layout and normal-duration brewing remain covered.
- **All six smithing overflow scenarios** passed: both methods and all three policies with three prepared input sets and completely full storage. Stop before dropping crafts/drops none and keeps all three sets; Drop and continue crafts/drops three; Stop after first drop crafts/drops one and keeps two sets. Exact trimmed output components, both empty cursors and every unrelated storage stack are verified. Together with the crafting matrix, **all 30 overflow scenarios** passed.
- **68 packaged multiplayer assertions across ten scenarios** passed against an unmodified Mojang 26.3 server on localhost using the exact final installable JAR, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 and optional Mod Menu 21.0.0. Learned and undiscovered 68-batch refill scenarios use exactly two actual result `QUICK_MOVE` packets and zero result pickups. Both strategies, ordinary controls, authoritative output/input counts and input return passed. Cloth Config opened; Architectury was absent.
- **32 connected backpack scenarios** passed with the exact final JAR: 16 native and 16 with Polymer. Each profile includes eight repeated crafting/table cases and eight returned-bucket policy/ordinary-right-click cases, for 16 bucket cases in total. Both 2x2/3x3 grids and leather/netherite capacities pass. Each 68-batch cycle uses exactly two result `QUICK_MOVE` packets and zero result pickups, storing crafted output in the bag and keeping its holder item component consistent. Actual settings Save/Cancel, pins, wide/narrow layout, scrolling and 32 registered Chalk variants remain covered. Bucket cases verify both strategies, all overflow policies and ordinary right-click safety; returned buckets follow vanilla player/grid remainder handling, not backpack storage.
- **22 fresh screenshots** are included for recipe/creative expansion, native pinned tabs, workstation layouts, all settings controls and connected native/Polymer backpack screens. Runtime checks also verify control bounds and inventory preservation.
- Ordinary left-click, Shift-left maximum placement, one-batch right-click, local/undiscovered recipe placement, protected named ingredients, partial-capacity refusal, cursor preservation and cancellation checks remain covered by this run.

## Current behavior

Craft all defaults to **One at a time**. **Fill grid** maximizes ingredient placement and performs actual result Shift-click transfers, then waits for the complete confirmed inventory/grid snapshot before refilling. Supported paths include native crafting, ordinary crafting remainders, smithing and known backpack output-storage groups. It does not silently substitute individual pickups for an unsupported bulk load.

| Craft overflow setting for Shift-right | Behavior |
| --- | --- |
| **Stop before dropping** (default) | Stop before a batch would spill output or remainders into the world. |
| **Drop and continue** | Allow dropped output while crafting all available inputs. When result storage is full, use the result's bulk `THROW` path. |
| **Stop after first drop** | Complete the first batch that drops an item, then stop. At that overflow edge, use one explicit result pickup so a bulk click cannot run through additional batches. |

Ordinary right-click remains one batch and retains its storage safeguards. Changing the method or overflow policy, disabling instant crafting, user container input, a screen/container/connection change, an unexpected server state or a timeout stops subsequent automatic work.

Public delivery checks are performed after upload; packaged evidence covers the candidate tested above.

Evidence lives in `qa/release-evidence/` in source and `qa/` in the release package. Detailed local launch logs remain outside public source.

## Scope and limitations

This is targeted regression/gameplay QA, not exhaustive modpack certification or a frame-rate benchmark. The preserved Mouse Wheelie integration remains untested because no compatible 26.3 release was available.

Fill grid requires equal counts in every consumed ingredient stack. Uneven depleting stacks stop conservatively because exhausting one ingredient could select a different recipe during the native Shift-click loop. Smithing recipes with optional unused input slots are also outside the supported bulk path. Unsupported custom reusable or multi-count remainders, untracked offhand merges and ambiguous identical backpack destinations stop conservatively. The untracked-offhand guard also applies to the single-batch overflow path; choosing One at a time does not make an unobservable remainder merge safe. Under Stop before dropping, a partial-capacity bulk load that would spill also stops rather than substituting individual crafts. These guards preserve inventory state and do not silently fall back to per-batch crafting. The explicit Stop after first drop edge described above is the intentional single-batch exception.

Grouping joins distinct recipes producing the same item and components; it does not split alternatives inside a single tag-based ingredient into separate recipes. Brewing/smelting retain normal durations. Multiplayer previews cannot know undisclosed server-only recipe changes. Original brewing/smithing favorites using old potion/template aliases may need to be pinned again.

Stonecutters use an always-visible selector rather than a collapsible recipe book. The original developer release and previous port releases are preserved; the original 1.21.1 startup check remains historical evidence rather than a fresh gameplay test.

Backpack integration targets Vanilla++ Backpack Crafting Experiment 1.4.0-beta.1+26.3, separately from the stable suite that lacks this crafting book.
