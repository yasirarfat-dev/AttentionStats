"""Privacy/context boundaries for the operator-only fixed-token probe."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from probe_builtin_autoscroll import inspect

PACKAGE = "com.example.synthetic"


def screen(*nodes):
    return '<hierarchy>' + ''.join(nodes) + '</hierarchy>'


def node(**attributes):
    attributes = {"package": PACKAGE, "class": "android.widget.Switch", **attributes}
    return '<node ' + ' '.join(f'{key}="{value}"' for key, value in attributes.items()) + '/>'


class ProbeBoundaries(unittest.TestCase):
    def test_exact_fixed_control_only(self):
        matches = inspect(screen(node(text="Auto-scroll", checked="true"),
                                 node(text="Synthetic caption mentioning auto-scroll")), PACKAGE)
        self.assertEqual([label for label, _ in matches], ["auto-scroll", "auto-scroll phrase (unqualified)"])
        self.assertEqual(matches[0][1].get("checked"), "true")

    def test_content_description_and_normalization(self):
        matches = inspect(screen(node(**{"content-desc": " AUTO   SCROLL "})), PACKAGE)
        self.assertEqual([label for label, _ in matches], ["auto scroll"])

    def test_foreign_window_rejected(self):
        with self.assertRaises(ValueError):
            inspect(screen(node(text="Auto-scroll", package="com.example.foreign")), PACKAGE)

    def test_system_chrome_never_qualifies(self):
        self.assertEqual(inspect(screen(node(text="Auto-scroll", package="com.android.systemui")), PACKAGE), [])

    def test_password_rejects_entire_context(self):
        with self.assertRaises(ValueError):
            inspect(screen(node(text="Auto-scroll"), node(password="true")), PACKAGE)

    def test_editable_rejects_entire_context(self):
        with self.assertRaises(ValueError):
            inspect(screen(node(text="Auto-scroll"), node(**{"class": "android.widget.EditText"})), PACKAGE)

    def test_budget_is_bounded(self):
        with self.assertRaises(ValueError):
            inspect(screen(*[node(text="Auto-scroll") for _ in range(513)]), PACKAGE)

    def test_empty_is_unavailable(self):
        with self.assertRaises(ValueError):
            inspect(screen(), PACKAGE)


if __name__ == "__main__":
    unittest.main()
