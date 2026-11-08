# BRBR 1.11.8+26.3 QA evidence - 2026-11-08

Exact installable JAR `f02f61c6b710fc9ae04503dd245bb4283ef323ccfa278408fad58467ecbd9e16`, QA fixture `7d426ae7c6b15da98a2a3d97df7a549b73ffe7fd1b61145ac9afa212327da8af`. All clients ran on a private Xvfb display. Every run folder keeps its own `launch-audit.json` or `audit.json` with the exact command, dependency JARs and hashes.

| Folder | What it shows |
| --- | --- |
| `packaged-all/` | Full client fixture with Vanilla++ 1.4.5, Fabric API 0.165.2, Loader 0.20.1 and Mod Menu 21.0.0: 1,968 checks, 26 screenshots. |
| `upgrade/seed-1.11.7`, `upgrade/verify-1.11.8` | Settings, pins and the pin key saved by the real 1.11.7 JAR, then loaded unchanged by 1.11.8. |
| `upgrade/recovery-*` | Truncated, mistyped and unknown-option settings files on 1.11.8. Their `*-seed` folders hold the prepared inputs. |
| `upgrade/baseline-1.11.7-recovery-*` | The same inputs on 1.11.7, reproducing the original faults (expected failures). |
| `multiplayer/easy/` | Loopback vanilla-server runs before the copied test world was set to peaceful: candidate runs 1-5 (run 1 stopped conservatively, run 5 lost the player to a slime) and one 1.11.7 baseline. |
| `multiplayer/peaceful/` | Three candidate and three 1.11.7 baseline runs on the peaceful world, all passing 68 assertions. |
| `connected/` | Four connected backpack profiles: native and Polymer 0.18.2 servers, Single batches and Fill grid. |
| `build-1.11.8.log` | `gradlew clean check build clientqaJar` with all standard regressions. |

Worlds, runtime mods, assets and Minecraft libraries are excluded.
