# Clan fees

Leaving costs 32 diamonds paid by the departing player; kicking costs 64 paid by the clan leader. Dissolving a clan costs 32 plus 64 per other member to prevent bypassing either fee. Only diamonds in the normal inventory are counted. Insufficient funds leave membership and inventory unchanged. A failed clan file save restores the old membership and diamonds; abrupt crashes between world/player saves are not atomic. All fees apply to OP players as well when using normal clan commands.

# Media reliability

`/skin <username|NameMC profile/skin|MineSkin skin page|public HTTPS PNG> [classic|slim]`, `/skin reset`, `/skin status`. A five-second cooldown and rolling limit of three successful changes in ten hours, including reset. Signed Mojang texture data and successful-change timestamps are saved to skins.yml; rejoin does not consume a change. Direct PNGs are passed to MineSkin's queue service, not fetched by the Minecraft server. API key may be set by administrator via MINESKIN_API_KEY or media.mineskin-api-key; external limits and failures remain possible. Live profile changes use Paper setPlayerProfile. Disc URL metadata and thumbnail maps remain supported; no actual YouTube streaming in vanilla Minecraft.

# Season start and permissions

Console `/s` (alias `/start`) enters WAITING; non-operators join and see a waiting title. Console `/a` enters a ten-minute global SCOUT phase (adventure, flight speed 0.25, clan creation, no combat, building, items, portals). At expiry players land and enter survival. The phase and deadline survive restart; operators are exempt. New installations begin WAITING. Public commands are allowlisted; admin roots and subcommands are hidden and blocked for non-OP, including module aliases. Public clan management remains available. `/u recipes` opens the live custom recipe menu.

# Gameplay update

Medusa: gaze does not require reciprocal eye contact; stun lasts 15 seconds even when damaged, shield reflects a front-facing gaze, boss/allied/peace-zone/shop exclusions remain. Old Koschei bindings retire when a new egg is placed; stale souls cannot work; old loaded eggs are deleted and unloaded eggs are cleaned on next load. Mythic relics are obtained through the reliquary only in survival; all five seal recipes and redemption are removed. Existing relics remain valid. Extra recipes for independent claim, Verity and frog hide; the personal shop token is guaranteed at wandering traders (32 diamonds) and also has a more expensive craft (four diamond blocks, four emerald blocks, one name tag). Koschei saves the physical egg world UUID and refuses activation when a loaded egg block is missing. End dragon egg odds 100% first kill, 50% later; five-hour persistent respawn timer, starts the vanilla sequence when a player is in the End. `/u dragon` shows status.

# Recipe and media update

Personal merchant token is guaranteed in every wandering trader, including existing traders without an offer, for 32 diamonds. `/u recipes` opens a protected recipe viewer populated from registered custom recipes and unlocks the native recipe book. Added survival crafts for Soulbound, Triple Shot and return charm. Personal merchant, Verity and independent claims remain trader-exclusive; mythic talismans remain ritual rewards. `/skin` applies the Paper live profile update; `/disc` stores a link on a disc, `clear` removes it, and inserting the disc in a jukebox sends a clickable link to nearby players (32 blocks). `/u video <YouTube>` loads a static video thumbnail using ImageMaps. No streaming audio/video is implemented; vanilla clients need mods or a browser for actual playback.

# Latest fixes

Anubis receives 10 charge per gained XP; cap remains 500 and rescue costs 200 charge. Verity uses a full-size BlockDisplay with a resource-pack model for the admin-only structure-block DATA mode; ordinary survival block textures are unaffected. Resource pack status is logged per player for troubleshooting. Auth illustration requires UnifiedAuth 1.6.1 and compatible client pack ordering.

# Anubis XP and TAB update

Anubis charges from positive PlayerExpChangeEvent amounts while held (main or offhand, offhand priority), exactly one charge per received XP point, maximum 500. Normal player XP is retained; no duplicate charge from death attribution. Mob farms, mining, bottles and other vanilla XP pickups count. Creative/spectator do not. 200 XP rescue cost remains. Goblins now use visible baby-zombie bodies scaled to 1.20 and custom non-dyed armor. Verity excludes personal shop navigation villagers. TAB footer updated; numeric ping and clan-only HP/dimension maintained.

# Personal shops (development)

