# Android M14 CaptureTool Architecture

M14 is intentionally isolated from the China Unicom carrier query authority. CaptureTool is a separate diagnostic subsystem and must not become a second carrier networking implementation.

## M14-A — VPN boundary

Implemented:
- Dedicated `:capture` Android library module.
- Android 11 / API 30 minimum support.
- `VpnService.prepare(...)` as the only user VPN authorization seam.
- Private `CaptureVpnService` guarded by `android.permission.BIND_VPN_SERVICE`.
- Foreground VPN service lifecycle for modern Android targets.
- Durable local capture configuration and tunnel-state snapshots.
- Start / stop / revoke / failure state transitions.
- Reserved TEST-NET IPv4 tunnel address and route (`192.0.2.0/24`) only.

## M14-B — packet reader / decoder / session pipeline

Implemented:
- Nonblocking TUN packet reader using a duplicated file descriptor.
- Immediate raw-packet decoding into bounded metadata; raw bytes are never retained or persisted.
- IPv4 metadata decoding with header-length validation, protocol, endpoints, fragmentation state and safe TCP/UDP port extraction.
- IPv6 base-header decoding with protocol, endpoints and direct TCP/UDP port extraction.
- Process-local capture session counters for packets, bytes, TCP, UDP and other protocols.
- Recent metadata ring limited to 128 records.
- Controller read APIs for future CaptureTool UI.
- Packet parser/session unit fixtures.

Still deliberately disabled after M14-B:
- Default-route interception (`0.0.0.0/0`).
- Packet forwarding back to the network.
- User-space TCP/IP transport implementation.
- DNS interception.
- HTTP stream reconstruction or parsing.
- HTTPS interception / CONNECT proxying.
- TLS MITM.
- CA generation or installation.
- HAR export.
- Capture history UI.

The restricted route remains deliberate. M14-B can prove packet I/O and decoding on the reserved route without taking ownership of normal device connectivity. Default-route capture must not be enabled until a bidirectional forwarding path is proven.

## Authority and privacy boundary

The capture module must not contain China Unicom credentials, login state, `token_online`, cookies, carrier API clients, or existing repository refresh code. Raw packet byte arrays must not be written to SharedPreferences, databases, files, logs, or long-lived collections during M14-B. Only bounded packet metadata and aggregate counters may remain in process memory.

## Planned order

1. M14-A — VPN permission, lifecycle, safe TUN boundary. **Done**
2. M14-B — packet reader / decoder and session pipeline. **Implemented, awaiting CI**
3. M14-C — TCP/HTTP reconstruction and filtering.
4. M14-D — HTTPS/TLS architecture and certificate lifecycle.
5. M14-E — HAR/history/export and capture UI acceptance.
