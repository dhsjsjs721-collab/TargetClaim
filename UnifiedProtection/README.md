# UnifiedProtection 1.0.0

Independent Paper 26.3 / Java 25 plugin. Does not modify TargetClaim. Build with Gradle 9.2.1: `gradle build`.

## What it actually does

- Audits the unsafe Paper global settings (carpet/rail/TNT piston duplication, tripwire, headless pistons, permanent block exploits, unsafe portal teleportation). Reports unsafe settings without editing Paper files.
- Rejects inventory click/drag/drop/pickup operations involving item amounts beyond the item's own effective max stack size. Custom items with an overridden max-stack-size component are respected.
- Optional checks for hopper inventory moves/pickups (off by default for performance).
- Per-player 1-second bounded inventory-click traffic limiter, with player map cleanup.
- Cancels cross-dimensional portal transit for falling blocks (configurable) and optionally inventory vehicles.
- /uprotect status, audit, reload, scan <player>.
- Never modifies or deletes an item while validating, and does not scan all loaded inventories on a timer.
- Monitors canceled container-break events only when enabled, without claiming that this fixes Paper engine bugs.

## Requires secure Paper settings

In `config/paper-global.yml`, under `unsupported-settings`, set:
```yaml
unsupported-settings:
  allow-piston-duplication: false
  allow-headless-pistons: false
  skip-tripwire-hook-placement-validation: false
  allow-permanent-block-break-exploits: false
  allow-unsafe-end-portal-teleportation: false
```
Then restart Paper. These engine switches are the primary protection for carpet/rail/TNT/tripwire dupes. This plugin cannot safely replace Paper's engine-level fixes.

## Limits

This is a first line of defense, NOT a proven fix for every exploit, rollback dupe, dupe via item persistence, bundle serialization, or a third-party plugin. Neither an event listener nor unique item ID guarantees the absence of duplicates. Back up the world and test on a staging server with TargetClaim and all inventory plugins before deploying. Standard player and container data stays entirely under Paper ownership. `/uprotect scan` is diagnostic only and never deletes items.

Independent implementation; no third-party source copied.
