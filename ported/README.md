# Better Recipe Book: Rebetterered 1.11.1+26.3

Unofficial Fabric 26.3 port of marshmallow's original Better Recipe Book, with new grouping and crafting controls. MIT licensed. Original source and release are preserved separately in ../upstream/.

Requires Minecraft 26.3, Java 25+, Fabric Loader 0.19.5+ and Fabric API 0.161.0+26.3. Cloth Config 26.3.159 is included. Architectury is removed. Mod Menu is optional. Install `better-recipe-book-rebetterered-1.11.1+26.3.jar` in the client’s `mods/` folder; a server does not need this mod. Its mod ID remains `brb`, preserving existing settings and pins. Install only one BRB variant at a time.

Recipe display settings offer Vanilla, Expandable and All. Creative display offers Vanilla and Expandable. Expansion headers only change visibility. Expanded recipe variants do not cycle. Left-click places ingredients; Shift-left fills the grid normally. Right-click crafts one batch into inventory; Shift-right continues while materials and storage permit. Press the configured pin key over a recipe to pin its exact display variant.

Brewing and smithing retain recipe panels. Brewing and smelting use their vanilla processing durations; direct crafting applies to crafting grids and smithing. Smithing preserves native item components and trims. Pins are stored atomically; legacy native recipe IDs remain retained while exact display identities are added. Inventory transfers exclude equipment, never drop overflow, and cancel when the menu changes or synchronization fails.

Vanilla multiplayer does not transmit the full recipe registry. Singleplayer uses an immutable server snapshot rebased to client registries; multiplayer undiscovered/brewing/smithing previews use installed vanilla and mod recipe data. Unknown server-only datapack recipes cannot be previewed before the server shares their recipe displays. All crafting remains server-authoritative, including limited-crafting rules.

Settings are available through the recipe-book gear or optional Mod Menu. The original Mouse Wheelie hook is retained behind optional mod detection, but there is no verified 26.3 Mouse Wheelie release; do not install its older Minecraft artifact into this version.

Build with the complete Java 25 JDK: `bash gradlew clean check build clientqaJar`. Development client tests: `bash gradlew -PclientQa runClient`. QA fixtures are separate from the production JAR. See ../qa/ for build and runtime evidence. The `check` task runs sequence/ingredient, pin persistence and creative selector regressions. Actual client fixtures cover controls, crafting, workstations and dimension changes. See the release VALIDATION.md for executed results.

Implementation starts from original upstream commit ea7c7872d6270d07e3a696e0b56b1e2043028887. Extended was audited and consulted for API/behavior comparison; its implementation was not used as the base. Creative groups and the right-click crafting controller are newly implemented. This is an unofficial continuation, not an official upstream release.
