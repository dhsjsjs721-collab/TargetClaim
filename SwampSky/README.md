# SwampSky — Minecraft Java / Paper 26.3

Independent server-side plugin: flying pigs, steerable tamed pigs, giant swamp frogs, frog skin loot, frog gear and original pixel-art resource pack. No client-side mod required.

## Creatures

Flying pigs: 17% of naturally spawning pigs transform into flying pigs. Wings are two small moving ItemDisplay models and require the resource pack. Version 1.2.1 replaces the oversized dark wings with compact ivory/cream feathers on a single transparent PNG, using improved display orientation and subtle animated flapping. Flying pigs and regular pigs follow players holding pumpkins or pumpkin pie (winged ones fly toward the food; ground pigs walk). Both remain normal Pig entities. Neither pigs nor frogs are reskinned; only special pig wings are added. Use right click with either pumpkin (52% taming chance) or pumpkin pie (78% chance) to tame **any pig**. The food is consumed even if taming fails. Other players cannot mount your pig. After taming, right click with an empty hand to ride, **no saddle item or carrot-on-a-stick**. Controls: WASD movement, space jump / climb in flight, look downward while holding W to descend, Shift dismount. Mounted pigs use Paper PlayerInputEvent; velocity requires pig AI enabled and awareness disabled while mounted. Mounting is deferred to the next server tick to prevent cancelled interaction from blocking it. No chat hints or permanent creature nametags. On hills pigs have 1.45x speed; on snow 0.35x, and flying pigs rise to avoid it. Tamed pigs can be healed with food.

Flying pigs additionally drop 1–2 phantom membranes on death; ordinary pigs retain ordinary drops. The two sides reuse one cream-white wing texture, mirrored on the left.

Giant frogs: 24% of naturally spawned frogs in Swamp and Mangrove Swamp become giant. Size 2.8x, health 60 HP, leap toward nearby players, attack for 7 damage plus a short slowness. Below 40% health, every 5 seconds they can pull a nearby player toward their mouth with a particle tongue and deal 2 extra damage. On death they drop 1–3 **frog hides** and 6–12 slimeballs.

## Crafting

H = Frog Hide (special drop), S = vanilla Slime Ball, P = Phantom Membrane, space = empty slot.

Frog Boots: Jump Boost I, no fall damage, and bounce after a fall:

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

1. Copy SwampSky-1.2.1.jar to the Paper 26.3 server plugins folder; reboot server.
2. For custom textures, enable the combined resourcepacks/TalismanForge-SwampSky-Complete.zip on Minecraft Java 26.3 clients; this single pack also includes the pre-existing TalismanForge textures. Standalone SwampSky-ResourcePack.zip is available. Armor/elytra textures use the original vanilla Minecraft UV templates, recolored in a swamp theme, so boots/leggings appear on worn 3D armor meshes and frog wings render on the Elytra model.
3. Edit plugins/SwampSky/config.yml to tune spawn frequency, health, speed, and damage.

Original single-wing, frog-hide, and frog Elytra textures are stored in `SwampSky/assets/*.png.b64`, and the pack builder decodes them to transparent PNG files. Vanilla pig and frog skins remain untouched. The GitHub Actions push build publishes the combined ZIP and compiled JAR in `resourcepacks/` and `builds/` respectively.

Build with Java 25, Gradle 9.2.1:

    gradle -p SwampSky build --no-daemon
    python3 SwampSky/tools/build_pack.py

Artifacts: SwampSky/build/libs/SwampSky-1.2.1.jar and SwampSky/build/SwampSky-ResourcePack.zip.

This is a **Paper plugin**, not a Fabric/NeoForge mod. Creatures reuse vanilla Pig and Frog entities, and wings are attached visual displays; this avoids requiring a new client entity renderer. Client resource-pack format: 97.1. In-game tests are still recommended, particularly with anti-cheat plugins that monitor fast mounts.
