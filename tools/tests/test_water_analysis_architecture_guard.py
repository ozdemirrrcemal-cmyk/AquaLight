import importlib.util
from pathlib import Path
import tempfile
import unittest

MODULE_PATH = Path(__file__).resolve().parents[1] / 'water_analysis_architecture_guard.py'
SPEC = importlib.util.spec_from_file_location('water_analysis_architecture_guard', MODULE_PATH)
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


class WaterArchitectureGuardTest(unittest.TestCase):
    def test_platform_and_data_imports_are_rejected_in_engine_and_ui(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, 'application/aquarium/health/water/Engine.kt',
                       'import android.content.Context\nimport com.aqua.aqualight.data.Store\n')
            self.write(root, 'ui/tabs/aquarium/detail/health/Screen.kt',
                       'import com.aqua.aqualight.data.Store\n')
            self.assertEqual(3, len(guard.violations(root)))

    def test_ambient_time_is_rejected_but_explicit_time_is_allowed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = self.write(root, 'application/aquarium/health/water/Engine.kt',
                              'fun assess() = System.currentTimeMillis()')
            self.assertEqual(1, len(guard.violations(root)))
            path.write_text('fun assess(capturedAtMillis: Long) = capturedAtMillis')
            self.assertEqual([], guard.violations(root))

    def test_production_boundaries_pass(self):
        self.assertEqual([], guard.violations(MODULE_PATH.parent.parent))

    @staticmethod
    def write(root, relative, content):
        path = root / guard.BASE / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        return path
