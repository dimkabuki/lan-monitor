# Agent Progress

## Current status — 2026-10-24

- **Repository:** [dimkabuki/lan-monitor](https://github.com/dimkabuki/lan-monitor); default branch `main`.
- **Current `main` head:** `b23890420b0ad7dd44460f104cceef2f3cbe70de`, merged [PR #5](https://github.com/dimkabuki/lan-monitor/pull/5).
- **Most recent CI/release:** [PR CI run #38](https://github.com/dimkabuki/lan-monitor/actions/runs/40042106554) passed on the QR-pairing PR head; post-merge [development release run #5](https://github.com/dimkabuki/lan-monitor/actions/runs/40046268926) passed and published a debug APK. It is not production-signed.
- **Active branch:** `feat/m3-local-discovery`. The next PR is not opened yet; inspect its newest commit and required CI after opening one. Auto-cancel may cancel older runs.
- **Local discovery implementation on this branch:**
  - `LocalCameraNsd` advertises `_lanmonitor._tcp.` with the stable Camera UUID and protocol version in DNS-SD TXT data. It never advertises the temporary pairing token.
  - Monitor starts discovery after a QR has been scanned, resolves service records for the paired Camera ID, checks protocol version and requires the resolved endpoint to be a private/link-local IPv4 address before updating the address field.
  - Android NSD is optional. If unavailable or discovery fails, the QR address and manual IP/token fields remain available.
  - A temporary Wi-Fi multicast lock is acquired only for Android 13 devices without T extension 7, where foreground mDNS reception is not automatically managed, and is released on stop/failure.
  - `CameraDiscoveryPolicy` is pure Kotlin and unit-tested for matching identity, valid private addresses, malformed IDs and non-private addresses.
- **What this increment does not do:** it updates the address field when the matching Camera is discovered, but does not yet resume/rebuild an active WebRTC session after address change or Wi-Fi loss. Reconnect logic is the next code slice. Only one Monitor remains supported; Camera UI must remain open; signaling is unencrypted `ws://` on the trusted home LAN.
- **Physical verification:** per owner direction, physical tests are consolidated at MVP readiness. Device-only criteria remain `NOT TESTED` until the planned acceptance phase; don't gate every implementation PR on phone testing or claim a pass without evidence.
- **MVP device acceptance:** [Testing guide](testing.md) lists both S20 FE / S25 role assignments; permissions; QR/manual pairing; live video/audio and decoded-frame progress; invalid tokens; stop/cleanup; WAN-off; discovery/reconnect; background/screen-off; movement notifications; and two three-hour stability runs. Record `PASS` / `FAIL` / `NOT TESTED` for AC-01–AC-15.
- **M0/M1/M2 and QR:** merged. M2 live-media acceptance remains physically unverified, deferred to the consolidated MVP check.
- **Repository policy:** feature branches and pull requests only. Never commit directly to `main`, force-push, or merge PRs automatically. Required CI status context is `build-and-test`; squash-only merging is configured; auto-delete merged branches is enabled.
- **Build baseline:** JDK 17; Gradle Wrapper 8.14.6; Android Gradle Plugin 8.13.2; Kotlin Gradle plugin 2.4.21; compile/target SDK 36; min SDK 33; WebRTC `150.7871.01`; Ktor `3.6.0`; ZXing Android Embedded `4.3.0`.
- **Next step:** open a PR for `feat/m3-local-discovery`, inspect actual `build-and-test` results, and fix CI failures. After that, add active-session reconnect after a brief Wi-Fi outage or Camera IP change, retaining manual fallback and truthful media-health reporting.
