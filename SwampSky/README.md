# SwampSky — Minecraft Java / Paper 26.3

Independent server-side plugin: flying pigs, steerable tamed pigs, giant swamp frogs, frog skin loot, frog gear and original pixel-art resource pack. No client-side mod required.

## Creatures

Flying pigs: 28% of naturally appearing pigs become flying pigs in allowed Overworld biomes, 80% in Mushroom Fields. Additional flying pigs are spawned at controlled intervals near players in most biomes, except desert, badlands, snowy, frozen or icy biomes. Wings are two small moving ItemDisplay models and require the resource pack. Version 1.3.0 replaces the oversized dark wings with compact ivory/cream feathers on a single transparent PNG, using improved display orientation and subtle animated flapping. Flying pigs and regular pigs follow players holding pumpkins or pumpkin pie (winged ones fly toward the food; ground pigs walk). Both remain normal Pig entities. Neither pigs nor frogs are reskinned; only special pig wings are added. Use right click with either pumpkin (52% taming chance) or pumpkin pie (78% chance) to tame **any pig**. The food is consumed even if taming fails. Other players cannot mount your pig. After taming, right click with an empty hand to ride, **no saddle item or carrot-on-a-stick**. Controls: WASD movement, space jump / climb in flight, look downward while holding W to descend, Shift dismount. Mounted pigs use Paper PlayerInputEvent; velocity requires pig AI enabled and awareness disabled while mounted. Mounting is deferred to the next server tick to prevent cancelled interaction from blocking it. No chat hints or permanent creature nametags. On hills pigs have 1.45x speed; on snow 0.35x, and flying pigs rise to avoid it. Tamed pigs can be healed with food.

Flying pigs additionally drop 1–2 phantom membranes on death; ordinary pigs retain ordinary drops. The two sides reuse one cream-white wing texture, mirrored on the left.

Giant frogs: up to 80% of naturally spawned frogs in swamps become giant; the plugin also attempts to maintain 2–3 within each active 72-block swamp neighborhood around players (not precisely 2–3 per arbitrarily sized biome). Size 2.8x, health 60 HP, leap toward nearby players, attack for 7 damage plus a short slowness. Below 40% health, every 5 seconds they can pull a nearby player toward their mouth with a particle tongue and deal 2 extra damage. On death they drop 1–3 **frog hides** and 6–12 slimeballs.

## Crafting

H = Frog Hide (special drop), S = vanilla Slime Ball, P = Phantom Membrane, space = empty slot.

Frog Boots: Jump Boost I, no fall damage, and a slime-block-like landing bounce on any solid surface, even after drops too small to cause fall damage (sneak to stop):

    H H
    H H
     S

Frog Leggings: Speed I, slime-trail particles:

    HHH
    H H
    S S

Frog Wings: craft an Elytra with special green frog-wing texture; it glides like regular Elytra:

    HSH
    HPH
     H

Exact-choice recipe requires the real custom frog hide item, not vanilla rabbit hide.

## Operator commands

    /swampsky spawn flying_pig
    /swampsky spawn giant_frog
    /swampsky give Steve hide 5
    /swampsky give Steve boots
    /swampsky give Steve leggings
    /swampsky give Steve wings

Aliases: /wildlife and /ss.

## Installation

1. Copy SwampSky-1.6.0.jar to the Paper 26.3 server plugins folder; reboot server.
2. For custom textures, enable the combined resourcepacks/TalismanForge-SwampSky-Complete.zip on Minecraft Java 26.3 clients; this single pack also includes the pre-existing TalismanForge textures. Standalone SwampSky-ResourcePack.zip is available. Armor/elytra textures use correctly aligned vanilla 3D UV templates and Minecraft 26.3 equipment models in `assets/swampsky/models/equipment` (not the obsolete `assets/swampsky/equipment`). Boots, leggings, and wings explicitly equip in FEET, LEGS and CHEST, respectively. Existing legacy items are upgraded while online, and old head-equipped wings are moved to an empty chest slot.
3. Edit plugins/SwampSky/config.yml to tune spawn frequency, health, speed, and damage.

Original single-wing, frog-hide, and frog Elytra textures are stored in `SwampSky/assets/*.png.b64`, and the pack builder decodes them to transparent PNG files. Vanilla pig and frog skins remain untouched. The GitHub Actions push build publishes the combined ZIP and compiled JAR in `resourcepacks/` and `builds/` respectively.

Build with Java 25, Gradle 9.2.1:

    gradle -p SwampSky build --no-daemon
    python3 SwampSky/tools/build_pack.py

Artifacts: SwampSky/build/libs/SwampSky-1.5.0.jar and SwampSky/build/SwampSky-ResourcePack.zip.

This is a **Paper plugin**, not a Fabric/NeoForge mod. Creatures reuse vanilla Pig and Frog entities, and wings are attached visual displays; this avoids requiring a new client entity renderer. Client resource-pack format: 97.1. In-game tests are still recommended, particularly with anti-cheat plugins that monitor fast mounts.

## 1.3.0 fixes

- Fixed Elytra mistakenly equipping in the head slot, correct 3D model textures for leggings/boots/Elytra, and upgraded old items in inventories.
- Frogs face the player before/during strong leaps; flying pigs face the direction of travel rather than moving backward.
- Flying pigs spawn across most Overworld biomes except winter/ice/desert/badlands and more frequently in Mushroom Fields.
- Strong swamp frog population: 2–3 in the loaded swamp neighborhoods of active players with a local cap to avoid mob overload.
- Boots bounce on contact with any solid ground (stone, grass, dirt, etc.) regardless of whether a fall-damage event fires; successive bounces decay. Crouch to suppress bounce.

