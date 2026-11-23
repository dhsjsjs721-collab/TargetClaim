# UnifiedOptimizer 1.2 — safer dynamic simulation only

Paper 26.3 / Java 25.

This version defaults to **NOT dynamically modifying view distance**. It may still adjust world simulation distance, which changes ticking range but does not directly alter which world chunk packets the client receives. This is a conservative mitigation for reports of invisible or missing chunks after dynamic view-distance updates.

- Controls only simulation distance by default; world view distance remains entirely owned by Paper and the administrator.
- safety.prevent-dynamic-view-distance defaults to true and overrides an old config that still says adaptive.adjust-view-distance: true.
- Existing MSPT smoothing, recovery hysteresis, 45-second per-world cooldown, at most one adjustment per sample, conflict protection, no full world/entity scans, and restore-on-disable retained.
- No forced chunk loads/unloads or block modifications. No automatic removal of items/entities. Safe with normal farms except for the intentional reduced simulation distance under high load.

For active chunk holes: run /uopt pause (restores owned settings), disconnect and reconnect, press F3+A and fully restart Paper if needed. This plugin cannot repair a broken client's chunk cache or Paper server bug.

For diagnostics: /uopt status and /uopt profile. Watch MSPT, view distance and server logs. To enable dynamic world view changes, deliberately set safety.prevent-dynamic-view-distance: false and adaptive.adjust-view-distance: true ONLY after staging tests, accepting possible client chunk re-render issues.

Existing config.yml needs manual merging of safety keys. Build/automated tests are not proof of live chunk delivery correctness.
