# Agent Progress

## Current milestone: M1 - Android scaffold and CI

- **Working branch:** `chore/bootstrap-ci`
- **Pull request:** [#1 - chore: bootstrap Android scaffold and CI](https://github.com/dimkabuki/lan-monitor/pull/1), open and not merged.
- **Latest completed CI run previously verified:** [Run #18](https://github.com/dimkabuki/lan-monitor/actions/runs/40024795683) passed on commit `fb8817322f71eafddbd162e7f3f7793410231ec2`. It successfully completed Android SDK setup, Android platform installation, Gradle setup, `:app:assembleDebug`, `:app:testDebugUnitTest`, test-report upload, and debug APK upload.
- **Workflow triggers:** `.github/workflows/android-ci.yml` runs for pull requests targeting `main` and supports manual `workflow_dispatch`. It does not run automatically for pushes to branches without a matching open PR.
- **Repository governance — verified:** default branch is `main`; active repository ruleset [Protect main](https://github.com/dimkabuki/lan-monitor/rules/25756637) targets the default branch, requires a pull request and the `build-and-test` status check, blocks deletion and force pushes, has no bypass actors, and permits squash merging only. Repository settings also allow squash merge and disable merge commits, rebase merging, and auto-merge. The status-check rule does not require the branch to be up to date with `main`.
- **Completed in M1:** Kotlin/Jetpack Compose role-selection placeholder; application ID `com.dimkabuki.lanmonitor`; Gradle wrapper and pinned build configuration; unit test; GitHub Actions build/test workflow and conditional APK/test-report artifacts; README, architecture/testing/troubleshooting docs, and PR template; default-branch and merge-protection configuration.
- **Not included in M1:** runtime WebRTC, camera/microphone capture, LAN discovery, signaling, motion detection, or physical-device validation. No physical-device tests are claimed.
- **CI gate:** `build-and-test` must be green for the exact current PR head before merge. Use the live status on [PR #1](https://github.com/dimkabuki/lan-monitor/pull/1) as the source of truth; an earlier successful run does not validate a newer commit.
- **Remaining M1 action:** once the latest PR-head CI check is green, the repository owner can squash-merge PR #1. Do not merge it automatically.
- **Next milestone after M1 is merged:** start a new feature branch from the updated `main` and draft `docs/technical-spec.md` for M2. Define connection/pairing flow, local-only signaling and discovery boundaries, permissions and lifecycle behavior, error states, privacy constraints, and acceptance tests before implementing runtime networking or media.
