# UnifiedProtection 1.0 — inventory guard (chunk safety reviewed)

Paper 26.3 / Java 25. The plugin performs no chunk view-distance changes, chunk packet manipulation, world generator registration or chunk unloading. It cannot cause artificial terrain holes via those mechanisms.

Checks high-rate inventory clicks, overstacked items, optional hopper moves, falling block portal transit and unsupported Paper exploit flags. The flag audit does not alter Paper files. The plugin cannot guarantee all duplication exploits are blocked; most vanilla dupes require Paper core exploit fixes.

For disappearing chunks, first pause UnifiedOptimizer's dynamic view-distance controller, avoid hot-loading plugins, disable optional anti-xray config changes, and capture Paper's /paper debug chunks data if available. This plugin is unlikely to be the direct culprit, but no plugin can be ruled out without a test on the actual server.

Run /uprotect status and /uprotect audit. Stage-test with custom item plugins. This version was only reviewed, not changed by the 1.3 auth/1.2 optimizer/3.5 AntiESP update.
