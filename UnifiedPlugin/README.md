# MinecraftUnified 1.0.9 — One JAR for all five Paper plugins

A **real single Paper plugin** (not an installer ZIP) built from the latest maintained source branches of TargetClaim, TalismanForge 2.6.0, EnchantmentForge 1.2.0, SwampSky 1.7.1 and ImageMaps 1.1.0. The compile-time generator converts the existing five JavaPlugin subclasses into hosted listener/command modules, retaining their features and original data identifiers.

**Server:** Paper 26.3, Java 25.

## Installation

1. Stop the server, **back up the entire world and existing plugin data**.
2. Remove the old five separate JARs from `plugins/`. Do not keep the five old plugins alongside this file: duplicate recipes and event handlers would conflict.
3. Place only `MinecraftUnified-1.0.9.jar` in `plugins/`.
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

## v1.0.9 — stable pig motion
SwampSky is now compiled from the updated 1.6.0 branch. Ground pigs follow pumpkins through Paper navigation without being made unaware, while winged pigs fly between persistent waypoints, recover from sustained stalls, avoid walls and respect leashes. Use `/ss status` to see pig movement state or `/ss repair` to reinitialize nearby winged pigs if an older server version left them frozen. Resource-pack URL and all other four module mechanics stay unchanged. These fixes were compiled in CI but require testing with your anticheat and world conditions.

## v1.0.9 — Half-sized flying pigs, saddle-only riding, universal traders

Flying pigs are 50% of vanilla pig scale; their separate wing displays have been adjusted. Ordinary pigs stay vanilla size, and can be ridden by equipping a **saddle** and right-clicking. Carrot-on-a-stick is no longer needed. Use WASD to steer, sprint to accelerate, space to jump, and swim faster while in water (space to rise). Pig controls update each tick. Tamed winged pigs retain owner following, free flying and existing special controls. New config options: `pig-ground-ride-speed: 0.49` and `pig-swim-speed: 0.40` (automatic fallback for existing configs).

All wandering traders, including vanilla, summoned, previously saved and administratively spawned, now offer the same full catalog rather than randomized different small selections. Traders automatically upgrade on entity loading or right-clicking. `/ce trader` still summons one at the player's location. Existing trade prices remain.

All changes are server-side Java; no changes to the single unified resource pack or to the other three modules. The build compiles from the latest maintained branches, not snapshots of earlier SwampSky or EnchantmentForge versions. Minecraft server gameplay testing is still required to confirm all riding and merchant interactions.

## v1.0.9 — fixes confirmed by actual Purpur 26.3 logs

- SwampSky 1.7.1 avoids the `CraftMetaItem.getMaxDamage()` crash during frog-wing recipe creation.
- TalismanForge 2.6.0 uses typed purple DUST for Maiming resurrection, fixing the Float particle exception.
- Universal prefix `/u`, also `/unified` and `/mods`: `/u status`, `/u pig`, `/u frog`, `/u repair`, `/u trader`, `/u tf give <player> joker`, `/u ss give <player> boots`, `/u ce give <player> soulbound`, `/u p`, `/u team`, `/u map`. Original commands continue working. Tab completion delegates to each module.
- If a module fails to start, the unified plugin logs the failure, reports it in `/u status`, and leaves the other loaded modules available instead of disabling everything.
- One enabled plugin contains all five modules. No resourcepack update or data migration required. A real Purpur restart and gameplay test are still necessary after compilation.

## v1.0.9 — TalismanForge 2.6.0

Integrates the full talisman rework from the TalismanForge branch: Wrath's critical-only recharge and single resurrection, Hades' Spectator revival, End's two orbiting swords and no fall damage, Poseidon's pufferfish/axolotl and stronger ghost dolphin, Ages' moving Allays and Thorns, Defiance's restored shields, and Maiming's heavy slow. Uses the same unified resource pack, unchanged. Commands remain `/u`, `/tf`, `/ss`, `/ce` etc. Deployment requires replacing the old combined JAR and restarting Purpur.

- End lightning now inflicts its bonus damage through the standard Paper damage event, so armor, protected entities, other plugins, and resurrection effects are not bypassed by directly setting HP to zero. The unified build fetches TalismanForge source commit c33003265442917aeb46cff3781e808752616b7c or later.

## v1.0.9 — First new PvP consumable: Obsidian Trap

The five original modules and the five existing creature-movement improvements remain. New PvP item is available through a single integrated plugin, without any client mod or new resource-pack textures.

