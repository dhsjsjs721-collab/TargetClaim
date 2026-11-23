# UnifiedMachines 1.0.0 — damage block + chunk loader

Paper 26.3 / Java 25. Separate plugin to avoid modifying UnifiedDefend dungeons or UnifiedOptimizer's view-distance control.

## Damager block

Craft a special **Dropper** with this exact shaped recipe:

```
I I I     I = 3 iron blocks
R H R     R = 2 redstone dust, H = 1 hopper
O D O     O = 2 obsidian, D = 1 dispenser
```

Place the custom block, then right-click it to open its 9-slot graphical inventory with **exactly one usable weapon slot** (center). Drag a weapon to the cursor and click the center slot; click again with an empty cursor to remove it. Swords, axes, Minecraft 26.3 spears, tridents and maces are supported. Unbreakable weapons and non-weapons are rejected.

Every 20 ticks (nominally once per second) each damager attempts one attack on one mob within 2.5 blocks (not players). Default damage is based on weapon type/tier (not equal to actual vanilla attack attribute); Sharpness isn't currently included in the damage formula. The tool loses one durability point after a hit. At zero durability, it breaks and the slot empties. Damage is environmental (no player kill attribution), so XP and rare drops may not behave as with player kills. Items are persisted in the installed block's tile PDC, not in the fake GUI. The physical dropper's inventory stays empty, while its custom GUI stores the one weapon.

Default limits: max eight damage blocks per owner, max 256 global, max 96 machines attempted per second (rotating fairly), so machines beyond the budget are processed less often.

## Chunk loader block

Craft a special **Ender Chest** using:

```
D N D     D = 4 diamond blocks
O E O     N = 1 nether star
D C D     O = 2 obsidian
          E = 1 ender chest, C = 1 crying obsidian
```

- Maximum **one PLACED chunk loader per UUID**; items that haven't been placed don't count.
- Keeps **only its own one chunk** loaded, using Paper's plugin chunk tickets. No 3×3 or 5×5 areas.
- **Active only while the owner is online** by default. When they disconnect, the ticket is released; at reconnect it resumes from the saved world/position.
- Server-wide cap **48 distinct active ticketed chunks**, to avoid resource spikes. Some players may get no active ticket if cap reached; device placement still succeeds and is remembered.
- Tickets are ref-counted for multiple owners with machines in the same chunk. One player's logout does not remove a shared ticket.
- No view-distance tweaks, chunk unload hacks, forced repeated loading, world generation edits or custom packet modification.
- Ownership and block positions persist in `plugins/UnifiedMachines/machines.properties` (atomic updates). Weapon data persist in Minecraft tile PDC, automatically saved as part of the world.

## Commands

`/umachines` — see active chunks, damage machines, hits, failed operations, remaining global capacity.

`/umachines give <player> damager` and `/umachines give <player> loader` — admin-only test item grants.

## Safety and interactions

- Owners or admins may break blocks; the tool and crafted machine item drop back, while a chunk loader stops ticketing the chunk.
- Machine blocks are protected against standard explosions and piston moves. The damage block cancels dispensing and hopper insert/extract operations.
- No players, tamed pets, villagers or iron golems are targeted by the damager by default. Passive wild mobs are included by default; set `damager.damage-passive-mobs: false` for hostiles only.
- This plugin must run alongside the server's normal Paper chunk provider. A plugin chunk ticket affects chunk residency, not necessarily the client rendering radius or whether mobs stay active: Paper simulation distance and entity-activation rules remain relevant. It will **not** fix previously invisible/missing chunks.
- Because UnifiedDefend protects dungeons against storage/mechanism placement, these machines may be prohibited within a protected dungeon; that is intended.
- Do not use PlugMan or /reload. Stop server, back up worlds and plugin data, replace JAR and fully restart.

## Deployment checklist

Test on a staging Paper server: recipe creation and survival placing, clicking with cursor, breaking with a tool inside, persistence after restart, world portal transfers, one-loader limit across two worlds, owner logout/reactivation, duplicate items/hoppers/piston, Paper and other plugins' protection cancellations. Java compilation and JUnit unit tests are not substitutes for this runtime test.
