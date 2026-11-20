# TalismanForge 2.1.0
Paper 26.3, Java 25. Ages creates temporary moss on source water and soul soil on source lava, restores liquid automatically, protects paths from break, pistons, explosions, zero drops. Ritual chests show a particle beam without blocks or platform. Existing talisman mechanics preserved.


## 2.2.0 update
- Talismans activate in main hand and offhand (offhand priority for continuous effects if both occupied).
- Void rescue: Poseidon does not save; End/Zeus saves infinitely; all other talismans are consumed on void rescue.
- Hades is immune to fire and fire tick but **not** lava. Ghasts do not attack Hades wielders; nearby ghasts shoot zero-terrain-damage fireballs toward the wilder's combat target with a cooldown.
- Poseidon spirit dolphin moves every five ticks, follows players faster and delivers 2 hearts of damage on contact before vanishing.
- No resource pack changes are required for this update.

## v2.3 — Joker and crafting
- Retires Life talisman (existing Life items no longer have custom effects).
- Joker: one 50/50 attempt at fatal damage; consumes on a losing roll. On success, Speed II, Strength II, Resistance I for 5 minutes and 3 waves of Evoker fangs around the saved player.
- Ritual chest odds: Joker 15%, Defiance 25%, Wrath 25%, Poseidon 15%, Hades 12%, Ages 8%.
- Ordinary vanilla Totem of Undying crafting: 8 gold blocks around an emerald.
- Alternative name tag crafting: one paper and one string (shapeless). Vanilla recipe remains available in 26.3.
- Keeps dual-hand support, Hades ghast support and conditional void protections from 2.2.


## 2.4.0: Grappling Hook
- Right-click hook to pull a targeted player or mob towards the user (up to 36 blocks and clear line of sight).
- Cooldown 15 seconds per player; rod loses 8 durability per successful hook (8 uses).
- Craft: ISI / SNS / ITI, I iron ingot, S string, N iron nugget, T stick. No amethyst.
- Resource pack model: talismanforge:grappling_hook, FISHING_ROD material.
- Existing talismans, chests, and protections remain.

## 2.5.0 Grappling cast visualization
- New custom flying hook tip (ItemDisplay) rather than vanilla fishing bobber.
- Separate idle, cast, flying tip, chain-link pixel textures; rendered line consists of short repeated item displays.
- Hook uses 15-second cooldown, 36-block range, 8 durability points per cast (8 uses total).
- Pulls living entities back toward player; respects solid block line-of-sight and same-team friendly-fire disable.
- Existing item PDC keys are preserved; older hook items remain usable.
- No vanilla fishing rod item textures are overridden.


## TalismanForge 2.5.1 — hook visual fix
- Fix giant spiraling/overlapping sprites in first person: link displays are now 3D vanilla CHAIN block displays and are rotated to follow the rope direction, instead of camera-facing item sprites.
- Smaller hook projectile (scale 0.30), bounded chain spacing and offset away from player camera.
- No changes to the unified resource pack: reuse existing pack URL and SHA1. All previous talismans and other datapack textures unchanged.


## v2.5.2: native item cooldown / ender pearls
- Hook uses the vanilla gray cooldown sweep overlay (300 ticks / 15s) instead of action-bar countdown text; shows even for existing hooks via cooldown-group migration.
- Dedicated custom item cooldown group `talismanforge:grappling_hook_cooldown`; ordinary fishing rods are unaffected.
- Ender pearls gain a vanilla cooldown overlay for 200 ticks / 10s following a successful launch.
- Keep resource-pack URL constant and merge all 60 assets in the newly supplied SwampSky resource pack.


## v2.5.3: Ages and Joker improvements
- Ages: temporary moss/soul soil forms a 3-block radius disk (up to 29 blocks), refreshed as the player walks. Uses a fixed liquid surface Y to eliminate jump jitter and lifting only when submerged. Lifts a submerged player onto solid surface and provides upward buoyancy for deeper immersion. Temporary blocks are restored to source liquids.
- Ages pad cap increased to 900, platform updates throttled to 550ms per center and a quarter-tick safety refresh for players standing still. No resource pack changes.
- Joker: on successful 50/50 death save, pick 1–3 distinct random buffs out of Speed II, Strength II, Resistance I, each with an independent random duration of 20–300 seconds. Previous fangs remain.


