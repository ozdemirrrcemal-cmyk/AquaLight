import importlib.util
import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GUARD_PATH = ROOT / "tools/device_root_ui_architecture_guard.py"
SPEC = importlib.util.spec_from_file_location("device_root_ui_architecture_guard", GUARD_PATH)
assert SPEC is not None and SPEC.loader is not None
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class DeviceRootUiArchitectureGuardTest(unittest.TestCase):
    def test_repository_passes(self) -> None:
        result = subprocess.run(
            [sys.executable, str(GUARD_PATH)],
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_hard_coded_root_text_is_rejected(self) -> None:
        source = """<?xml version=\"1.0\" encoding=\"utf-8\"?>
<FrameLayout
    xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:background=\"@color/background_color\">
    <include android:id=\"@+id/appHeader\" layout=\"@layout/layout_aqua_header\" />
    <TextView android:text=\"Cooling\" />
</FrameLayout>
"""

        errors = GUARD.validate_layout_contract(Path("root.xml"), source)

        self.assertTrue(any("String resources" in error for error in errors), errors)

    def test_parallel_toolbar_is_rejected(self) -> None:
        source = """<?xml version=\"1.0\" encoding=\"utf-8\"?>
<FrameLayout
    xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:background=\"@color/background_color\">
    <include android:id=\"@+id/appHeader\" layout=\"@layout/layout_aqua_header\" />
    <androidx.appcompat.widget.Toolbar />
</FrameLayout>
"""

        errors = GUARD.validate_layout_contract(Path("root.xml"), source)

        self.assertTrue(any("parallel toolbar" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
