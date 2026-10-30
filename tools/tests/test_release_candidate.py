"""Release-admission guards using artificial manifests and isolated folders."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("release_candidate", Path(__file__).parents[1] / "release_candidate.py")
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)
MANIFEST = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
 package="attention.stats" android:versionCode="1" android:versionName="0.1.0">
 <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="37"/>
 <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"/>
 <queries><intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent>
 <package android:name="com.instagram.android"/><package android:name="com.facebook.katana"/>
 <package android:name="com.google.android.youtube"/><package android:name="com.zhiliaoapp.musically"/></queries>
 <application android:allowBackup="false" android:usesCleartextTraffic="false"
  android:fullBackupContent="@xml/backup_rules" android:dataExtractionRules="@xml/data_extraction_rules">
  <activity android:name="com.attentionstats.MainActivity" android:exported="true"/>
  <service android:name="com.attentionstats.reels.ReelsAccessibilityService" android:exported="true"
   android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"/>
 </application></manifest>'''


class ReleaseAdmission(unittest.TestCase):
    def test_exact_identity_with_distinct_namespace(self):
        self.assertEqual(release.audit_manifest(MANIFEST)["package"], "attention.stats")
        for wrong in ["com.attention.stats", "attention.stats.debug", "Attention.Stats"]:
            with self.subTest(wrong=wrong), self.assertRaises(ValueError):
                release.audit_manifest(MANIFEST.replace('package="attention.stats"', f'package="{wrong}"'))

    def test_rejects_debug_test_backup_and_cleartext(self):
        for changed in [
            MANIFEST.replace('<application ', '<application android:debuggable="true" '),
            MANIFEST.replace('<application ', '<application android:testOnly="true" '),
            MANIFEST.replace('allowBackup="false"', 'allowBackup="true"'),
            MANIFEST.replace('usesCleartextTraffic="false"', 'usesCleartextTraffic="true"'),
            MANIFEST.replace('android:dataExtractionRules="@xml/data_extraction_rules"', ''),
            MANIFEST.replace('</manifest>', '<instrumentation android:name="fixture"/></manifest>'),
        ]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                release.audit_manifest(changed)

    def test_rejects_network_or_broad_visibility(self):
        for changed in [
            MANIFEST.replace('<queries>', '<uses-permission android:name="android.permission.INTERNET"/><queries>'),
            MANIFEST.replace('<queries>', '<queries><intent><action android:name="fixture"/></intent>'),
            MANIFEST.replace('<queries>', '<queries><package android:name="example.unrelated"/>'),
            MANIFEST.replace('<package android:name="com.google.android.youtube"/>', ''),
        ]:
            with self.assertRaises(ValueError):
                release.audit_manifest(changed)

    def test_rejects_extra_export_and_unprotected_service(self):
        for changed in [
            MANIFEST.replace('</application>', '<provider android:name="fixture" android:exported="true"/></application>'),
            MANIFEST.replace('android.permission.BIND_ACCESSIBILITY_SERVICE', 'fixture.permission'),
        ]:
            with self.assertRaises(ValueError):
                release.audit_manifest(changed)

    def test_usage_access_keeps_other_sensitive_permissions_rejected(self):
        self.assertIn("android.permission.PACKAGE_USAGE_STATS", release.audit_manifest(MANIFEST)["permissions"])
        for permission in ["QUERY_ALL_PACKAGES", "READ_EXTERNAL_STORAGE", "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "POST_NOTIFICATIONS"]:
            with self.subTest(permission=permission), self.assertRaises(ValueError):
                release.audit_manifest(MANIFEST.replace('<queries>', f'<uses-permission android:name="android.permission.{permission}"/><queries>'))
        with self.assertRaises(ValueError):
            release.audit_manifest(MANIFEST.replace('<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"/>', ''))

    def test_launcher_visibility_is_exact(self):
        for changed in [
            MANIFEST.replace('android.intent.action.MAIN', 'android.intent.action.VIEW'),
            MANIFEST.replace('android.intent.category.LAUNCHER', 'android.intent.category.DEFAULT'),
            MANIFEST.replace('</intent>', '<data android:scheme="https"/></intent>'),
            MANIFEST.replace('<intent>', '<intent><category android:name="fixture"/>'),
            MANIFEST.replace('<intent>', '<intent android:label="fixture">'),
        ]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                release.audit_manifest(changed)

    def test_profile_receiver_requires_privileged_binding(self):
        protected = MANIFEST.replace('</application>',
            '<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" '
            'android:exported="true" android:permission="android.permission.DUMP"/></application>')
        release.audit_manifest(protected)
        with self.assertRaises(ValueError):
            release.audit_manifest(protected.replace('android.permission.DUMP', 'fixture.permission'))

    def test_archive_preserves_an_existing_release(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'releases/0.1.0+1'
            target.mkdir(parents=True)
            sentinel = target / 'keep.txt'
            sentinel.write_text('existing release')
            with self.assertRaisesRegex(ValueError, 'Existing release preserved'):
                release.archive(Path(directory))
            self.assertEqual(sentinel.read_text(), 'existing release')

    def test_store_text_is_within_console_limits(self):
        blocks = release.listing_blocks()
        self.assertEqual(blocks[0], 'Attention Stats')
        self.assertEqual(len(blocks), 4)


if __name__ == '__main__':
    unittest.main()
