#!/usr/bin/env python3
"""Prepare public APK evidence for a human review request; never submit an appeal."""
import argparse
import json
from pathlib import Path
from verify_apk import inspect_apk


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('--apk', required=True)
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--apksigner', required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--download-url', required=True)
    parser.add_argument('--source-commit', required=True)
    args = parser.parse_args()
    report = inspect_apk(args.apk, args.aapt, args.apksigner)
    report.update({'download_url': args.download_url, 'source_commit': args.source_commit,
                   'purpose': 'Candidate appeal evidence; confirm these exact bytes were blocked on the device',
                   'device_model': 'Not provided', 'android_version_patch': 'Not provided',
                   'play_protect_version': 'Not provided', 'country_region': 'Not provided',
                   'installer_package': 'Not recorded; supplied screenshot shows the Downloads/file-manager installation flow',
                   'installation_date_time': 'Not provided', 'google_approval': 'Not established'})
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / 'artifact.json').write_text(json.dumps(report, indent=2) + '\n')
    pins = ', '.join(report['signer_certificate_sha256'])
    text = f'''# Draft Play Protect review/appeal request — {report['version_name']}

This is a preparation document, not a submitted appeal or confirmation of a false positive.
Confirm that the device blocked this exact APK SHA-256 before submitting. If another APK
was blocked, regenerate this evidence from those exact bytes. Do not attach a re-signed
or recompiled substitute under an old hash.

## Verified public artifact identity

- Package: `{report['package_name']}`
- Version: `{report['version_name']}` (code `{report['version_code']}`)
- APK SHA-256: `{report['apk_sha256']}`
- Signing certificate SHA-256: `{pins}`
- Debuggable: `{report['debuggable']}`; APK signature verification: `{report['signature_verified']}`
- Android: API {report['min_sdk']} minimum; target API {report['target_sdk']}
- Exact download: {args.download_url}
- Source commit: `{args.source_commit}`

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
'''
    (args.output_dir / 'APPEAL-DRAFT.md').write_text(text)
    print('Prepared public metadata and a human-review draft in', args.output_dir)


if __name__ == '__main__':
    main()
