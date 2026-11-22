# UnifiedAuth 1.2.0 — 4-digit PIN, then password (before world load)

Paper 26.3 / Java 25. No external auth dependency.

## Login UI

1. The native Minecraft dialog appears **during the configuration phase**, before loading the main world.
2. Step 1: enter a **4-digit PIN** using clickable number buttons (masked dots).
3. Step 2: enter the **password into one text input field** and press Continue.
4. On success the configuration-phase connection is released and the world loads.

**IMPORTANT**: Vanilla Minecraft's built-in dialog text input is NOT a password field and DOES NOT mask characters. The typed password is visible on the user's screen. It is not printed into chat/logs by this plugin. If you require visually hidden password entry you must use a different client UI or a special client mod; Vanilla cannot provide a true masked native password dialog.

New registration requires creating and confirming PIN and password; passwords must be 10-64 characters. Both are stored independently as PBKDF2-HMAC-SHA256 hashes with per-secret random salt. Verification/registration run on bounded background worker threads.

## Existing account upgrade

**Do not delete or overwrite** `plugins/UnifiedAuth/accounts.properties`! Existing 1.0/1.1 8-digit PIN accounts are protected by legacy verification. After successfully typing the old 8-digit PIN once, the user is asked to create new 4-digit PIN and password. New data is stored separately in `credentials-v2.properties`; legacy account records remain as backups. Set `authentication.legacy-pin-length` if you customized the old PIN length.

**Old 1.1 remembered IP records are NOT trusted** after this upgrade: they would bypass the new password. Each user must finish the upgraded authentication and explicitly enable trust again.

## Opt-in IP trust

Admin must first add `remember-ip.enabled: true` and fully restart Paper. Each user may then tick `Remember this IP: ON` while authenticating or run `/ua remember on` after successful login. `/ua remember off` revokes trust; expiration defaults to seven days.

Trust uses a canonical client IP + account name + authenticated UUID and expires automatically. **IP is not hardware ID, nor a unique device token.** IPs behind shared NAT may be identical. Vanilla Minecraft does not send CPU/motherboard ID. HWID collection would require a separate **opt-in client mod**, and identifying a machine by hardware is not inherently reliable. This plugin does not collect client hardware or fingerprint by resource packs.

Trusted IP fingerprints are HMAC-hashed with an on-server secret, stored in `v2-trust/trusted-ips.properties` and `v2-trust/remember-key.bin` (both need secure backups). Existing 1.1 trust files are deliberately ignored. Offline-mode auto-login is disabled unless the administrator explicitly enables a secure authenticated proxy setup and restricts all direct backend access.

## Installation

Stop server, back up `plugins/UnifiedAuth` directory, remove 1.1 JAR, add 1.2 JAR, preserve all existing account files. Merge new config keys into existing `config.yml` (saveDefaultConfig does not overwrite existing config). Fully restart, never PlugMan. Test migration and registration on staging server first.

## Limits

A Java plugin cannot safely gather or prove actual hardware ID from an unmodified Minecraft client. A 4-digit PIN alone can be brute-forced from a stolen database; long password and limited attempts are necessary. Also, network packets, connections behind shared proxies, and failure behaviors must be tested on the intended server. GitHub builds + unit tests do not replace real multiplayer login tests.
