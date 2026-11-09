# Safety, sensitive access, and production readiness

**Do not disable Play Protect or bypass an installation block to use this project.**
A user reported Play Protect blocking the 1.0.1 debug APK. The screenshot identifies
sensitive-data access as a risk; it does not establish a specific malware finding or
prove that debug signing alone caused the block. Build success, source availability,
a checksum, and a valid signature do not establish Google approval or complete safety.

## Why this app is sensitive

A notification island needs `SYSTEM_ALERT_WINDOW` and user-granted notification-listener
access. Android grants the listener broad access, potentially including private messages
and verification codes; per-app filtering happens inside this app after Android grants
access. The overlay can draw interactive UI over other apps. These capabilities cannot
be renamed, hidden, or removed while retaining the same functionality.

The app starts disabled with no applications selected. Grant access only after reading
the disclosure. Avoid enabling banking, password-manager, and authentication apps.
Revoke grants in Android settings and uninstall if you do not trust the app. The app
cannot promise to recognize and remove every OTP or sensitive message. Android's own
sensitive-content redaction is respected; no accessibility or restriction bypass exists.

## Implemented boundaries

- No internet permission, telemetry, ads, remote configuration, SMS, call-log, contacts,
  location, camera, microphone, accessibility service, wake locks, or foreground service.
- Only incoming notifications from explicitly selected apps are eligible. Secret,
  ongoing, group-summary, media, alarm, event and reminder notifications are excluded.
- Processing stops before parsing notification content when disabled, disconnected,
  screen-off, missing a runtime grant, or locked with default hide-on-lock enabled.
- Notification content is transient in memory. Settings alone persist; backup/device
  transfer exclusions are configured. No private content is logged.
- Locked-device text and actions are hidden. Device lock is checked again immediately
  before opening an intent or sending a reply/action, including a lock race after rendering.
- Only PendingIntents owned by the notification's originating package are accepted;
  ownership is checked again at dispatch. Delegated cross-app actions may be unavailable.
- Actions require a direct user gesture. On Android 16 the sender opts into background
  activity launch only when visible; platform/creator restrictions remain authoritative.
- Permission/settings controls and island actions reject fully or partially obscured touch events.
- The overlay occupies a bounded rectangle, releases its window when disabled or revoked,
  and does not request system-wide input capture. It may be hidden on protected screens.

## Automated checks and their limits

`scripts/verify_apk.py` checks the **packaged** APK permission allowlist, SDK boundary,
backup/cleartext flags and exported-component protections. It verifies APK signatures;
production mode rejects debuggable APKs and Android Debug certificates and requires
an exact match with the trusted public `ANDROID_SIGNING_CERT_SHA256` repository variable. CI runs it before
artifact upload and production release publication. Production releases also include
SHA-256 checksums and public signer-certificate details. A signature establishes identity
and integrity, not whether the app is safe or whether Play Protect will allow installation.

JUnit/Robolectric tests cover notification filtering, intent ownership, lock races,
obscured settings touches, queue timing, permissions, settings persistence and cleanup.
These checks do not replace independent review or testing on actual devices.

## Distribution policy

Automatic public debug prerelease publication is disabled. Debug APKs remain developer
CI artifacts. Publishing a debug prerelease requires an explicit manual workflow input
and carries a warning; it is not a recommended daily-use installation path. The previously
published 1.0.1 preview remains identifiable as a debug prerelease with an advisory.

Production signing uses repository secrets, never committed keys. No production APK is
published when signing credentials are absent. A release certificate must be managed and
backed up securely; do not put signing secrets in chat. This environment cannot inspect
repository signing-secret configuration with its current GitHub integration permissions.
No production signing identity or Google review is established by this change.

Before recommending daily use, the maintainer must:

1. Configure a securely managed release signing identity and validate signed update paths.
2. Exercise the manual device checklist in QA.md on Android 10 and current supported
   versions, including lock/unlock races, permission revocation, financial/secure app
   screens, keyboard replies, cutouts, rotation, battery behavior and accessibility.
3. Obtain independent security/privacy review proportionate to these sensitive grants.
4. Complete the applicable Google Play policy declarations/review for Play distribution,
   or use Google's official review/appeal process for an erroneous Play Protect finding.
   Google makes the decision; changing signing keys or installation sources is not a fix
   for a security block and approval is not guaranteed.

## Official guidance

- [How Google Play Protect works](https://support.google.com/googleplay/answer/2812853)
- [Google Play Protect for developers](https://developers.google.com/android/play-protect)
- [Potentially harmful applications](https://developers.google.com/android/play-protect/potentially-harmful-applications)
- [Google Play Protect appeals](https://support.google.com/googleplay/android-developer/contact/protectappeals)

Do not submit private notification contents, signing keys, or personal device data in
public reports. See SECURITY.md for reporting security concerns.
