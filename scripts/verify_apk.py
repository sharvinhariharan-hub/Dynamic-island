#!/usr/bin/env python3
"""Check the packaged APK's permission/component boundary; not malware certification."""
import argparse
import re
import subprocess


def dump(tool, *args):
    return subprocess.check_output([tool, *args], text=True)


def nodes(xml):
    result, stack = [], []
    for line in xml.splitlines():
        element = re.match(r'(\s*)E: ([\w-]+)', line)
        if element:
            depth = len(element[1])
            while stack and stack[-1][0] >= depth:
                stack.pop()
            node = {"tag": element[2], "attrs": {}}
            result.append(node)
            stack.append((depth, node))
        attr = re.match(r'\s*A: (android:\w+)(?:\([^)]*\))?=(.*)', line)
        if attr and stack:
            stack[-1][1]['attrs'][attr[1]] = attr[2]
    return result


def quoted(value):
    match = re.match(r'"([^"]*)"', value)
    return match[1] if match else None


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('apk')
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--apksigner', required=True)
    parser.add_argument('--production', action='store_true')
    parser.add_argument('--expected-cert-sha256', help='Trusted public signing certificate fingerprint; mandatory in production mode')
    parser.add_argument('--unsigned', action='store_true', help='Local release compilation check only; cannot be combined with --production')
    args = parser.parse_args()
    if args.production and args.unsigned:
        raise SystemExit('Production APKs must be signed')
    badging = dump(args.aapt, 'dump', 'badging', args.apk)
    assert "package: name='org.dynamicisland.app'" in badging, 'Unexpected app identity'
    assert "sdkVersion:'29'" in badging and "targetSdkVersion:'36'" in badging, 'Unexpected SDK boundary'
    permissions = set(re.findall(r"uses-permission: name='([^']+)'", dump(args.aapt, 'dump', 'permissions', args.apk)))
    allowed = {'android.permission.SYSTEM_ALERT_WINDOW', 'android.permission.VIBRATE',
               'org.dynamicisland.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
    assert permissions <= allowed, f'Unexpected permissions: {permissions - allowed}'
    elements = nodes(dump(args.aapt, 'dump', 'xmltree', args.apk, 'AndroidManifest.xml'))
    app = next(n['attrs'] for n in elements if n['tag'] == 'application')
    assert app.get('android:allowBackup') == '(type 0x12)0x0', 'Backup must remain disabled'
    assert app.get('android:usesCleartextTraffic') == '(type 0x12)0x0', 'Cleartext must remain disabled'
    exported = []
    for node in elements:
        attrs = node['attrs']
        if node['tag'] in {'activity', 'activity-alias', 'service', 'receiver', 'provider'}:
            if attrs.get('android:exported') == '(type 0x12)0xffffffff':
                exported.append((node['tag'], quoted(attrs.get('android:name', '')), quoted(attrs.get('android:permission', ''))))
    assert set(exported) == {
        ('activity', 'org.dynamicisland.app.MainActivity', None),
        ('service', 'org.dynamicisland.app.IslandNotificationListener', 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE'),
        ('receiver', 'androidx.profileinstaller.ProfileInstallReceiver', 'android.permission.DUMP'),
    }, f'Unexpected exported component boundary: {exported}'
    if args.production:
        assert 'application-debuggable' not in badging, 'Debuggable APK must never be a production release'
    if not args.unsigned:
        certificate = dump(args.apksigner, 'verify', '--print-certs', args.apk)
        if args.production:
            assert 'CN=Android Debug' not in certificate, 'Debug signing certificate cannot be a production identity'
            expected = (args.expected_cert_sha256 or '').replace(':', '').lower()
            assert re.fullmatch(r'[0-9a-f]{64}', expected), 'Production requires a trusted public signing-certificate SHA-256 pin'
            actual = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})', certificate)
            assert actual == [expected], 'Production signing certificate does not match the configured identity'
        print(certificate.strip())  # Public certificate details only; never private key material.
    print('APK permission, backup, cleartext, and exported-component checks: PASS')
    print('This verifies build properties only; it does not establish Play Protect approval or hardware safety.')


if __name__ == '__main__':
    main()