Every newly configured wandering trader sells a personal merchant token for 32 diamonds. Place it on solid ground in your own claim (maximum 3). Right-click to browse; owner Shift+right-click opens settings. Three stock slots, each with separate diamond and emerald prices and sale quantity; use the cursor to deposit stock and set price, right-click to withdraw or disable a payment option. Earnings have three separate stack slots; every ten seconds the NPC searches loaded owner-claim chests within 35 blocks and walks to a reachable chest, deposits only fitting stacks and returns. No chest means earnings stay in the buffer; sales stop when payment cannot fit. `/u shop name &6Name`, `/u shop style normal|bold|italic`, `/u shop skin self|reset|MinecraftName|textures.minecraft.net URL`. Uses Paper mannequin plus invisible villager navigation. Existing shop data in `plugins/MinecraftUnified/shops.yml`. No arbitrary external image uploads. Synchronous atomic YAML replacement guards normal transaction errors; player/chest/world saves are separate, so abrupt crash atomicity is not guaranteed. Test multiplayer purchases, claim removal, chunk unload, chest capacity and pathing before production.

# MinecraftUnified 1.1.21

All active relic abilities require the relic in either hand (offhand priority). Koschei placement creates a bound Soul of Koschei token; old placed eggs automatically receive a token once, /u talisman link reissues it only near the bound egg and invalidates earlier tokens. Egg takes three manual left clicks, with short debounce, environment remains blocked. Active minions require the same held Anubis. Thor landing grace intentionally survives unequipping. /u spawn [frog|goblin [count] [forest|mushroom]|trader] and tab completion; existing commands preserved.

# MinecraftUnified 1.1.20

Yellow Verity block and particles, a single line of local dialogue. Mirror chestplate default display name changed to Зеркальная броня; legacy default names upgraded on inventory access without altering enchants or damage. Auth art overlay retained in resource pack. No changes to damage balance or other gameplay.

# MinecraftUnified 1.1.19 — Verity

Rare smiling block with a 5% chance to appear in each newly configured wandering trader's stable offers. One purchase, random survival-obtainable item and quantity from 1 to its stack limit (maximum 64). Custom blackstone cube, persistent chunk marker, normal mining, no piston/explosion displacement. Repels loaded mobs within 16 blocks using navigation and horizontal push; prevents attacks by frightened mobs, no player knockback. Riding mobs are excluded. Four per chunk, speech throttled, optional via verity.speech. Local chat and sound, not recorded voice. /u give verity [player].

# MinecraftUnified 1.1.18 — goblins

Custom small fast zombies, 14 HP, scale 0.68, movement 0.32, iron swords, zero base armor, no sunlight burning, no reinforcements, no equipment pickup, two resource-pack costumes. Surface packs of 2–4 in dark forest areas and 4–7 in mushroom fields even by day; near-player caps 8/18 and world loaded cap 48, no forced chunk generation, no spawn in claims or peaceful zone. Respects mob-spawn rules and Peaceful difficulty. `/u goblin [1–12] [forest|mushroom]` is admin-only. Goblins have normal zombie AI, not a new client entity. Custom costume does not drop; natural drops capped. Visual and live combat tests required.

# MinecraftUnified 1.1.17

Mirror chestplate: craft 8 tinted glass in chestplate shape. 7 armor, 1 toughness, 400 durability, 1/3 independent chance to cancel incoming damage and reflect final damage to the eligible attacker. Environmental damage is absorbed on proc, no recipient. Administrative kill excluded. Reflected hits cannot reflect again; claims and allied PvP rules remain effective. The item retains normal armor enchantments and ordinary durability. /u give mirror [player]. Live player combat verification required.

# MinecraftUnified 1.1.16

Peaceful spawn blocks hostile combat, not passive relics or death saves. Relics work in either hand (offhand priority). Anubis gets souls from all player-killed mobs including spawners, except summoned/visual entities. Awakening automatically detects nearby manual drops and works on suitable floors, including spawn. Koschei placement gives a specific denial reason; unclaimed spawn is allowed. Thor can fly throughout rain and receives one landing grace after flight ends. New claim cores reserve maximum horizontal footprints (210/105 blocks in normal/spawn zones); old existing sizes preserved; new spawn cores have half-size in all dimensions. No destructive claim migration. Existing overlap may prevent upgrades.

# MinecraftUnified 1.1.15 — relic and interface overhaul

