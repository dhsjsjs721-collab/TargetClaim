# UnifiedAuth 1.1.0 — Pre-world PIN + Optional Remember IP

Independent Paper 26.3/Java 25 plugin. Requires no external login plugins. Unlike a normal in-world /login command, blocks configuration phase until the player verifies their PIN.

## New in 1.1

- **Remember IP is OFF by default.** Admin sets `remember-ip.enabled: true` in `plugins/UnifiedAuth/config.yml`, then restarts the server.
- **Players must separately opt in after entering a correct PIN**: toggle **Remember IP: ON** in the keypad dialog, or use `/ua remember on` after entering the world.
- Subsequent joins from the **same IP and the same verified account UUID/name** within seven days skip the keypad dialog. New IPs, expired or revoked records require PIN again.
- `/ua remember status` shows whether the current network is trusted; `/ua remember off` revokes the account's trust.
- Only one IP per account is trusted at a time. No plaintext IP is saved: HMAC-SHA256 keyed fingerprints are stored in `trusted-ips.properties`, and the secret key in `remember-key.bin`. Back up both files securely.
- Disable by setting `remember-ip.enabled: false` and restarting. Old records are ignored when disabled. Deleting `trusted-ips.properties` while stopped clears all trust.
- `/ua status` remains admin-only; `/ua remember` is available to authenticated players.

## Security notice

IP addresses are **not device identifiers**. People behind a shared NAT, campus Wi-Fi, internet café or same household may share one public address. In offline-mode, anyone may be able to connect using another person's name; **therefore IP remembering is blocked unless online-mode is true or the administrator explicitly confirms secure authenticated proxy forwarding** using `allow-verified-proxy-offline-mode: true`. Verify firewall rules, authenticated proxy forwarding, trusted player UUIDs and any IP-forwarding configuration before enabling it. Do not enable on an unprotected offline-mode backend. A private-IP configuration can lead to unauthorized access.

This mode is an optional convenience shortcut, not a replacement for PIN hashing, firewalls or a stronger authentication method. Use it only if the shared-IP risk is acceptable. First registration **always** requires PIN confirmation.

## Existing security

- Salted PBKDF2-HMAC-SHA256 PIN storage; bounded off-thread hashing.
- 3 attempts per connection, 90-second login timeout, bounded pending queue and IP rate limiter.
- Does not modify the world and does not show or log PINs.

## Installation

Replace the old `UnifiedAuth-1.0.0.jar` with `UnifiedAuth-1.1.0.jar`, preserve `accounts.properties`, and restart. Existing `config.yml` is **not automatically rewritten by saveDefaultConfig**; merge the `remember-ip` section from the new default or recreate the config after backing it up.

**Important:** Binary compilation and JUnit tests are not end-to-end proof of functioning Paper configuration-phase dialogs or proxy forwarding. Test on a staging Paper 26.3 server with a second account, wrong IP, new IP, expired IP, IP revocation and an authenticated proxy before production.
