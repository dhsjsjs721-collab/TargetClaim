# UnifiedAntiESP 3.3.0 — Longer-range entity ESP and cave-mob mode

Java 25 / Paper 26.3. Standalone plugin, no NMS or client mods required.

## Why 3.2.0 leaked underground mobs

The shipped 3.2.0 defaults skipped entities beyond **56 blocks** and players beyond **72 blocks**, and excluded entities at Y>=96. A distant-entity radar at 70–125 blocks could still receive these tracked mobs. Those were actual holes in the protection.

## What's fixed

- Mob/item/armor stand/display/minecart ESP range extended to **192 blocks**; player ESP to **160 blocks**, only when the target is in the same world.
- **Strict distant underground mode** enabled by default: mobs and other living entities in dark caves more than 40 blocks from a viewer are concealed even if a long open tunnel would give an unobstructed ray. Surface mobs with daylight skylight are exempt from this strict distance rule. Normal wall occlusion is still tested for other entities.
- Cave detection uses skylight level <=3, or true in Nether. Some entities under roofs or in dark buildings will be affected; configure `entity-esp.strict-distant-underground-mobs: false` if this is not wanted. Close mobs are not hidden.
- A low-overhead bounded tracker cache, per-tick ray budget, and capped scans preserve main-thread load. `PlayerTrackEntityEvent` suppresses initial tracking, and `Player.hideEntity` removes already tracked entities.
- **Legacy config upgrade:** if an old generated config has no `config-version` and exactly the old default ranges (56/72, Y=96), the plugin upgrades them **in memory** on startup. Custom values stay unchanged. To opt out, add `config-version: 33` and explicitly set desired ranges. An old `entity-esp.interval-ticks` is not silently changed.
- New Paper X-ray audit hints for `lava-obscures` and cave fluid limitations.

## Open caves, exposed ore, water and lava

This JAR does not rewrite chunk packets. To hide more exposed ores, use the strong **Overworld-only** Paper profile `paper-anti-xray-strong.yml` (bundled in the JAR and in `presets/`). Merge **only the `anticheat.anti-xray` subtree** into the existing `config/paper-world-defaults.yml` after a backup. Use **engine-mode 3** with `air` among `hidden-blocks`, `lava-obscures: true`, and `max-block-height: 320`; then fully restart Paper.

**Limit:** Paper Anti-Xray obfuscates some cave-finder information, but cannot guarantee hiding the true shape of open caves, visible water/lava blocks, every exposed ore vein, light updates, particles/sounds or already transmitted chunks. A no-compromise full fluid/cave obscurer requires separately designed and tested **packet-level cave obfuscation** with correct light, block entity, fluid and update-handling. Do not confuse improved ore obfuscation with proof that every cave is hidden.

**Performance caution:** `air` with engine mode 3 and max height 320 can impact client FPS and chunk-packet generation. If you already had invisible or missing chunks, **do not apply this preset yet**. First test the JAR alone on a copied/staging Paper server with the current Paper world config; only test the stronger preset separately after backing up world and configs.

## Basic checks

1. Remove `UnifiedAntiESP-3.2.0.jar`; install **only** `UnifiedAntiESP-3.3.0.jar`, fully restart Paper. Avoid PlugMan or other hot-reloaders.
2. Run `/uesp` to confirm EntityESP maxRange=192 and strictDistance=40. Run `/uesp audit` to inspect whether native Paper Anti-Xray is enabled.
3. With two players and X-Ray/ESP client, verify cave mobs >40 blocks hidden, mobs in visual range shown, visible caves still load normally. Check mob farms and legitimate PvP.
4. If your old config overrides defaults, merge settings manually or add `config-version: 33` with desired values.
5. Validate chunk rendering with `F3+A` after each server restart, and inspect logs for player/entity visibility warnings.

`/uesp reload` reloads plugin config only, **not Paper world Anti-Xray**.

Compilation and unit tests cannot guarantee a live Minecraft client cannot infer entity or block locations by other channels. Built with independent source.
