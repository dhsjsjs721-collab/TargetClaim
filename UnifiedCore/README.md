# UnifiedCore 1.0 — Paper 26.3 / Java 25

Safe central diagnostic and player-utility module: `/ucore status` reports TPS/MSPT/Java version/heap memory; `/ucore check` reports whether known Unified modules and TargetClaim are present and shows world view/simulation distance and loaded chunk counts. `/ucore reload` reloads the config.

`/ucspawn` teleports asynchronously to an admin-defined spawn location or the current-world vanilla spawn when no configured point exists; `/ucsetspawn` saves the location. `/ucping` displays own latency.

Admin-only commands use `unifiedcore.admin`; players get spawn/ping by default. Optional personal join message is off by default. This plugin never force-generates chunks, changes other plugins' permissions, manages auth, changes entity visibility, resets worlds or introduces scheduled scanning. It is intentionally separate from TargetClaim.

**Scope:** UnifiedCore is an initial minimal server core, not a replacement for Essentials, LuckPerms, Vault, economy, teleport requests or a full permissions manager. More integrations must be specified and tested individually. Run on a clone before production.
