# UnifiedAuth 1.0.0 (Paper 26.3, Java 25)

A separate **pre-world login plugin**. It holds each connection in Paper's configuration phase, shows a native dialog **before Minecraft loads the world**, and only releases the connection after successful authentication. No lobby or world spawn changes.

## Security and design

- 8-digit PIN entered through a keypad, rendered only as masked dots. This is **not** a native typing field: vanilla Minecraft does not have a masked password-type text input for dialogs.
- First-time registration requires entering the same PIN twice; subsequent visits require the saved PIN.
- Per-account random 16-byte salt and PBKDF2-HMAC-SHA256 (260000 iterations by default), constant-time hash verification, atomic account-file replacement.
- Hashing and file writes run on a bounded worker pool, never on the server main thread. No PIN logging.
- Three attempts per connection, authentication timeout (90 seconds), cap on pending logins, IP join burst limiter and bounded cleanup.
- Compatible with standard Vanilla Java clients that support the new dialog protocol and Paper 26.3. **Not a Velocity proxy plugin or Folia plugin.**
- Refuses to run simultaneously with nLogin, OpeNLogin or AuthMe. **Do not install two auth plugins.**
- Does not modify TargetClaim, its player data, or other project files.

**Risk notice:** 8-digit PINs are weaker than long passwords against offline database attacks, so protect the server files, use online-mode when possible, make secure backups and test the entire login flow on a staging server. The built-in rate limiter only resists small join floods; a network-level DDoS requires a trusted proxy/firewall. Offline-mode username ownership is established by the first registrant; configure a whitelist/invite process where this matters. If behind Velocity, block direct backend access and use secure forwarding.

**Recovery:** Stop the server, back up `plugins/UnifiedAuth/accounts.properties`, and remove the exact line of an account you intend to reset. After restart that name will register again. Restrict access to this file.

**Commands:** `/ua status` (OP only). The plugin never accepts passwords in commands/chat. Existing auth plugins must be removed only after safe account migration, which is **not implemented in version 1.0**.

**Limitations:** build & compilation tests do not prove end-to-end login behavior; real server tests should cover disconnect mid-auth, wrong PIN, simultaneous logins, proxy forwarding, shutdown under load, authentication timeouts, and PIN recovery.
