# UnifiedAuth 1.6.1 — Land of Losers

Paper 26.3 / Java 25.

- Login and registration: password first, PIN second. Legacy accounts must prove the old PIN before choosing a new password. No player enters the world before successful completion of both steps.
- Password verification happens on the bounded crypto worker before the PIN screen. New-account password is hashed in stage one and the account is saved only after PIN submission; existing credential files remain compatible.
- Four-digit PIN uses a native numeric keypad with dots, not an exposed text input. Standard Minecraft password input still displays characters.
- Opt-in remembered IP only recognizes a known network; it never skips either credential, including verified online identities. Existing v3 network history is reused; prior v2 password-skip trust is not promoted.
- Retry limits cover both steps; only completed authentication clears failures.
- Single configurable welcome title (welcome.text), no additional chat or sound.
- Auth-only artwork: a separate bitmap font `landoflosers:auth_art` (U+E100), referenced only in auth dialogs. It is an inline banner, not a full-screen replacement. No global Minecraft textures are overridden. Set `branding.auth-art-enabled: true` only after the overlay is merged into the server pack and confirmed loaded before authentication. Otherwise leave false for plain UI. The bitmap is 240 GUI pixels wide, 64 high (ascent 8).
- Existing configs are not overwritten. Set `remember-ip.enabled: true`, optionally add `welcome.animation: true`, and add the art setting only when the pack is ready.

## Upgrade
Back up plugins/UnifiedAuth and world; replace only the old UnifiedAuth JAR with 1.6.1 and fully restart. Existing credentials remain unchanged. Test both steps, wrong password, wrong PIN, registration, reconnect, expiry, migration, shared IP, client GUI scales and pack load ordering on staging.

CI compiles and runs unit tests; actual Minecraft UI requires a real client.

The server's pinned resource pack from 666236945d8b2f0def337bd4eb005646eec64c8d is preserved in the auth variant `resourcepacks/TalismanForge-2.0.0-ResourcePack-auth.zip`, with only two namespaced auth art files added. SHA-1: `aa82f9ec96662a044ca8bc12cd95095d5008fa8d`. Set `branding.auth-art-enabled: true` in the existing live config. The bitmap is an auth-only illustration, not an independent full-screen background.
