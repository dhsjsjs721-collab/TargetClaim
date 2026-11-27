# Land Minimap — Fabric 26.3 / Paper 26.3

Client HUD (north-up, 128 pixels). White = self; green = same-world online clan members. Edge markers indicate allies beyond the map; distance is in horizontal blocks. The map records only the 4×4 cell the local player physically occupies, using the color of the block immediately below the player. No remote terrain scan, ore radar, mob radar, seed access, or enemy marker. This deliberately gives a trail map rather than a full aerial image. Visiting a cave may replace the color of the corresponding surface cell; no separate cave layer yet.

The bridge must be installed on the Purpur/Paper server, with MinecraftUnified present. It checks areClanmates(viewer,target) on the server and sends only those members' current positions, every 10 ticks, to clients that registered landminimap:team. No incoming location requests. Failed clan lookups fail closed. The client clears teammate data after 3 seconds without updates or when the world/connection changes. Glow is only requested locally for the UUIDs in the authorized snapshot; entities outside normal tracking range are not rendered or outlined.

Exploration is saved locally, separately by player UUID and world UUID, in land-minimap/. It does not reveal explored terrain to other teammates. This is a client feature, not a security boundary against modified clients.

## Installation
- Client: Minecraft 26.3, Java 25, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3, LandMinimap-Fabric-0.1.0.jar in mods/.
- Server: LandMinimap-Bridge-0.1.0.jar in plugins/ alongside MinecraftUnified; full restart required. Vanilla clients need not install the mod.
- Build: Gradle 9.6.0, Java 25, `gradle -p LandMinimap :client:build :bridge:build`.
- Verify on a test server with two teammates, one enemy, clan leave/rejoin, reconnect, and cross-world transition before production. GitHub Actions compiles both JARs. Compilation does not prove runtime compatibility.