- **Obsidian Trap:** craft `OIO / IDI / OIO` where O=obsidian, I=iron ingot, D=diamond. Result is one named Echo Shard with a persistent custom-item tag.
- Right-click while looking toward an enemy player within ~5.5 blocks. A 3×3 obsidian cage appears around their feet for **15 seconds**. No Wither or damage effects. The trap is a single-use item and has a **25-second** per-player cooldown. Maximum 1 active cage per owner and 40 cages server-wide.
- Cage wall/ceiling blocks are only constructed in air; existing solid floors are left intact but temporarily protected against mining. No chests, fluids, protected/private territories or complex original blocks are overwritten. Inside another player's TargetClaim region, the placement is refused unless the acting player has build permission.
- The cage's placed blocks, expiry and original block states are written to `plugins/MinecraftUnified/temporary-traps.yml`; block restoration occurs after expiry, during shutdown and for unloaded chunks when those chunks next load. Explosion, piston, building and mining protection prevents easy accidental breakage.
- **Commands:** `/u trap` to see the recipe, `/u trap give <player> [1–64]` for admins, and `/trap` / `/trapka` as direct aliases. Existing `/u`, `/ss`, `/ce`, `/tf`, `/im` commands are unaffected.
- This intentionally implements only the researched **basic** FunTime 15-second cage. More powerful variants, trapskins and other PvP consumables were not added automatically.

Deploy the new JAR after backing up the world. A clean Purpur 26.3 startup and command smoke test validates server compatibility, but players still need to test placement around terrain and claims in-game.

## MinecraftUnified 1.0.9 — three combat shields and smarter creatures

SwampSky 1.8.0, TalismanForge 2.7.1 and the other original three modules all remain in one server JAR. The obsidian PvP trap is preserved.

- Wither Shield: drains health/hunger during successful blocking, transmits negative effects to the attacker and steals compatible positive effects, excluding talisman-powered effects. Incoming projectiles create a temporary allied frog or silverfish, which cannot target its owner.
- Turtle Shield: rear protection while not actively blocking; durable shield with double max damage, reduced knockback and a short-range repelling aura; crafted from turtle scutes and deepslate.
- Crusher Shield: 7 damage / 9 crits while in main hand, active blocking improves next attack and slows melee opponents, but projectiles pass through. All three have their own shaped recipes using a vanilla shield and materials.
- Admin test: `/u shield give <player> <wither|turtle|crusher> [amount]`, help: `/u shield`.
- Talisman Joker: gigantic Evoker animation, 5-second attacker stun on successful revival. End/Zeus: boomerang swords chase last attacker 40 blocks; one transforms into an alternate-hit reflecting shield below half health. Hook: shorter limited pull, temporary no-attack, damage/slow, shield reflection, anvil Magnet upgrade, chain model optimization.
- SwampSky: mounting a tamed flying pig by right-click; jumping toward your pig draws it closer. Slower gentler orbit and more responsive wings. Giant frogs wander when not fighting. Ordinary saddle-only pig riding remains.

Only compilations/automated real Purpur startup are guaranteed by CI; player-in-game PvP ability tests still required.

## v1.0.9 — unified pixel art resourcepack and tested startup

The original main resource pack ZIP (same path and link) now includes 32×32 transparent pixel textures and item models for the hook, hook-tip, active hook, chain links, Wither Shield, Turtle Shield and Crusher Shield. No separate pack is needed. `server.properties` SHA-1 **must** be updated to match the new main ZIP when forcing the pack.

Joker creates one 3-second giant even when the revival had no attacker (only an actual enemy receives five seconds of stun). Zeus swords chase a target for up to 20 seconds and can become a low-health defensive shield. Wither Shield now absorbs a target's transferable good and bad effects for 5–20 seconds while respecting active talisman-generated effects. Giant frogs and winged pigs have smoother idle behavior; pig riding is now right-click and optionally approach by jumping toward the pig.

Automated CI boot checks five module startup, original obsidian trap and the new shields. Combat behaviors require actual in-world testing.

## v1.0.9 — final gameplay stability fixes

- Giant frog pathfinding exceptions are isolated and rate-limited so they cannot cancel the shared SwampSky tick task or freeze flying pigs.
- The grappling hook's temporary combat lock prevents the two tethered entities from attacking, but does not make them invulnerable to unrelated players, projectiles or mobs.
- Rebuild includes the newest SwampSky and TalismanForge sources plus all three shields, PvP trap and the existing five core modules. Custom models remain in the same main resourcepack ZIP, with SHA-1 a196e2d077e5678d4fdb2be5045f3c15ec55cfa2.