## v2.5.4: short absorption effects and dropped-talisman fluids
- Hades Absorption III and Defiance Absorption II now last five seconds and refresh before expiry while equipped, rather than granting extremely long effects. Refreshing a consumed absorption shield does not restore yellow hearts. Legacy long-duration absorption effects are replaced when a talisman is held.
- Buffs are removed when a talisman is unequipped; Ages fire resistance is also removed and Poseidon water/rain buffs end when conditions are no longer met.
- All dropped talismans except Poseidon resist fire/lava damage, float upward in lava and sink in water. Poseidon floats in water and retains vanilla lava vulnerability.
- Dropped talismans are tracked on spawn and chunk entity load; an initial scan catches items already loaded when the plugin starts.


## TalismanForge 2.5.5 — Reliquary and balanced talisman additions
- Ritual chest is now the **Reliquary**. Newly crafted reliquaries are BARRIER blocks with a client-visible ItemDisplay using item_model `talismanforge:reliquary`. The 32x32 texture/model assets must be merged into the existing resource pack separately. The magical vertical particle beam still needs no physical beacon. Existing placed ritual chests and legacy crafted chest items are still recognized.
- Maiming: critical hits add Weakness I (3 seconds) alongside the existing Wither.
- Wrath: consecutive critical hits on a player (no non-critical strikes, 8-second combo timeout) gradually increase opponent armor wear up to approximately x1.5. The prior damage and attack-speed bonuses remain.
- Defiance: perfect parries were NOT added. Shield disable protection is retained. When the last resurrection charge is spent, three orbiting visual shields appear, each has two hearts of durability; each qualifying incoming hit is fully blocked by one shield, even if that one hit exceeds its remaining durability. Damage overflow does not pass to subsequent shields. They disappear when individually broken or when their player disconnects.
- Ages: first resurrection has no added buff; second resurrection gives exactly 3 seconds of damage immunity and glowing; final charge summons four temporary allays for 2.5 seconds with a short nearby nausea effect. Existing thorn, fire immunity and temporary paths remain.
- Ages temporary paths vary moss types over water, choose stone suitable to the neighboring Nether or underground terrain over lava, and restore submerged plant block data (kelp/seagrass) where replaced. Legacy pad saves are still loaded.
- Joker, Poseidon, Hades, End/Zeus are unchanged.
- For the resource pack addon, include the supplied `assets/talismanforge/items/reliquary.json`, `assets/talismanforge/models/block/reliquary.json` and matching PNG textures. Without the updated pack the BARRIER base block appears invisible.


## v2.5.6 — lightweight gameplay tuning; no new textures
- Maiming (Увечья): PvP hits steal exactly 0.5 raw food point (half a `foodLevel` unit), accumulated across two hits to handle Minecraft's integer food bar. The stolen food is transferred to the wielder without exceeding their cap. Wither and critical-hit Weakness are unchanged.
- Wrath (Кары): no HUD bar, maximum 4 consecutive critical hits, reset after a non-critical or 5 seconds between hits. Bonus damage is only +1, +2, +3, +4% on top of existing Wrath base; a critical combo sometimes wears **one** equipped armor piece by one additional durability, respecting Unbreaking.
- Joker (Джокер): newly generated items randomly have 1–3 resurrection attempts. Success consumes one attempt but restores the item if any remain; failure destroys it. Without Luck success is 50%; while under a Luck potion effect it is 75%. The original random effects and fangs remain.
- Poseidon: the short-lived ghost dolphin is 1.65× normal size, non-collidable, non-gravitational, and chases by direct teleports through walls. Its original brief duration and damage are retained.
- Defiance: keep the three ordinary SHIELD item displays (2 hearts each, incoming hits individually absorbed) with saved state; no new entity models or textures.
- Potion housekeeping: periodic short effects from talismans are tracked so that replacing/removing talismans does not intentionally strip separate longer or stronger vanilla potion effects; consumed Absorption hearts are not refilled by the plugin's refresh.
- Small existing-particle/sound polish for Maiming, Poseidon and End; ghasts check line of sight before supporting Hades.
- Reliquary and all model/texture pack assets remain as in 2.5.5, with no new texture requirement introduced by this release.


## v2.5.7 — consistency fixes (textures unchanged)
- Maiming's 0.5 food-point transfer remains fractional between hits; partial food transactions now reset after any unrelated food change, death, respawn, or quit. Self-induced food changes are guarded to preserve the relevant pending half-point.
- Talismans' short-lived potion effects are now individually tracked; external effect applications/changes revoke plugin ownership so taking off a talisman does not remove an unrelated potion. Potions that are present and not owned by the plugin are not overwritten by refresh.
- Temporary Ages paths recover both halves of tall seagrass when possible. Unloaded chunks are not forced loaded during shutdown; leftover restore records stay persisted. Chunk unload/load handlers attempt restoration as soon as safely available.
- Phantom dolphins, end reflections, Defiance shield damage, and the lightweight particle limits retain their previous behavior. No textures or resource-pack files changed.

