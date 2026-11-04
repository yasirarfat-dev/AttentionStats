"""Prevent broad/default storage inclusion when enabling the system backup agent."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("backup_policy", ROOT / "tools/check_backup_policy.py")
policy = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(policy)


class BackupPolicy(unittest.TestCase):
    def setUp(self):
        base = ROOT / "android/app/src/main"
        self.manifest = (base / "AndroidManifest.xml").read_text()
        self.legacy = (base / "res/xml/backup_rules.xml").read_text()
        self.modern = (base / "res/xml/data_extraction_rules.xml").read_text()

    def test_current_boundary(self):
        policy.audit(self.manifest, self.legacy, self.modern)

    def test_rejects_default_agent_or_missing_rules(self):
        for changed in [self.manifest.replace('.backup.SystemBackupAgent', 'android.app.backup.BackupAgent'),
                        self.manifest.replace('android:fullBackupOnly="true"', ''),
                        self.manifest.replace('android:dataExtractionRules="@xml/data_extraction_rules"', '')]:
            with self.subTest(changed=changed), self.assertRaises(AssertionError):
                policy.audit(changed, self.legacy, self.modern)

    def test_rejects_legacy_unprotected_or_whole_directory(self):
        for changed in [self.legacy.replace(' requireFlags="clientSideEncryption"', ''),
                        self.legacy.replace(policy.ARCHIVE, '.'),
                        self.legacy.replace('</full-backup-content>', '<include domain="database" path="."/></full-backup-content>')]:
            with self.subTest(changed=changed), self.assertRaises(AssertionError):
                policy.audit(self.manifest, changed, self.modern)

    def test_rejects_modern_unprotected_broad_and_device_transfer(self):
        for changed in [self.modern.replace('disableIfNoEncryptionCapabilities="true"', 'disableIfNoEncryptionCapabilities="false"'),
                        self.modern.replace(policy.ARCHIVE, '.'),
                        self.modern.replace('<exclude domain="database" path="." />', '<include domain="database" path="." />'),
                        self.modern.replace('<exclude domain="device_sharedpref" path="." />', '')]:
            with self.subTest(changed=changed), self.assertRaises(AssertionError):
                policy.audit(self.manifest, self.legacy, changed)
