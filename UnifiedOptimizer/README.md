# UnifiedOptimizer 1.0.0

Separate, low-overhead **Paper 26.3 / Java 25** plugin.

**Does:** samples Bukkit's MSPT and TPS every 10 seconds, smooths MSPT, changes world-level view distance and simulation distance only after several sustained bad/good samples and a 45-second cooldown; tracks world player, entity and chunk counts using O(1) Paper API methods. Prevents oscillation, restores original world settings on disable/pause/reset, and relinquishes control if another plugin changes the same values.

**Does NOT:** delete items, mobs, minecarts, farms or entities; force chunk loads; run full scans; silently stop machines; generate chunks; promise extra TPS if the problem is elsewhere. Use Chunky separately for pre-generation.

Commands: `/uopt status`, `/uopt pause`, `/uopt resume`, `/uopt reset`, `/uopt reload` (op only).

If ViewDistanceTweaks or another plugin owns the same world distance settings, disable one of the two controllers.

No third-party code is copied; independent implementation.