Unlimited independent relics; automatic migration with backup. One bound Koschei egg per player. Eight repaired talisman textures. Distinct VFX shapes/sounds, safe obstacle-aware Kitsune decoy motion and teleport checks, per-instance combat attribution, Medusa 30-second gaze cooldown. `/u give <item> [player]`, `/u guide`, `/u recipes`, `/u claim create`, `/u vfx <kind>` (admin, visual only). Existing permission checks remain in force. Game-client multiplayer and crash testing is still necessary.

# MinecraftUnified 1.1.15

Unlimited independently registered relics. Old singleton registry migrates preserving serials. One bound Koschei egg per player. Each instance has its own charges, storage and awakening. Live multiplayer testing required.

# MinecraftUnified 1.1.14

Miner cap is a leather helmet (1 armor), unbreakable, light level 12, subtle owner-only ore particles on the first visible ore hit by a ray, up to 7 blocks. No through-wall ore reveal. Multitool recipe and issuance retired; existing tagged iron multitools migrate to ordinary iron pickaxes on inventory access with proportional wear and enchantments preserved. New cap item and correctly laid-out humanoid head texture. No changes to other modules.

# MinecraftUnified 1.1.13

Adds manually drawn 32px icons for miner helmet and iron multitool, and a custom worn miner helmet texture. Existing tagged items receive the models on access without replacing their enchantments or durability. The full pack is committed alongside the build; SHA-1 is verified by CI. Coordinate packet remapping is deferred due to documented GrimAC incompatibility. Use Paper Anti-Xray mode 3 as a baseline; light-conditioned packet hiding is not included.

# MinecraftUnified 1.1.12 — peaceful spawn and mining

500×500 square around overworld spawn; ordinary building and mining allowed subject to existing claims. PvP, hostile mobs, explosions and mythic activation are blocked. 16 containers per chunk within zone (existing containers are preserved). Set server.properties spawn-protection=0 if ordinary players should build at exact spawn. Dynamic lighting uses temporary LIGHT blocks only in air, with chunk-persistent cleanup metadata and no forced chunk loads; existing blocks are not replaced. Shift+right click a torch throws it and places it on a permitted surface; unsuccessful throws refund once while server remains running. Miner helmet and iron multitool craftable, vanilla model icons for now. Coordinate packet translation and darkness-based ore masking are not included. Back up before deployment; test player interactions and crash recovery.

# MinecraftUnified 1.1.11

Frog leggings: 5 armor, 1 toughness, 25% knockback resistance, sprint-only speed and crouch wall slide. Frog wings: 3 armor, 1024 durability, slower controlled gliding with rain advantage, sprint swimming, no rockets or infinite rain ascent. Existing equipment migrates on access by updating keyed attributes, lore and proportional wear. Vanilla Elytra and frog boots unchanged. Fixes ambiguous Damageable import in retired shield migration.

# MinecraftUnified 1.1.10

Retired all three custom shields. Existing tagged shields are converted to vanilla shields on inventory/chunk access, retaining enchantments and proportional durability. Recipes, special effects and /u shield are removed. Unused shield textures remain in the unchanged compatible resource pack. Spawn peace-zone redesign and other gameplay changes are not included.

# MinecraftUnified 1.1.9

Anubis soul attribution now uses Paper spawn reasons and recent confirmed hits, with clear feedback for excluded mobs. Drop an ordinary dragon egg and one unawakened relic next to each other on solid ground, same survival player, then remain within 8 blocks for six seconds. The ritual locks both inputs against pickup, merging, hoppers, damage and teleportation. It uses an atomic registry commit for spent egg UUIDs, new serials and crash recovery. Existing placed Koschei eggs support an adjacent egg offering from their owner. A crash across Minecraft player/chunk saves cannot be guaranteed fully atomic; test disconnects and crash recovery in staging. New pixel textures for reliquary and hook, same unified resource pack path; packaging pins the matching pack commit. No other systems changed.

# MinecraftUnified 1.1.8 — five mythic relics

One JAR with Thor, Anubis, Koschei, Kitsune, Medusa. Replaces all older talisman mechanics and removes retired custom tagged items on access. Keeps TargetClaim, EnchantmentForge, SwampSky, ImageMaps, grappling hook, custom shields, traps, the existing three-minute reliquary and other recipes. Hosted claim APIs enforce Koschei placement and team damage rules. The unified resource pack is packaged from main. Use allow-flight=true. Stop server and back up existing plugin data, then replace the old MinecraftUnified JAR; do not install standalone TargetClaim/TalismanForge alongside it.

