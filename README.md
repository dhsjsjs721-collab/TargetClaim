# TargetClaim 1.5.2 (development test)

Minecraft Paper 26.3, Java 25. One claim per player.

- Unique target core (TARGET), crafted with 3 paper and 6 amethyst shards; when its claim is deleted, only 3 paper drop (no upgrade materials).
- One-use special chest key (TRIPWIRE_HOOK), crafted as **one key** from a vertical column: top 1 amethyst shard, bottom 1 stick; the rest of the crafting grid is empty. /p givekey <nick> gives one key for admin testing.
- To use, hold the special key in the MAIN hand and right-click a CHEST or TRAPPED_CHEST inside someone else's claim where the player lacks access. It never opens the chest; it removes ONE random item from storage and drops that one item into the world above the chest. The key is consumed once on success. If the chest is empty, the key is not consumed. Key use is blocked in Creative/Spectator.
- Anti-dupe: only server-tagged keys work (not ordinary tripwire hooks); the event is processed from the main hand only, once per player per server tick; selection is weighted by the actual number of items in all eligible stacks; exactly one item is removed synchronously from the original inventory before the world drop; double-chest halves from different claims are not mixed; a drop exception rolls the inventory/key back. Keys are spent only on a successful draw. Test with hopper and other inventory plugins before production.
- Volumetric 3D protected zone (width x height x depth):
  - Level 0: 16 x 32 x 16 (craft)
  - Level 1: 40 x 48 x 40 (23 amethyst shards)
  - Level 2: 80 x 80 x 80 (64 amethyst shards)
  - Level 3: 120 x 120 x 120 (3 netherite scraps)
  - Level 4: 160 x 160 x 160 (1 dragon egg)
- Keep the upgrade ingredient in the MAIN hand in one stack when upgrading.
- Compact 27-slot chest-inventory GUI opened via right-click on the TARGET core or /p. Borders, owner/team building permissions and team PvP toggles change their button in place without closing or reopening the inventory; upgrade, team and deletion use separate pages.
- Minimal TextDisplay label above the core. No beacon or beam effects are created.
- Red 3D claim borders have restrained particles. They can be switched on or off in the GUI or with /p border on|off.
- Holding the full NEXT upgrade cost in the main hand previews the expanded territory in CYAN. If the next expansion is blocked by another claim or the world height, the preview is ORANGE. The maximum level-4 bounds are also previewed in GOLD when a further level remains.
- The Upgrade inventory page shows current size, next size, maximum size, upgrade cost, and whether the resources in the main hand are sufficient.
- Teams: /team create <name>; /team invite <nick>; /team accept; /team leave; /team pvp on|off; /team menu.
- Teams limited to 3 people; randomized assigned color; PvP between teammates requires BOTH players to consent.
- Claims default to owner-only building and interactions; owners can switch between only owner and owner + team in the inventory menu.
- Strict protection: non-owners cannot place, break, open containers or use doors, buttons, switches or other blocks (the one-use chest key allows only a one-item draw without opening a protected chest). Protection applies to OP users too. Only the claim owner and explicitly-enabled team members may build and interact. The targetclaim.admin permission is for /p give ONLY, not a bypass. Buckets, ignition, armor-stand manipulation and block damage are also guarded.
- Territory PvP is always on (except consensual team friendly-fire rules).
- TNT, creepers, pistons, fire, liquids and other environmental mechanics are vanilla. Only the claim core itself is protected against explosion and piston movement.
- Performance: 3D chunk-based claim lookup; bounded particle counts per player, distance-culling of borders and upgrade previews. No beacon block or beam effects. Viewer border preferences are stored in data.yml.
- Clicking Delete in the claim inventory starts the 10-second (200-tick) deletion countdown immediately, without a confirmation page; the inventory closes. During the countdown, the owner can right-click their claim core again to cancel immediately. /p remove starts the countdown, /p cancel cancels it, and attempting to break the core also starts the countdown. Only 3 paper drop upon completion. All claim data lives in plugins/TargetClaim/data.yml.

**Important:** This is a test build. Back up the world and database before replacing an older TargetClaim version. Test the claim's Y bounds, arrows/potions, doors, item frames and piston interaction on a staging server.

## Build
GitHub Actions / Build plugin JAR; then download the artifact from the completed run.

## Commands
/p, /p info, /p border [on|off], /p upgrade, /p remove, /p cancel, /p give <nick>, /p givekey <nick>
/team, /team create <name>, /team invite <nick>, /team accept, /team deny, /team leave, /team kick <nick>, /team disband, /team pvp [on|off], /team info

## v1.5.2: Key artwork
- Crafted one-use chest keys now carry the item model `targetclaim:chest_key` (pixel-art amethyst/gold key).
- The existing special-key PDC tag, theft mechanics and recipe are unchanged. Players' already-crafted keys are migrated on login.
- Merge the `assets/targetclaim` namespace into the **single main resourcepack** (`resourcepacks/TalismanForge-2.0.0-ResourcePack.zip`). That same main pack also includes the TalismanForge and SwampSky assets and the Reliquary model; no other packs should be enabled simultaneously.