## v2.5.8 — Purpur 26.3 revival effect bugfix

- Fixed `IllegalArgumentException: missing required data class java.lang.Float` from `reviveParticles` when reviving with the Maiming talisman. The effect now spawns color-matched `DUST` with explicitly typed `DustOptions` instead of an untyped `DRAGON_BREATH` particle.
- Particle counts reduced. No item, rescue probability, talisman rules or resource-pack changes.

## v2.6.0 — full talisman combat rework

- **Wrath:** 4 fewer normal hearts (total penalty -8 HP), passive Strength I instead of II, one save from death. Removed permanent +30% attack speed. Landing a critical briefly boosts attack-speed/recovery for 0.85 seconds, while all healing is reduced by 40%. Existing four-hit crit combo and weapon multiplier persist. Wrath is the rarest relic drop (1% of reliquary rolls; 0.3% bastion bridges; 0.1% fortress loot).
- **Defiance:** visibly animated 3 SHIELD displays after the second/final life is expended. Shields respawn if visual entities disappear, and partially depleted shield health is persisted. Each absorbs a hit up to 2 hearts of stored protection (one hit is fully canceled even if stronger).
- **Ages:** 3 dancing Allays now actually orbit the owner for 2.5 seconds; a 1-heart thorns effect fires for each accepted melee or ranged hit, protected from recursive retribution between Ages owners.
- **End/Zeus:** each critical hit on a player or mob creates an extra lightning effect and 1 heart of damage. Fall damage is canceled. Two permanent orbiting gold sword displays respond to the last attacker within 9 blocks, each hitting for 3 HP at most once every 2 seconds. Swords clean up on unequip/quit/reload.
- **Maiming:** hits additionally inflict Slowness III for 2.25 seconds (plus existing Wither, Weakness on crit, and fractional hunger siphon).
- **Poseidon:** rain creates two short-range hovering pufferfish to poison enemies, and an axolotl that applies Regeneration I to the owner while submerged. Maximum three creatures per owner, safely cleaned up in other conditions. Dry death causes seven *visual* lightning strikes and a phasing 12-second dolphin, attacking for 4.5 hearts, Slowness II for 3 seconds, then disappearing. The dead owner cannot be targeted by their own dolphin.
- **Hades:** maximum health reduced by 6 hearts. On successful ordinary totem save, after immediate survival the player becomes a ghost in Spectator mode for 3 seconds and returns to Survival or the prior gamemode at the chosen safe loaded location. Gamemode is recovered on logout/restart; kills grant golden hearts equal to the target's max health, capped at 10 hearts per kill and 20 hearts total. Existing monster neutrality, ghast assistance and conditional revival are unchanged.
- Corrected an additional Wither-boss particle to use typed purple DUST to avoid Purpur's missing-data exceptions.
- All item models and textures unchanged. Requires Java 25 and Paper/Purpur 26.3. Server compilation/startup tests do not replace gameplay testing.

## TalismanForge 2.7.0 — Joker, Zeus and grappling hook

- Joker successful resurrection now summons a giant Evoker illusion for three seconds to swing its arms at the last attacker and stuns the actual attacker for five seconds. Victim targeting uses recent damage attribution, including projectiles, and ignores allies. NPC AI is restored after the stun.
- Zeus golden swords now pursue the last enemy up to 40 blocks using boomerang movements with flame particles. Below half health one sword changes into a protective shield and cancels/reflects every second incoming hit. The other attacks a little faster at long range.
- Grappling hook: 21-second cooldown, 6-point impact damage plus Slowness II, two-second capped pull time, light chain and speed cap, reflected by an actively blocking shield (reducing hook durability). Puller and target cannot attack each other during the pull. The new Magnet I upgrade is made by combining a grappling hook with Lodestone in an anvil and applies mild auto-aim.
- Defiance's already working three floating shield system remains in place; it is spawned after the final resurrection.
- Existing texture/model IDs remain unchanged until a tested unified resourcepack update.

## v2.7.1 hotfix

- Joker creates the 3-second giant avenger on *every* successful resurrection; only recent living attackers receive the 5-second stun (environmental deaths still get the visual event).
- Zeus swords keep chasing the last attacker up to 40 blocks for 20 seconds. When under half HP, one sword transforms into a shielding display and every second eligible damaging event is reflected. No additional changes to talisman model IDs.
- Grappling chain is now textured as compact ItemDisplay links rather than oversized block displays. This matches the updated main unified resource pack's new chain-link and hook icon assets.
