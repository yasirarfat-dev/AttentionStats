"""Enforce additive social adapters without cross-platform or infrastructure coupling."""

import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

SPEC = importlib.util.spec_from_file_location("architecture", Path(__file__).resolve().parents[1] / "check_architecture.py")
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


class DetectorBoundaries(unittest.TestCase):
    def check_source(self, reference):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "social/synthetic/Detector.kt"
            path.parent.mkdir(parents=True)
            path.write_text("package com.attentionstats.detectors.social.synthetic\n" + reference, encoding="utf-8")
            errors = []
            self.assertEqual(checker.check_detectors(root, errors), 1)
            return errors

    def test_own_adapter_and_shared_contract_allowed(self):
        self.assertFalse(self.check_source("import com.attentionstats.detectors.SurfaceEvidence\n"
                                           "import com.attentionstats.detectors.social.synthetic.Selectors\n"))

    def test_sibling_import_and_qualified_bypass_rejected(self):
        for reference in ["import com.attentionstats.detectors.social.other.Selectors\n",
                          "import com.attentionstats.detectors.social.*\n",
                          "val source = com.attentionstats.detectors.social.other.Selectors\n"]:
            with self.subTest(reference=reference):
                self.assertTrue(self.check_source(reference))

    def test_android_storage_bridge_and_coordinator_rejected(self):
        for reference in ["import android.view.View\n", "import org.json.JSONObject\n",
                          "import com.attentionstats.storage.HistoryStore\n",
                          "val store = com.attentionstats.reels.ReelsCoordinator\n",
                          "import com.attentionstats.detectors.accessibility.StructuralSnapshotReader\n",
                          "val registry = com.attentionstats.detectors.DetectorRegistry\n",
                          "import com.attentionstats.bridge.NativeReelsSpec\n"]:
            with self.subTest(reference=reference):
                self.assertTrue(self.check_source(reference))


class NativeActionBoundaries(unittest.TestCase):
    def check_source(self, name, text):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            errors = []
            checker.check_native_actions(root, errors)
            return errors

    def test_foreign_label_reads_rejected_and_acquisition_owner_allowed(self):
        for value in ["val title = node.text", "val label = root.contentDescription", "val label = event.getText()"]:
            with self.subTest(value=value):
                self.assertTrue(self.check_source("another/Reader.kt", value))
                self.assertFalse(self.check_source("detection/SnapshotReader.kt", value))

    def test_actions_cannot_escape_declared_owners(self):
        for value in ["node.performAction(AccessibilityNodeInfo.ACTION_CLICK)", "performGlobalAction(1)", "dispatchGesture(gesture)"]:
            with self.subTest(value=value):
                self.assertTrue(self.check_source("another/Dispatcher.kt", value))
        self.assertFalse(self.check_source("protection/PlaybackPauseDispatcher.kt", "node.performAction(AccessibilityNodeInfo.ACTION_CLICK)"))

    def test_pause_owner_cannot_change_volume_broadcast_keys_or_navigate(self):
        for value in ["performGlobalAction(1)", "dispatchGesture(gesture)", "dispatchMediaKeyEvent(event)", "setStreamVolume(1)", "adjustStreamVolume(1)"]:
            with self.subTest(value=value):
                self.assertTrue(self.check_source("protection/PlaybackPauseDispatcher.kt", value))


if __name__ == "__main__":
    unittest.main()
