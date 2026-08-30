import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GUARD_PATH = ROOT / "tools/cooling_compose_architecture_guard.py"
SPEC = importlib.util.spec_from_file_location("cooling_compose_architecture_guard", GUARD_PATH)
assert SPEC is not None and SPEC.loader is not None
GUARD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GUARD)


class CoolingComposeArchitectureGuardTest(unittest.TestCase):
    def test_repository_passes(self) -> None:
        self.assertEqual([], GUARD.validate_repository(ROOT))

    def test_raw_dp_outside_design_contract_is_rejected(self) -> None:
        path = GUARD.HERO_ROOT / "ExampleLayer.kt"
        errors = GUARD.validate_cooling_source(path, "package example\nval padding = 12.dp\n")
        self.assertTrue(any("dimensions" in error for error in errors), errors)

    def test_artwork_hash_is_immutable(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for index, relative_path in enumerate(GUARD.ARTWORK_PARTS):
                path = repository / relative_path
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("Y2hhbmdlZA==" if index == 0 else "", encoding="utf-8")
            errors = GUARD.validate_artwork(repository)
            self.assertTrue(any("hash changed" in error for error in errors), errors)

    def test_navigation_guard_keeps_cooling_guard_wired(self) -> None:
        source = (ROOT / "tools/navigation_guard.py").read_text(encoding="utf-8")
        self.assertIn(
            "from cooling_compose_architecture_guard import validate_repository as validate_cooling_compose",
            source,
        )
        self.assertIn("validate_cooling_compose(ROOT)", source)


if __name__ == "__main__":
    unittest.main()
