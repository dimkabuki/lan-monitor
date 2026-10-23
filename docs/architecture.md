# Architecture

## M1 scaffold and M2 baseline

The app is a single native Android application using Kotlin and Jetpack Compose.

M2 currently adds an initial Camera/Monitor implementation:

- **Camera:** after an explicit tap and runtime permission grants, captures the rear camera and microphone. It hosts a local Ktor WebSocket signaling endpoint on port `8765`.
- **Monitor:** manually enters the Camera's private IPv4 address and the random in-memory pairing token shown by Camera.
- **Signaling:** offer/answer SDP and ICE candidates are exchanged over one authenticated WebSocket connection. The Camera accepts only one Monitor in M2. Invalid tokens and non-private IPv4 peers are rejected.
- **Media:** native WebRTC carries audio/video directly between peers. No STUN/TURN servers are configured. Capture profile is currently 640×480 at 15 fps.
- **Health:** Monitor reports whether decoded video frames have advanced recently; Camera does not report streaming health based only on ICE connected state. A pure `VideoFrameHealth` policy evaluates the three-second stall boundary and is covered by deterministic JVM unit tests.
- **Lifecycle/scope limits:** the Camera UI must stay open in this M2 baseline. Background/screen-off operation, QR pairing, mDNS/DNS-SD rediscovery, address-change recovery, two Monitors, adaptive quality, and movement detection remain future work.
- **Physical validation:** CI can compile and test pure Kotlin logic but cannot prove live media or device-specific behavior. M2 must be exercised on two physical Android devices before its end-to-end media acceptance is declared passed.

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
| compileSdk / targetSdk | 36 |
| minSdk | 33 |
| Namespace / application ID | `com.dimkabuki.lanmonitor` |

Re-check official compatibility tables before updating pinned versions. The target is API 36; targeting API 37 or later requires implementing and testing the version-gated local-network permission flow in the technical specification.

## Network and privacy boundaries

- Runtime signaling and media are intended for the local Wi-Fi network only.
- The current implementation uses unencrypted `ws://` signaling on TCP port 8765. Cleartext is enabled for this prototype. Use it only on a trusted home Wi-Fi network; add pairing/discovery polish and evaluate stronger channel protection before expanding exposure.
- Manual client input is restricted to private IPv4 ranges, and the server rejects remote peers with non-private IPv4 addresses. This is a prototype restriction, not a replacement for robust authentication or platform firewall controls.
- The temporary pairing token is generated with a cryptographically secure random generator, remains in memory, and is neither stored nor logged.
- Do not add cloud services, external signaling, STUN/TURN, analytics, telemetry, external media storage, or persistent audio/video recording.

## CI and development distribution

- Pull requests targeting `main` compile debug Kotlin code, run unit tests, and upload test reports. They do not publish an APK artifact.
- Manual Android CI runs can optionally build and upload a debug APK after checks pass.
- Pushes to `main` trigger a development pre-release containing a debug APK. The APK is not production-signed; production signing/versioning is a separate future decision.
- GitHub Releases are used only for distributing build artifacts.

## Next architecture steps

M3 should replace manual address/token entry with QR pairing and stable identity plus local rediscovery. M4 addresses foreground services, background restrictions and cleanup. Later stages add two-Monitor sessions, adaptive quality, motion detection and extended resilience.
