# UnifiedAuth 1.4 — one-time PIN/password registration without confirmation loop

Paper 26.3 / Java 25.

## How login works

**First registration:** enter a 4-digit PIN and a 10–64 character password once in two text inputs. Clicking Continue atomically saves both salted hashes, rechecks both and releases the player into the world. There is NO repeat/confirmation page. If disk write fails, login is denied and a diagnostic is logged, instead of showing endless registration.

**Subsequent login from an untrusted IP:** the same two input fields, PIN and password, in one form; the player remains in Paper's configuration phase and cannot enter the world until both hashes verify.

**After explicit opt-in to trust the current IP:** the subsequent login for the same authenticated UUID, username, and trusted IP requires only the 4-digit PIN. It **never bypasses the PIN**. The IP trust expires in seven days by default and can be revoked with /ua remember off. A native checkbox beside the inputs asks whether to remember the IP, and is **unchecked by default**. This feature is also OFF by default on the server and requires an administrator to enable remember-ip.enabled in config.

**Passwords and PINs are visible as typed in Minecraft's standard text fields.** Vanilla's dialog API does not support password masking. They are not printed into chat or server logs by UnifiedAuth. Do not stream your screen while typing. For masked entry, use a server-side PIN keypad (older version) or a dedicated client mod.

**Shared IP != device identity or hardware ID.** The plugin cannot read CPU, drive serial or any HWID from vanilla clients; attempting to emulate that using IP and UUID would be misleading. It records a salted HMAC IP fingerprint bound to the verified account and expiry, not raw IP. A user on the same NAT may meet the IP condition but still needs the PIN. Prefer stronger account authentication when using public/shared networks. Offline-mode remembering is blocked unless you explicitly confirm secure proxy forwarding and protect backend access.

## Security changes

- Both credentials are PBKDF2-HMAC-SHA256 hashes with individual random salts. Crypto runs in a bounded worker pool, not on the Paper main thread.
- Three attempts per configuration session plus **eight wrong PIN/password attempts per account+IP in a 15-minute window**, across reconnects.
- Existing pre-world authentication timeout, bounded concurrent sessions, and per-IP connection-rate limit remain.
- Old 1.0/1.1 accounts migrate only after proving their old PIN; accounts.properties is retained and the upgraded record is written to credentials-v2.properties.
- Old 1.1 trust is not reused. Accounts enrolled in 1.2 can continue using opt-in trust from v2-trust only after PIN validation.
- /ua remember on cannot extend trust after a PIN-only login; reconnect with full password first, or use the login form checkbox. /ua remember off revokes a trust after successful authentication.

## Upgrade

1. Back up all of plugins/UnifiedAuth, especially accounts.properties, credentials-v2.properties and v2-trust.
2. Remove all old UnifiedAuth JARs, install only UnifiedAuth-1.3.0.jar and restart Paper completely. Do not hot reload.
3. Existing config.yml is not overwritten: add remember-ip.enabled (false by default) and preserve your existing settings.
4. Test the two-field form, checkbox, trusted PIN-only return, expired IP, wrong PIN, password recovery and legacy migration on a staging server.

### Important

GitHub CI verifies compilation and credential-storage unit tests only. It does not simulate actual Minecraft clients, plugin interactions or proxies; actual configuration-phase dialog behavior requires end-to-end testing. Running simultaneously with nLogin/AuthMe is not supported.
