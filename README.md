# Better Recipe Book: Rebetterered

![Original Better Recipe Book icon with ++](ported/fabric/src/main/resources/assets/brb/icon.png)

An unofficial Fabric 26.3 continuation of marshmallow's original Better Recipe Book, with configurable recipe and creative grouping, exact recipe pins, and right-click crafting.

See [installation, controls and build instructions](ported/README.md). Requires Java 25+, Fabric Loader 0.19.5+, and Fabric API 0.161.0+26.3. Cloth Config 26.3.159 is included; Architectury is removed. Install only one BRB variant. The internal mod ID remains `brb`.

## Build and test

Use a complete Java 25 JDK:

```sh
cd ported
bash gradlew clean check build clientqaJar
bash gradlew -PclientQa runClient
```

The first command runs the standard regression checks and builds the installable JAR and separate development fixture. The second starts actual Minecraft gameplay QA and requires a graphical environment. Use `-PqaSuite=recipes`, `creative`, `crafting`, `workstation`, or `lifecycle` to select a suite. Development fixtures are excluded from the installable JAR.

See [packaged multiplayer QA](qa/REMOTE-QA.md) and [release validation](docs/VALIDATION.md). Releases include the installable JAR, source, checksums and verification records.

## Provenance

MIT licensed. Based on original BRB commit `ea7c7872d6270d07e3a696e0b56b1e2043028887`; original attribution and source are preserved under `upstream/`. Better Recipe Book Extended was audited and consulted for API/behavior comparison; its implementation was not used as the base. This repository is an unofficial continuation.

The original developer-release 1.21/1.21.1 artifact remains preserved separately in the local release archive with its original dependencies. The modified 26.3 build does not replace that compatibility track.
