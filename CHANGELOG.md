# Changelog

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
