# Security, CA trust & LAN access — Kotlin client

- The app talks to a self-hosted media server over **HTTPS on the LAN** (for example `https://192.168.1.11:8000`). The server's certificate is usually issued by a private CA.
- `android/app/src/main/res/xml/network_security_config.xml` trusts the **system** and **user** CA stores and permits cleartext, so a user-installed CA (mkcert or your own) works out of the box:
  - Install the CA once on the phone: **Settings → Security → Install a certificate → CA certificate**, pick `rootCA.pem`.
  - Because the CA is long-lived, this survives every server-cert reissue and every app update.
- **Personal/CI builds:** `android/app/src/personal/res/` (gitignored) overlays a trust anchor for the owner's local CA — `res/raw/respotit_ca.pem` — so the app reaches the self-hosted HTTPS server with no user CA install. Public/CI builds omit that overlay.
- The certificate must cover the PC's LAN IP. Reissue the leaf with the current IP in the SAN list (`scripts/local-certs.sh <ip>` in the server repo) — no app rebuild needed.
- **Firewall/port:** allow inbound TCP on the chosen port, and forward Docker's published port to the LAN if it is bound to loopback only (`0.0.0.0:8000 → 127.0.0.1:18000` via `netsh interface portproxy`).
- Credentials, tokens and keys are never written to logs or committed: `keystore.properties`, `*.keystore`, `.env` and `src/personal/` are gitignored.
