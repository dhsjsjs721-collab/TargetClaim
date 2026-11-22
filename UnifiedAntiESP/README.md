# UnifiedAntiESP 3.4.0 - wall occlusion hardening

Paper 26.3 / Java 25. Standalone plugin.

## Changes

- No near-distance exception: player and mob occlusion is checked even one block behind a wall.
- PlayerTrackEntityEvent cancels an occluded player or entity before its spawn packet is sent. Already tracked entities are hidden per viewer using Paper's hideEntity API.
- No repeated 224-block entity cube scans. The plugin keeps bounded candidate watch-lists from Paper tracker events, up to 2048 candidates per player, then revisits a limited number on each scan.
- Default range 224 blocks for entity and player ESP, plus strict underground mob mode at >=40 blocks. Legacy 3.2/3.3 default ranges are upgraded in memory when config-version is absent.
- Scan budgets are configurable; no automated block/chunk/entity removal, chunk loads or redstone changes.

## Config and performance

Use /uesp to see rays, hidden entity counts, scan time, and budget stops. Use /uesp audit for the read-only Paper native Anti-Xray audit. Configure entity-esp.* and player-esp.* in plugins/UnifiedAntiESP/config.yml. If using an existing config file, merge the new section; the default file is not overwritten on upgrade.

The strict-distant-underground-mobs setting hides dark cave mobs beyond 40 blocks even with an unobstructed line of sight, which can impact normal play. Disable if you need far cave mob visibility.

Paper's separate Anti-Xray config still controls ore and cave *blocks*. This JAR does not rewrite chunk packets, cannot fully hide water/lava or open cave geometry and cannot eliminate every side channel (sound, particles, light updates, client-cached terrain). There is no guarantee of absolutely zero ESP information leakage, especially with a bounded main-thread CPU budget.

## Install and test

1. Back up world files and plugin config. Replace the older UnifiedAntiESP JAR and fully restart Paper, not PlugMan or /reload.
2. Do not enable the optional strong Paper Anti-Xray preset until after checking whether previously invisible chunks are now rendered correctly.
3. Test with a live client: mobs behind walls at 1, 5, 30, 70, 125 blocks, as well as mobs in direct sight, player PvP, walls breaking, Nether and mob farms.
4. Inspect /uesp on the test server and monitor MSPT using spark. Plugin compilation and unit tests do not establish runtime performance or correctness in these scenarios.

Independent implementation. Existing seed protections and ore shuffling are preserved.
