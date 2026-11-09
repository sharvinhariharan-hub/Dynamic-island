# What the island reads, retains, and discards (1.0.4)

Displaying text from another app requires Android notification-listener access. Android
does not provide a separate permission that lets a third-party overlay display that text
without receiving notification data. This app does not request notification history from
a server or replay active-notification history. Android delivers incoming events after
the user explicitly grants access. That grant is broad even when this app filters events.

## Audit finding

No notification-payload file, database, log or network upload path was found. DataStore
serializes settings only: enabled state, selected package names, offsets, width, duration,
animation speed, haptics and privacy toggles. The application has no INTERNET permission;
packaged-APK checks enforce that boundary. Replies are sent only to the originating
application's exposed PendingIntent on a direct user action; that app handles its own
normal messaging behavior and is outside this app's network boundary.

A temporary memory queue is necessary for visible previews, actions and swiping between
notifications. Previously the privacy toggle hid text only at render time, and expansion
paused the preview timeout indefinitely. These were minimization/retention gaps, not
proof of disk storage, exfiltration, fraud, or the cause of Google's installation block.

## Current data path

1. Android invokes the notification listener on main (guaranteed on all supported API 29+
   devices). There is no second Handler queue holding raw StatusBarNotification objects.
2. Runtime grants, enabled state, screen/lock policy, selected package, category, ongoing
   flags and secret visibility are checked before eligible content processing.
3. With **Metadata-only previews** enabled, or while locked, the app does not extract title,
   body or notification actions and does not hash hidden text. It retains only identifiers,
   app icon, visibility, a generic label and an allowed opaque content-intent handle.
   Android still supplies the raw callback object: the app cannot narrow the OS grant.
4. Otherwise, a bounded copy of the text and valid action handles is used in RAM for the
   live island. Only the two displayed action labels/handles are retained. Inline replies
   retain only required result keys for text input, not unused labels, suggestions or extras. No raw Notification, Bundle or StatusBarNotification is retained afterward.
5. Each retained key has a one-minute deadline from its first retention in the current live queue session.
   Updates do not extend it. One event-driven expiry timer covers the nearest deadline;
   there is no polling or wake lock. Normal preview dismissal usually discards data sooner.
6. Expiry, removal, dismissal, disable, permission loss, listener teardown and screen-off
   drop the associated application references. Turning metadata-only mode on or entering
   the lock screen also purges existing payloads. Turning privacy off does not recover
   old text; a new eligible notification event is needed.
7. Old text-view contents, reply drafts, accessibility descriptions and child click
   listeners are cleared when replaced/discarded. Even expanded content expires. Fresh
   queued notifications retain their own deadlines and are not erased with older entries.

## Retention boundaries

| Data | App retention |
| --- | --- |
| Notification titles, bodies, action labels/handles, reply drafts | In-memory live UI/model only; removed on dismissal/cleanup or one-minute deadline |
| Notification keys and deduplication fingerprints | Bounded RAM metadata (at most 200 dedup entries), cleared on removal/reset/process death; no payload history on disk |
| Selected app package names and UI preferences | DataStore in private app storage; backup/device transfer excluded |
| User's system notification/history and originating app data | Controlled by Android/originating app; island dismissal does not erase them |

After a deadline, the repository no longer returns an expired item. Cleanup runs when
the process can execute; a suspended/frozen process cannot run code at an exact wall-clock
instant. Kotlin/Android use garbage-collected immutable strings: clearing application
references is not a promise of immediate byte-level erasure from RAM, OS snapshots or
another process. No permanent notification history is created by this app.

The island opts out of automatic autofill and, on Android 11+ where the public setter
is available, automatic content capture. Reply fields
request no personalized IME learning; a keyboard's compliance and user-enabled accessibility
services, screenshots, Android notification history, or a compromised device are outside
the app's control. These flags do not replace the system permission disclosures.

## Verification

Tests use a title/body that throws if converted to text in hidden/locked mode; a private
app-files snapshot remains unchanged across notification parsing, queuing and cleanup;
virtual time verifies expiry and that updates cannot extend retention; a held old text
view is empty after expiry; tightening privacy purges retained content; window tests
verify content-capture/autofill opt-out. Settings persistence is tested independently.

These findings do not establish that Play Protect approved the app. Required notification
access remains in the manifest, and Google's sensitive-permission sideloading policy can
apply regardless of this app's retention behavior. Keep Play Protect enabled. See SAFETY.md
and docs/security/AUDIT.md for the installation restriction and production limitations.
