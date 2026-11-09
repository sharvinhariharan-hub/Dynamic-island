# Contributing

Open an issue describing the device, Android version, reproduction steps and expected
behavior. Never attach private notification content, OTPs or signing files.

Use JDK 17+ and SDK 36. Run `./gradlew assembleDebug testDebugUnitTest lintDebug`
before submitting a focused pull request. Add meaningful tests for behavior changes.
Manually check the device flows in QA.md when modifying overlays or gestures.
Keep notification processing local, minimize permissions and preserve opt-in filtering.
Do not add accessibility workarounds or features outside notification presentation.
Contributions are licensed under MIT. Be respectful and constructive in reviews.
