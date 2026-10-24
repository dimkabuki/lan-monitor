# Architecture

## M1–M3 baseline

Lan Monitor is a single native Android app using Kotlin and Jetpack Compose.

- **Camera:** After an explicit action and camera/microphone permission grants, captures the rear camera and microphone. It hosts the Ktor WebSocket signaling endpoint on TCP port 8765.
- **Monitor:** Can import pairing details by scanning the Camera's QR code or enter a private IPv4 address and temporary token manually.
- **Pairing payload:** A versioned `lanmonitor://pair` URI contains a stable Camera UUID, currently observed private IPv4 addresses, the signaling port, and a 96-bit temporary token. The Camera UUID is persisted locally; the session token is regenerated each time Camera starts a new session.
- **Validation:** The decoder rejects unknown versions/fields, duplicate singleton fields, malformed identities/tokens, non-private IP addresses, unexpected URI authority/path, and a port other than 8765. Manual entry remains available as a fallback.
- **Signaling:** Offer/answer SDP and ICE candidates are exchanged over one authenticated WebSocket connection. The Camera accepts only one Monitor in the current implementation.
- **Media:** WebRTC carries audio/video directly between peers. No STUN/TURN servers are configured. Capture profile is 640×480 at 15 fps.
- **Health:** Monitor reports whether decoded video frames have advanced recently. Camera does not claim healthy media based only on ICE state. The three-second decoded-frame timeout policy has deterministic JVM tests.
- **Discovery:** Camera advertises the stable UUID through Android `NsdManager` using DNS-SD/mDNS; the TXT record includes only the identity and protocol version, never the pairing token. After scanning the QR, Monitor discovers services matching the stable ID, resolves the service, validates its protocol version and private IPv4 address, then updates the connection address. Manual IP and QR address hints remain fallbacks.
- **Lifecycle/scope limits:** Camera UI must remain open. Discovery address updates are implemented, but automatic media reconnection following IP changes or Wi-Fi loss is not yet implemented. Background/screen-off operation, two Monitors, adaptive quality and movement detection remain future work.

## Pairing and identity boundaries

- The Camera identity is a random UUID stored in private app preferences and is intended to remain stable across sessions on the same installation. Clearing app data creates a new identity.
- A fresh cryptographic pairing token is created for each Camera session and is included in the QR as well as the manual fallback. The token is not stored across sessions and is not logged.
- The QR has current private IPv4 addresses as connection hints, not as its identity. The stable UUID is advertised through local DNS-SD/mDNS and is used to find the same Camera when its address changes. Until discovery succeeds, the Monitor can use one of the address hints or manual entry.
- QR payloads are unencrypted and contain the temporary token. Only show them to the intended Monitor on the trusted home LAN.
- Manual host entry continues to accept private IPv4 addresses only; the Camera signaling server rejects remote peers outside the allowed private/link-local IPv4 ranges and requires the token.

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
- The pairing payload can contain current IP addresses and a temporary secret. It is not intended to be posted or stored externally.
- Do not add cloud services, external signaling, STUN/TURN, analytics, telemetry, external media storage, or persistent audio/video recording.

## CI and development distribution

- Pull requests targeting main compile debug Kotlin code, run unit tests, and upload test reports. They do not publish an APK artifact.
- Manual Android CI runs can optionally build and upload a debug APK after checks pass.
- Pushes to main trigger a development pre-release containing a debug APK. It is not production-signed.

## Next architecture steps

1. Add lifecycle-safe reconnect after brief Wi-Fi loss and Camera IP changes without rebuilding capture unnecessarily, using the current discovery result while retaining manual fallback.
2. Add foreground-service-backed monitoring, screen-off behavior, movement detection and notifications.
3. Add two-Monitor handling, adaptive quality and extended stability/diagnostic tests.
