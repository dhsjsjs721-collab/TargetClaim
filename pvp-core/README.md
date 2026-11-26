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

Version 1.9.6 enables Auto Move on R along with aim and attacks. A selected practice target can be followed up to 12 blocks while visible (initial selection within 6). Movement uses a short-horizon rear-position objective, ordinary input and local collision/ground checks; manual movement keys override it. Locked aim acquires faster outside the box, never corrects inside it, and uses the player view and eye position rather than the third-person camera location. Target death ends hotkey practice. No obstacle route finding or free-camera implementation. Third-person gameplay and pursuit require real game testing.

Version 1.9.7 adds bounded ready-attack fallback, explicit diagnostic attack gates, rear-side continuity, and disengagement during unfavorable exchanges with the selected practice opponent. It tracks confirmed outgoing damage separately from incoming damage. Retreat uses ordinary movement and collision checks. No range/cooldown bypass; no claim of guaranteed flanking or combo escape. Authorized practice only; game behavior requires verification.

Version 1.9.8 adds a shared support turn during R-activated authorized practice. AutoTotem searches main inventory/hotbar and equips offhand through normal container input. AutoBuff selects safe splash potions (healing, speed, strength) in hotbar only, waits for at least five blocks from the selected target and grounded state, skips harmful mixtures, and refreshes effects only if absent or expiring. Healing requires <=60% health. A support action skips aim/attack for that tick and restores the selected hotbar slot. Locked aiming uses a larger interior margin and bounded direct corrections outside the box. No automated drinkable potion handling or inventory restocking yet; actual gameplay and inventory synchronization need verification.

Version 1.9.9 adds a reproducible geometry-only AimBenchmark (eight synthetic scenarios, three controllers). It reports alignment, first acquisition, longest miss, turn speed and successive correction deltas. The experimental controller is test-only; no live aiming change is claimed. Runtime diagnostics distinguish geometry alignment from actual attack-ray alignment and record correction delta RMS/peak, excluding gaps in tracking. This benchmark is not an in-game combat simulation, FPS measurement or proof of multiplayer performance. Shields and food automation are out of scope.

Version 1.10.0 combines interior correction selection with previous-turn cost, a fourth benchmark variant, a short-path fallback with stall diagnostics, separated totem/potion delays, empty-hotbar potion replenishment, local slot observation after swaps, support-time accounting and a periodic target/resource status overlay. Slot observation is not server confirmation. No shield or food automation. Full obstacle routing, independent FreeLook, armor upgrades, replay and real-game validation remain incomplete.

Version 1.10.1 records up to 2400 client tick observations per R practice session and exports a local CSV with Save diagnostics. Relative coordinates only; no identifiers or packets. The predecision charge/gate is sampled separately from end-of-tick status. Each R activation resets session metrics and stopping preserves the report. Actual position progress is checked after requested movement, with a bounded flat-ground local detour search (not a full world pathfinder) for stalled pursuit. No vertical obstacle routing, independent camera, or live-game verification. Shield and food automation remain out of scope.
