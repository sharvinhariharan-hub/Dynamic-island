# Dynamic Island

A local, native Android notification island. Kotlin, Jetpack Compose settings,
Android WindowManager overlay, MIT licensed. No accounts, servers, ads, analytics,
or internet permission. This independent project is not affiliated with Apple.

## Features

- Compact black pill, notification preview and expanded presentation with damped
  spring motion. Only the island rectangle receives touches.
- Explicit per-app opt-in, bounded FIFO queue, update deduplication and removal handling.
- App icon, title, preview, original PendingIntent, actions and free-form RemoteInput
  replies when provided by the originating app. Canceled intents fail gracefully.
- Tap opens, hold expands, swipe up dismisses the presentation, horizontal swipe
  cycles the queue. Outside touches collapse expanded content where Android delivers them.
- Native haptics: Off, Subtle, Standard; system touch-feedback setting is respected.
- Position, preview width, animation speed, duration and privacy settings in DataStore.
- No media controls, alarms, timers, calendars or reminder management. Notifications
  categorized as transport, alarm, event or reminder, ongoing notifications and group
  summaries are excluded.

## Compatibility

Android 10+ (API 29 minimum), compiled and targeted against Android 16 / API 36.
A display cutout is optional. Centered top cutouts inform positioning; corner cutouts,
foldables, OEM status bars and rotation can require manual offset adjustment. Android
controls which surfaces overlays may cover. Hardware behavior is not universally identical.

## Installation

Open [Actions](https://github.com/sharvinhariharan-hub/dynamic-island/actions)
→ **Android build → successful run → Artifacts → debug-apk** and unzip
the downloaded artifact. Install `app-debug.apk`, allowing installation from your chosen
file manager if requested. Debug artifacts are development builds, not production releases.
Signed production APKs appear under Releases only for version tags with configured signing.

1. Open Dynamic Island and grant **Display over other apps**.
2. Grant **Notification access** in the linked Android settings screen. Android may
   require Settings → App info → menu → Allow restricted settings for sideloaded apps.
3. Choose applications; none are selected by default. Enable Dynamic Island.
4. Use **Test island preview**, then receive a real notification from a selected app.
5. If your OEM stops the listener, review battery/background settings using the guidance
   in the app. No exemption is requested automatically.

## Permissions

| Access | Purpose |
| --- | --- |
| SYSTEM_ALERT_WINDOW | Small floating island using TYPE_APPLICATION_OVERLAY |
| Notification listener access | Read incoming selected notifications and exposed actions |
| VIBRATE | Short, optional interaction feedback |

`BIND_NOTIFICATION_LISTENER_SERVICE` protects the exported listener so only Android can
bind it. Launcher-app visibility is limited to enumerating selectable installed apps.
There is no AccessibilityService, internet permission, boot receiver, wake lock or
foreground service. Notification access is powerful: Android grants broad access even
though processing applies your opt-in filter.

## Build from source

Install JDK 17 (21 also works), Android SDK Platform 36, Build Tools 35.0.0 and platform-tools.
Set `ANDROID_HOME` or put `sdk.dir=/your/sdk` in untracked `local.properties`.

```sh
git clone https://github.com/sharvinhariharan-hub/dynamic-island.git
cd dynamic-island
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Gradle wrapper pins 8.13 and its distribution SHA-256. Development dependencies
require network access to Google Maven and Maven Central; the installed application
requires no network. Debug APKs use the local Android debug signing key.

## Release signing and CI

Every push/PR builds, runs unit tests and lint, and uploads a debug APK. Version tags
`v*` additionally build and publish a signed release **only when all signing secrets
exist**. Configure repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded release keystore
- `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`

Keep the same release key securely backed up for updates. The workflow decodes it into
runner temporary storage, verifies the APK signature and deletes it after use. No signing
credentials belong in Git. Missing secrets skip release publication; debug builds are
never substituted for production releases. Tag a reviewed, passing commit to release it.

## Privacy and Android limitations

- Notification content and action handles live only in a bounded in-memory queue;
  they are discarded on dismissal, removal, lock-protected screen-off, listener teardown,
  disable or process death. Settings alone persist, with Android app backup disabled.
- Secret notifications are dropped. Locked-device content and actions are always hidden;
  default settings hide the whole island while locked. The additional privacy toggle
  hides all notification text and actions even when unlocked. No content is logged.
- Sensitive-content redaction by Android (for example OTP protection) cannot and must not
  be bypassed. The listener sees only content Android exposes.
- System heads-up banners are not universally suppressed; both presentations can appear.
- Secure screens, permission dialogs, the lock screen and OEM policy may hide overlays.
  The top status-bar area may be untouchable or occluded by system UI.
- Expanded content takes focus for replies; a small compact window does not take keyboard
  focus. Outside-touch delivery depends on Android. It does not capture a full-screen layer.
- Swiping dismisses only the island, not the originating notification. A genuinely changed
  update may appear again. The queue holds at most 20 distinct notifications; oldest entries
  are evicted. Active expanded content is preserved while other arrivals wait.
- On listener reconnect or process restart only future notification events are presented;
  stale active notifications are deliberately not replayed. Android normally rebinds enabled
  listeners; force-stop requires reopening the app. Permission revocation is checked on
  events, settings changes and resume; Android also removes windows when permission is revoked.
- No polling or continuous animations. Haptic strength and cutout positioning need testing
  on actual target devices. Accessibility screen readers can use labeled content and buttons;
  device gesture behavior still needs manual verification.

## Architecture and testing

`MainActivity` / `SettingsViewModel` expose settings via StateFlow and Compose.
`IslandNotificationListener` serializes callbacks on main; `NotificationRepository`
parses content, `NotificationQueueManager` bounds and deduplicates it, and
`IslandStateMachine` controls presentation. `IslandOverlayController` owns finite
animations and gestures; `IslandWindowManager` owns exactly one native window;
`HapticEngine` owns optional vibration; `SettingsRepository` owns DataStore.

Tests cover filtering, queue eviction/cycling, duplicate updates, state transitions,
gesture thresholds, all permission combinations, idempotent window ownership and
settings persistence/reset. See [QA.md](QA.md) for actual verification and device checklist.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md) and
[CHANGELOG.md](CHANGELOG.md). Contributions are welcome under the MIT license.
