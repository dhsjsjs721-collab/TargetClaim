# UnifiedAuth 1.5 — secure login and welcome

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
2. Replace old JARs with only `UnifiedAuth-1.5.0.jar`; restart, do not hot-reload.
3. Existing config is not overwritten: set `remember-ip.enabled: true`, leave `allow-verified-proxy-offline-mode: false` unless verified, and optionally add the `welcome` section from the bundled config.
4. Test registration, regular login, checkbox persistence, unchecked revocation, offline same-IP full login, changed IP, wrong credentials, verified proxy flow (if used), and the welcome title on a real client.

CI builds and runs unit tests; Minecraft client dialog and proxy behavior still require integration testing.
