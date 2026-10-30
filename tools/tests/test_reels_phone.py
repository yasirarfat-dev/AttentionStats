"""Preserve live data and foreign test-package ownership when phone operations fail."""

import importlib.util
import io
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("reels_phone", Path(__file__).resolve().parents[1] / "reels_phone.py")
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class ReelsPhoneSafety(unittest.TestCase):
    def test_structure_read_saves_private_metadata_but_prints_only_aggregates(self):
        value = self.structure()
        with TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "preview.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/index.android.bundle", b"synthetic")
            adb = root / "adb"; adb.touch()
            def phone(command, **_):
                if command[1:] == ["devices"]: output = "List of devices attached\nprivate-fixture-id\tdevice\n"
                else:
                    args = command[3:]
                    if args == ["shell", "getprop", "ro.kernel.qemu"]: output = "0\n"
                    elif args == ["shell", "getprop", "ro.product.cpu.abi"]: output = "arm64-v8a\n"
                    elif args == ["shell", "getprop", "ro.build.version.sdk"]: output = "36\n"
                    elif args == ["shell", "am", "get-current-user"]: output = "0\n"
                    elif args[:4] == ["shell", "pm", "list", "packages"]: output = ""
                    elif args == ["exec-out", "run-as", probe.APP, "cat", "no_backup/reels-structure.json"]:
                        output = json.dumps(value).encode()
                    else: raise AssertionError("Unexpected phone operation")
                return SimpleNamespace(returncode=0, stdout=output)
            printed = io.StringIO()
            with patch.object(probe, "PREVIEW", apk), patch.object(probe, "CACHE", root / "cache"), \
                 patch.object(probe, "audit_apks"), patch.object(probe.subprocess, "run", side_effect=phone), \
                 patch.object(probe.sys, "argv", ["reels_phone.py", "structure", "--adb", str(adb)]), \
                 patch("sys.stdout", printed):
                self.assertEqual(probe.main(), 0)
            self.assertEqual(json.loads(printed.getvalue()), {"savedPrivateStructure": True, "targets": {
                "instagram": {"bounded": True, "editableExcluded": False, "layoutIdCount": 1, "positionCount": 0}}})
            self.assertNotIn("private-fixture-id", printed.getvalue())
            self.assertEqual(json.loads((root / "cache/structure-reels-entry.json").read_text()), value)

    def test_display_opt_in_requires_explicit_authorization_before_phone_access(self):
        with patch.object(probe.sys, "argv", ["reels_phone.py", "show-counter"]), \
             patch.object(probe.subprocess, "run") as run, self.assertRaises(SystemExit), \
             patch("sys.stderr"):
            probe.main()
        run.assert_not_called()

    def run_failure(self, existing=False, install_failure=False, active_user="0"):
        calls = []
        with TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "preview.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/index.android.bundle", b"synthetic")
            test = root / "test.apk"
            test.write_bytes(b"synthetic")
            adb = root / "adb"
            adb.touch()

            def phone(command, **_kwargs):
                calls.append(command)
                if command[1:] == ["devices"]:
                    output = "List of devices attached\nprivate-fixture-id\tdevice\n"
                else:
                    args = command[3:]
                    if args == ["shell", "getprop", "ro.kernel.qemu"]: output = "0\n"
                    elif args == ["shell", "getprop", "ro.product.cpu.abi"]: output = "arm64-v8a\n"
                    elif args == ["shell", "getprop", "ro.build.version.sdk"]: output = "36\n"
                    elif args == ["shell", "am", "get-current-user"]: output = active_user + "\n"
                    elif args[:4] == ["shell", "pm", "list", "packages"]:
                        output = "package:" + probe.TEST + "\n" if existing else ""
                    elif args[:2] == ["install", str(test)]:
                        output = "Failure" if install_failure else "Success\n"
                    elif args[0] in ["install", "uninstall"]: output = "Success\n"
                    elif args[:3] == ["shell", "am", "instrument"]:
                        output = "INSTRUMENTATION_RESULT: stream=FAIL: IllegalStateException\nINSTRUMENTATION_CODE: 0\n"
                    elif args[:3] == ["shell", "am", "start"]: output = "Starting owned app\n"
                    else: raise AssertionError("Unexpected phone operation")
                return SimpleNamespace(returncode=0, stdout=output)

            with patch.object(probe, "PREVIEW", apk), patch.object(probe, "TEST_APK", test), \
                 patch.object(probe, "audit_apks"), \
                 patch.object(probe, "CACHE", root / "cache"), \
                 patch.object(probe.sys, "argv", ["reels_phone.py", "qualify", "--adb", str(adb)]), \
                 patch.object(probe.subprocess, "run", side_effect=phone), self.assertRaises(RuntimeError):
                probe.main()
            evidence = root / "cache/phone-result.json"
            result = json.loads(evidence.read_text()) if evidence.exists() else None
            if evidence.exists(): self.assertNotIn("private-fixture-id", evidence.read_text())
        return [call[3:] for call in calls if "-s" in call], result

    def test_existing_test_package_preserved_before_any_install(self):
        calls, evidence = self.run_failure(existing=True)
        self.assertFalse(any(call[0] in ["install", "uninstall"] for call in calls))
        self.assertIsNone(evidence)

    def test_package_check_is_scoped_to_active_profile(self):
        calls, _ = self.run_failure(existing=True, active_user="12")
        self.assertIn(["shell", "pm", "list", "packages", "--user", "12", probe.TEST], calls)
        self.assertFalse(any(call[:4] == ["shell", "pm", "list", "packages"] and "--user" not in call for call in calls))

    def test_unknown_profile_never_falls_back_or_installs(self):
        calls, evidence = self.run_failure(active_user="unavailable")
        self.assertFalse(any(call[0] in ["install", "uninstall"] for call in calls))
        self.assertIsNone(evidence)

    def test_failed_probe_removes_only_test_package_and_leaves_preview(self):
        calls, evidence = self.run_failure()
        self.assertEqual([call for call in calls if call[0] == "uninstall"], [["uninstall", probe.TEST]])
        self.assertTrue(evidence["installed"] and evidence["testPackageRemoved"])
        self.assertFalse(evidence["passed"])
        self.assertTrue(any(call[:3] == ["shell", "am", "start"] for call in calls))
        self.assertFalse(any("clear" in call or "grant" in call for call in calls))

    def test_failed_test_install_never_uninstalls_unowned_package(self):
        calls, evidence = self.run_failure(install_failure=True)
        self.assertFalse(any(call[0] == "uninstall" for call in calls))
        self.assertTrue(evidence["installed"])

    def test_contradictory_completion_rejected(self):
        output = "\n".join(["INSTRUMENTATION_RESULT: stream=PASS: own-window React Native preview screens",
                            "INSTRUMENTATION_RESULT: ui_checks=120", "INSTRUMENTATION_RESULT: shell_routes=20", "INSTRUMENTATION_CODE: -1"])
        self.assertEqual(probe.parse_result(output, "ui"), {"ui_checks": 120, "shell_routes": 20})
        for extra in ["INSTRUMENTATION_CODE: 0", "INSTRUMENTATION_RESULT: ui_checks=120", "INSTRUMENTATION_RESULT: shell_routes=19"]:
            with self.assertRaises(RuntimeError): probe.parse_result(output + "\n" + extra, "ui")

    def test_autoscroll_requires_its_own_complete_success_receipt(self):
        output = "\n".join([
            "INSTRUMENTATION_RESULT: stream=PASS: synthetic auto-scroll decision safety",
            "INSTRUMENTATION_RESULT: autoscroll_checks=100", "INSTRUMENTATION_CODE: -1"])
        self.assertEqual(probe.parse_result(output, "autoscroll"), {"autoscroll_checks": 100})
        for changed in [output.replace("=100", "=0"), output.replace("=100", "=201"),
                        output.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0"),
                        output + "\nINSTRUMENTATION_RESULT: autoscroll_checks=100",
                        output.replace("synthetic auto-scroll decision safety", "own-window React Native preview screens")]:
            with self.subTest(changed=changed), self.assertRaises(RuntimeError):
                probe.parse_result(changed, "autoscroll")

    def structure(self):
        return {"scope": "consented structural detector qualification only; no content", "targets": {
            "instagram": {"version": "fixture.1", "nodes": 2, "bounded": True, "editable": False,
                          "ids": ["synthetic_pager"], "classes": ["android.view.View"], "positions": [], "controls": [], "scrolls": []}}}

    def test_only_bounded_structural_diagnostic_accepted(self):
        value = self.structure()
        self.assertEqual(probe.validate_structure(json.dumps(value).encode()), value)

    def test_content_fields_and_arbitrary_strings_rejected(self):
        extra = self.structure()
        extra["targets"]["instagram"]["text"] = "PRIVATE_SYNTHETIC_MARKER"
        classes = self.structure()
        classes["targets"]["instagram"]["classes"] = ["private synthetic words"]
        target = self.structure()
        target["targets"]["unknown"] = target["targets"].pop("instagram")
        for value in [extra, classes, target]:
            with self.assertRaisesRegex(RuntimeError, "metadata-only schema"):
                probe.validate_structure(json.dumps(value).encode())

    def test_malformed_duplicate_and_oversized_diagnostics_rejected(self):
        for raw in [b"{", b"\xff", b" " * 32769, b'{"scope":1,"scope":2,"targets":{}}']:
            with self.assertRaises(RuntimeError): probe.validate_structure(raw)

    def test_node_and_position_bounds_rejected(self):
        for key, value in [("nodes", 193), ("nodes", True), ("positions", [{}] * 13), ("scrolls", [{}] * 17)]:
            data = self.structure()
            data["targets"]["instagram"][key] = value
            with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(data).encode())

    def test_dense_tiktok_budget_does_not_expand_other_sources(self):
        data = self.structure()
        data['targets']['tiktok'] = data['targets'].pop('instagram')
        data['targets']['tiktok']['nodes'] = 384
        self.assertEqual(probe.validate_structure(json.dumps(data).encode()), data)
        data['targets']['tiktok']['nodes'] = 385
        with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(data).encode())

    def geometry(self):
        value = self.structure()
        target = value["targets"]["instagram"]
        target["nodes"] = 4
        layout = {"id": "synthetic_media", "slot": 3, "row": -1, "column": -1,
                  "left": 0, "top": 100, "right": 400, "bottom": 600}
        target.update(geometryRevision=1, viewport={"left": 0, "top": 0, "right": 400, "bottom": 800},
                      anchors=[dict(layout, ancestors=[dict(layout, id="synthetic_container", slot=1)])])
        return value

    def test_bounded_geometry_and_scan_local_association_accepted(self):
        value = self.geometry()
        self.assertEqual(probe.validate_structure(json.dumps(value).encode()), value)

    def test_fixed_interface_tokens_are_facebook_only_and_strictly_bounded(self):
        value = self.association()
        target = value["targets"].pop("instagram")
        value["targets"]["facebook"] = target
        target.update(interfaceRevision=1, interfaceLabels=[{
            "token": "reels", "class": "android.widget.Button", "left": 0, "top": 0,
            "right": 40, "bottom": 40, "selected": True, "clickable": True}])
        self.assertEqual(probe.validate_structure(json.dumps(value).encode()), value)
        import copy
        for mutate in [lambda t: t.update(interfaceRevision=True),
                       lambda t: t["interfaceLabels"][0].update(token="PRIVATE_SYNTHETIC_MARKER"),
                       lambda t: t["interfaceLabels"][0].update(description="PRIVATE_SYNTHETIC_MARKER"),
                       lambda t: t["interfaceLabels"][0].update(selected=1),
                       lambda t: t["interfaceLabels"][0].update(left=100),
                       lambda t: t.update(interfaceLabels=t["interfaceLabels"] * 17)]:
            changed = copy.deepcopy(value)
            mutate(changed["targets"]["facebook"])
            with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(changed).encode())

        value["targets"]["instagram"] = value["targets"].pop("facebook")
        with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(value).encode())

    def test_interface_two_preserves_only_fixed_tokens_and_bounded_ancestors(self):
        import copy
        value = self.association()
        target = value["targets"].pop("instagram")
        value["targets"]["facebook"] = target
        ancestor = target["anchors"][0]["ancestors"][0]
        target.update(interfaceRevision=2, interfaceLabels=[{
            "token": "reels_tab", "class": "android.view.View", "left": 0, "top": 0,
            "right": 40, "bottom": 40, "selected": True, "clickable": True,
            "slot": 3, "ancestors": [ancestor]}])
        self.assertEqual(probe.validate_structure(json.dumps(value).encode()), value)
        for mutate in [lambda t: t["interfaceLabels"][0]["ancestors"][0].update(text="PRIVATE_SYNTHETIC_MARKER"),
                       lambda t: t["interfaceLabels"][0].update(slot=1),
                       lambda t: t["interfaceLabels"][0].update(ancestors=[ancestor] * 4),
                       lambda t: t["interfaceLabels"][0].update(token="PRIVATE_SYNTHETIC_MARKER")]:
            changed = copy.deepcopy(value)
            mutate(changed["targets"]["facebook"])
            with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(changed).encode())

        target["interfaceRevision"] = 3
        target["interfaceLabels"][0]["token"] = "play_video"
        self.assertEqual(probe.validate_structure(json.dumps(value).encode()), value)
        value["targets"]["instagram"] = value["targets"].pop("facebook")
        self.assertEqual(probe.validate_structure(json.dumps(value).encode()), value)
        target["interfaceLabels"][0]["token"] = "PRIVATE_SYNTHETIC_MARKER"
        with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(value).encode())

    def test_geometry_rejects_content_extra_fields_and_invalid_revision(self):
        for change in [lambda t: t.update(geometryRevision=2),
                       lambda t: t.update(geometryRevision=True),
                       lambda t: t["anchors"][0].update(text="PRIVATE_SYNTHETIC_MARKER"),
                       lambda t: t["anchors"][0]["ancestors"][0].update(url="synthetic"),
                       lambda t: t["viewport"].update(image="synthetic"),
                       lambda t: t.pop("geometryRevision")]:
            value = self.geometry()
            change(value["targets"]["instagram"])
            with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(value).encode())

    def test_geometry_rejects_invalid_bounds_slots_and_depth(self):
        for change in [lambda t: t.update(anchors=t["anchors"] * 17),
                       lambda t: t["anchors"][0].update(slot=4),
                       lambda t: t["anchors"][0].update(slot=True),
                       lambda t: t["anchors"][0].update(left=401),
                       lambda t: t["anchors"][0].update(bottom=float("inf")),
                       lambda t: t["anchors"][0].update(ancestors=t["anchors"][0]["ancestors"] * 4),
                       lambda t: t["anchors"][0]["ancestors"][0].update(slot=3),
                       lambda t: t.update(anchors=t["anchors"] * 2)]:
            value = self.geometry()
            change(value["targets"]["instagram"])
            with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(value).encode())

    def association(self):
        value = self.geometry()
        value["targets"]["instagram"]["association"] = {
            "revision": 1, "startSequence": 2, "endSequence": 2, "durationMs": 20, "stable": True,
            "events": [{"source": "synthetic_pager", "sourceClass": "android.view.View",
                "from": -1, "to": -1, "items": -1, "scrollY": -1, "maxScrollY": -1, "deltaY": 40,
                "sourceBounds": {"left": 0, "top": 0, "right": 400, "bottom": 800},
                "sequence": 2, "ageMs": 80, "deliveryDelayMs": 12}]}
        return value

    def test_bracketed_events_and_explicitly_unstable_scans_accepted(self):
        for stable in [True, False]:
            value = self.association()
            trace = value["targets"]["instagram"]["association"]
            trace.update(stable=stable, endSequence=2 if stable else 3)
            self.assertEqual(probe.validate_structure(json.dumps(value).encode()), value)

    def test_association_rejects_fabricated_stability_and_stale_or_future_events(self):
        for change in [lambda t: t.update(stable=False), lambda t: t.update(endSequence=1),
                       lambda t: t.update(startSequence=True), lambda t: t.update(durationMs=2_001),
                       lambda t: t.update(revision=True), lambda t: t.update(revision=2),
                       lambda t: t["events"][0].update(sequence=3), lambda t: t["events"][0].update(sequence=0),
                       lambda t: t["events"][0].update(ageMs=-1), lambda t: t["events"][0].update(ageMs=2_001),
                       lambda t: t["events"][0].update(deliveryDelayMs=2_001),
                       lambda t: t["events"].append(t["events"][0]),
                       lambda t: t["events"][0]["sourceBounds"].update(text="PRIVATE_SYNTHETIC_MARKER"),
                       lambda t: t["events"][0].update(sourceBounds=None),
                       lambda t: t.update(windowId=123)]:
            value = self.association()
            change(value["targets"]["instagram"]["association"])
            with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(value).encode())

    def test_association_requires_geometry_and_rejects_parallel_old_scrolls(self):
        for key in ["geometryRevision", "viewport", "anchors"]:
            value = self.association()
            del value["targets"]["instagram"][key]
            with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(value).encode())
        value = self.association()
        target = value["targets"]["instagram"]
        target["scrolls"] = [{k: v for k, v in target["association"]["events"][0].items()
                              if k not in {"sourceBounds", "sequence", "ageMs", "deliveryDelayMs"}}]
        with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(value).encode())

    def test_unavailable_source_has_no_source_metadata(self):
        value = self.association()
        event = value["targets"]["instagram"]["association"]["events"][0]
        event.update(source="", sourceClass="", sourceBounds=None)
        self.assertEqual(probe.validate_structure(json.dumps(value).encode()), value)

    def test_association_rejects_reordered_ages_and_oversized_events(self):
        value = self.association()
        trace = value["targets"]["instagram"]["association"]
        trace["events"].insert(0, dict(trace["events"][0], sequence=1, ageMs=40))
        with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(value).encode())
        trace["events"] = [dict(trace["events"][0], sequence=i + 1) for i in range(17)]
        trace.update(startSequence=17, endSequence=17)
        with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(value).encode())


if __name__ == "__main__":
    unittest.main()
