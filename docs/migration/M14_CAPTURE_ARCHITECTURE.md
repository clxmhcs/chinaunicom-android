# Android M14 CaptureTool Architecture

## M14-A scope

M14 is intentionally isolated from the China Unicom carrier query authority. The first Android capture phase establishes only the platform VPN boundary and lifecycle.

### Implemented in M14-A

- Dedicated `:capture` Android library module.
- Android 11 / API 30 minimum support.
- `VpnService.prepare(...)` remains the only user VPN authorization seam.
- Private `CaptureVpnService` guarded by `android.permission.BIND_VPN_SERVICE`.
- Foreground VPN service lifecycle for modern Android targets.
- Durable local capture configuration and tunnel-state snapshots.
- Start / stop / revoke / failure state transitions.
- A reserved TEST-NET IPv4 tunnel address and route (`192.0.2.0/24`) only.

### Deliberately not implemented in M14-A

- Default-route interception (`0.0.0.0/0`).
- Packet reads or writes from the TUN descriptor.
- User-space TCP/IP forwarding.
- DNS interception.
- HTTP parsing.
- HTTPS interception.
- CONNECT proxying.
- TLS MITM.
- CA generation or installation.
- HAR export.
- Capture history UI.

The restricted route is deliberate: until packet forwarding exists, routing all traffic into the TUN descriptor would break device connectivity. Later M14 phases may widen routing only after a forwarding pipeline is proven.

## Authority boundary

The capture module must not contain China Unicom credentials, login state, `token_online`, cookies, carrier API clients, or existing repository refresh code. CaptureTool is a separate diagnostic subsystem and must not become a second carrier networking implementation.

## Planned order

1. M14-A — VPN permission, lifecycle, safe TUN boundary.
2. M14-B — packet reader / decoder and session pipeline.
3. M14-C — TCP/HTTP reconstruction and filtering.
4. M14-D — HTTPS/TLS architecture and certificate lifecycle.
5. M14-E — HAR/history/export and capture UI acceptance.