Commands: /u tf give <player> thor|anubis|koschei|kitsune|medusa|all|reliquary|hook; /u tf awaken; /u tf restore; /u tf status. World-unique relics cannot be reissued with give. Paper 26.3 / Java 25. Runtime test required.

## Final integration adjustments
Cerberus chains critical hits A-B-A-B for two enemies and A-B-C-A for three; Hydra lost heads regenerate in a consecutive two-minute queue and reject negative potion effects at application; a Cerberus ghost cannot pass through solid blocks or descend below rescue Y; Phoenix displays armor-adjacent heat particles proportional to stored damage. Active JAR build includes these updates and grounded-only pigs.

# Five mythic PvP talismans and grounded pigs

The relic chest/reliquary now grants exactly five mythic totems in rotation: Cerberus, Kraken, Gorgon, Phoenix and Hydra. Old eight talismans and the retired Olympian five are purged from inventoried and dropped custom totems. Vanilla untagged Totems of Undying remain. Admin commands: /tf give <player> cerberus|kraken|gorgon|phoenix|hydra|reliquary. Cerberus has chaining crits, dynamic combat health, a 3-second bounded Spectator ghost rescue and neutral Nether mobs; Kraken marks PvP opponents and slows/pulls fleeing targets, has +3 hearts and two lives; Gorgon stuns shield attackers, progressively curses staring players and stone-slows mobs; Phoenix stores absorbed PvP damage, releases a four-second wave that bypasses Fire Resistance, replenishes absorption, and revives; Hydra gives venom crits, debuff immunity, and persists three heads with two-minute regrowth. SwampSky permanently disables flying pig creation, wing visuals and breeding; grounded saddle pig riding remains.
Back up player inventories and world data before installing: retired custom talismans are deleted, not migrated.

# Zeus critical-hit lightning update

Zeus now triggers its visual lightning, +3 damage and Slowness on **every valid critical PvP hit**. No random probability and no talisman cooldown; ordinary hits do not trigger the ability. This uses Paper `Player.canCriticalAttack(target)` and retains vanilla critical-hit eligibility. Other Olympian talismans stay PvP-only and unchanged.

# Pure PvP Olympian talismans — no speed or random proc chances

The five Olympian talismans are combat-only. Zeus: guaranteed +3 damage, visual lightning and Slowness on a player hit (4 s cooldown). Poseidon: heals 1.5 hearts, grants temporary Absorption and Weakness to the player hit (7 s). Ares: applies Wither and heals one heart (5 s). Athena: applies Weakness on hit (6 s), grants stronger Resistance on receiving PvP damage (8 s). Hermes: applies Blindness and Slowness on hit (5 s), retaliates with Weakness when struck in PvP (8 s). No Speed, Jump Boost, Haste, water movement bonuses or random activation rolls. Only direct player-versus-player combat triggers abilities. One-time death rescue applies to PvP damage; VOID/KILL are excluded. Old retired talismans are purged as inventories load. Back up server data before upgrading.

# Olympian five-talisman release

The active TalismanForge module offers exactly five Olympic Greek-god talismans: Zeus, Poseidon, Ares, Athena, Hermes. All combat effects activate deterministically when ready (no proc chances), with fixed cooldowns. The reliquary rotates in a fixed order through the five types. Previously issued five non-Olympian talismans and all eight original versions are purged upon loading. Use /tf give <player> zeus|olympus_poseidon|ares|athena|hermes for OP tests. The five talismans retain deterministic one-time death protection (excluding VOID/KILL). Back up world/player data before updating.


## Five universal talismans + flying pig retirement (2026-11-24)

Exactly five talismans remain: Phoenix (fire immunity, low-health regeneration, rescue blast), Tempest (mining haste and lightning strikes), Guardian (absorption and damage resistance), Shade (speed, dark vision and blinding strikes), Tide (underwater mobility and on-hit healing). The old eight talisman types are **removed**, not converted, as player inventories and chests load; ordinary vanilla totems are retained. Back up player inventories and world data before deployment. Flying pigs are no longer spawned and existing PDC-tagged winged pigs are removed as chunks load, including saved pets. Ordinary pigs, frogs, frog equipment, claims and traps remain unchanged. 

