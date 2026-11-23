# UnifiedDefend 1.0.0 — Paper 26.3, Java 25

Independent plugin. Uses actual generated-structure bounding boxes returned by Paper's **already loaded** chunks; no /locate requests, forced chunk loading, or world-wide scans.

## Structures

Nether Fortress, Bastion Remnant, Stronghold (End portal fortress), End City, Ancient City (Warden), Trial Chambers (copper dungeon). Each type is configurable.

## Mechanics

- **Temporary placed blocks**: ordinary player-placed blocks inside a detected structure expire **10 minutes** after placement. Aim at them (up to 6 blocks) to see an action-bar countdown; no permanent hologram entities are spawned. Storage/tile-inventory blocks are disallowed in protected structures to prevent deletion of players' stored items. This does not include water/lava placed with buckets.
- **Spawners**: normal and trial spawners cannot be broken by players or destroyed in standard explosions. Vaults and generated chests/barrels are also protected from ordinary mining/explosions so that dungeon rewards remain repeatable.
- **Terrain regeneration**: when a player breaks an ordinary structural block, the original block data is recorded. **After 35 minutes** it is rebuilt **only if the location is still air**; any new solid block, water or lava takes priority. Newly placed temporary blocks are removed in 10 minutes. Nothing is regenerated from world seed or bulk chunk resets.
- **Valuable resources never respawn**: diamond, iron, redstone, gold, emerald, lapis, netherite, quartz, amethyst, copper, raw metal, ore blocks and precious metal blocks. In **Trial Chambers only**, an original copper block broken by a player/explosion is repaired as a plain **deepslate** block, never copper. This protects copper dungeon layout against infinite copper farming. Fluids are not overwritten or removed.
- **Chest / barrel loot**: records the original vanilla loot-table key on first interaction, falling back to an appropriate structure table when missing. First refresh becomes eligible **35 minutes later**. It fills **only when the inventory is completely empty**, avoiding deletion of player-deposited items. Restock is lazy on the next player interaction after the timer, and limited to 9 stacks per refresh. Trial vaults/trial spawner rewards are untouched and follow vanilla mechanics. Any mismatched/missing custom loot table is skipped safely.
- **Timer on look**: look at a temporary block, a broken block pending repair, or a recorded chest within 6 blocks. Its remaining time appears in the action bar once per second.
- **Persistence**: `plugins/UnifiedDefend/state.properties` stores change/chest timers across restarts; atomic write. Uses fixed upper bounds, per-second task budgets, caches, and no chunk force-loading.

## Important limitations

1. Existing damage from before installation **cannot be reconstructed** without a previously recorded structure snapshot; this plugin restores blocks it sees broken **after installation**. It never blindly regenerates a whole dungeon.
2. If a chest is already looted before installation, vanilla may have erased its original loot table; a structure-level fallback is used and will **not** reproduce exactly that chest's original variant. Some new or custom loot tables may not exist; these chests are skipped.
3. A chest with player items or a non-empty inventory will **not** be overwritten; countdown shows when restock becomes eligible, not when loot is guaranteed to appear.
4. Physics, TNT, command blocks, and external plugins can change blocks through paths outside ordinary place/break/explosion events. No event-driven plugin can promise perfect restoration against all block edits. Lava/water flows and explosions may still affect surrounding areas.
5. "Near cursor" timer uses the Minecraft action bar, not a floating world hologram, to keep entity count and CPU use minimal.
6. Structure bounding boxes include some air/courtyard spaces, so building there may be considered in a protected structure.
7. Test on a cloned Paper 26.3 world before live deployment; this build is not a substitute for live gameplay testing.

## Installation

Back up the full server (world, world_nether, world_the_end, and plugins). Copy `UnifiedDefend-1.0.0.jar` into plugins and fully restart. Avoid PlugMan hot reload. Test in a fresh Trial Chamber and a Stronghold. `/udefend` shows counts; `/udefend save` writes state.

No other plugins are modified. The design aims to be interoperable with TargetClaim, UnifiedProtection and UnifiedAntiESP by never rewriting whole chunks.
