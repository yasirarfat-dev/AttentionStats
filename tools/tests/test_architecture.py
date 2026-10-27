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


if __name__ == "__main__":
    unittest.main()
