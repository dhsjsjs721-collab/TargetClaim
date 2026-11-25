# Land of Losers

**Precision. Control. Adaptation.**

A compact Minecraft PvP practice client for offline solo practice.

- Adaptive aim assistance
- Weapon-aware attack scheduling
- Predictive movement
- Local combat diagnostics

## Installation

Minecraft 26.3, Fabric Loader 0.20.1 or later, Fabric API, Java 25 or later. Place the Fabric JAR in the mods directory. Down Arrow opens the menu. Save diagnostics writes a local report to `logs/land-of-losers-diagnostics.txt` (overwrites the previous report). Remove older builds first.

## Build

The Fabric workflow builds the playable mod with Gradle. The separate standalone Java 21+ model is built using `bash build.sh`; its JAR is not a playable Minecraft mod.

Automated tests cover the mathematical model. Actual in-game performance has not been independently verified.

FOV and golem practice preference are stored in `config/land-of-losers.properties`. Combat automation is never restored automatically. Disable all & close leaves the menu accessible for explicit reactivation.

Version 1.9.0 restricts automation to unpublished integrated worlds and iron golem targets. Body geometry does not recenter an intersecting ray.
