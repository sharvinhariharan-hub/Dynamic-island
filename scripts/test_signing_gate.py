#!/usr/bin/env python3
"""Exercise the production gate using disposable fixture keys; never publish fixture APKs."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile
from verify_apk import inspect_apk, require


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('--unsigned-apk', required=True)
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--apksigner', required=True)
    parser.add_argument('--keytool', default='keytool')
    args = parser.parse_args()
    gate = Path(__file__).with_name('verify_apk.py')
    version = inspect_apk(args.unsigned_apk, args.aapt, args.apksigner, unsigned=True)['version_name']
    with tempfile.TemporaryDirectory(prefix='island-signing-fixture-') as folder:
        folder = Path(folder)
        for name, subject in [('release-fixture', 'CN=One-day CI signing fixture'), ('debug-fixture', 'CN=Android Debug')]:
            key = folder / (name + '.p12')
            # This public, fixed fixture password protects a one-day disposable test key only.
            password = 'disposable-fixture-only'
            subprocess.run([args.keytool, '-genkeypair', '-alias', name, '-keystore', str(key), '-storepass', password,
                            '-keypass', password, '-dname', subject, '-validity', '1', '-keyalg', 'RSA', '-keysize', '2048'],
                           check=True, capture_output=True)
            der = subprocess.check_output([args.keytool, '-exportcert', '-alias', name, '-keystore', str(key), '-storepass', password])
            pin = hashlib.sha256(der).hexdigest()
            apk = folder / (name + '.apk')
            subprocess.run([args.apksigner, 'sign', '--ks', str(key), '--ks-key-alias', name, '--ks-pass', 'pass:' + password,
                            '--out', str(apk), args.unsigned_apk], check=True, capture_output=True)
            command = [sys.executable, '-O', str(gate), str(apk), '--production', '--aapt', args.aapt,
                       '--apksigner', args.apksigner, '--expected-version', version]
            valid = subprocess.run(command + ['--expected-cert-sha256', pin], capture_output=True, text=True)
            if name == 'debug-fixture':
                require(valid.returncode != 0 and 'Debug signing identity' in valid.stderr, 'Debug certificate was not rejected')
                continue
            require(valid.returncode == 0, valid.stderr)
            for extra, message in [([], 'requires a trusted'), (['--expected-cert-sha256', '0' * 64], 'does not match')]:
                rejected = subprocess.run(command + extra, capture_output=True, text=True)
                require(rejected.returncode != 0 and message in rejected.stderr, 'Missing/incorrect pin did not fail closed')
    print('Real-APK signing gates passed under python -O: correct identity accepted; debug/missing/wrong identities rejected.')
    print('Disposable fixture keys and APKs deleted; no production identity was created.')


if __name__ == '__main__':
    main()
