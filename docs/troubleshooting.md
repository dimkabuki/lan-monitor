# Troubleshooting

## Java missing or incompatible

Use JDK 17. CI configures Temurin JDK 17 explicitly. Check `java -version` and `JAVA_HOME`.

## Android SDK Platform 36 is missing

Install Android SDK Platform 36 through Android Studio SDK Manager or the command-line SDK manager. Do not commit `local.properties`.

## Wrapper cannot download Gradle

The wrapper downloads Gradle 8.13 from `services.gradle.org`. Verify network access to that host and retry. Do not replace the wrapper with a system Gradle version to hide a build failure.

## CI fails

Open the failing run linked from the pull request, inspect the job log and test reports, fix the cause on the working branch, and push the fix. Do not disable checks or mark failed tests successful.

## Current scaffold limitation

Camera and Monitor buttons only select a placeholder role. Streaming, permissions, pairing, notifications, and background behavior are not yet implemented.
