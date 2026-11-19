# TargetClaim 1.3.0 (development test)

Minecraft Paper 26.3, Java 25. One claim per player.

- Unique target core (TARGET), crafted with 3 paper and 6 amethyst shards; it never drops when broken.
- Volumetric 3D protected zone (width x height x depth):
  - Level 0: 16 x 32 x 16 (craft)
  - Level 1: 40 x 48 x 40 (23 amethyst shards)
  - Level 2: 80 x 80 x 80 (64 amethyst shards)
  - Level 3: 120 x 120 x 120 (3 netherite scraps)
  - Level 4: 160 x 160 x 160 (1 dragon egg)
- Keep the upgrade ingredient in the MAIN hand in one stack when upgrading.
- Dialog UI opens on right-click on the placed core, or /p.
- TextDisplay floating label above the core; color and particle effects; toggle /p border on|off.
- Teams: /team create <name>; /team invite <nick>; /team accept; /team leave; /team pvp on|off; /team menu.
- Teams limited to 3 people; randomized assigned color; PvP between teammates requires BOTH players to consent.
- Team permissions on claims are opt-in via owner control, claims are protected from other players.
- The optional restrictions on explosions, fire, fluids, hoppers and pistons remain removed; vanilla behavior applies.
- All claim and team data saved in plugins/TargetClaim/data.yml.

**Important:** This is a test build. Back up the world and database before replacing TargetClaim 1.2.0. Test the claim's Y bounds, arrows/potions, doors, item frames and piston interaction on a staging server.

## Build
GitHub Actions / Build plugin JAR; then download the artifact from the completed run.

## Commands
/p, /p info, /p border [on|off], /p upgrade, /p remove, /p give <nick>
/team, /team create <name>, /team invite <nick>, /team accept, /team deny, /team leave, /team kick <nick>, /team disband, /team pvp [on|off], /team info
