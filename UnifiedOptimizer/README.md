# UnifiedOptimizer 1.1.0

Independent **Paper 26.3 / Java 25** plugin. This is not a Folia plugin.

## Features

- One cheap sample every 200 server ticks (configurable), using Paper's average tick time and 1-minute TPS with exponential MSPT smoothing.
- Sustained overload/recovery hysteresis (3 bad, 6 good) with direct MSPT confirmation to avoid reacting to a single spike or stale TPS alone.
- **At most one world adjusted per sample**, plus 45-second per-world cooldown, one chunk-distance step per adjustment. Controls world-level view and simulation distances, not player packet queues.
- Restores original distances on pause/disable/reload, and optionally when a world becomes empty. Restores only values still owned by this plugin.
- Detects external world-distance controllers and stops touching that world, avoiding control loops with ViewDistanceTweaks or other plugins.
- Tracks only a bounded 12-sample MSPT window and one state object per world. No repeated entity/chunk scanning, no GC forcing, no item/mob removal.
- `/uopt status`: players, loaded chunks and entities, current/baseline distances, adjustment counters.
- `/uopt profile`: last MSPT, EMA, last-window mean/peak, TPS, streaks, microseconds spent sampling.
- JUnit 5 tests for the adaptation policy run during CI.

## Commands

`/uopt status`, `/uopt profile`, `/uopt pause`, `/uopt resume`, `/uopt reset`, `/uopt reload`.

## Limitations

World-level distance changes cannot fix unbounded plugin loops, storage I/O bottlenecks, faulty chunk generation, or memory leaks. The plugin will never delete entities, force unload chunks, reduce redstone tick rate, or change hopper logic. Use Chunky for planned chunk pre-generation.

When a newer JAR replaces the old version, check generated `plugins/UnifiedOptimizer/config.yml` and merge any new keys; Bukkit normally does not rewrite existing configuration files. Disable ViewDistanceTweaks or any other world-distance controller to avoid overlap. Stage-test on Paper 26.3 and inspect server timings/spark profiles before production.

Source written independently; no external plugin source copied.
