# Architecture

## M1–M3 baseline

Lan Monitor is a single native Android app using Kotlin and Jetpack Compose.

- **Camera:** After an explicit action and camera/microphone permission grants, captures the rear camera and microphone. It hosts the Ktor WebSocket signaling endpoint on TCP port 8765.
- **Monitor:** Can discover every advertised Lan Monitor Camera on the local network without prior pairing, select a Camera and connect without a QR code or token. QR scanning and manual private IPv4/token entry remain optional alternatives.
- **Pairing payload:** A versioned `lanmonitor://pair` URI contains a stable Camera UUID, currently observed private IPv4 addresses, the signaling port, and a 96-bit temporary token. The Camera UUID is persisted locally; the session token is regenerated each time Camera starts a new session.
- **Validation:** The decoder rejects unknown versions/fields, duplicate singleton fields, malformed identities/tokens, non-private IP addresses, unexpected URI authority/path, and a port other than 8765. Manual entry remains available as a fallback.
- **Signaling:** Offer/answer SDP and ICE candidates are exchanged over a WebSocket connection. A blank join token selects the explicitly unauthenticated open-LAN mode; a non-empty token must match the current Camera session token. The Camera accepts up to two Monitor sockets. Each socket owns an independent sender, WebRTC peer connection, SDP state and ICE candidate queues; the Camera capture tracks are shared between those peer connections.
- **Media:** WebRTC carries audio/video directly between peers. No STUN/TURN servers are configured. Capture profile is 640×480 at 15 fps.
- **Health:** Monitor reports whether decoded video frames have advanced recently. Camera does not claim healthy media based only on ICE state. The three-second decoded-frame timeout policy has deterministic JVM tests.
- **Movement alerts (M5 initial slice):** Monitor samples the decoded I420 luma plane into a 96×72 grid at up to 4 Hz. Mean brightness change is removed before a pixel-difference threshold is applied; movement requires 3 consecutive positive samples, then 4 quiet samples to re-arm. Android movement notifications use their own channel and an 8-second session cooldown. Only a reduced luma sample is kept in memory; full frames are neither stored nor logged. The initial values still need tuning against real rooms and target devices.
- **Discovery:** Camera advertises the stable UUID through Android `NsdManager` using DNS-SD/mDNS; the TXT record includes only the identity and protocol version, never the pairing token. Monitor can search for all Lan Monitor advertisements without an expected identity, or filter to the stable ID imported from a QR. The resolved identity/version and private IPv4 endpoint are validated before a result is shown or used. During an active session, discovery remains filtered to the selected Camera so its address can be refreshed.
- **Lifecycle:** User-initiated Camera sessions use Android's `camera|microphone` foreground-service types; Monitor sessions use `mediaPlayback`. Both show an ongoing notification with a Stop action. `SessionControllerStore` holds process-level controller instances and tracks UI/service attachments. The foreground service retains a strong reference to its active controller, while Compose screen disposal only detaches the renderer. The same controller can be reattached after Activity recreation while the process/service remain alive. Back and notification Stop terminate the active session; removing the task requests cleanup. Process-death recovery is not implemented (`START_NOT_STICKY`). Camera media startup transfers ownership of the capturer, texture helper, source and tracks only after initialization succeeds; failures during acquisition release the partial resource set.
- **Recovery:** Each Monitor keeps the selected Camera target, retries signaling with bounded exponential backoff, and refreshes its target from validated mDNS results. A Monitor rebuilds only its own WebRTC peer after signaling loss, ICE failure, or a sustained video-frame stall; the other Monitor's peer is not affected. Recovery, background/screen-off behavior, movement false positives, alert latency and simultaneous two-Monitor operation remain unverified on physical devices. Adaptive quality remains future work.

## Pairing and identity boundaries

- The Camera identity is a random UUID stored in private app preferences and is intended to remain stable across sessions on the same installation. Clearing app data creates a new identity.
- A fresh cryptographic pairing token is created for each Camera session and is included in the optional QR/manual paired flow. The token is not stored across sessions and is not logged.
- The QR has current private IPv4 addresses as connection hints, not as its identity. The stable UUID is advertised through local DNS-SD/mDNS and is used to find the same Camera when its address changes.
- Open LAN joins intentionally do not require a token. Every signaling peer is still checked against the accepted private/link-local IPv4 ranges, but any client on that trusted LAN can join while the Camera session is active.
- A non-empty token is validated for compatibility with the paired flow; it is not an access-control boundary while tokenless joins are enabled.

## Pinned build baseline

| Component | Version / value |
|---|---|
| JDK | 17 |
| Gradle Wrapper | 8.14.6 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin Gradle plugin | 2.4.21 |
| Compose BOM | 2026.06.01 |
| Activity Compose | 1.13.0 |
| WebRTC Android SDK | 150.7871.01 |
| Ktor client/server | 3.6.0 |
| QR scanner / QR encoder | ZXing Android Embedded 4.3.0 |
| Local discovery | Android `NsdManager` / DNS-SD |
| compileSdk / targetSdk | 36 |
| minSdk | 33 |
| Namespace / application ID | `com.dimkabuki.lanmonitor` |

Re-check official compatibility tables before updating pinned versions. The target is API 36; targeting API 37 or later requires implementing and testing the version-gated local-network permission flow in the technical specification.

## Network and privacy boundaries

- Runtime signaling and media are intended for the local network only.
- Current signaling uses unencrypted `ws://` on TCP port 8765. Cleartext is enabled for this prototype. Use it only on a trusted home Wi-Fi network; do not expose the port to other networks.
- The optional pairing payload can contain current IP addresses and a temporary secret. Open LAN discovery does not publish the token. Because tokenless joins are enabled, any accepted private-LAN peer can connect; the network itself is the trust boundary.
- Do not add cloud services, external signaling, STUN/TURN, analytics, telemetry, external media storage, or persistent audio/video recording.

## CI and development distribution

- Pull requests targeting main compile debug Kotlin code, run unit tests, and upload test reports. They do not publish an APK artifact.
- Manual Android CI runs can optionally build and upload a debug APK after checks pass.
- Pushes to main trigger a development pre-release containing a debug APK. It is not production-signed.

## Next architecture steps

1. Verify lifecycle-safe reconnect after brief Wi-Fi loss and Camera IP changes on the target Samsung devices, including recovery time, frame/audio resumption and Stop cleanup.
2. Validate controller retention during Activity recreation, role-navigation cleanup, notification Stop and unexpected service destruction; then extend the service/controller contract for any required restart/recovery policy. Continue with screen-off behavior, movement detection and notifications after lifecycle behavior is stable.
3. Add two-Monitor handling, adaptive quality and extended stability/diagnostic tests.
