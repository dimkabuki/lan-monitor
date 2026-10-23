# Testing

## Pull-request CI

The required status-check context is `build-and-test` from the **Android CI** workflow.

For each pull request targeting `main`, CI compiles the app's debug Kotlin code with `:app:compileDebugKotlin`, runs `:app:testDebugUnitTest`, and uploads unit-test reports for 14 days. The PR workflow does not build or publish a downloadable APK.

## Build an APK on demand

In GitHub, open **Actions → Android CI → Run workflow** and enable **Build and upload a debug APK**. The workflow runs the same compile/tests first; if they pass, the separate `build-apk` job builds `:app:assembleDebug` and uploads `lan-monitor-debug-apk` as an artifact for 14 days.

The APK is a debug build and is not production-signed.

## Development releases

A push to `main` triggers `.github/workflows/android-release.yml`. It runs the unit tests, builds the debug APK, and creates a GitHub pre-release named with the source commit's short SHA (tag pattern `snapshot-<short-sha>`). The release contains `lan-monitor-debug.apk`.

These are development snapshots only, not production releases. A production release requires a deliberate versioning/signing policy and secure release-signing setup. Do not store signing keys or passwords in the repository.

## Local verification

With JDK 17 and Android SDK Platform 36 installed:

```sh
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

Current unit coverage checks the English role labels. It does not test media, networking, background lifecycle, movement detection, or reconnect behavior because those features are not implemented.

## Acceptance scope

No emulator or physical-device acceptance test is claimed by M1. Later milestones must add deterministic tests for state transitions, media-progress timeouts, retry/backoff, signaling validation, and synthetic-frame movement detection. Final acceptance must include both Samsung device role assignments, screen-off tests, and the specified three-hour runs.
