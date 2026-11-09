# Draft Play Protect review/appeal request — 1.0.2

This is a preparation document, not a submitted appeal or confirmation of a false positive.
Confirm that the device blocked this exact APK SHA-256 before submitting. If another APK
was blocked, regenerate this evidence from those exact bytes. Do not attach a re-signed
or recompiled substitute under an old hash.

## Verified public artifact identity

- Package: `org.dynamicisland.app`
- Version: `1.0.2` (code `3`)
- APK SHA-256: `0a7d632a358d31f7c94e0fd4401f1467d7ee124c42ff149fccd184ee0bf34d44`
- Signing certificate SHA-256: `ce9286ba0873ad74c0849a55ed16b1a4757a27fd8944c109390a804ec0f74762`
- Debuggable: `True`; APK signature verification: `True`
- Android: API 29 minimum; target API 36
- Exact download: https://github.com/sharvinhariharan-hub/Dynamic-island/releases/download/preview-4-1/dynamic-island-debug.apk
- Source commit: `edead74cd512dd89dd240186323fb34160313f2a`

The certificate is a debug/development identity if marked so in artifact.json. It is
not the future production signing identity. A valid signature demonstrates integrity,
not safety or Google approval. artifact.json includes actual permissions, components
and public certificate details, without private signing material.

## Proposed explanation for the reviewer

This independent, MIT-licensed native Android notification utility draws a small island
using WindowManager and presents notifications from user-selected apps. It has no
accounts, server, analytics, ads, or internet permission. Notification data is transient
in local memory, never uploaded or logged. It starts disabled with no apps selected.

`SYSTEM_ALERT_WINDOW` is necessary to display the floating island. Notification access
is necessary to receive user-selected notifications and expose their original intents,
actions and supported inline replies. The listener service requires Android's
`BIND_NOTIFICATION_LISTENER_SERVICE` permission of its binder; the app does not request
that signature permission with uses-permission. Users explicitly grant notification
access in Android settings. `VIBRATE` is used for short, optional haptic feedback.
The AndroidX internal receiver permission is signature-protected; it is not access to
SMS, financial accounts, or external user data. The app has no AccessibilityService,
SMS, contacts, location, camera, microphone, or internet permission.

Please review the classification and confirm whether enhanced fraud-protection rules
for internet-sideloaded notification listeners explain the installation block. The
reported warning is consistent with that restriction; we cannot independently confirm
Google's internal verdict and do not assert that a false positive has been established.
We do not ask users to disable Play Protect or evade the restriction.

The repository contains later safety/remediation changes; the original APK's metadata
above remains unchanged. Review the exact artifact alongside its corresponding source,
not only the latest branch. Production release signing and device validation are separate
outstanding tasks, and changing signatures is not offered as a way around this block.

## Installation details to complete privately before submission

- Exact device model, Android version and security patch: NOT PROVIDED
- Play Store / Play services / Play Protect versions: NOT PROVIDED
- Country/region and installation timestamp: NOT PROVIDED
- Actual installer package and exact download route: NOT RECORDED
- Supplied screenshot: shows Downloads/file manager and Play Protect's sensitive-data warning
- Exact on-device APK SHA-256 matches the candidate above: NOT YET CONFIRMED
- Screenshot or screen recording with personal content redacted: maintainer to provide
- Whether Google has returned an explicit malware-family verdict: NOT PROVIDED

Do not invent these details or upload private notifications, OTPs, accounts or keys.
Use Google's official form: https://support.google.com/googleplay/android-developer/contact/protectappeals
No appeal has been submitted by this script.
