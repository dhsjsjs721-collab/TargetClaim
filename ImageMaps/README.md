# ImageMaps 1.0.0

**Standalone Paper 26.3 server plugin, Java 25.** Clients do not need a mod or resource pack.

Create custom image maps visible to all players, including when placed in vanilla item frames. Each map shows a 128 × 128 image, scaled from an arbitrary PNG, JPEG, or first frame of a GIF.

## Use
1. Hold an empty MAP or a FILLED_MAP in your **main hand**.
2. Run `/imagemap`, then paste a **direct public HTTPS image URL** into chat. The chat message with the URL is hidden from other players. Alternatively run `/imagemap https://example.com/picture.png`.
3. Do not switch your held map while loading.
4. A custom FILLED_MAP replaces **one** map item. Place it in an item frame: everybody sees the same artwork (no client-side mod needed).
5. Hold an empty map and sneak + right-click to open the URL prompt directly.

Commands: `/imagemap`, `/im`, `/imagemap cancel`, `/imagemap help`, `/imagemap <https://url>`.

### Local uploaded images
For images from your computer without a public URL, a server administrator can upload them into `plugins/ImageMaps/uploads/` and use `/imagemap file photo.png` with a map in hand. This operation requires `imagemaps.file` (OP by default). The plugin cannot access a player's local computer files from vanilla Minecraft chat.

### Details
- Saved files: `plugins/ImageMaps/maps/<id>.png`; index: `plugins/ImageMaps/data.yml`. Persistent through normal server restarts.
- 128px per Minecraft map; larger pictures are fitted with white padding, transparency composited on white.
- Uses a separate worker thread for remote downloads and decoding (never blocks main game thread). Map creation and inventory changes occur on the main server thread.
- URL must use HTTPS and port 443; redirects and typical private/reserved network addresses are rejected. Max response size 8 MB, decoded image max 12 million pixels and 4096 pixels on either axis.
- Only one upload in flight per player; 15-second cooldown after a successful picture; server-wide maximum 512 created plugin maps. Neither player's map nor inventory is touched on loading failure. All generated map art can be installed in vanilla frames.
- ImageMaps does not filter image subject matter; server admins should moderate who gets `imagemaps.use` permission and consider whether players may upload copyrighted or inappropriate material.
- Back up both `plugins/ImageMaps/data.yml` and `plugins/ImageMaps/maps` alongside the world and its `map_*.dat` files. World/map files and plugin data must be restored together.

**Build**: `gradle -p ImageMaps build` from repository root using Java 25 / Gradle 9.2.1; CI workflow: `.github/workflows/imagemaps-build.yml`.

**Limitations**: Animated GIFs show only the first frame; a single map is low-resolution, and vanilla Minecraft's map palette limits color fidelity. For multi-map murals, another feature would be needed.
