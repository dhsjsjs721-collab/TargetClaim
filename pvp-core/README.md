# Land of Losers

**Precision. Control. Adaptation.**

A compact Minecraft PvP practice client for solo and authorized server bot practice.

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

Version 1.9.1 keeps local iron golem practice and adds explicit server/LAN practice target selection. On an authorized practice server, aim at your bot (player entity or iron golem) within interaction reach, open the menu with Down Arrow, click Select server practice target, enable the desired modules and close the menu. Only the selected entity UUID is eligible; selection is cleared on world/player changes and is not persisted. Click Clear target or Disable all & close to stop. Body geometry does not recenter an intersecting ray. No anti-cheat bypass or server compatibility guarantee.

Version 1.9.2 adds live status to diagnostics, aim-correction counts and peak per-tick rotation, explicit attack rejection events and additional regression coverage. In-game performance remains unverified.
