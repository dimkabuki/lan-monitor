# Agent Progress

## Current status — 2026-10-24

- **Repository:** [dimkabuki/lan-monitor](https://github.com/dimkabuki/lan-monitor); default branch `main`.
- **Current main head:** `32a83d873544c66c32fa76eab4c6bb154179babd`, merged [PR #4](https://github.com/dimkabuki/lan-monitor/pull/4).
- **Most recent main CI/release:** [PR CI run #35](https://github.com/dimkabuki/lan-monitor/actions/runs/40038254993) passed; [development release run #4](https://github.com/dimkabuki/lan-monitor/actions/runs/40038519594) passed and published a debug APK. It is not production-signed.
- **Active branch:** `feat/m3-qr-pairing`, latest implementation commit before this handoff: `b65e6a30457cebff65ed0803e1be0789d3800213`. A PR has not been opened yet, so CI for this branch has not run.
- **Current changes on M3 branch:**
  - `CameraPairingCodec`: versioned `lanmonitor://pair` payload with a stable Camera UUID, private IPv4 address hints, fixed signaling port and 96-bit URL-safe pairing token. Validation rejects unknown versions/fields, duplicate singleton parameters, malformed IDs/tokens, non-private addresses and unexpected URI authority/path.
  - `CameraIdentityStore`: persists a random local Camera UUID in private preferences.
  - `QrCodeBitmap`: encodes the payload to a QR bitmap using ZXing.
  - Camera UI shows the QR code alongside a manual token/address fallback.
  - Monitor UI scans a QR code, validates its contents, and fills the manual connection fields. Invalid QR data is rejected; denied scanner permission leaves manual pairing available.
  - `CameraPairingCodecTest` covers payload round-tripping and invalid/malformed/untrusted data.
  - README, architecture, testing guide and technical specification now describe QR pairing and group physical-device verification into a single MVP acceptance phase.
- **Current M3 limitations:** QR scanning supplies current private IPv4 address hints, but mDNS/DNS-SD discovery and reconnection after address changes/Wi-Fi loss are not yet implemented. Monitor still connects through the current address hint, and only one Monitor is supported. Camera UI must remain open. Signaling remains unencrypted `ws://` on the trusted home LAN only.
- **Automated validation:** M3 has not yet passed CI. The next step is review the branch diff, open a PR, then inspect the actual `build-and-test` result and fix any failures. Do not claim compilation or tests pass until that result is available.
- **Physical verification policy:** per the owner's direction, do not gate each incremental implementation PR on manual phone testing. Device-only checks remain `NOT TESTED` until the planned consolidated MVP acceptance phase; do not mark them passed by inference.
- **MVP device checklist:** [Testing guide](testing.md) contains the planned check for S20 FE and S25 in both role assignments: install/permissions, QR/manual pairing, live video/audio and frame progress, invalid tokens, stop/cleanup, WAN-off operation, rediscovery/reconnect, background/screen-off notifications and movement, and two three-hour stability runs. Record PASS / FAIL / NOT TESTED and evidence for acceptance criteria AC-01–AC-15.
- **M0/M1/M2:** implementation and CI baseline are merged. M2's physical live-media acceptance remains unverified but is deferred to the MVP validation phase as agreed.
- **Branch governance:** use feature branches and pull requests; never commit directly to `main`, force-push, or merge PRs automatically. The required status context is `build-and-test`; squash-only merge is configured. Automatic deletion of merged branches is enabled (`delete_branch_on_merge=true`); only `main` and the active `feat/m3-qr-pairing` branch were listed at the start of this work.
- **Build baseline:** JDK 17; Gradle Wrapper 8.14.6; Android Gradle Plugin 8.13.2; Kotlin Gradle plugin 2.4.21; compile/target SDK 36; min SDK 33; WebRTC SDK `150.7871.01`; Ktor `3.6.0`; ZXing Android Embedded `4.3.0`.
- **Next product slice after this PR:** add local NSD/DNS-SD discovery using the stable Camera UUID while retaining manual fallback; then implement reconnect after short Wi-Fi outages or Camera IP changes. Continue in small PRs and keep CI green.
