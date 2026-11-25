# Mythic relics — review build

Paper 26.3 / Java 25. Five new world-unique artifacts, distributed via the existing three-minute altar or admin /tf give. Old items remain readable; old loot generation is disabled by default.

- Thor: critical hits visual lightning, other melee hits +13%, natural weather lightning attracts to held/dropped artifact and recharges; awakened flight in rain, 30-second localized storm, healing and enemy debuffs.
- Anubis: 500 souls, 200 per rescue; natural-spawn entities only; death holograms visible through blocks to holder within 128 blocks for 24h; 4–10 one-HP undead, awakened adds three 40-HP netherite guards for 30 seconds.
- Koschei: placed dragon egg outside claims, three rescues or three manual breaks destroy it; one phantom membrane restores a life, 16 restore destroyed egg, awakened infinite rescues until destroyed. Claim creation/upgrade checks egg bounds. Egg curses -2 HP and reduced food.
- Kitsune: player visibility hidden from other clients, including gear, after 180s peaceful or 30s sneaking; hit reveals and starts cooldown. 3 or 9 mannequin decoys with actual player profiles, 5 seconds, no damage; storage recharge.
- Medusa: gaze 3s / 1s awakened, 15s or first damage for players, indefinite or first damage for eligible mobs; shield reflection, 30s immunity after thaw, 20 credited kills per charge.

## Commands
/tf give <player> thor|anubis|koschei|kitsune|medusa (OP, one issuance per world-wide registry)
/tf awaken (ordinary dragon egg in inventory; artifact offhand, or bound placed Koschei)
/tf restore (bound broken egg, 16 phantom membranes)
/tf status

## Verify before merge
- Build both TalismanForge and TargetClaim with their existing Gradle workflows on Java 25.
- Test damage event ordering against UnifiedDefend and existing combat plugins; void rescue, kill commands, resurrection with vanilla totems, disconnect during rescue.
- Test claim placement/upgrade from every direction, egg movement and unloaded chunks, crash recovery and simultaneous inventory transfers.
- Test Kitsune conceal/reveal with UnifiedAntiESP, mannequin profiles, ender chest and unloaded-container storage, death marker visibility, statue save/load and mob cleanup.
- The current patch uses vanilla item icons with colored lore, action bars, sound and particles. Custom pack models are optional and require art assets; the existing main ZIP is not modified in this review build.
- Existing legacy items are retained for migration; they are not issued from the altar or loot by default. The new artifact registry is in plugins/TalismanForge/mythic-artifacts.yml. Back up before testing.

This is a review build, not a production release. No local Paper 26.3 runtime or Gradle toolchain is available in the editing environment.
