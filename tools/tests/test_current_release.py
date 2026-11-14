"""Current release guards: hostile manifests and compiled backup rule mutations."""
from pathlib import Path
import io
import sys
import unittest
import xml.etree.ElementTree as ET
import zipfile
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import audit_current_release as current
import release_candidate as release
from test_release_candidate import MANIFEST

CURRENT = MANIFEST.replace('versionName="0.1.0"', 'versionName="1.0.0"').replace('versionCode="1"', 'versionCode="10"').replace(
    '</queries>', '<package android:name="com.ss.android.ugc.trill"/><package android:name="com.zhiliaoapp.musically.go"/>'
    '<package android:name="com.snapchat.android"/>'
    '<intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.HOME"/></intent>'
    '<intent><action android:name="android.intent.action.DIAL"/></intent></queries>').replace(
    'allowBackup="false"', 'allowBackup="true" android:backupAgent="com.attentionstats.backup.SystemBackupAgent" android:fullBackupOnly="true"').replace(
    '</application>', '<activity android:name="com.attentionstats.widget.WidgetConfigureActivity" android:exported="true"/></application>')

OLD = '''E: full-backup-content (line=2)
  E: include (line=4)
    A: domain="file" (Raw: "file")
    A: path="system-backup/history.attentionbackup" (Raw: "system-backup/history.attentionbackup")
    A: requireFlags="clientSideEncryption" (Raw: "clientSideEncryption")'''
MODERN = '''E: data-extraction-rules (line=2)
  E: cloud-backup (line=3)
    A: disableIfNoEncryptionCapabilities=(type 0x12)0xffffffff
    E: include (line=5)
      A: domain="file" (Raw: "file")
      A: path="system-backup/history.attentionbackup" (Raw: "system-backup/history.attentionbackup")
  E: device-transfer (line=7)'''
for domain in ["root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref"]:
    MODERN += '\n    E: exclude (line=8)\n      A: domain="' + domain + '" (Raw: "' + domain + '")\n      A: path="." (Raw: ".")'


def audit(text=CURRENT, **kwargs):
    return release.audit_manifest(text, 10, version_name="1.0.0", profile="current", **kwargs)