Build the updated MinecraftUnified 1.1.7 JAR from this branch's GitHub Actions workflow (using Paper-compatible container inventory cleanup); replace the older MinecraftUnified JAR (do not install duplicate copies), and verify the five modules in the server console. A successful compile does not replace testing gameplay on a staging world.
# MinecraftUnified 1.1.7 — ровно пять новых талисманов

В отличие от 1.1.6, TalismanForge 2.9.0 теперь выдаёт только **пять оригинальных талисманов** через реликварий (кейс): Феникса (12%), Грозы (19%), Стража (26%), Сумрака (23%), Прилива (20%). Реликварий готов через три минуты; крафт прежний — семь осколков аметиста, обычный тотем и обсидиан. **Яйцо дракона не требуется** и больше не превращается в талисман.

| Талисман | Уникальная способность |
|---|---|
| Феникса | Огнестойкость, при спасении поджигает до восьми монстров в радиусе четырёх блоков |
| Грозы | Шанс усиленного электрического удара с перезарядкой |
| Стража | Поглощение I и кратковременное сопротивление после ранения |
| Сумрака | Скорость I в темноте и шанс ослепить цель |
| Прилива | Подводное дыхание, ускорение, лечение при атаке в воде |

Каждый из пяти один раз спасает от смертельного урона (кроме бездны). Прямые выпадения из мобов, рыбалки, сундуков Незера и ритуал с яйцом Энда удалены. Старые восемь разновидностей в инвентарях игроков автоматически конвертируются в пять новых; содержимое сундуков не стирается. Для тестирования OP: `/tf give <ник> reliquary` и `/tf give <ник> phoenix|tempest|guardian|shade|tide`.

**Важное ограничение:** новые талисманы пока используют обычную иконку тотема с различными именами и описаниями; новые 3D-модели не добавлены в существующий ресурс-пак. Остальные пять систем, PvP-ловушки, щиты, команды и ресурспак не изменены. Для обновления удалите старый `MinecraftUnified-1.1.6.jar`, оставьте существующие папки данных, создайте резервную копию playerdata и поставьте только один `MinecraftUnified-1.1.7.jar`.

---

# Архив документации MinecraftUnified 1.1.6

A **real single Paper plugin** (not an installer ZIP) built from the latest maintained source branches of TargetClaim, TalismanForge 2.6.0, EnchantmentForge 1.2.0, SwampSky 1.7.1 and ImageMaps 1.1.0. The compile-time generator converts the existing five JavaPlugin subclasses into hosted listener/command modules, retaining their features and original data identifiers.

**Server:** Paper 26.3, Java 25.

## Installation

1. Stop the server, **back up the entire world and existing plugin data**.
2. Remove the old five separate JARs from `plugins/`. Do not keep the five old plugins alongside this file: duplicate recipes and event handlers would conflict.
3. Place only `MinecraftUnified-1.1.6.jar` in `plugins/`.
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

## MinecraftUnified 1.1.0 — two claims, 210-block maximum and rotating trader inventory

- TargetClaim 1.6.0: each player can own **up to two claims**, tracked by separate claim IDs. Old `plugins/TargetClaim/data.yml` records with owner UUID claim keys load without manual migration. Standard level sizes (X×Y×Z) are **32×48×32, 64³, 96³, 144³ and 210³**. Existing upgrade costs/ingredients and deletion behavior remain. Use `/p list`, `/p 1`, `/p 2` or the second-claim compass button in the claim GUI.
- EnchantmentForge 1.3.0: **every wandering trader** sells an independent claim target for **exactly 20 diamonds** (two purchases per merchant). This is a valid protected 96×96×96 level-2 claim with a permanent no-upgrades flag and occupies one of the player's two slots. Normal claim cores remain craftable.
- Traders now carry **14–21** varied items instead of the whole catalog. Most prices vary slightly (about ±12%) and follow rarity tiers: crops and food are cheap, spawn eggs and enchantments moderate, while spawners, netherite templates and Elytra are expensive. A trader has 22% chance to carry a single random spawner, not all five. Special enchant-book recipes retain their original non-diamond ingredients.
- The updated trader marker reconfigures old existing trader entities. Offers are picked once per entity and not rerolled on right-click. All trader spawning methods, `/u trader`, and all existing 1.0.9 gameplay modules remain.
- **Deployment:** back up both your world and `plugins/TargetClaim/data.yml` because existing claims change their physical dimensions at all levels. Only install this one combined JAR, not TargetClaim/EnchantmentForge separate JARs. Main unified resource pack unchanged; original SHA1 remains valid. New functionality needs in-world testing, as the CI smoke test only validates server startup and command dispatch.

