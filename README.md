# Better Recipe Book: Rebetterered

[What it is](#what-it-is) · [Features](#features) · [Controls](#controls) · [Multiplayer](#multiplayer) · [Installation](#installation) · [Technical info](#technical-info) · [Downloads](https://github.com/THENATHE/better-recipe-book-rebetterered/releases)

![Better Recipe Book: Rebetterered icon](ported/fabric/src/main/resources/assets/brb/icon.png)

The **1.11.2** update brings vanilla-style workstation books, simpler settings with working mode buttons, combined same-output recipe groups, and remembered brewing/smithing book states. See the [changelog](docs/CHANGELOG.md) for this update and the [latest release](https://github.com/THENATHE/better-recipe-book-rebetterered/releases/latest) for downloads.

## What it is

A more useful Minecraft recipe book, with **expandable recipe groups, a tidier creative inventory, exact recipe pins, and right-click crafting**, maintained by **THENATHE** for **Fabric on Minecraft 26.3**.

Rebetterered continues marshmallow's original **Better Recipe Book**, keeping its quality-of-life improvements and adding more control over how recipes are displayed and crafted. Choose familiar vanilla cycling, expand groups with **+ / −**, or show every recipe variant individually so the one you want stays still while you click it.

This is an unofficial community continuation. Credit for the original mod belongs to its creators, thanked below. The mod runs on your client and works in singleplayer or when joining an unmodified vanilla server.

## Features

### Better Recipe Book

[Original mod on Modrinth](https://modrinth.com/mod/brb) · [Original source](https://github.com/mrshmllow/BetterRecipeBook/tree/ea7c7872d6270d07e3a696e0b56b1e2043028887)

Thanks for your hard work, **marshmallow**, and everyone who contributed to the original Better Recipe Book!

- Browse recipes before discovering them with the configurable **unlock-all** setting.
- Pin recipes so they are easier to find again.
- Scroll recipe pages, with optional wrapping from the last page back to the first.
- Keep the inventory centered when opening the book.
- Configure recipe animation and alternative-recipe presentation.
- Use vanilla-style recipe panels for brewing stands and smithing tables, each remembering its last open or closed state across restarts.
- Open settings directly from the recipe book.

#### My tweaks and additions

- Updated the original BRB implementation for **Minecraft 26.3**.
- Added separate display choices for the recipe book and creative inventory.
- Added right-click crafting with checks for ingredients, inventory space, and server responses.
- Made pins remember the selected recipe variant, including distinctions represented by its ingredients and item components.
- Removed Architectury while retaining and bundling Cloth Config.
- Added regression checks and actual-client tests for menus, crafting, pins, and multiplayer behavior.

### Recipe groups that stay still

Choose the presentation that works best for you:

| Recipe display mode | How it works |
| --- | --- |
| **Vanilla** | Keep the familiar grouped, cycling recipe presentation. |
| **Expandable** | Use **+ / −** to reveal or hide individual variants inside a group. Expanded variants stay fixed. |
| **All** | Show every recipe variant individually, without cycling. |

Recipes producing the same item and components share an expandable group, even when they use different ingredients or produce different counts. Each expanded recipe keeps its own ingredients and crafting behavior.

Expansion headers only change which recipes are visible. Clicking a **+ / −** header never places ingredients or crafts an item. **Expandable** is the default.

### A tidier creative inventory

- Condense related creative items into expandable **+ / −** groups.
- Expand a group to choose its individual items, then collapse it when you are done.
- Switch back to **Vanilla** creative display independently of your recipe-book setting.
- Keep creative search, the inventory tab, and saved hotbars using their normal presentation.

### Craft straight into your inventory

- **Right-click** a recipe to craft one recipe batch directly into your inventory.
- **Shift + right-click** to keep crafting while ingredients and inventory space allow.
- Keep normal **left-click** placement and **Shift + left-click** maximum ingredient placement.
- Automatically stop when the menu changes, crafting is disabled, or the expected server state no longer matches.
- Check storage before taking the result, so overflow is not dropped on the ground.

A batch means one recipe execution: for example, one log produces four planks. Direct crafting supports the crafting grid and smithing table. Brewing and smelting keep their normal processing times.

### Brewing, smithing, and pins

- Search and browse brewing and smithing recipes from their workstation panels.
- Use the same recipe display choices in those panels.
- Preserve native names, damage, enchantments, and trims when smithing.
- Pin a specific variant and find it again from the pins view.
- Choose the hovered recipe when pinning, with keyboard focus as a fallback.

### Settings your way

Open the **recipe-book settings button**, or select **Better Recipe Book: Rebetterered** through optional **Mod Menu**.

Recipe display, creative display, instant crafting, pinning, scrolling, centering, and unlock-all behavior are configurable. The pin key defaults to **R** and can be changed in Minecraft's Controls settings.

## Controls

| Control | Action |
| --- | --- |
| **+ / −** group header | Expand or collapse a group without placing ingredients or crafting. |
| **Left-click** a recipe | Place its ingredients normally. |
| **Shift + left-click** | Place as many ingredients as fit. |
| **Right-click** | Craft one recipe batch into inventory when instant crafting is enabled. |
| **Shift + right-click** | Craft the maximum supported by available ingredients and inventory space. |
| **R** over a recipe, by default | Pin or unpin that exact recipe variant. |
| **Mouse wheel** over the recipe book | Change recipe pages when scrolling is enabled. |

The direct-crafting controls apply to crafting grids and smithing. In the brewing panel, right-click places ingredients for normal-duration brewing.

## Multiplayer

**Install Rebetterered on your client. The server does not need it.** Other players can use their own recipe-book setup.

| How you play | What you get |
| --- | --- |
| Singleplayer | Recipe-book improvements, creative grouping, brewing/smithing panels, and direct crafting using the world's recipe data. |
| Joining an unmodified vanilla server | Client-side menus and crafting controls, with crafting validated by the server. |
| Joining a server with custom recipes | Learned recipe displays come from the server; undiscovered and workstation previews use recipe data available to your client. |

Server rules still apply, including limited crafting. Recipes supplied only by a server datapack may be missing from previews or differ until the server shares their displays. Rebetterered covers the vanilla recipe book and its brewing/smithing panels; arbitrary modded machine recipes need their own supported interface.

## Installation

1. Use **Minecraft 26.3**, **Java 25 or newer**, and **Fabric Loader 0.19.5 or newer**.
2. Install **Fabric API 0.161.0+26.3** for this release.
3. Download `better-recipe-book-rebetterered-1.11.2+26.3.jar` from [Releases](https://github.com/THENATHE/better-recipe-book-rebetterered/releases/latest) and put it in your client's `mods/` folder.
4. Remove other BRB variants from that instance. Keep a backup of your existing settings and pins when upgrading.
5. Launch Minecraft and choose your preferred display and crafting settings from the recipe-book settings button or Mod Menu.

| Dependency | Installation |
| --- | --- |
| **Fabric API 0.161.0+26.3** | Required separate download. |
| **Cloth Config 26.3.159** | Included in the mod JAR. |
| **Mod Menu 21.0.0** | Optional; provides another way to open settings. |
| **Architectury** | Not required. |

Install only the main mod JAR for normal play. The sources JAR, full source ZIP, and QA evidence are development downloads. Release checksums are provided alongside the files.

The internal mod ID remains `brb`, keeping the existing settings and pin-file locations. Original brewing/smithing favorites using older identifiers may need to be pinned again. The previous release and original developer-release artifacts remain preserved separately.

## Technical info

### Source and credits

MIT licensed. The implementation starts from original BRB commit `ea7c7872d6270d07e3a696e0b56b1e2043028887`. Original attribution and source are preserved under [upstream/](upstream/), with license notices retained in the production JAR.

Better Recipe Book Extended was audited and consulted for API and behavior comparison; its implementation was not used as the base. The creative grouping and right-click crafting controller are new implementations. See [provenance](docs/PROVENANCE.json) for the recorded inputs and dependencies.

### Building and testing

Use a complete Java 25 JDK:

```sh
cd ported
bash gradlew clean check build clientqaJar
bash gradlew -PclientQa runClient
```

The first command runs the standard regression checks and builds the mod and separate development fixture. The second starts actual Minecraft gameplay QA and requires a graphical environment. Select an individual suite with `-PqaSuite=recipes`, `creative`, `crafting`, `workstation`, or `lifecycle`. Development fixtures are excluded from the installable JAR.

### Verification and compatibility limits

Version **1.11.2+26.3** passed **505 recorded singleplayer checks**, including 225 settings checks, **42 packaged multiplayer assertions** against an unmodified vanilla server, and the standard regression checks. A separate source build reproduced both JARs byte-for-byte. See [validation](docs/VALIDATION.md) for evidence and scope.

See [validation](docs/VALIDATION.md), [packaged multiplayer QA](qa/REMOTE-QA.md), and the [changelog](docs/CHANGELOG.md) for coverage and remaining limits. No compatible Minecraft 26.3 Mouse Wheelie release was available to test its optional integration. Other mod combinations and custom datapacks have not been exhaustively tested.