class CurrentReleaseTests(unittest.TestCase):
    def test_current_identity_and_legacy_stay_distinct(self):
        result = audit()
        self.assertEqual((result['versionName'], result['versionCode'], result['backupDisabled']), ('1.0.0', 10, False))
        with self.assertRaises(ValueError):
            release.audit_manifest(CURRENT)
        with self.assertRaises(ValueError):
            audit(MANIFEST)

    def test_changed_expected_version_is_explicit(self):
        with self.assertRaises(ValueError):
            release.audit_manifest(CURRENT, 11, version_name="1.1.0", profile="current")
        for code, name, profile in [(0, '1.0.0', 'current'), (True, '1.0.0', 'current'), (10, '1.0.0-preview', 'current'), (10, '1.0.0', 'unknown')]:
            with self.subTest(code=code, name=name, profile=profile), self.assertRaises(ValueError):
                release.audit_manifest(CURRENT, code, version_name=name, profile=profile)

    def test_current_manifest_rejects_security_changes(self):
        for before, after in [
            ('allowBackup="true"', 'allowBackup="false"'),
            ('fullBackupOnly="true"', 'fullBackupOnly="false"'),
            ('com.attentionstats.backup.SystemBackupAgent', 'example.OtherAgent'),
            ('android.permission.BIND_ACCESSIBILITY_SERVICE', 'example.permission'),
            ('usesCleartextTraffic="false"', 'usesCleartextTraffic="true"'),
            ('</queries>', '<intent><action android:name="android.intent.action.VIEW"/></intent></queries>'),
            ('android.intent.category.HOME', 'android.intent.category.DEFAULT'),
            ('android.intent.action.DIAL', 'android.intent.action.CALL'),
            ('</queries>', '<package android:name="example.other"/></queries>'),
            ('</manifest>', '<uses-permission android:name="android.permission.INTERNET"/></manifest>'),
            ('</manifest>', '<uses-permission-sdk-23 android:name="android.permission.INTERNET"/></manifest>'),
            ('</manifest>', '<application/></manifest>'),
            ('package="attention.stats"', 'package="attention.stats" android:sharedUserId="example.uid"'),
            ('</application>', '<provider android:name="example.Other" android:exported="true"/></application>'),
            ('<application ', '<application android:debuggable="true" '),
            ('<application ', '<application android:testOnly="true" '),
        ]:
            with self.subTest(after=after), self.assertRaises(ValueError):
                audit(CURRENT.replace(before, after))

    def test_compiled_allowlist_and_transport(self):
        self.assertTrue(current.audit_backup(OLD, MODERN)['deviceTransferExcluded'])
        for before, after in [('file', 'database'), ('history.attentionbackup', '.'), ('clientSideEncryption', 'deviceToDeviceTransfer')]:
            with self.subTest(after=after), self.assertRaises(ValueError):
                current.audit_backup(OLD.replace(before, after), MODERN)
        for changed in [
            MODERN.replace('0xffffffff', '0x0'),
            MODERN.replace('A: path="." (Raw: ".")', 'A: path="other" (Raw: "other")', 1),
            MODERN.replace('device_sharedpref', 'sharedpref'),
            MODERN + '\n    E: include (line=20)\n      A: path="." (Raw: ".")',
            MODERN.replace('E: exclude', 'E: include', 1),
            MODERN.replace('    E: include', '      E: include', 1),
        ]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                current.audit_backup(OLD, changed)

    def test_parser_rejects_ignored_or_ambiguous_security_data(self):
        for text in ['', OLD + '\nE: extra (line=9)', OLD + '\n    C: unexpected',
                     OLD.replace('Raw: "file"', 'Raw: "database"'),
                     OLD + '\n    A: domain="file" (Raw: "file")',
                     OLD.replace('A: domain=', 'A: android:domain='),
                     OLD.replace('A: domain="file" (Raw: "file")', 'A: domain=(type 0x10)0x1')]:
            with self.subTest(text=text), self.assertRaises(ValueError):
                current.xmltree(text)

    def test_compiled_manifest_namespace_and_typed_values(self):
        text = '''N: android=http://schemas.android.com/apk/res/android
  E: manifest (line=1)
    A: android:versionCode(0x0101021b)=(type 0x10)0xa
    A: package="attention.stats" (Raw: "attention.stats")
    E: application (line=2)
      A: android:allowBackup(0x01010280)=(type 0x12)0xffffffff
      A: android:fullBackupContent(0x010104eb)=@0x7f130000'''
        node = current.xmltree(text, manifest=True)
        self.assertEqual(node.get(release.ANDROID + 'versionCode'), '10')
        self.assertEqual(node.find('application').get(release.ANDROID + 'allowBackup'), 'true')
        self.assertEqual(node.find('application').get(release.ANDROID + 'fullBackupContent'), '@0x7f130000')
        with self.assertRaises(ValueError):
            current.xmltree(text.replace('android:versionCode', 'unknown:versionCode'), manifest=True)

    def test_bundle_resource_requires_unique_compiled_path(self):
        resources = '0x7f130000 - xml/backup_rules\n\t(default) - [FILE] res/xml/backup_rules.xml\n'
        with patch.object(release, 'call', return_value='<full-backup-content/>') as call:
            resource_id, node = current.bundle_resource(Path('synthetic.aab'), 'backup_rules', resources)
            self.assertEqual(resource_id, '0x7f130000')
            self.assertEqual(node.tag, 'full-backup-content')
            self.assertEqual(call.call_args.args[-1], 'base/res/xml/backup_rules.xml')
            for invalid in ['', resources + resources]:
                with self.assertRaises(ValueError):
                    current.bundle_resource(Path('synthetic.aab'), 'backup_rules', invalid)

    def test_backup_shape_preserves_attributes_and_children(self):
        first = ET.fromstring('<rules><include domain="file" path="safe"/></rules>')
        reordered = ET.fromstring('<rules><include path="safe" domain="file"/></rules>')
        changed = ET.fromstring('<rules><include domain="file" path="other"/></rules>')
        self.assertEqual(current.shape(first), current.shape(reordered))
        self.assertNotEqual(current.shape(first), current.shape(changed))

    def test_accessibility_checks_bind_the_actual_service_resource(self):
        app = ET.fromstring(CURRENT).find('application')
        with self.assertRaises(ValueError):
            current.service_resource_reference(app)
        service = app.find('service')
        metadata = ET.SubElement(service, 'meta-data', {release.ANDROID + 'name': 'android.accessibilityservice',
                                                       release.ANDROID + 'resource': '@xml/reels_accessibility_service'})
        self.assertEqual(current.service_resource_reference(app), '@xml/reels_accessibility_service')
        service.append(ET.fromstring(ET.tostring(metadata)))
        with self.assertRaises(ValueError):
            current.service_resource_reference(app)

    def test_assets_and_baseline_profiles_are_compared_in_both_formats(self):
        def archive(entries):
            stream = io.BytesIO()
            with zipfile.ZipFile(stream, 'w') as z:
                for name, value in entries.items():
                    z.writestr(name, value)
            return zipfile.ZipFile(stream)
        apk = {'assets/index.android.bundle': b'code', 'assets/dexopt/baseline.prof': b'profile', 'assets/dexopt/baseline.profm': b'metadata'}
        bundle = {'base/assets/index.android.bundle': b'code',
                  'BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof': b'profile',
                  'BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm': b'metadata'}
        with archive(apk) as a, archive(bundle) as b:
            current.compare_assets(a, b)
        for key in bundle:
            changed = dict(bundle)
            changed[key] = b'changed'
            with self.subTest(key=key), archive(apk) as a, archive(changed) as b, self.assertRaises(ValueError):
                current.compare_assets(a, b)
        missing = dict(bundle)
        del missing['BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof']
        with archive(apk) as a, archive(missing) as b, self.assertRaises(ValueError):
            current.compare_assets(a, b)


if __name__ == '__main__':
    unittest.main()
