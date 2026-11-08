# Validation - Better Recipe Book: Rebetterered 1.11.8+26.3

Tested 2026-11-08. Installable JAR SHA-256: `f02f61c6b710fc9ae04503dd245bb4283ef323ccfa278408fad58467ecbd9e16`.

Sources JAR SHA-256: `5438ff7fbdec4521676276d631f863fa8d1a4822b1061f4a8cee654805a21996`. QA fixture (not shipped): `7d426ae7c6b15da98a2a3d97df7a549b73ffe7fd1b61145ac9afa212327da8af`.

## This update

Settings now go through a temporary file. A settings file that cannot be read is copied aside before the first save replaces it. Every read failure leaves the settings registered with Cloth Config, and unknown option values fall back to their defaults. Fabric API moves to 0.165.2+26.3, built and tested with Fabric Loader 0.20.1. See the [audit](AUDIT-2026-11-08.md) for findings, upstream review and dependency pins.

## Verification

All results below are fresh runs of the exact installable JAR above, on a private Xvfb display (Mesa llvmpipe, OpenGL 4.6 core), Microsoft OpenJDK 25.0.4.1.

- **Standard regressions** (`gradlew clean check build clientqaJar`): 131,072 sequence assertions, six ingredient-assignment cases, 1,155 creative selector assertions, 11 pin persistence checks, plus the new **15 settings persistence checks** and **12 Vanilla++ backpack-crafting contract checks**.
- **Clean source rebuild**: an independent export of the committed source reproduced the main JAR, sources JAR and QA fixture byte for byte.
- **Full packaged client**: **1,968 checks passed**, with 26 screenshots. BRBR, Fabric API 0.165.2+26.3, Loader 0.20.1, Mod Menu 21.0.0 and Vanilla++ 1.4.5+26.3 (with codecui, Fabric Language Kotlin, fzzy_config and mixson) ran together. This covers recipe groups, pagination, search and filtering, creative groups, native pins, 2x2/3x3 single-batch and Fill grid crafting, overflow policies, bucket remainders, rejected-transfer corrections, workstation preferences, lifecycle state and all 15 settings controls at three window sizes. Loading Vanilla++ applies its required BRBR mixins at startup.
- **Upgrade from 1.11.7**: the real 1.11.7 JAR (Fabric API 0.161.0, Loader 0.19.5) saved non-default values for all 20 settings, five exact pins (three crafting, one smithing, one brewing variant) and a pin key rebound to P. The 1.11.8 JAR then loaded **all 20 settings, all five pins and the key unchanged**. Its startup re-save wrote byte-identical settings and left no temporary file (5 checks).
- **Settings recovery**: a truncated `brb.toml` and one with mismatched value types each loaded defaults, kept the settings registered, opened the settings screen, and preserved exactly one byte-identical `brb.toml.unreadable-*` copy across two saves (7 checks each). A file with unknown option names kept every valid value and restored only the unknown ones, including Craft all overflow to Stop before dropping (9 checks). The same truncated and unknown-option files on the exact 1.11.7 JAR reproduced the original faults: the settings were not registered, and overflow was left unset.
- **Multiplayer**: **68 packaged assertions across ten scenarios passed** against an unmodified Mojang 26.3 server on loopback, with Loader 0.19.5, Fabric API 0.165.2 and optional Mod Menu 21.0.0. Of eight candidate runs, six passed, all three after setting the copied test world to the peaceful difficulty its provenance records. One run failed because a slime killed the test player on Easy. One run, also on Easy, stopped conservatively after Shift-right-click maximum placement, with all eight logs still in the grid and none lost. Four 1.11.7 baseline runs in the same environment passed. The intermittent stop is listed below as an open observation.
- **Connected backpack profiles**: dedicated Vanilla++ servers without Polymer passed for both **Single batches** and **Fill grid** with Vanilla++ 1.4.5, Fabric API 0.165.2 and Loader 0.19.5. Each covered leather and netherite backpacks, 2x2/3x3 grids, 272-ingredient refills with output into a full inventory's backpack, eight prepared-cake remainder cases, and wide/narrow layout, settings and pin-tab controls. Fill grid included 48 delayed-predicate checks. The same two profiles also passed with Polymer 0.18.2+26.3 on the server, for four connected profiles in total.

## Scope and limits

- Tiered Backpacks 1.0.21+26.3 was checked against its published source (`DataComponents.CONTAINER` storage and menu slots unchanged). Vanilla++ 1.4.5 still bundles Tiered Backpacks 1.0.20, so 1.0.21 has not run with BRBR yet. It will need the next suite build and the combined check.
- Hotkey Wheel, Blocktone and Buildprint were not loaded in this run; their owners are updating them, and the combined check covers them together. The 1.11.7 combined record in [COMBINED-QA-2026-10-13.md](COMBINED-QA-2026-10-13.md) is historical.
- Open observation: one multiplayer run in eight stopped after a maximum placement without crafting. Pending crafts are cancelled whenever an unmatched full inventory update arrives, so items stay safe, but the cause was not isolated. It did not recur in the next seven runs.
- Mouse Wheelie remains preserved but untested: no 26.3 release exists. Recipe previews cannot know undisclosed server-only recipe changes. Brewing and smelting keep normal durations. This is not exhaustive modpack certification.

Requirements: Minecraft 26.3, Java 25+, Fabric Loader 0.19.5+, Fabric API 0.165.2+26.3. Cloth Config 26.3.159 is bundled; Mod Menu 21.0.0 is optional. Install the main JAR on the client.

Evidence lives in `qa/release-evidence/1.11.8/` in source and `qa/` in the release package. Earlier release validation stays in each release folder.
