# UnifiedAntiESP 3.2.0 — Player + Entity ESP + Paper cave/ore obfuscation

Paper 26.3 / Java 25.

## What changed

- **UndergroundEntityGuard:** Uses Paper's cancellable `PlayerTrackEntityEvent` to prevent hidden underground mob/item spawns reaching the client in the first place. Periodic bounded scans use `Player.hideEntity(plugin, entity)` to conceal already tracked entities when fully blocked by terrain.
- Targets mobs, dropped items, experience orbs, armor stands, paintings, item frames, display entities, minecarts and boats by default. Excludes players (existing player ESP guard handles those) and destructive gameplay entities such as projectiles/TNT.
- Up to 2 viewers, 100 entities and 1,800 microseconds per 10-tick pass by default. 250ms tracked-pair cache and up to 192 hidden entities per viewer. All Bukkit API operations are on the main server thread. Empty world/close range/line-of-sight entities remain visible.
- Restores entities when visible, nearby, on viewer teleport/respawn, plugin reload or disable.
- Adds read-only `/uesp audit` to flag weak built-in Anti-Xray settings. **Does not modify Paper configs automatically.**
- Existing player ESP, seed command protection and ore shuffling retained.

## Cave/ore concealment

For caves and ores **also** merge `presets/paper-world-defaults-xray-strong.yml` into `config/paper-world-defaults.yml`. Merge only `anticheat.anti-xray`, not the entire file. It uses `engine-mode: 3`, `air` in `hidden-blocks`, and protection up to Y=192. After editing, fully restart Paper and run `/uesp audit`; check per-world `paper-world.yml` overrides.

**Technical limit:** Paper Anti-Xray with fake air significantly complicates X-Ray cave finders but does *not* completely remove all original underground cave/structure geometry or every block-entity update from the data stream. True total underground-chunk concealment needs tested **packet-level** rewriting including fake air, later block updates, light, block entities, particles and sounds. See [ChunkVeil](https://github.com/DeKaeyman/ChunkVeil) for an independently maintained ProtocolLib implementation; 26.3 compatibility must be tested. Do not run two conflicting chunk packet rewriters without testing.

### Cave preset performance

Fake-air cave obfuscation can lower client FPS. If it is too costly, remove `air` and/or reduce `max-block-height` (less protection). It does not cover terrain above configured max height. Vanilla world geometry a client already received cannot be retroactively erased without chunk resend.

### Entity limits

The guard hides **visual entity tracking**, not sound packets, particles, tab-list metadata, map data, inventory and block-entity updates or all possible side-channels. It is not a general-purpose anti-cheat. Near/same-cave mobs remain visible. Some hidden entities may briefly pop in as the server budget rotates over viewers. This was compiled against Paper but requires a staging server test with X-Ray/freecam, invisibility, PvP, portals, farms and other plugins.

## Commands

`/uesp` (status), `/uesp audit`, `/uesp reload`. Permission `unifiedantiesp.admin`. Existing bypass permission `unifiedantiesp.bypass` plus entity-only `unifiedantiesp.entitybypass`.

## Installation

Replace `UnifiedAntiESP-3.1.1.jar` with `UnifiedAntiESP-3.2.0.jar` (do not load both). Preserve the old `config.yml` and merge the new `entity-esp` section. The plugin creates the new defaults only when the config does not exist; it does not silently overwrite your changes. **Restart** after changing Paper Anti-Xray, do not use Bukkit `/reload`.

Implementation independent of external plugin code.
