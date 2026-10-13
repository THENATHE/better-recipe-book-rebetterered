# Validation - Better Recipe Book: Rebetterered 1.11.2+26.3

Tested 2026-10-12. Installable JAR SHA-256: `b18f78dff65c6e5a2859c8ae5e0f966c0374b41ea3d62282d678f86e6c31d8dc`.

## Passed

- Clean `check build clientqaJar` with complete Microsoft Java 25, Gradle 9.6.1 and Loom 1.17.20; combined client run also completed successfully.
- Standard regressions: 131,072 container-sequence comparisons, six ingredient-assignment cases, 11 pin persistence/identity checks and 27 creative selector checks.
- **505 recorded actual-client checks**, covering all recipe and creative modes, normal/Shift-left placement, right/Shift-right crafting, exact pins, brewing/smithing, pagination, overflow preservation and dimension-change recipe cleanup. Recipe-mode assertions are grouped into one evidence entry.
- **225 settings checks** within that total: all 13 visible controls clicked through actual screen input at 320x240, 427x240 and 640x360 GUI sizes; non-overlapping labels; every recipe/creative mode and wraparound; keyboard activation; wheel scrolling; boolean/enum Reset; Save; TOML reread; reopening; Cancel/Discard; unchanged live settings before Save.
- **110 workstation checks** within that total: native-style controls, category tabs, craftable filtering, search, paging, exact pins, crafting/brewing, narrow layouts with blocked background slots, focus release after clicking away, and actual settings Save disabling/re-enabling the same workstation screen.
- Brewing and smithing each reopen in their last open/closed state, remember Escape closure, and preserve the other workstation preference. Separate TOML deserialization verifies disk persistence. Furnace, blast furnace and smoker retain native recipe-book state handling.
- Same-output grouping checks cover separate source collections, component isolation, output-count differences, connected recipe families, exact source IDs/pins, craftability and unchanged Vanilla/All behavior.
- Screenshots inspected for the vanilla-style workstation layouts, upper/lower config rows and inset centered native + / − badges.
- **42 packaged multiplayer assertions** against an unmodified Mojang 26.3 server on localhost using the exact installable JAR, Fabric API 0.161.0+26.3 and optional Mod Menu 21.0.0. No server mods. Learned/undiscovered recipe controls and authoritative item counts passed; Cloth Config opened and Architectury was absent.
- An independent sanitized source export passed clean build and standard checks, reproducing both installable and sources JARs byte-for-byte.
- Production ZIP integrity, metadata, nested Cloth Config, icon equality and exclusion of QA classes checked. Original developer JAR checksum rechecked unchanged.

Evidence lives in `qa/release-evidence/` in source and `qa/` in the release package. Detailed local launch logs remain outside public source.

## Scope and limitations

This is targeted regression/gameplay QA, not exhaustive modpack certification or a frame-rate benchmark. Singleplayer omits Mod Menu; packaged multiplayer includes it. The preserved Mouse Wheelie integration remains untested because no compatible 26.3 release was available.

Grouping joins distinct recipes producing the same item and components; it does not split alternatives inside a single tag-based ingredient into separate recipes. Brewing/smelting retain normal durations. Multiplayer previews cannot know undisclosed server-only recipe changes. Original brewing/smithing favorites using old potion/template aliases may need to be pinned again.

Workstation state persistence was verified by actual close/reopen and independent on-disk deserialization, without an additional full game restart. Stonecutters use an always-visible selector, not a collapsible recipe book. The original developer release and previous port releases are preserved; the original 1.21.1 startup check remains historical evidence rather than a fresh gameplay test.
