# Play Protect review/appeal evidence

Use the metadata for the **exact APK bytes blocked on the device**, not a newly compiled
or re-signed substitute. The original screenshot did not include a file hash, Android
version, model, patch level, installer package, region or Play Protect version. Confirm
those details privately before submitting; do not invent them.

- [1.0.1 / preview-2-1 candidate](preview-2-1/APPEAL-DRAFT.md): original installation report context.
- [1.0.2 / preview-4-1 candidate](preview-4-1/APPEAL-DRAFT.md): subsequently shared hardened debug build.

Each folder includes `artifact.json`: package/version, APK SHA-256, public signing
certificate SHA-256, verified signature details, actual permissions, and components.
The per-build debug certificates differ. Neither is the future production identity.

Regenerate evidence from any exact signed APK using only Android build tools and Python:

```sh
python3 scripts/prepare_appeal.py --apk /path/to/exact.apk \
  --aapt "$ANDROID_HOME/build-tools/35.0.0/aapt" \
  --apksigner "$ANDROID_HOME/build-tools/35.0.0/apksigner" \
  --output-dir /tmp/appeal-evidence \
  --download-url https://your-exact-artifact-url \
  --source-commit YOUR_EXACT_SOURCE_COMMIT
```

The script does not upload or submit anything. Complete the missing installation facts
and use [Google's official review/appeal form](https://support.google.com/googleplay/android-developer/contact/protectappeals).
Do not include notification contents, OTPs, private keys or credentials. Google alone can
confirm the classification or approve an installation. See [the audit](../security/AUDIT.md)
and [SAFETY.md](../../SAFETY.md) for the platform restriction versus code-defect distinction.
