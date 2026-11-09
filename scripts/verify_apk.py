#!/usr/bin/env python3
"""Inspect packaged APKs and enforce release boundaries. Not Play Protect certification."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

PACKAGE = 'org.dynamicisland.app'
ALLOWED_PERMISSIONS = {
    'android.permission.SYSTEM_ALERT_WINDOW', 'android.permission.VIBRATE',
    PACKAGE + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION',
}
MAIN = PACKAGE + '.MainActivity'
LISTENER = PACKAGE + '.IslandNotificationListener'
BIND = 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE'


class VerificationError(ValueError):
    pass


def require(condition, message):
    # Never use assert for a release gate: python -O disables assertions.
    if not condition:
        raise VerificationError(message)


def dump(tool, *args):
    return subprocess.check_output([tool, *map(str, args)], text=True)


def nodes(xml):
    result, stack = [], []
    for line in xml.splitlines():
        element = re.match(r'(\s*)E: ([\w-]+)', line)
        if element:
            depth = len(element[1])
            while stack and stack[-1][0] >= depth:
                stack.pop()
            node = {'tag': element[2], 'attrs': {}}
            result.append(node)
            stack.append((depth, node))
        attr = re.match(r'\s*A: (android:\w+)(?:\([^)]*\))?=(.*)', line)
        if attr and stack:
            stack[-1][1]['attrs'][attr[1]] = attr[2]
    return result


def quoted(value):
    match = re.match(r'"([^"]*)"', value)
    return match[1] if match else None


def boolean(value):
    if value == '(type 0x12)0x0':
        return False
    if value == '(type 0x12)0xffffffff':
        return True
    return None


def inspect_apk(apk, aapt, apksigner, unsigned=False):
    apk = Path(apk).resolve()
    require(apk.is_file(), 'APK file not found')
    badging = dump(aapt, 'dump', 'badging', apk)
    package_line = next((s for s in badging.splitlines() if s.startswith('package: ')), '')
    package = dict(re.findall(r"(\w+)='([^']*)'", package_line))
    min_sdk = re.search(r"sdkVersion:'(\d+)'", badging)
    target_sdk = re.search(r"targetSdkVersion:'(\d+)'", badging)
    require(min_sdk and target_sdk, 'Missing APK SDK metadata')
    elements = nodes(dump(aapt, 'dump', 'xmltree', apk, 'AndroidManifest.xml'))
    app = next((n['attrs'] for n in elements if n['tag'] == 'application'), None)
    require(app is not None, 'Missing application manifest')
    components = []
    for node in elements:
        if node['tag'] in {'activity', 'activity-alias', 'service', 'receiver', 'provider'}:
            attrs = node['attrs']
            components.append({
                'type': node['tag'], 'name': quoted(attrs.get('android:name', '')),
                'exported': boolean(attrs.get('android:exported')),
                'permission': quoted(attrs.get('android:permission', '')),
            })
    certificate = '' if unsigned else dump(apksigner, 'verify', '--verbose', '--print-certs', apk)
    return {
        'apk_name': apk.name, 'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'package_name': package.get('name'), 'version_name': package.get('versionName'),
        'version_code': package.get('versionCode'), 'min_sdk': int(min_sdk[1]), 'target_sdk': int(target_sdk[1]),
        'permissions': sorted(set(re.findall(r"uses-permission: name='([^']+)'", dump(aapt, 'dump', 'permissions', apk)))),
        'components': components, 'backup_allowed': boolean(app.get('android:allowBackup')),
        'cleartext_allowed': boolean(app.get('android:usesCleartextTraffic')),
        'debuggable': boolean(app.get('android:debuggable')) is True or 'application-debuggable' in badging,
        'test_only': boolean(app.get('android:testOnly')) is True,
        'signature_verified': not unsigned,
        'signer_certificate_sha256': re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})', certificate),
        'signer_certificate_subjects': re.findall(r'Signer #\d+ certificate DN: (.+)', certificate),
        'signature_details': certificate.strip(),
    }


def verify_report(report, production=False, expected_cert=None, expected_version=None):
    require(report['package_name'] == PACKAGE, 'Unexpected app identity')
    require(report['min_sdk'] == 29 and report['target_sdk'] == 36, 'Unexpected SDK boundary')
    permissions = set(report['permissions'])
    require(permissions <= ALLOWED_PERMISSIONS, 'Unexpected permissions: ' + str(sorted(permissions - ALLOWED_PERMISSIONS)))
    require({'android.permission.SYSTEM_ALERT_WINDOW', 'android.permission.VIBRATE'} <= permissions, 'Missing core feature permissions')
    require(report['backup_allowed'] is False, 'Backup must remain disabled')
    require(report['cleartext_allowed'] is False, 'Cleartext must remain explicitly disabled')
    expected = {
        ('activity', MAIN, True, None),
        ('service', LISTENER, True, BIND),
        ('provider', 'androidx.startup.InitializationProvider', False, None),
    }
    actual = {(c['type'], c['name'], c['exported'], c['permission']) for c in report['components']}
    require(actual == expected and len(report['components']) == len(expected), 'Unexpected or unprotected component boundary')
    if expected_version is not None:
        require(report['version_name'] == expected_version, 'Release tag does not match APK versionName')
    if production:
        require(not report['debuggable'] and not report['test_only'], 'Debuggable/test-only APK cannot be a production release')
        require(report['signature_verified'], 'Production APK must be signed and signature-verified')
        require(not any('CN=Android Debug' in s for s in report['signer_certificate_subjects']), 'Debug signing identity cannot be a production identity')
        pin = re.sub(r'[:\s]', '', expected_cert or '').lower()
        require(re.fullmatch(r'[0-9a-f]{64}', pin) is not None, 'Production requires a trusted public signing-certificate SHA-256 pin')
        require(report['signer_certificate_sha256'] == [pin], 'Production signing certificate does not match the configured identity')
        require('Verified using v2 scheme (APK Signature Scheme v2): true' in report['signature_details'] or
                'Verified using v3 scheme (APK Signature Scheme v3): true' in report['signature_details'], 'A modern APK signature is required')


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('apk')
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--apksigner', required=True)
    parser.add_argument('--production', action='store_true')
    parser.add_argument('--expected-cert-sha256')
    parser.add_argument('--expected-version')
    parser.add_argument('--unsigned', action='store_true', help='Local release inspection only; incompatible with --production')
    parser.add_argument('--report-json', type=Path)
    parser.add_argument('--metadata-only', action='store_true', help='Inspect historical appeal evidence; do not claim current policy validation')
    args = parser.parse_args()
    try:
        require(not (args.production and (args.unsigned or args.metadata_only)), 'Production requires full verification of a signed APK')
        report = inspect_apk(args.apk, args.aapt, args.apksigner, args.unsigned)
        if not args.metadata_only:
            verify_report(report, args.production, args.expected_cert_sha256, args.expected_version)
        report['current_policy_checked'] = not args.metadata_only
        report['production_policy_checked'] = args.production
        if args.report_json:
            args.report_json.parent.mkdir(parents=True, exist_ok=True)
            args.report_json.write_text(json.dumps(report, indent=2) + '\n')
        print('APK metadata inspected' if args.metadata_only else 'APK security boundary checks: PASS')
        print('APK SHA-256:', report['apk_sha256'])
        for pin in report['signer_certificate_sha256']:
            print('Public signing-certificate SHA-256:', pin)
        print('These checks do not establish Google approval or hardware safety.')
    except (VerificationError, subprocess.CalledProcessError, OSError) as error:
        print('APK verification failed:', error, file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