## MinecraftUnified 1.1.5: claim counters and trader inventory

New normal claim caps are tracked per dimension/world: two in the Overworld, two in the Nether, one in The End, two in other worlds. The independently purchased **20-diamond** non-upgradable level-2 claim has a separate **global quota of 3 per player, across every world combined**; it does not count against ordinary limits. All five original modules, three special shields and FunTime-style obsidian traps still live in one JAR.

TargetClaim 1.7.0 includes /p list and /p N selection, UUID-keyed multi-claim save data with backward compatibility for existing owner-keyed saves, and all ordinary levels up to 210 blocks in width. EnchantmentForge 1.3.1 rotates traders' stock and uses proportional rarity-sensitive pricing with mild randomized discounts; the independent claim always costs 20 diamonds. Keep the SAME unified resourcepack ZIP; no new textures are needed for the special core, which uses the vanilla TARGET.

Important: the increase from earlier 16–160-size levels can cause existing nearby claim volumes to overlap. Back up existing `plugins/TargetClaim/data.yml` and worlds and verify claim boundaries after upgrade.

## v1.1.2 — unloaded-world claim safety

Ordinary claims have separate active quotas per world (two in normal/Nether worlds, one in End worlds). Independently purchased no-upgrade claims have a shared maximum of three across all worlds combined. A world's absence at plugin enable time no longer discards saved claims from that world: TargetClaim stores each world's UUID even while unloaded. The independent core remains a fixed-price 20-diamond trader item and the shared resource pack is unchanged.

## 1.1.4 — traveling Wandering Traders

EnchantmentForge 1.4.0 uses new default trader spawning every five minutes, with up to four traders per regular world, preference for players visiting villages and real walking behavior toward loaded villagers, all bounded to loaded chunks. Normal custom 14–21 item trades and the always-20-diamond independent claim are unchanged. Use `/u trader` for admin testing. No changes to resources, textures or installed file structure. Player tests should check merchants in villages versus ordinary terrain.

## MinecraftUnified 1.1.5 — agreed Deepslate craft

The *only new gameplay feature compared with 1.1.3* is a vanilla crafting-table shaped recipe for **8 Deepslate**, available to all races/players:

```
B K B
K O K
B K B
```

B = Basalt (4), K = Blackstone (4), O = Obsidian (1). The produced item is ordinary `minecraft:deepslate`, stackable and usable in vanilla recipes. No creative-mode permission or special custom item is required. The crafting recipe is registered once at server enable and removed on disable. `/u status` reports its registration.

**Unchanged from 1.1.3:** all five main systems; random wandering village traders (roughly five minutes, maximum four per world), the 20-diamond independent claim, 2 regular privates per ordinary world / Nether, 1 regular private per End world, 3 independent privates globally, 210-block maximum normal claim size, all talismans, custom shields, obsidian PvP trap, flying pigs and giant frogs, and existing one resourcepack.

**Not included:** the proposed playable races (Elf, Dwarf, Deepborn), flower arrows, the proposed eight unapproved craftable items and new race bonuses. These remain concepts; no unrequested mechanics have been activated.

**Upgrade:** replace `MinecraftUnified-1.1.3.jar` with `MinecraftUnified-1.1.5.jar` after making a backup of worlds and plugin data. Do not install both. No resourcepack change required.

## 1.1.5 — pig AI/steering repair and 3D guardian shields

