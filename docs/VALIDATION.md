# Validation - Better Recipe Book: Rebetterered 1.11.1+26.3

Tested 2026-10-12. Installable JAR SHA-256: `1788316a99505b767f6076884469910de62bb04737212bc2a6195e28e143fa04`.

## Passed

- Clean `check build clientqaJar` with complete Microsoft Java 25, Gradle 9.6.1 and Loom 1.17.20.
- Standard regressions: 131,072 container-sequence comparisons, six ingredient-assignment cases, 11 pin persistence/identity checks and 27 creative selector checks. These now run under Gradle `check`.
- **192 recorded actual-client checks**, including all four reviewed fixes. The recipe suite also contains multiple assertions grouped into one evidence entry. All three recipe modes, both creative modes, normal/Shift-left placement, right/Shift-right crafting, exact pins, brewing/smithing, settings, pagination and overflow preservation passed.
- New crafting cases exercise actual widgets in both 2x2 and 3x3 grids: stacked-grid left/Shift-left redistribution, right/Shift-right crafting, named-material refusal, full-inventory refusal, authoritative item conservation and empty cursors.
- New popup and pin cases verify the instant toggle closes the vanilla alternatives popup, externally enabled instant crafting does not consume clicks through it, and native/workstation pins prefer hovered recipes over stale focus.
- An actual Nether dimension roundtrip verifies the same recipe book survives, disabling unlock-all removes every local recipe, server-learned entries remain, and subsequent toggles stay correct.
- **42 packaged multiplayer assertions** against an unmodified Mojang 26.3 server bound to localhost. The exact installable JAR above was used, with Fabric API 0.161.0+26.3 and optional Mod Menu 21.0.0. There were no server mods. Both learned and undiscovered recipe controls and server inventory counts passed. Cloth Config opened successfully; Architectury was absent.
- An independent sanitized source export passed clean build and all standard checks, reproducing the installable and sources JARs byte-for-byte.
- Production ZIP integrity, metadata, nested Cloth Config and exclusion of QA classes checked. The icon is unchanged from 1.11.0.

Evidence lives in `qa/release-evidence/` in source and `qa/` in the release package. Detailed local launch logs are retained in the development workspace, outside the public source.

## Scope and limitations

This is targeted regression and gameplay QA, not an exhaustive modpack/security certification or frame-rate benchmark. The singleplayer profile omits Mod Menu; packaged multiplayer includes it. No compatible 26.3 Mouse Wheelie release was available, so its present profile remains untested.

Redistribution preflights temporary inventory storage and safely refuses if the grid cannot be returned there; it does not attempt a direct cursor-only shuffle through a full inventory. Brewing/smelting retain normal durations. Multiplayer previews cannot know undisclosed server-only recipe changes. Original brewing/smithing favorites using old potion/template aliases may need to be pinned again; that separate migration issue was outside these four fixes.

The unchanged original developer release and previous 1.11.0 port are preserved. The original JAR's checksum was rechecked; its prior 1.21.1 startup smoke remains historical evidence, not a new gameplay test in this release.
