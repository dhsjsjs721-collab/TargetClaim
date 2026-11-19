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
