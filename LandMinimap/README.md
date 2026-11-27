# Land Minimap — Fabric 26.3 / Paper 26.3

Client HUD (north-up, 128 pixels). White = self; green = same-world online clan members. Edge markers indicate allies beyond the map; distance is in horizontal blocks. The map records only the 4×4 cell the local player physically occupies, using the color of the block immediately below the player. No remote terrain scan, ore radar, mob radar, seed access, or enemy marker. This deliberately gives a trail map rather than a full aerial image. Visiting a cave may replace the color of the corresponding surface cell; no separate cave layer yet.

The bridge must be installed on the Purpur/Paper server, with MinecraftUnified present. It checks areClanmates(viewer,target) on the server and sends only those members' current positions, every 10 ticks, to clients that registered landminimap:team. No incoming location requests. Failed clan lookups fail closed. The client clears teammate data after 3 seconds without updates or when the world/connection changes. Glow is only requested locally for the UUIDs in the authorized snapshot; entities outside normal tracking range are not rendered or outlined.

Exploration is saved locally, separately by player UUID and world UUID, in land-minimap/. It does not reveal explored terrain to other teammates. This is a client feature, not a security boundary against modified clients.

## Installation
- Client: Minecraft 26.3, Java 25, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3, LandMinimap-Fabric-0.2.0.jar in mods/.
- Server: LandMinimap-Bridge-0.2.0.jar in plugins/ alongside MinecraftUnified; full restart required. Vanilla clients need not install the mod.
- Build: Gradle 9.8.0, Java 25, `gradle -p LandMinimap :client:build :bridge:build`.
- Verify on a test server with two teammates, one enemy, clan leave/rejoin, reconnect, and cross-world transition before production. GitHub Actions compiles both JARs. Compilation does not prove runtime compatibility.


## 0.2.0
- M opens the large map; arrows pan, +/- zoom, Я recenters.
- Select personal or clan mode, enter a label and left-click the map. Right-click near your own marker to remove it. Personal (gold, up to 64) stays local; clan (blue, up to 32 per clan) persists on the server and is delivered only to the current clan in the same dimension.
- Select an ally and click Инвентарь. They receive a 60-second request; they must select you and click Разрешить to grant 10 minutes of access. Отозвать revokes immediately. No consent survives a reconnect or clan change. A read-only 54-slot snapshot shows storage, armor, offhand, not ender chests; all inventory clicks and drags are canceled server-side. Both sides must use the mod to approve.
- Protocol v2 requires updating both JARs together (new client can read old v1 positions, but no new features without new bridge).
- Build and runtime verification are separate. Test inventory shift-click, hotbar swaps, creative actions, death, disconnect and permission revocation before deployment.

## 0.3.0: world overlays
Both JARs should be updated to 0.3.0. An independent, versioned overlays channel preserves v2 team-protocol compatibility.
- Own saved beds and anchors (up to six each), current spawn, world spawn, clan spawn, and own last death are shown in the current dimension. "(?)" means the saved block is in an unloaded chunk and has not been verified; unloaded chunks are not scanned for the saved-point list.
- Public peace-zone (green), world border (red), and accessible claims (purple) use actual server coordinates. Claim height is displayed on hover; claim membership is checked on every snapshot. Enemy claims and teammates' owner-only claims are not transmitted. Unknown claim layouts fail closed.
- "Точки спавна" cycles through available points and centers the map; "Зоны вкл/выкл" toggles overlays on the big map. Hover on the map to see point/zone details. Fog-of-war remains intact.
- No gameplay changes, teleportation, chunk terrain scan, or private points of other players. Requires runtime testing on your exact MinecraftUnified build.

## 0.4.0
- Shared exploration is opt-in via Разведка. Only the server records actual visited 4x4 cells; it never accepts arbitrary client-supplied terrain. Contributions persist per player and clan and are deleted when disabled or leaving the clan. Shared tiles are not copied to personal disk caches. Caps: 20,000 per contributor, 200,000 total, nearest 2,000 per viewport snapshot. Personal and shared maps now have separate surface and 16-block Y layers; pre-0.4 mixed-height tiles are retained on disk but not rendered by the new layered map.
- World-anchored waypoint labels are HUD projections (approximate first-person FOV), with nearest-point directional navigation. Local and clan markers have XYZ. Old 2D markers default to Y=64; newly placed remote surface markers use the player's current Y; cave markers use the layer midpoint. Marker categories are presets for labels.
- Team HUD: health, max health, armor, hunger, dead/cross-world flags, and optional voice activity using the existing voicechat API (microphone activity only, no audio recording). Race text is read from server-owned PDC landminimapbridge:race if a race plugin provides it; no race assignments or abilities are fabricated.
- Quick pings Help/Danger/Come here use the sender's actual location, expire after 30 seconds, and are delivered only to their current clan. Death and low-health alerts are clan-only. Own five most recent deaths are persisted on the player. Claim overlay shows level and next upgrade material; use existing /u claim for actual upgrades.
- Both JARs should be updated together. Runtime testing is required; CI compilation alone is insufficient.

## 0.4.1 (client)
- 72 GUI-pixel HUD, surface map by default; 3D labels off by default; compact teammate info moved into map tabs.
- Incrementally sample only exposed surface blocks in already loaded client chunks, 320 columns per tick maximum; cave layers still record visited floor only. No forced chunk loading or subsurface scans. Existing cache remains compatible.
- Compact tabbed map controls, preparation outside render, local surface map survives a temporary team packet timeout after the initial bridge handshake. Team positions still expire after 3 seconds.
- Compatible with LandMinimap-Bridge 0.4.0; replace only client JAR. In-game validation still required.
