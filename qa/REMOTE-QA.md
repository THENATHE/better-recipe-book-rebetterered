# Better Recipe Book: Rebetterered packaged remote client QA

The opt-in `RemoteClientQa` is packaged by `run_remote_client.py` as a separate development fixture. It is absent from the production release and only registers its tick runner when `-Dbrb.remoteQa=true` is set. It connects to an owned, offline localhost server as `brbremoteqa`, which needs operator permission for the test commands. The server can remain the unmodified Mojang 26.3 server.

The fixture checks that no integrated server exists, loads the packaged vanilla recipe catalogue and remote brewing/smithing adapters, then drives six actual survival recipe-widget scenarios: left placement, Shift-left maximum placement, right single batch, Shift-right maximum crafting, and undiscovered local placement/crafting. Operator commands count items on the authoritative server through `clear ... 0`, which does not remove items. Unique command-response markers synchronize the assertions. Closing each menu then checks that every unconsumed crafting input returned to server inventory.

Prepare or run with a fresh directory:

```sh
python3 qa/run_remote_client.py \
  --server 127.0.0.1:25593 \
  --username brbremoteqa \
  --run qa/remote-packaged-final \
  --jar ported/build/libs/better-recipe-book-rebetterered-1.11.1+26.3.jar \
  --launch
```

Omit `--launch` to prepare and audit without connecting. Compile `compileClientqaJava` and build the intended production JAR first. The launcher uses a full retained JDK 25, verifies the Mojang client and 66 official libraries against the local Mojang manifest, verifies the official asset index, and records the SHA-256 of the exact production JAR and separately packaged fixture. Architectury is not supplied; Cloth Config must be nested in the release.

The retained Loom asset index had equivalent JSON objects with different raw bytes. The exact official index referenced by the manifest was downloaded to `qa/remote-inputs/34.json` and verified as SHA-1 `1e4e4a68dece1c0f51b8f93b45da8fe8da51bcd3`; no retained cache files were overwritten. All 5,147 referenced assets were already present. Official source: `https://piston-meta.mojang.com/v1/packages/1e4e4a68dece1c0f51b8f93b45da8fe8da51bcd3/34.json`.

Preparation passed. A connection result is only established by the chosen run directory's `remoteqa-result.txt` and matching `launch-audit.json`. The fixture has a 2,500-tick deadline; the launcher terminates a stuck process after 240 seconds.
