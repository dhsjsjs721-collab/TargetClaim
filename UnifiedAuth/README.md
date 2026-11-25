# UnifiedAuth 1.5.1 — secure login and welcome

Paper 26.3 / Java 25.

- Before entering the world: registration and login use a four-digit PIN and a 10–64 character password. Existing credential files and legacy migration remain compatible.
- Native opt-in checkbox remembers the current network for seven days by default. The feature is enabled by default for new configs; existing configs must set `remember-ip.enabled: true`. Known networks appear pre-selected on full login; unchecking forgets the current remembered network after successful verification.
- On direct offline-mode servers (including cracked servers) the IP fingerprint is **recognition only** and both credentials are always required. It is stored separately in `v3-network-history` and is never promoted to verified trust.
- On online-mode or explicitly verified, firewalled proxy deployments, `remember-ip.allow-password-skip` permits PIN-only login on an opted-in trusted network. The PIN is never bypassed. Choose "Войти с паролем" for full login and preference management. Do not enable verified-proxy mode without secure forwarding and blocked direct backend access.
- The server stores an account-bound HMAC IP fingerprint, not raw IP. Shared IPs are not device identifiers. Opt-in is never automatic for new networks. `/ua remember on|off|status` manages the setting; enabling requires full password authentication.
- New native colored dialog and a configurable post-authentication welcome title, subtitle, action bar, chat message and optional sound. The welcome is shown only after successful pre-world authentication.
- Standard Minecraft input fields do not mask PIN or password. Do not share your screen while typing.
- Existing PBKDF2 hashes, bounded workers, retry limits and pre-world timeout remain.

## Upgrade

1. Back up `plugins/UnifiedAuth/`, including credential files and trust directories.
2. Replace old JARs with only `UnifiedAuth-1.5.1.jar`; restart, do not hot-reload.
3. Existing config is not overwritten: set `remember-ip.enabled: true`, leave `allow-verified-proxy-offline-mode: false` unless verified, and optionally add the `welcome` section from the bundled config.
4. Test registration, regular login, checkbox persistence, unchecked revocation, offline same-IP full login, changed IP, wrong credentials, verified proxy flow (if used), and the welcome title on a real client.

CI builds and runs unit tests; Minecraft client dialog and proxy behavior still require integration testing.

## Land of Losers branding and resource pack

The `branding` configuration controls the server name, edition, auth subtitle and optional TAB header. The default gradient matches MiniMOTD. Technical plugin name and permission nodes remain unchanged. Disable `branding.tab-header-enabled` if another plugin manages TAB.

Optional menu textures can be merged into the current server resource pack with:

```sh
python3 UnifiedAuth/tools/merge_menu_background.py resourcepacks/TalismanForge-2.0.0-ResourcePack.zip my-menu.png resourcepacks/LandOfLosers-Menu.zip
```

Use a square 32x32 or higher-resolution square PNG designed to tile. The script replaces only the shared vanilla pre-world menu texture, preserving existing item assets, and writes a new ZIP plus its SHA-1. Optionally pass `--panorama path/to/six-faces` containing panorama_0.png through panorama_5.png. These six images must be square and equally sized. Shared menu textures can affect other menus, and the server pack must already be applied when the authentication dialog appears. The script does not modify server.properties or the original pack. Test initial join, pack acceptance, rejoin and various GUI scales before publishing. An independent full-screen image for only one dialog is not a vanilla Dialog API property; custom client rendering is needed for a true arbitrary full-screen background.
