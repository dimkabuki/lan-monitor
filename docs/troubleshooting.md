# Troubleshooting

## Collect app diagnostics from both phones

No laptop or Android Studio is required. Lan Monitor can write a diagnostic text file to the phone's public Downloads folder when explicitly enabled.

1. Install the same recent development APK on both phones and open Lan Monitor.
2. On the Camera phone, choose **Camera**, enable **Diagnostic logging**, then tap **Start Camera**.
3. On the Monitor phone, choose **Monitor**, enable **Diagnostic logging**, scan the Camera's QR, and attempt to connect. Leave it retrying long enough to capture several attempts (about 45-60 seconds is usually sufficient).
4. Stop the session on both phones.
5. On each phone, turn **Diagnostic logging** off. The status should show the saved file location.
6. Open **My Files → Downloads → Lan Monitor** and share the corresponding `lan-monitor-camera-*.txt` and `lan-monitor-monitor-*.txt` files for analysis.

Logging is off by default and only runs while the switch is on. A separate file is created for each enabled capture and each file is capped at 2 MiB. Each line is written and closed immediately, so the file remains usable after an app crash. If the app reports that it could not create or write the file, check free storage and retry.

The log contains timestamps, app role, pairing-flow milestones, local discovery status/error codes, private LAN addresses, WebSocket connection failures, WebRTC signaling/ICE state transitions, decoded-video progress and retry intervals. It does **not** intentionally record pairing tokens, QR contents, SDP descriptions, full ICE candidate payloads, audio, or video. Review the logs before sharing them; LAN addresses and device/network diagnostic information may still be sensitive. Do not post the logs publicly.

To reproduce a connection issue, restart the Camera session first to generate a fresh pairing token, then enable logging and follow the steps above. Do not share screenshots containing a live pairing QR or temporary token.

## Java missing or incompatible

Use JDK 17. CI configures Temurin JDK 17 explicitly. Check `java -version` and `JAVA_HOME`.

## Android SDK Platform 36 is missing

Install Android SDK Platform 36 through Android Studio SDK Manager or the command-line SDK manager. Do not commit `local.properties`.

## Wrapper cannot download Gradle

Use the pinned Gradle Wrapper version in `gradle/wrapper/gradle-wrapper.properties`. Verify network access to the distribution host and retry. Do not replace the wrapper with a system Gradle version to hide a build failure.

## CI fails

Open the failing run linked from the pull request, inspect the job log and test reports, fix the cause on the working branch, and push the fix. Do not disable checks or mark failed tests successful.