- **Pig riding:** no automatic mounting on jumps. Ordinary right-click mounts tame flying pigs. Ordinary saddled pigs now move and turn with horse-like acceleration, braking, Space jump and steering. Flying pigs use reduced speed, Space ascent, and look down+W descent with collision predictions to prevent digging into solid blocks.
- **Calm companion:** a winged pig stands/lands when within about 8.5 blocks of its owner, follows behind on foot until far away, and flies only to catch up. Wild pigs have short flight bursts separated by prolonged resting and normal land wandering. Descending from altitude and upgrading previously saved flying pigs are handled without free-fall deaths; `/ss repair` respects landing state.
- **Camera/guardian items:** End swords stay behind the character and rotate with their owner, including the low-HP transformed shield. Defiance's three spectral guardian shields remain in a small row **behind** the player instead of orbiting at camera height. Turtle's shield back display follows yaw and re-creates correctly after world changes.
- **Real 3D meshes:** the Wither, Turtle, Crusher and Spectral shields use extruded block geometry with raised front panel, center boss and back handle. This is in the one main resource pack, same file name and hosting path. SHA-1: `742640f383fdbaab59680bbcf9143e3b403f4738`. Players must download the updated pack and servers must update the resource-pack-sha1 when forcing a pack.
- **Preserved:** Deepslate craft (4 basalt + 4 blackstone + obsidian -> 8 Deepslate), 5 modules, claim quotas, trader rotation, PvP trap, enchantments, talismans and ordinary frog mechanics. No new races are implemented.
- **Verification:** Gradle CI compiles and starts a real Purpur 26.3 server with five modules and the extra shields/recipe. Actual visual appearance, live mounted handling and PvP still require player-in-world testing.

Upgrade by stopping the server, backing up worlds and data, removing `MinecraftUnified-1.1.4.jar`, installing **only** `MinecraftUnified-1.1.5.jar`, replacing the existing resource pack with its updated ZIP and restarting. Do not keep old and new plugin JARs simultaneously.

## 1.1.6 — advanced pig AI, 2.55× faster normal riding and synced wings

**This release improves the previous 1.1.5 behavior rather than adding new race concepts.**

- **2.55× faster normal pigs:** ordinary saddled mounts ride at **0.97 blocks/tick** (versus old 0.38), sprinting at up to 1.14 blocks/tick, swimming at 0.64. Horse-style WASD movement uses gentle acceleration/braking, smooth turning, automatic stepping up one-block obstacles and multiple forward collision probes to handle the increased speed. Compatible settings use NEW keys `pig-horse-ride-speed-v2` and `pig-horse-swim-speed-v2`, even when the old config contains slower speeds.
- **Wing display sync:** all loaded flying pigs near players render wing positions **every tick** instead of every third tick; remove delayed teleport interpolation and predict a small portion of velocity to reduce visible trailing during fast turns and flying.
- **Improved flying-pig AI:** stays behind and to the side of its owner, walks when nearby, rests instead of covering the player's view, and flies only when catching up or when navigation is blocked. The AI now checks whether it has stopped moving for several route refreshes and, if so, takes a short obstacle-safe airborne detour. It also avoids flying into low-roofed caves.
- **Safer ground contact:** the collision check samples the entire pig body, not just the center point. Dismounted flying pigs return to the ground safely and don't take fall damage; the companion is slightly larger (scale 0.70 instead of 0.50) so the rider seat is less likely to enter terrain.
- **Natural pumpkin behavior:** the wild pig remembers recently held pumpkin bait for ~3 seconds instead of abruptly reversing its flight as soon as food is lowered. A pig near the food-holder lands instead of hovering in the camera.
- **No automatic mounting on normal jumping.** The flying pig can be mounted only by right-clicking it; the default normal pig still needs a saddle, but not a carrot-on-a-stick.
- **Other modules retained:** existing five original components, End and Defiance swords/shields behind the player's back, 3D spectral/Turtle/Wither/Crusher models (same updated main resourcepack SHA1 `742640f383fdbaab59680bbcf9143e3b403f4738`), obsidian PvP trap, all claims and merchant rules, Deepslate recipe, talismans and frogs.

Upgrade: back up worlds and plugin data, stop the server, remove the old JAR, install only `MinecraftUnified-1.1.6.jar`, and restart. If updating directly from 1.1.4 or older, also install the latest 3D unified resourcepack and change its server SHA-1. **GitHub Actions validates build and boot but cannot certify actual riding, flight physics, anticheat behavior or visuals in a live client.**

## SwampSky equipment and pig-wing fix (2026-11-22)

The unified build fetches `feature/swampsky-creatures` during CI, incorporating the revised pig wing attachment, diamond-equivalent frog boots/leggings armor with reduced durability, boots fall-damage immunity, wall sliding with leggings and full knockback resistance. Existing SwampSky equipment is migrated by its periodic equipment repair routine. Test in-game visuals and movement on Purpur 26.3 after installing the rebuilt unified JAR.
