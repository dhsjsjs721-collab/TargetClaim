# UnifiedWorld 1.0 — Manual World Management / Paper 26.3 / Java 25

**Commands:** `/uworld status` shows loaded-chunk counts and border sizes for each world. `/uworld pregen <radiusChunks> [world]` manually generates a square radius in **chunks** centered on the admin's position (or world spawn from console). Requests use Paper's asynchronous chunk generation, **one in-flight request at a time**, avoiding bursts; no generated chunk is kept permanently ticketed by this plugin. `/uworld cancel` stops new requests; one outstanding request may finish. `/uworld border <radiusBlocks> [world]` sets the vanilla border center to your position or world spawn from console. All commands require admin permission.

**Safe by default:** no scheduled pregen on startup, no region-file deletion, no chunk regeneration/reset, no arbitrary player teleport, no forced manual unload while active. Default max pregen radius is 32 chunks (4225 total requests), changeable after a restart. Worldborder persists via vanilla world storage. Active pregen progress does not persist across server restarts and must be restarted manually.

**Limitations:** Pregen consumes disk/CPU even if requests are paced, and can cause lag on weak hardware. Test first with radius 2–5 and monitor MSPT. Do not assume pregeneration repairs an already missing/invisible client chunk: check client F3+A, Paper logs and mods separately. No automatic cleanup of unused region files is included because it risks world corruption without robust ownership and backup support.

This plugin operates independently of UnifiedOptimizer chunk distance changes and UnifiedDefend event-based structure regeneration. Test Paper 26.3 real-world chunk generation behavior before deploying to production.
