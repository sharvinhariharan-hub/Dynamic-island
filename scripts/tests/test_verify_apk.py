import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_apk import BIND, LISTENER, MAIN, PACKAGE, VerificationError, nodes, verify_report


class PackagedApkPolicyTest(unittest.TestCase):
    def setUp(self):
        self.pin = 'a' * 64
        self.report = {
            'package_name': PACKAGE, 'min_sdk': 29, 'target_sdk': 36,
            'version_name': '1.0.3', 'permissions': ['android.permission.SYSTEM_ALERT_WINDOW', 'android.permission.VIBRATE'],
            'backup_allowed': False, 'cleartext_allowed': False,
            'components': [
                {'type': 'activity', 'name': MAIN, 'exported': True, 'permission': None},
                {'type': 'service', 'name': LISTENER, 'exported': True, 'permission': BIND},
                {'type': 'provider', 'name': 'androidx.startup.InitializationProvider', 'exported': False, 'permission': None}],
            'debuggable': False, 'test_only': False, 'signature_verified': True,
            'signer_certificate_subjects': ['CN=Release test fixture'], 'signer_certificate_sha256': [self.pin],
            'signature_details': 'Verified using v2 scheme (APK Signature Scheme v2): true',
        }

    def test_valid_production_boundary(self):
        verify_report(self.report, True, self.pin, '1.0.3')

    def test_network_and_other_sensitive_permissions_are_rejected(self):
        for permission in ['android.permission.INTERNET', 'android.permission.READ_SMS', 'android.permission.RECORD_AUDIO']:
            report = copy.deepcopy(self.report)
            report['permissions'].append(permission)
            with self.assertRaises(VerificationError):
                verify_report(report)

    def test_listener_must_retain_system_binding_protection(self):
        self.report['components'][1]['permission'] = None
        with self.assertRaises(VerificationError):
            verify_report(self.report)

    def test_unused_or_exported_components_are_rejected(self):
        self.report['components'].append({'type': 'receiver', 'name': 'example.Unused', 'exported': True, 'permission': None})
        with self.assertRaises(VerificationError):
            verify_report(self.report)

    def test_backup_and_cleartext_fail_closed(self):
        for key in ['backup_allowed', 'cleartext_allowed']:
            for value in [True, None]:
                report = copy.deepcopy(self.report)
                report[key] = value
                with self.assertRaises(VerificationError):
                    verify_report(report)

    def test_debug_testonly_unsigned_cannot_be_production(self):
        for key, value in [('debuggable', True), ('test_only', True), ('signature_verified', False)]:
            report = copy.deepcopy(self.report)
            report[key] = value
            with self.assertRaises(VerificationError):
                verify_report(report, True, self.pin)

    def test_debug_certificate_is_rejected_even_without_debuggable_flag(self):
        self.report['signer_certificate_subjects'] = ['C=US, O=Android, CN=Android Debug']
        with self.assertRaises(VerificationError):
            verify_report(self.report, True, self.pin)

    def test_missing_malformed_or_wrong_pin_is_rejected(self):
        for pin in [None, '', 'abc', 'b' * 64]:
            with self.assertRaises(VerificationError):
                verify_report(self.report, True, pin)

    def test_old_signature_and_wrong_release_version_rejected(self):
        with self.assertRaises(VerificationError):
            verify_report(self.report, True, self.pin, '1.0.2')
        self.report['signature_details'] = 'Verified using v1 scheme (JAR signing): true'
        with self.assertRaises(VerificationError):
            verify_report(self.report, True, self.pin)

    def test_compiled_manifest_parser_does_not_mix_child_attributes(self):
        manifest = '''  E: application
    A: android:allowBackup(0x01010280)=(type 0x12)0x0
    E: service
      A: android:name(0x01010003)="org.dynamicisland.app.IslandNotificationListener"
      A: android:permission(0x01010006)="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
      E: intent-filter
        E: action
          A: android:name(0x01010003)="android.service.notification.NotificationListenerService"
'''
        parsed = nodes(manifest)
        self.assertEqual(parsed[1]['attrs']['android:permission'], '"android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"')
        self.assertNotIn('android:permission', parsed[-1]['attrs'])


if __name__ == '__main__':
    unittest.main()
