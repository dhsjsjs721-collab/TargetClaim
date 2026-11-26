# Regression audit (2026-11-26)

Production 1.1.19 console: /u talisman selftest 5/5. This is a registry test, not combat coverage. Branch contains 1.1.21. Fixes: baby goblin native scale; held torch light types and thrown torch variants; temporary light lease cleanup before placement; natural-only Anubis soul eligibility; safe egg respawn position and facing; Medusa inventory/hand/drop checks and reconnect freeze duration; mirror model migration and vanilla clan filler fallback.

## Required live integration matrix (not yet executed)
- Thor: critical vs noncritical damage, natural vs artificial lightning, 3 charges max, rain flight teardown, claim/peace permissions, heal and debuffs, cross-world and logout.
- Anubis: natural, spawner, egg, breeding, plugin and transformed mob origins; last-hit attribution; 199/200/500 souls; 1HP minions and 40HP elites; no loot/XP or friendly damage; expiry after 30s; marker visibility only to holder.
- Koschei: 3 lives then destroyed egg; awakened infinite; membrane refill; no claim overlap; egg attack and regen; blocked/lava/claimed destination; correct facing; no free resurrection on cancelled teleport; cross-world, restart, simultaneous lethal hits.
- Kitsune: 180/30s concealment; 60s reveal cooldown; attack or damage; armor/hand visibility, clone expiry, no loot/damage; chest, double chest, ender chest, hopper, broken chest, unloaded chunk, restart and duplicate serial.
- Medusa: gaze, shield, line of sight, PvP flags, movement and item interactions, relog, thaw, cooldown and kills; no infinite mob stasis without documented design.
- Shared: item identity, stack sizes, duplication, death fallback, dragon egg ritual rollback, inventory transactions, persistence failure, server crash and permissions.
- UI: actual client pack hash and both inventory/equipped mirror models, clan filler, authentication overlay and all torch placement faces.

Added executable policy tests to the existing selftest command and required them in the Purpur CI smoke test. Covered resurrection costs, Anubis army scaling and spawn provenance, Kitsune storage timing and cap. Medusa's mob freeze is bounded to 15 seconds (including chunk reload); this is a provisional balance choice.
