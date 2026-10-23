# Architecture

## M1 scaffold

The initial app is a single native Android application using Kotlin and Jetpack Compose. `MainActivity` renders a role-selection placeholder, and `AppRole` contains the labels Camera and Monitor. The buttons do not start capture, networking, or monitoring yet.

## Pinned build baseline

| Component | Version / value |
|---|---|
| JDK | 17 |
| Gradle Wrapper | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin Gradle plugin | 2.4.21 |
| Compose BOM | 2026.06.01 |
| Activity Compose | 1.13.0 |
| compileSdk / targetSdk | 36 |
| minSdk | 33 |
| Namespace / application ID | `com.dimkabuki.lanmonitor` |

Re-check official compatibility tables before updating pinned versions. The target is API 36; targeting API 37 or later requires implementing and testing the version-gated local-network permission flow in the technical specification.

## Intended runtime boundaries

Future milestones are expected to introduce direct WebRTC media on the LAN, Camera-hosted local signaling, local discovery, per-Monitor peer connections (up to two Monitors), foreground-service lifecycle handling, truthful media health, and local frame-difference movement detection.

No runtime WebRTC, camera/microphone permission, discovery, or network service has been added in M1. Do not add cloud dependencies, STUN/TURN, external signaling, analytics, telemetry, or persistent media recording.
