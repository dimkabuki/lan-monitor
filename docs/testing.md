# Testing

## Pull-request CI

The required status-check context is `build-and-test` from the **Android CI** workflow.

For each pull request targeting `main`, CI compiles the app's debug Kotlin code with `:app:compileDebugKotlin`, runs `:app:testDebugUnitTest`, and uploads unit-test reports for 14 days. The PR workflow does not build or publish an APK artifact.

Current pure unit tests cover the English role labels, private IPv4 input validation, URL-safe pairing-token generation and exact token matching, and decoded-video-frame health boundaries (no frame observed, the three-second timeout boundary, and a stalled frame). These tests exercise the policy helpers deterministically; they do not prove that WebRTC can connect across two phones or that camera/microphone capture works.

## M2 two-device smoke test — not yet claimed as passed

Use two physical Android devices connected to the same Wi-Fi network with client isolation disabled. For this M2 baseline, keep the Camera activity open.

1. On the Camera device, choose **Camera** and grant camera/microphone permission when prompted.
2. Tap **Start Camera** and wait for the local address and temporary pairing token to appear.
3. On the other device, choose **Monitor**. Enter one of the Camera's private IPv4 addresses and the token, then tap **Connect to Camera**.
4. Confirm video appears and decoded video-frame progress is reported. Confirm remote microphone audio is audible.
5. Verify a wrong token is rejected and an ordinary public IPv4 address is refused by the Monitor UI.
6. Tap **Stop Monitoring**, then **Stop Session**, and verify the UI leaves its active state.
7. Repeat with the two device roles swapped.

Record the exact devices, Android versions, whether video/audio worked, time to connect, and any warnings or crashes. This smoke test is not a substitute for later network-loss, screen-off, background, reconnection, or three-hour acceptance tests.

## Build an APK on demand

In GitHub, open **Actions → Android CI → Run workflow** and enable **Build and upload a debug APK**. The workflow runs compile/tests first; if they pass, the separate `build-apk` job builds `:app:assembleDebug` and uploads the `lan-monitor-debug-apk` artifact for 14 days.

The APK is a debug build, signed with the Android debug key, and is not production-signed.

## Development releases

A push to `main` triggers `.github/workflows/android-release.yml`. It runs the unit tests, builds the debug APK, and creates a GitHub pre-release named with the source commit's short SHA (tag pattern `snapshot-<short-sha>`). New releases attach the APK as `lan-monitor-debug.apk`.

The first successful release, [`snapshot-d166a2f`](https://github.com/dimkabuki/lan-monitor/releases/tag/snapshot-d166a2f), contains the original asset name `app-debug.apk`; the workflow was subsequently updated to normalize the asset name for future releases. Existing published assets are not renamed retroactively.

These are development snapshots only, not production releases. A production release requires a deliberate versioning/signing policy and secure release-signing setup. Do not store signing keys or passwords in the repository.

## Local verification

With JDK 17 and Android SDK Platform 36 installed:

```sh
./gradlew --no-daemon :app:compileDebugKotlin :app:testDebugUnitTest
```

Use `:app:assembleDebug` additionally when a complete installable APK is needed.

## Remaining acceptance scope

M2 introduces the first attempt at direct LAN WebRTC media but does not yet prove or implement QR pairing, mDNS/DNS-SD rediscovery, background/screen-off monitoring, multiple Monitors, movement detection, or automatic reconnect after IP/Wi-Fi changes. Later milestones must add deterministic tests for state transitions, media-progress timeouts, retry/backoff, signaling validation, and synthetic-frame movement detection. Final acceptance must include both Samsung device role assignments, screen-off tests, and the specified three-hour runs.
