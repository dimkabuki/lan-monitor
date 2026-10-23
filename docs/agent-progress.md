# Agent Progress

## Current status — 2026-10-24

- **Repository:** [dimkabuki/lan-monitor](https://github.com/dimkabuki/lan-monitor); default branch `main`.
- **Current main head:** `32a83d873544c66c32fa76eab4c6bb154179babd`, merged [PR #4](https://github.com/dimkabuki/lan-monitor/pull/4).
- **Main CI/release baseline:** [PR CI run #35](https://github.com/dimkabuki/lan-monitor/actions/runs/40038254993) and [development release run #4](https://github.com/dimkabuki/lan-monitor/actions/runs/40038519594) passed. The release APK is a debug build, not production-signed.
- **Active branch / open PR:** [`feat/m3-qr-pairing`, PR #5](https://github.com/dimkabuki/lan-monitor/pull/5). Check its latest Actions run before reporting CI as green; updates to documentation or code will cancel superseded PR runs.
- **M3 QR pairing implementation:**
  - `CameraPairingCodec`: versioned `lanmonitor://pair` payload with stable Camera UUID, current private IPv4 address hints, fixed signaling port and 96-bit URL-safe session token.
  - The decoder rejects unknown versions/fields, duplicate singleton parameters, malformed identity/token, unexpected URI authority/path, unsupported port, non-private address hints and oversized payloads.
  - `CameraIdentityStore` persists a random local Camera UUID in private app preferences.
  - `QrCodeBitmap` creates the Camera QR code; Monitor uses the ZXing scan contract to import the payload into the existing host/token fields. Manual entry remains available if QR scanning is denied or unavailable.
  - `CameraPairingCodecTest` covers round-trip payloads and malformed/untrusted input.
  - README, architecture, technical specification and testing guide now describe the implementation and the consolidated MVP acceptance plan.
- **Current M3 limitations:** the QR's IPv4 values are connection hints only. mDNS/DNS-SD discovery and reconnection after Camera IP changes or Wi-Fi loss are still unimplemented. Only one Monitor is supported, the Camera UI must stay open, and signaling is unencrypted `ws://` on the trusted home LAN.
- **Physical verification:** deferred until the core MVP is ready, as directed by the owner. Physical-only criteria remain `NOT TESTED`; do not block every incremental PR on phone testing or claim those checks passed before evidence exists.
- **Planned MVP device acceptance:** [Testing guide](testing.md) lists install/permissions, both device role assignments (S20 FE and S25), QR and manual pairing, live video/audio and decoded frame progress, invalid-token handling, stop/cleanup, WAN-off operation, discovery/reconnect, background/screen-off alerts and movement detection, plus two three-hour stability runs. Capture `PASS` / `FAIL` / `NOT TESTED` evidence for AC-01–AC-15.
- **M0/M1/M2:** implementation and CI baseline are merged. M2's physical live-media acceptance remains unverified and is deferred to the consolidated MVP device phase.
- **Repository policy:** never commit directly to `main`, force-push or merge PRs automatically. Required CI status context is `build-and-test`; squash-only merging is configured. Auto-delete merged branches is enabled (`delete_branch_on_merge=true`).
- **Build baseline:** JDK 17; Gradle Wrapper 8.14.6; Android Gradle Plugin 8.13.2; Kotlin Gradle plugin 2.4.21; compile/target SDK 36; min SDK 33; WebRTC `150.7871.01`; Ktor `3.6.0`; ZXing Android Embedded `4.3.0`.
- **Next step after PR #5 CI:** merge remains owner-controlled. The next code slice is NSD/DNS-SD discovery using the stable Camera UUID, while keeping manual IP/token entry as a fallback; then add reconnection after brief Wi-Fi outages or Camera address changes.
