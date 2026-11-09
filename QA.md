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
