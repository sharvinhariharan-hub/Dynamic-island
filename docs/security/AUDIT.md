# Android security and distribution audit — 1.0.3 (2026-11-09)

## Finding: reported installation warning

The supplied screenshot reports a Play Protect sensitive-data/financial-fraud installation
block. It does not identify a malware family, prove a specific code vulnerability, or
establish that debug signing alone caused the block.

Google's [2024 enhanced-fraud-protection announcement](https://security.googleblog.com/2024/02/piloting-new-ways-to-protect-Android-users-from%20financial-fraud.html)
explicitly names notification binding (`BIND_Notifications` in that announcement) among
sensitive capabilities inspected when installing from browsers, messaging apps and file
managers. Its [2026 ecosystem report](https://security.googleblog.com/2026/02/keeping-google-play-android-app-ecosystem-safe-2025.html)
confirms broader deployment of this protection. This application's essential notification
listener and the reported GitHub/Downloads installation route are consistent with that
policy restriction. Google's exact classification and the device's enforcement state
are not available to this audit. Do not call a false positive confirmed or change package
names, signatures, permissions, or installation routes to evade the protection.

## Scope and observed boundaries

Reviewed every application Kotlin source file, source and merged manifests, runtime
library inventory, Gradle and shrinker configuration, both APK variants, listener and
window lifecycle, notification queue/content handling, permission disclosures, pending
intents, gesture/touch processing, haptics, persistent settings, signing/release scripts,
Git exclusions, and the actual published APKs used for appeal evidence.

| Area | Result |
| --- | --- |
| Required access | `SYSTEM_ALERT_WINDOW` and optional haptics `VIBRATE`; Android grants notification access separately |
| Listener | Only app service; protected by Android's signature-level `BIND_NOTIFICATION_LISTENER_SERVICE` binder requirement |
| Dangerous/unrelated access | No SMS, contacts, call-log, location, camera, microphone, internet, accessibility, or broad package-visibility permission |
| Exported app entry points | Launcher activity and protected notification listener only |
| Internal provider | AndroidX Startup provider remains non-exported |
| Background execution | Android-managed listener, event-driven callbacks; no boot receiver, polling, wake lock, foreground service, alarm or media service |
| Notification storage | At most 20 in-memory queue entries; removed on dismissal/removal/disable/teardown/screen-off; no payload saved or logged |
| Settings | DataStore stores preferences and selected package names, not notification contents; backup and device-transfer exclusions enabled |
| Network | No app internet permission, telemetry, account, endpoint, ad SDK, remote code loading or WebView |
| Actions/replies | User gesture required; creator package, device lock and current grants checked at dispatch |
| Touch safety | Fully/partially obscured settings and overlay input rejected |

The AndroidX signature-protected `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is an internal
compatibility permission, not access to another app's sensitive information. The listener
binder permission is a restriction on who may bind the service, not a uses-permission
request by this app. Broad notification access itself is still sensitive and may expose
private messages/OTPs before app-level filtering. The disclosures say so explicitly.

## Changes in this audit

- Enabled R8 optimization and resource shrinking for a non-debuggable release variant.
  Framework entry points and library consumers use AGP/AndroidX keep rules; no blanket
  keep or dontwarn rules hide shrinker errors. Mapping generation is checked in CI.
- Removed the unused AndroidX adb diagnostic `ProfileInstallReceiver`. The useful profile
  installer initializer remains for startup performance. A narrowly scoped MissingClass
  lint suppression applies only to the tools:node removal marker, never a shipped class.
- Added dispatch-time access checks to close permission-revocation races after rendering.
  Overlay, notification presentation, controls, animations, gestures, haptics and the
  legitimate notification-listener background lifecycle remain implemented.
- Replaced assertion-based release checks with explicit failures that survive `python -O`.
- Check actual packaged permissions, components, backup/cleartext policy, SDK/package
  metadata, version-tag agreement, modern signature schemes and the trusted certificate pin.
- Reject missing/mismatched release identity, debug signing, debuggable or test-only APKs,
  and unsigned production artifacts. Gradle rejects partial signing configuration.
- Restricted keystore/password environment values to signing-related CI steps. Keystore
  files are created with umask 077, removed via an EXIT trap, and never committed. Release
  checksums use the downloadable basename; public JSON inspection reports are included.
- Added gate unit tests, optimized-Python denial tests, and real-APK signing integration
  tests with one-day disposable fixture keys that are deleted and never distributed.
- Prepared reproducible, public appeal metadata for the exact published 1.0.1 and 1.0.2
  artifacts. Unknown device/installer details remain explicitly unfilled.

## Dependency assessment

`runtime-dependencies.json` records 108 resolved public Maven coordinates (including
platform/BOM and multiplatform variants) for this build. They consist of AndroidX UI,
lifecycle, DataStore and startup components; Kotlin/coroutines; annotations and utility
libraries such as Guava/Okio. No ad, analytics, accessibility, dynamic-code or network-client
SDK was identified. Okio is an I/O dependency and is not itself an HTTP client.

These exact public coordinates were queried against OSV on 2026-11-09. No advisory match
was returned; see `dependency-advisories.json`. No source, notification, credential or
personal device data was sent. This is a point-in-time advisory check, not proof that
all libraries are vulnerability-free. Build/test tools are separate from runtime libraries.

## Validation and remaining limits

Debug and optimized unsigned-release compilation, both unit-test variants, debug/release
lint and packaged-APK inspection are required checks. The Kotlin suite has 22 tests per
variant; the Python gate has 10 cases, also run under `python -O`. Actual release-gate
acceptance/rejection is exercised with temporary signing identities and a real optimized
APK. These identities do not establish a production signing identity.

No managed production signing credentials are configured in this execution environment.
The GitHub integration cannot enumerate repository signing secrets (HTTP 403), so no claim
is made about unseen repository settings. No production version tag is created and no
production APK is distributed by this audit. The unsigned optimized APK is not installable
and must not be advertised as a signed release. Existing debug APKs remain development
artifacts, with the reported Play Protect issue disclosed.

ADB reports no connected device; no emulator is installed. On-device R8 startup, camera
cutout placement, keyboard/gestures, haptics, OEM behavior and the actual Play Protect
installation decision remain unverified. Before daily-use distribution, provision a
securely managed release identity, perform QA.md's device checks, and complete applicable
Google Play review or the official Play Protect review/appeal process. A new signature,
R8, source audit or successful CI does not guarantee removal of Google's block.
