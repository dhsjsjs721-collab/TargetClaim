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

Version 1.9.3: R toggles aim and attack practice against the nearest visible eligible player entity on an authorized practice server, or iron golem in solo practice, within 6 blocks. The selected UUID stays locked until disabled; no automatic retargeting. Hotkey selection uses the full field of view without changing saved FOV. Press R again to disable all modules. Auto Move is not automatically enabled. The hotkey can be remapped in Minecraft Controls; it is ignored while a menu is open and resets on world change or death. In-game behavior is not verified.

Version 1.9.4 adds distance-adaptive combat: sprint hits toward the edge of reach, critical jumps when comfortably within reach, and hysteresis between styles. Sprint is requested via the normal sprint key when moving forward; movement remains manual unless Auto Move is enabled. Attacks respect weapon charge and normal target reach; no anti-cheat bypass. Diagnostics distinguish sprint and critical attempts, not server-verified hit types. Authorized practice only; in-game behavior remains unverified.

Version 1.9.5 tracks incoming damage, including damage events without a health decrease, and observed knockback recovery. New critical jumps are avoided during unstable grounded recovery; existing falling attack opportunities remain available. Recovery requires two stable grounded ticks or a 12-tick timeout. Jump timing samples under incoming pressure are excluded. The client never changes knockback velocity. Diagnostics record incoming damage and recovery samples. In-game behavior is unverified.
