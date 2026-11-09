# Quality assurance

## Automated coverage

- Explicit app opt-in, own-package, disabled, category, ongoing and summary filtering.
- Stable content fingerprints, changed updates, transient storage and secret visibility.
- Bounded FIFO queue, cycling, deduplication after dismissal, removal and pinned eviction.
- Compact/preview/expanded transitions and swipe/hold/tap classification thresholds.
- All enable/overlay/listener permission combinations.
- Idempotent window ownership, actual Robolectric window attach/detach and revocation cleanup.
- DataStore encode/decode bounds, disk persistence across repository instances and reset.
- RemoteInput preservation, reply dispatch and canceled PendingIntent failure.

## Version 1.0.3 release/security audit (2026-11-09)

Both debug and R8-optimized/resource-shrunk release APKs compile successfully. All 22
Kotlin/JUnit/Robolectric tests pass in each variant (44 executions), and debug/release
lint pass. R8 produces a mapping file and retains the framework manifest entry points.
No device/emulator is available to validate actual optimized startup or hardware behavior.

All 10 Python APK-policy tests pass normally and under `python -O`; the release checks
use explicit failures, not removable assertions. Real optimized-APK tests sign temporary
copies with one-day disposable identities and accept only the correct certificate pin;
debug certificates, missing pins and incorrect pins fail. Fixture keys/APKs are deleted.
Both packaged variants pass actual permission/component/backup/cleartext inspection;
the unused profiling receiver is absent. Release remains unsigned without managed keys.

108 resolved runtime Maven coordinates were checked against OSV, with no advisory match
returned; this does not establish absence of vulnerabilities. See docs/security/AUDIT.md,
the dependency inventory and point-in-time advisory results. Exact old APK hashes and
public signing certificates are in docs/play-protect/ for human appeal preparation.
Google approval and an on-device installation success are not claimed.

## Version 1.0.2 security validation (2026-11-09)

The safety-hardening build compiles in debug and release variants. All 21 tests pass,
including foreign-PendingIntent rejection, lock-state changes between rendering and
dispatch, and fully/partially obscured permission-control and island-action touches. Android lint passes.
The packaged APK passes the permission allowlist, explicit no-backup/no-cleartext and
exported-component protection checks. Production mode rejects the debug APK as expected. A temporary one-day test certificate
verified that the production gate accepts an exact public certificate pin and rejects
missing/mismatched pins; the fixture key and APK were then deleted. This did not create
a production signing identity.
An unsigned release APK is checked only with the explicit local `--unsigned` mode; it
cannot be used with production verification or published by the release workflow.

A user reported Play Protect blocking the 1.0.1 preview. These changes do not demonstrate
that Google's block has been removed. No signed production identity, Google approval,
or physical-device certification has been established. Read SAFETY.md before use.

## Version 1.0.1 validation (2026-11-09)

Debug/release compilation, all 17 JUnit/Robolectric tests and Android lint passed.
The additional tests exercise real overlay-controller expiry with virtual time:
waiting notifications cannot starve the current preview, while changed current
content receives a fresh preview duration. The debug APK signature was verified,
and the manifest reports versionCode 2 / versionName 1.0.1, API 29 minimum and API 36 target.

The workflow YAML and embedded Bash were syntax-checked locally. Direct-download
prereleases are gated on a passing build, restricted to main-branch push/manual events,
and explicitly marked as debug prereleases. Remote Actions execution and public
asset download cannot be verified until GitHub repository creation/access is available.
The configured integration currently rejects repository creation with HTTP 403.

No physical device was connected in this validation session. Visual motion, keyboard
behavior, cutouts and vibration still require the manual checks below.

## Version 1.0.0 recorded local results (2026-11-09)

`assembleDebug assembleRelease testDebugUnitTest lintDebug` completed successfully
on the final source. All 15 tests passed. Android lint reported 0 errors and 15
warnings; the checked-in code uses pinned stable dependencies, English-only UI,
and compatibility APIs for Android 10. Reports are available in the Actions
`quality-reports` artifact. Release compilation produced an unsigned APK because
no production signing credentials were supplied; it is not distributed as a release.
The debug APK passed `apksigner verify` (APK Signature Scheme v2). Manifest inspection
confirmed minimum API 29, target API 36 and no INTERNET permission.

## Environment

Validation uses Linux x86_64, Azul OpenJDK 21, Gradle 8.13, Android SDK 36,
Build Tools 35.0.0, JUnit 4 and Robolectric at API 29. Robolectric simulates Android;
it does not verify system UI composition, actual vibration or OEM restrictions.

No physical device was connected (`adb devices` returned an empty list). The environment
has no installed emulator or `/dev/kvm`. Installation and interactive hardware smoke
tests were not performed. Do not interpret unit-test success as hardware certification.

## Required manual device checks

1. Fresh install on Android 10 and a current Android release. Verify both permission
   denial paths, grant access, select an app and enable. Check test preview.
2. Receive selected/unselected app notifications. Confirm icon/title/text, repeated
   identical updates, changed updates and multiple arrivals without duplicate windows.
3. Tap, hold, swipe up, swipe horizontally and tap outside expanded content. Check
   touches outside the island still reach the underlying app and system status bar.
4. Exercise original notification intent, regular actions and inline reply. Remove the
   original notification while expanded; cancel an action intent. Verify graceful failure.
5. Lock/unlock and switch screen off during preview, expansion and reply entry. Verify
   no title, text or action is exposed while locked, including with hide-on-lock disabled.
6. Revoke each permission, disable, reset, rotate, change resolution/font scaling,
   force-stop and reopen. Confirm no leaked window or stale notification replay.
7. Check centered/corner/no cutouts, landscape, foldable posture and manual alignment.
8. Try each haptic mode and disable system touch feedback. Check no vibration on
   unrelated outside taps and no sustained vibration during notification bursts.
9. Review battery behavior and listener reconnection on OEM firmware. Verify no network
   permission, content logs, analytics or notification content in persistent storage.
