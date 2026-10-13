# Backpack layout regression fixture

Copied from Vanilla++ Suite's development-only `qa-backpack-crafting` launcher and fixtures, then extended here. The original suite checkout and release are unchanged. The supplied suite JAR must contain the backpack crafting addon; the stable 1.3.1 suite does not include this book.

Pass the exact suite artifact with `--jar` and the exact BRB candidate with `--brbr`. Use a fresh `--label` for each run; add `--without-polymer` for the native profile. `--cycles 2 --backpack` covers leather and netherite capacities with 2x2 and 3x3 hammers, ordinary and maximum instant crafting. Add `--craft-all-method fill_grid` to exercise maximum grid filling; the default `single_batches` retains individual transfers. The selected method appears in each client observation and the final result JSON.

The client verifies the Pins tab position against the native book origin and visible category count, selects it by mouse, then restores the original native tab and search without changing inventory. It checks settings and instant buttons against the actual native recipe-book origin at wide and narrow sizes, drives their mouse callbacks, and verifies settings Save/Cancel return to the same backpack. It compares every slot and carried stack before and after controls, and tests scrolling at the book's left edge. The inherited server fixture checks authoritative ingredient conservation. Screenshots and exact artifact hashes are saved under the unique run directory. Input uses Minecraft screen callbacks, not physical mouse automation.

These sources are development-only and never shipped in the mod JAR. Generated run directories remain ignored. The optional integration launcher expects the retained Vanilla++ workspace and its local launch helpers. Merely preparing these fixtures does not establish a passing run.

The client also checks all 32 registered normal/glowing Chalk items from the suite with its colorful addon. Their group preserves the original stack objects and order. Test logs and result JSON identify the exact suite and BRB JAR hashes.

Use `--output-to-backpack --backpack` to fill player storage and verify that output enters backpack storage, including its item component update. Fill grid maximum actions require one actual QUICK_MOVE and zero result PICKUP packets for each loaded grid.

Set `--ingredients 272 --cycles 2 --craft-all-method fill_grid` to verify two output Shift-clicks refill beyond 64 crafting-table recipes. Combine it with `--output-to-backpack --backpack` to check the holder component and output-storage route across both loads.
