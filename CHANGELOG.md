# Changelog

## 1.0.4

- Make metadata-only/locked previews skip title, body and action extraction entirely.
- Purge already retained payloads when privacy tightens or the device locks.
- Bound live payload retention to one minute per key, independent of expansion and updates.
- Scrub replaced text views, reply drafts, descriptions and child click handlers.
- Remove redundant raw-notification Handler queuing on supported Android versions.
- Opt the overlay out of automatic content capture/autofill; request no personalized IME learning.
- Add data-flow, no-disk-write, hidden-payload, timed-expiry and view-cleanup regression tests.

## 1.0.3

- Enable R8/resource shrinking for non-debuggable releases and reject partial signing configuration.
- Remove the unused exported profiling diagnostic receiver; retain AndroidX startup optimization.
- Recheck overlay/notification grants at action dispatch after permission revocation.
- Replace Python assertions in production gates with checks that remain active under optimization.
- Add security gate and real-signature fixture tests, packaged-APK JSON reports, and version-tag checks.
- Limit CI signing secrets to signing steps and correct downloadable release checksum filenames.
- Publish dependency audit results and exact-artifact metadata/drafts for official Play Protect review.
- Keep required notification/overlay access and all core notification features; no protection bypass.

## 1.0.2

- Disclose sensitive grants before opening Android permission settings.
- Reject obscured settings touches and foreign notification/action PendingIntents.
- Recheck device lock at dispatch and runtime permission state before parsing content.
- Restrict Android 16 activity-start opt-in to visible senders.
- Add packaged-APK permission/component checks and production debug-key/debuggable rejection.
- Stop automatic public debug previews and document the reported Play Protect block.
- Require release signing, device validation and applicable Google review before recommending daily use.

## 1.0.1

- Clarify phone installation and publish explicitly labeled development APK prereleases after successful main builds.
- Add setup status cards, an island illustration, full-row accessible switches and empty app-search feedback.
- Refine notification text, action buttons and queued-notification navigation indicators.
- Prevent waiting arrivals from indefinitely postponing visible notification expiry.
- Avoid restarting animations for unchanged dimensions and reset animation geometry after teardown.
- Clear stale accessibility descriptions and preserve active reply focus on outside touches.

## 1.0.0

- Native notification island with three states, finite spring motion and gestures.
- Opt-in application filtering, bounded queue, deduplication and removal handling.
- Notification intents, actions and supported inline replies.
- DataStore settings, privacy defaults and optional native haptics.
- Automated build, lint, unit tests and optional signed tag releases.
