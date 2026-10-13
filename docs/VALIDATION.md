# Validation - Better Recipe Book: Rebetterered 1.11.7+26.3

Tested 2026-10-13. Installable JAR SHA-256: `28868f38950057d850d634364881855503093dcd8e70064faf7febcecb81621f`.

Sources JAR SHA-256: `efdf216b9e7223c41595929685287c26291fba11708e5998d32ea18bc8beb8b2`.

## This update

Smithing and brewing save Show craftable independently in the existing client configuration. Both Show craftable and Show all are restored when reopening the same workstation. New panels read the persisted setting before their first recipe list and filter-button state are built. Existing configurations default to Show all. The two preferences are excluded from the configuration GUI; users change them through the normal recipe-book filter button.

## Verification

**245 actual-client workstation checks passed**, including 50 saved-filter checks. Standard regressions passed: 131,072 sequence assertions, six ingredient-assignment cases, 1,155 creative grouping assertions, and 11 pin persistence/identity checks. A clean independent source build reproduced both JARs byte for byte. **261 actual-client settings checks passed** across all 15 controls at 320x240, 427x240 and 640x360. **68 packaged multiplayer assertions across ten scenarios passed** against an unmodified vanilla server using the exact installable JAR, Fabric API and optional Mod Menu 21.0.0. The packaged mod includes Cloth Config and excludes the QA fixture. Workstation tests include actual mouse filter toggles, menu closure/reopening, switching between both workstation types, persisted TOML values, reconstructed panels using freshly deserialized settings, and preservation of unrelated preferences. The reconstructed-panel check simulates the loading path; it does not claim a full client process restart.

Evidence lives in `qa/release-evidence/` in source and `qa/` in the release package. Public downloads and CI are checked after publishing.

## Scope

This is focused validation for the saved-filter change. Full-client and native/Polymer backpack results for 1.11.6 are historical and remain in [that release](https://github.com/THENATHE/better-recipe-book-rebetterered/releases/tag/v1.11.6%2B26.3); they are not fresh exact-JAR claims for 1.11.7. The original developer release and previous ports remain in their separate release folders. No additional upstream port is being created.

Crafting, grouping and transfer behavior is retained. Fill grid conservatively refuses uneven consumed ingredient counts, optional unused smithing inputs, unsupported custom reusable/multi-count remainders, untracked offhand merges, ambiguous identical backpack destinations and partial-capacity loads that would spill under Stop before dropping. Recipe previews cannot know undisclosed server-only changes. Brewing and smelting retain normal durations. Mouse Wheelie remains preserved but untested on 26.3. This is not exhaustive modpack certification.

Requirements: Minecraft 26.3, Java 25+, Fabric Loader 0.19.5+, Fabric API 0.161.0+26.3. Cloth Config 26.3.159 is bundled; Mod Menu 21.0.0 is optional. Install the main JAR on the client.
