# TargetClaim 1.3.0 (development test)

Minecraft Paper 26.3, Java 25. One claim per player.

- Unique target core (TARGET), crafted with 3 paper and 6 amethyst shards; when its claim is deleted, only 3 paper drop (no upgrade materials).
- Volumetric 3D protected zone (width x height x depth):
  - Level 0: 16 x 32 x 16 (craft)
  - Level 1: 40 x 48 x 40 (23 amethyst shards)
  - Level 2: 80 x 80 x 80 (64 amethyst shards)
  - Level 3: 120 x 120 x 120 (3 netherite scraps)
  - Level 4: 160 x 160 x 160 (1 dragon egg)
- Keep the upgrade ingredient in the MAIN hand in one stack when upgrading.
- Dialog UI opens on right-click on the placed core, or /p.
- TextDisplay floating label above the core; one brighter red 3D particle border; toggle /p border on|off or in /p.
- Teams: /team create <name>; /team invite <nick>; /team accept; /team leave; /team pvp on|off; /team menu.
- Teams limited to 3 people; randomized assigned color; PvP between teammates requires BOTH players to consent.
- Claims default to owner-only building and interactions; owners can switch between only owner and owner + team in the claim dialog.
- Territory PvP is always on (except consensual team friendly-fire rules). Explosions and pistons still follow vanilla behavior except they cannot destroy or move protected claim cores.
- Claim deletion requires confirmation, waits 10 seconds, and can be cancelled in the dialog or using /p cancel. Data is saved in plugins/TargetClaim/data.yml.

**Important:** This is a test build. Back up the world and database before replacing TargetClaim 1.2.0. Test the claim's Y bounds, arrows/potions, doors, item frames and piston interaction on a staging server.

## Build
GitHub Actions / Build plugin JAR; then download the artifact from the completed run.

## Commands
/p, /p info, /p border [on|off], /p upgrade, /p remove, /p cancel, /p give <nick>
/team, /team create <name>, /team invite <nick>, /team accept, /team deny, /team leave, /team kick <nick>, /team disband, /team pvp [on|off], /team info
