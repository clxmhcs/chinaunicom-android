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
- Immediate raw-packet decoding into bounded metadata; raw packet arrays are never persisted.
- IPv4 metadata decoding with header-length validation, protocol, endpoints, fragmentation state and safe TCP/UDP port extraction.
- IPv6 base-header decoding with protocol, endpoints and direct TCP/UDP port extraction.
- Process-local capture session counters for packets, bytes, TCP, UDP and other protocols.
- Recent metadata ring limited to 128 records.
- Controller read APIs for future CaptureTool UI.

## M14-C — bounded TCP / cleartext HTTP reconstruction

Implemented:
- Ephemeral TCP segment decoding for direct IPv4/IPv6 TCP packets.
- IPv4 fragmented packets are excluded from TCP stream reconstruction rather than guessed.
- Directional TCP stream keys and TCP sequence tracking.
- Bounded in-memory ordered reconstruction with overlap handling and a small pending out-of-order segment queue.
- Maximum 64 active directional streams.
- Maximum 64 KiB per stream and 16 pending segments per stream.
- HTTP/1.x request and response header detection after `\r\n\r\n` reconstruction.
- Parsed structured messages contain request method/target/host or response status plus headers only.
- `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, token/API-key style headers are redacted before publication.
- HTTP body bytes are never published to `CaptureHttpMessage` and are discarded when the completed header is parsed.
- Recent structured HTTP messages are process-local and limited to 128 records.
- `CaptureVpnController` exposes only bounded structured HTTP snapshots for later UI work.

## M14-D — HTTPS/TLS architecture and certificate lifecycle

Implemented as architecture/state management only, matching the actual capability level found in the iOS source:
- Durable `CaptureMitmConfiguration` with enabled/HTTPS/include/exclude host policy.
- Host normalization and an explicit disabled-state guard before any interception decision.
- Passive `CaptureTlsInspector` eligibility seam for HTTPS port 443; it does not parse/decrypt TLS or open sockets.
- Certificate lifecycle states: missing, generated/imported, installation-ready, user-confirmed-trusted.
- Process-local root certificate store with defensive byte-array copies.
- Source-parity `CaptureMitmCertificateGenerator` seam that deliberately returns no certificate rather than fabricating certificate material.
- Explicit `HOST_CERTIFICATE_GENERATION_AVAILABLE=false` and `ACTIVE_TLS_DECRYPTION_AVAILABLE=false` production capability gates.
- Installable `.cer` export only to the app cache after a root certificate exists.
- Android 11+ certificate flow is manual: an ordinary app cannot install a CA certificate through `KeyChain.createInstallIntent()`, so the user must install the exported CA from system Settings.
- Trust completion is recorded only after explicit user confirmation; it is not presented as a privileged/system trust-store probe.
- `CaptureMitmProxyCoordinator` mirrors the iOS orchestration states but production M14-D cannot enter READY/RUNNING while real host-certificate signing and TLS relay remain unavailable.
- Controller seams expose M14-E UI operations for configuration, certificate status/instructions, certificate registration/export, trust confirmation/reset, and MITM proxy state.

Important Android platform limitation:
- On Android 7+ many apps do not trust user-added CAs by default unless their Network Security Configuration opts in, and certificate pinning can independently prevent interception. M14-D therefore does not claim that installing a user CA makes arbitrary third-party HTTPS decryptable.

## M14-E — HAR/history/export and CaptureTool UI

Implemented against the capabilities that actually exist after M14-D:
- `其它业务 → 抓包工具` functional entry in the Android main App.
- Compose status page for VPN state, packet counters and structured HTTP counters.
- User-driven `VpnService.prepare(...)` permission launch and explicit start/stop controls.
- Editable capture host/path/additional-host configuration using the existing M14-A store.
- Editable HTTPS/MITM include/exclude configuration using the existing M14-D store.
- Certificate state UI with explicit root-certificate import, explicit user export through Android Storage Access Framework, user trust confirmation/revocation and reset.
- UI always displays that dynamic host-certificate generation and active TLS decryption are unavailable while the M14-D production capability switches are false.
- Capture history is only a view over M14-C's bounded process-local 128-message ring. `CaptureHistoryStore` does not create a database, preference store or second payload collection.
- Search over method, host, target, status, stream ID and already-redacted header values.
- Detail page for overview, stream and headers. The Body section explicitly states that HTTP bodies are not published or stored.
- User-requested HAR 1.2 export through the Android Storage Access Framework.
- HAR export is metadata-only: it contains only fields already present in `CaptureHttpMessage`, keeps sensitive headers redacted, has no `postData`, request body or response body, and does not create automatic background export files.
- HAR response/request counterparts that are not known from the directional structured message remain empty/zero rather than being fabricated; `_captureMessageKind` and `_captureStreamId` preserve the actual source message type.

Source parity note:
- The iOS `CaptureRecordHistoryStore` in the supplied source is also in-memory only. Android therefore does not invent durable capture-history persistence in M14-E.
- The iOS UI/HAR layer can model bodies because its `CaptureRecord` type has body fields; Android M14-C deliberately does not publish body bytes, so M14-E exports only the data its source pipeline actually owns.

## M14 is not yet real full-device capture

M14-E does **not** close the remaining transport gap:
- the VPN still routes only `192.0.2.0/24` TEST-NET traffic;
- packet forwarding back to the network is not implemented;
- normal device traffic is not sent through the TUN;
- therefore ordinary browser/App traffic cannot yet be used as proof of real CaptureTool interception.

This is intentional. Enabling `0.0.0.0/0` before a proven bidirectional forwarding path would break device connectivity and would create a false-positive capture implementation.

Still deliberately disabled after M14-E:
- Default-route interception (`0.0.0.0/0` / `::/0`).
- Packet forwarding back to the network.
- User-space TCP/IP transport implementation.
- DNS interception.
- CONNECT proxying.
- TLS socket relay / TLS MITM.
- Dynamic host-certificate signing.
- Automatic CA installation.
- SNI ClientHello parsing.
- Persistent packet or HTTP-body history.

## Authority and privacy boundary

The capture module must not contain China Unicom credentials, login state, `token_online`, cookies from the carrier repository, carrier API clients, or existing repository refresh code. TUN raw packet arrays must not be written to SharedPreferences, databases, files, logs, or long-lived collections. M14-C permits only bounded ephemeral TCP payload buffering required to reconstruct cleartext HTTP headers; HTTP bodies are not published or persisted, and sensitive authentication/cookie header values are redacted before becoming structured capture records.

M14-D may write only a user-requested root-certificate export to the app cache. Certificate bytes are not captured network payload and the in-process certificate registry is resettable. No private key, host certificate, TLS plaintext, CONNECT payload, or decrypted HTTPS body is generated, stored, or exported in M14-D.

M14-E may write structured metadata only when the user explicitly selects an export destination through Android's Storage Access Framework. Capture history itself remains process-local. No automatic HAR export, background capture archive, request body, response body, cookie secret, token or raw packet is persisted.

## Planned order

1. M14-A — VPN permission, lifecycle, safe TUN boundary. **Done**
2. M14-B — packet reader / decoder and session pipeline. **Done**
3. M14-C — TCP/HTTP reconstruction and filtering. **Done**
4. M14-D — HTTPS/TLS architecture and certificate lifecycle. **Done**
5. M14-E — HAR/history/export and CaptureTool UI. **Implemented, awaiting CI and UI/device acceptance**
6. M14-F — real bidirectional forwarding/default-route capture, followed by full real-device CaptureTool acceptance. **Not started**
