# UnifiedAntiESP 3.5 — safe chunk generation defaults

Paper 26.3 / Java 25.

**Safety change:** ore-shuffle worldgen mutation is now OFF by default and forcibly disabled when chunk-safety.disable-ore-worldgen is true. This safety key defaults to true even if the existing 3.4 config enables ore-shuffle. The plugin no longer modifies new chunk ore blocks unless the administrator deliberately opts out of the safety guard and enables ore-shuffle.

Other protections remain: pre-track entity and player hiding, ray-trace occlusion behind nearby walls, 224-block maximum configured protection, dark underground mob strict mode, seed command restriction, and read-only native Paper Anti-Xray audit.

**Performance change:** on block break/place, piston move or explosion, the visibility cache is invalidated by incrementing a counter (O(1)) rather than clearing every player-pair cache entry (O(number of players squared)).

### Still not provided

The plugin does **not** modify the full chunk packets or replace water/lava/cave shape; thus X-Ray can still see an open underground cavern that leads outside, water, lava, and some exposed ores. Native Paper Anti-Xray with engine-mode 3 and fake air obscures some ore and cave information but is not a complete cave-fluid hide mechanism, and can reduce FPS. Do not blindly apply an aggressive profile to a live server with known chunk-visibility glitches.

No changes are made to existing world region files. Turning off ore-shuffle does not reverse ore changes previously made during chunk generation.

### Installation and testing

Back up server world and plugins/UnifiedAntiESP/config.yml. Replace 3.4 jar with 3.5, ensure chunk-safety.disable-ore-worldgen: true, fully restart Paper (no /reload). Run /uesp and /uesp audit; confirm chunk mutations=OFF. Test chunk visibility on an ordinary unmodified vanilla client, then compare with X-Ray/ESP only if necessary. Do not assume ESP is completely removed: equipment metadata and other side channels still require independent testing.

### Limits

Actual runtime behavior must be verified on the intended server. O(1) invalidation lowers overhead but per-viewer visibility checks still cost CPU when many players and tracked entities are present.
