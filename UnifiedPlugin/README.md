# MinecraftUnified 1.0.1 — One JAR for all five Paper plugins

A **real single Paper plugin** (not an installer ZIP) built from the latest maintained source branches of TargetClaim, TalismanForge 2.5.7, EnchantmentForge, SwampSky 1.5.0 and ImageMaps 1.1.0. The compile-time generator converts the existing five JavaPlugin subclasses into hosted listener/command modules, retaining their features and original data identifiers.

**Server:** Paper 26.3, Java 25.

## Installation

1. Stop the server, **back up the entire world and existing plugin data**.
2. Remove the old five separate JARs from `plugins/`. Do not keep the five old plugins alongside this file: duplicate recipes and event handlers would conflict.
3. Place only `MinecraftUnified-1.0.1.jar` in `plugins/`.
4. Keep using the single `resourcepacks/TalismanForge-2.0.0-ResourcePack.zip` from the main branch, with the SHA1 shown in the main repo; it includes the SwampSky equipment, TargetClaim key and Reliquary artwork.
5. Start the server and run `/unified`: all five modules should appear as loaded.

## Commands

- `/p` and `/team`: TargetClaim protections
- `/tf`: TalismanForge (8 talismans)
- `/ce trader`: **force** a diamond-merchant Wandering Trader close to the player. This now uses a near-player location instead of randomly searching distant chunks. Admin/OP permission required.
- `/ce give`, `/ce apply`: EnchantmentForge books, triple arrows and inventory charm
- `/ss spawn giant_frog`, `/ss spawn flying_pig`: SwampSky diagnostics; use these to check creature functions.
- `/im` or Shift+right-click while holding a map: ImageMaps URL editor
- `/unified`: show which of the five modules initialized successfully

## Persistence and compatibility

Data folders remain `plugins/TargetClaim/`, `plugins/TalismanForge/`, `plugins/EnchantmentForge/`, `plugins/SwampSky/`, `plugins/ImageMaps/`. Existing item PDC namespaces, custom recipes and map storage keep their original names. This is a one-to-one runtime host, not a new implementation of creature AI.

## SwampSky issue investigation

The current combined main resource pack was inspected and has all 22 SwampSky assets, identical to the old combined SwampSky pack. Both the original and unified plugin sources compile on Paper API 26.3. This **does not prove** the existing in-game issue is fixed. The actual problem requires identifying symptoms or a server `logs/latest.log` stack trace. To isolate, check `/unified`, then `/ss spawn giant_frog` and `/ss spawn flying_pig`. If an entity spawns but looks wrong, it is likely a texture/model/client-cache issue; if commands fail or animals never move, examine the Java exception/anticheat logs.

## Build and test

GitHub Actions → Build single unified Minecraft plugin. Full-checkout on the build runner reads module Java sources from main and three feature branches, then runs `gradle -p UnifiedPlugin build` (Java 25). No compiled Minecraft client mods or unused resource-pack duplicates are embedded in the JAR.

**Build success checks Java compilation, not gameplay or anti-cheat compatibility. Test on a staging server first.**
