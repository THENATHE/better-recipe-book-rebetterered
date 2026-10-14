# BRBR combined QA review - 2026-10-13

Better Recipe Book: Rebetterered **1.11.7+26.3** keeps the original published main JAR and sources JAR. Fresh source review found no production BRBR issue requiring a change. The all-four installation below passed **1,968 full-client checks**, including **24 checks across four actual rejected-transfer correction cases**, and **four connected backpack profiles**. A separate clean Java 25 build reproduced the published main and sources JARs byte for byte.

## Exact installation

| Mod ID | Version | SHA-256 |
| --- | --- | --- |
| brb | 1.11.7+26.3 | `28868f38950057d850d634364881855503093dcd8e70064faf7febcecb81621f` |
| vpp_mod_suite | 1.4.4+26.3 | `e4663dddf2e176c3d5d7a669f59822dc76286ed28e195cc1a77b1bb1b3e7d269` |
| hotkey_wheel | 1.0.7+26.3 | `3095b28e981dd995aabc582b7abe785baeccfdc4ac00128eabd6bda0d5a55fd6` |
| buildprint_toning | 0.1.24 | `b9c42fd4679f9c70c584a6f8a888e51911c0dfe90f10dddd92c4e427efec5940` |
| buildprint | 0.1.19+26.x | `bd60814e7ba944d7a67bfbf9831a8fa156223277345dc87569cd358a326e3f1f` |

Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Microsoft OpenJDK 25.0.4.1+1-LTS. The full fixture also loads Mod Menu 21.0.0, YACL 3.9.7+26.3-fabric and the suite's locked dependencies. Cloth Config 26.3.159 is bundled in BRBR. Each launch audit records exact dependency JARs, command and hashes. The separate QA fixture is `bc027d66a82bb66874d6ac449e0ba6803ede07f907b9bd015e16b1642c9f21a8` and is excluded from the shipping mod.

## Full packaged client

The exact installable BRBR, suite, wheel, Blocktone and Buildprint JARs ran together on the hardware X11 display. **1,968 checks passed, exit 0, with 26 screenshots.** Coverage includes real native recipe-group headers, fixed variants, pagination/search/filtering, creative groups and custom namespace item preservation, native recipe pins, 2x2/3x3 single-batch and fill-grid crafting, full/partial inventory overflow policies, bucket remainders, menu-close cancellation, workstation saved preferences, lifecycle state and all 15 settings controls at three window sizes.

The new correction cases intercept real predicted result packets before they reach the integrated server. Each verifies a live pending request before the authoritative full-state correction, then cancellation, unchanged server ingredients, restored client storage and empty cursors. Both crafting strategies and both grid sizes pass. This is actual corrective state handling; the packet rejection is deliberately introduced by the QA observer.

## Connected backpack profiles

| Server installation | Craft-all strategy | Repeated backpack cases | Prepared-cake policy cases | Delayed predicate checks |
| --- | --- | --- | --- | --- |
| Native suite, Polymer absent | Single batches | 8 | 8 | 0 |
| Native suite, Polymer absent | Fill grid | 8 | 8 | 48 |
| Suite + Polymer 0.18.2+26.3 | Single batches | 8 | 8 | 0 |
| Suite + Polymer 0.18.2+26.3 | Fill grid | 8 | 8 | 48 |

All four runners passed. Native modded clients load all four requested mods and connect to separate dedicated suite servers. Each profile repeats leather/netherite backpacks, 2x2/3x3 grids, ordinary and maximum crafting, wide/narrow layouts, pin-tab selection, settings Save/Cancel and instant-toggle controls while checking authoritative ingredient conservation, output location, holder components and cursors.

Each profile starts with 272 planks. All four maximum cases produce and store 68 crafting tables with player storage full. Each fill-grid maximum performs exactly two real result QUICK_MOVE transfers and zero result PICKUP transfers, proving refill after 64 recipes. Each profile also passes eight player-only bucket remainder cases covering both strategies and all overflow policies. The additional delayed-grid checks deliberately hold the observation predicates unsettled to exercise the suite acknowledgement guard; they do not simulate packet loss.

Supported Polymer artifact: `polymer-bundled-0.18.2+26.3.jar`, SHA-256 `3d79318e8202d0e87af308d7f232220714e5f1409d06c23db774877ec6f3848f`. These are native modded client checks with and without Polymer on the server; they do not claim a vanilla client can use BRBR.

## Source and evidence

Production source remains the original BRB continuation at base commit `f7621db5137a3c037baa5eab2cc974bd4660d5fd`; this review changes QA fixtures, launch tooling and documentation. The main JAR remains `28868f38950057d850d634364881855503093dcd8e70064faf7febcecb81621f`; sources JAR remains `efdf216b9e7223c41595929685287c26291fba11708e5998d32ea18bc8beb8b2`. Clean independent source export and complete Java 25 build passed all 131,072 sequence assertions, six assignment cases, 1,155 creative selector assertions and 11 pin checks, reproducing both shipping JARs and the final QA fixture exactly.

Detailed results and hashes are in `qa/release-evidence/fresh-review/`: `review-summary.json`, `packaged-all-four/`, `connected/`, `final-source-rebuild.json` and `final-build-inputs.json`. Runtime execution snapshots preserve the actual fixtures and launcher source used. Evidence copies exclude worlds, runtime mods and game assets. Failed/environmental/interrupted attempts remain explicitly separated in `attempts.json` and `diagnostics/`; the initial synthetic recipe failure did not record its chosen IDs, so no unrecorded cause is asserted. The creative fixture's namespace assumption was corrected and the synthetic recipe fixture now intentionally selects Minecraft output IDs for its Minecraft namespace search.

The original release tag, main JAR, sources JAR, original full source ZIP, original source manifest and historical `qa-evidence.zip` are preserved. The new reviewed-source and combined-QA archives are separately named; the historical tag continues to identify the original shipping source and new QA documentation is committed on main. Original upstream developer artifacts and previous ports remain separate. The paused SSO port was not built or tested; the suite uses the authorized private official developer SSO release.

These results cover the exact candidate installation above. They do not establish exhaustive modpack or graphics performance certification. Mouse Wheelie remains preserved but untested on 26.3. Conservative instant-crafting guards documented in VALIDATION remain applicable. The isolated software-renderer attempt failed during graphics initialization and is diagnostic evidence only; the final passing run uses the hardware display.
