# ImageMaps 1.1.0

**Standalone Paper 26.3 server plugin, Java 25.** Clients do not need a mod or resource pack.

Create custom image maps visible to all players, including when placed in vanilla item frames. Each map shows a 128 × 128 image, scaled from an arbitrary PNG, JPEG, or first frame of a GIF.

## Use
1. Hold an empty MAP or a FILLED_MAP in your **main hand**.
2. **Sneak + right click** with an empty or filled map in the main hand to open a native Minecraft dialog with an HTTPS URL field, crop-to-fill option, zoom slider (1×–3×) and X/Y pan controls. `/imagemap` opens the same window. No client mod or resource pack required.
3. Do not switch your held map while loading.
4. A custom FILLED_MAP replaces **one** map item. Place it in an item frame: everybody sees the same artwork (no client-side mod needed).
5. The changes are saved as a normal persistent 128×128 map. The original item stack loses one map when the new map is created, and the image is visible in item frames to all players.

Commands: `/imagemap`, `/im`, `/imagemap chat`, `/imagemap cancel`, `/imagemap help`, `/imagemap <https://url>`. `chat` offers a fallback invisible-to-other-players URL prompt, while the new native dialog supports one-step URL + image adjustments.

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

### Cropping and scale
- **Fit** (default): keep the whole image with white padding. **Fill**: cover the 128×128 square, cropping outside edges.
- **Zoom** multiplies the image size from 1.0 to 3.0; horizontal and vertical pan position the enlarged picture.
- All settings apply server-side while the image loads asynchronously. A single map is limited to vanilla 128×128 image pixels; the dialog edits the composition before committing, rather than providing a live rendered preview.
- Full-featured image-editing tools and direct upload of a local desktop file from the Minecraft client are not available through vanilla dialogs. Admins can use the existing file import command.