## 1.4.0 changes

- **Pig wings**: smaller folded wings placed on the upper back, angled rearward and using the same single transparent wing image on both sides. They no longer protrude sideways at the head.
- **Frog boots**: one immediate landing bounce per jump, with no chain of little bounces. The bounce is rearmed after 4 grounded ticks. Sneak to cancel. No fall damage.
- **Frog wings**: iron chestplate-level armor (+6), 4096 durability; cannot be repaired on an anvil/grindstone or with Mending XP. Existing wing items are upgraded while online.
- **Frog flight**: horizontal glide speed capped at 0.65 blocks/tick, slight downward movement even when looking up. Firework boost canceled. In water, they propel the wearer at up to 0.95 blocks/tick in the look direction. During rain under open sky they gently climb (Y +0.085 per tick) and retain horizontal momentum up to 0.58 blocks/tick.
- **Regular Elytra** retain all vanilla behavior; these restrictions apply only to the special SwampSky frog wings.
- Frog wing controls use server-side velocity adjustments while gliding. Anticheat plugins may need an exemption. Test specifically in rain, underwater, and while using fireworks.


## SwampSky 1.5.0: pet AI, breeding and interaction

- **Shift+right-click** (while not holding food or a leash) is the ONLY way to mount your tamed pig. **Shift while riding** dismounts normally. Plain right click no longer mounts it. Normal leash interactions (lead in main/offhand) are left to vanilla.
- Pumpkin or pumpkin pie right-click tames an adult pig, now making it a winged companion even if it was a normal pig. A tamed pig flies in a small orbit 4 blocks above its online owner; it speeds up toward nearby players with pumpkins or pies, and swoops, climbs, turns and circles while free. It follows the leash holder when tethered, unless fleeing danger.
- Right-click tamed adults with pumpkins to set their love mode (if mature and breed-ready). Two nearby winged pigs with the same owner both in love produce **one baby winged pig** with inherited ownership. A normal vanilla saddle or carrot-on-a-stick is not required.
- After dismounting your pig, the **first fall-damage event** is cancelled, for up to 12 seconds (later falls behave normally).
- Winged pigs are knocked backward on each melee player hit. Three player hits inside 12 seconds enrage a pig for 20 seconds. It circles and dives on the attacker like a phantom, instead of flying blindly at them.
- Winged pigs avoid snow golems, strays, polar bears, husks, camels and other mobs in winter/desert habitats. They hunt spiders and cave spiders when unthreatened.
- Flying-pig AI has a speed setting and disables its follow/attack override while ridden; all existing mechanics and loot remain. First in-game behavior validation and anti-cheat exemptions may be necessary.

The pig and frog vanilla skins and the existing resourcepack remain unchanged in 1.5.0; this release updates server-side AI and controls.

## SwampSky 1.6.0: pig AI reliability and diagnostics

- Fixed a likely cause of passive pigs freezing: ordinary pigs following pumpkin holders now use the vanilla Paper pathfinder while remaining AI-aware, instead of repeatedly disabling awareness and setting raw velocity. Pathfinding runs only on meaningful target movement or a bounded refresh interval.
- Winged pigs retain AI=true (important: mobs with AI disabled do not move on their own). Their free-flight uses stable waypoints for several seconds instead of choosing a changing relative destination every tick. Tamed pigs maintain a smooth owner orbit, and leash following now takes priority over pumpkin lures, spider hunting, and player retaliation except immediate fleeing from danger.
- Added front/head clearance and basic sideways obstacle avoidance, safe-height constraints, no forced chunk loading, and a small safe recovery step if a winged pig stalls for repeated checks. No teleporting through walls.
- Per-pig motion and display exceptions are isolated; one broken entity no longer interrupts the whole movement loop. Repeated exception messages are rate-limited per pig.
- New operator diagnostics: `/ss status` shows tracked creature counts, nearest pig AI flags and velocity; `/ss repair` reinitializes the flying AI near the player. Existing `/ss spawn flying_pig` and `/ss spawn giant_frog` are unchanged.
- On chunk reload, persistent flying pigs have movement state repaired; stale in-memory waypoints and stall samples are removed when entities die or unload.
- Textures, equipment and crafting mechanics remain unchanged. The existing unified resource pack from the main branch works without any modification.
- Source compilation in GitHub Actions does not substitute for an on-server movement test. If a pig still stands still, run `/ss status` and inspect `logs/latest.log` for `SwampSky pig` errors and any anticheat velocity cancellations.

## v1.7.0 — Half-scale flying pigs; fast saddle-only pig riding

- Only the special flying pigs are now **0.5× vanilla pig size** (including after chunk reload). Both wing ItemDisplay props are proportionally smaller and relocated to the smaller body. Ordinary pigs and giant frogs retain their scale.
- On **ordinary pigs**, equip a vanilla saddle and right-click to ride. The carrot-on-a-stick is not needed. WASD steers, sprint raises speed, space jumps and auto-hops small slopes. Water swimming stays fast, and space moves upward in water. No client mod required. Existing item use outside pig riding is unchanged.
- Mounted controls are evaluated every tick, not every 3 ticks; when dismounted the ordinary pig returns to its vanilla pathfinding. For tamed winged pigs, the original shift/release mounting and free flight remain available.
- Configuration: `pig-ground-ride-speed` 0.49, `pig-swim-speed` 0.40, `pig-speed` for winged pigs. Old config files are compatible through fallback defaults.
- Entity AI/physics can still be affected by other plugins, including anticheat. Gameplay testing is required beyond a successful Paper compile.
