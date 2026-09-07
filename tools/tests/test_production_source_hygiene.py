from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
MAIN_ROOT = ROOT / "app" / "src" / "main"
MAIN_CODE_ROOT = MAIN_ROOT / "java"
MAIN_RES_ROOT = MAIN_ROOT / "res"

FORBIDDEN_CODE_TOKENS = {
    "BuildConfig.DEBUG": "runtime build-type branching belongs outside src/main",
    "DEBUG_PREVIEW": "preview fixtures belong outside src/main",
    "DEBUG_FIXTURE": "debug fixtures belong outside src/main",
}

FORBIDDEN_RESOURCE_PHRASES = {
    "local preview": "preview-only user copy belongs outside src/main resources",
    "yerel önizleme": "preview-only user copy belongs outside src/main resources",
    "debug build": "debug-build copy belongs outside src/main resources",
    "demo-only": "demo-only copy belongs outside src/main resources",
}


class ProductionSourceHygieneTest(unittest.TestCase):

    def test_main_code_has_no_debug_runtime_behavior(self) -> None:
        violations: list[str] = []
        for path in MAIN_CODE_ROOT.rglob("*"):
            if path.suffix not in {".kt", ".java"}:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            for token, reason in FORBIDDEN_CODE_TOKENS.items():
                if token in text:
                    relative = path.relative_to(ROOT)
                    violations.append(f"{relative}: {reason}: {token}")

        self.assertEqual([], violations, "\n".join(violations))

    def test_main_resources_have_no_debug_preview_copy(self) -> None:
        violations: list[str] = []
        for path in MAIN_RES_ROOT.rglob("*.xml"):
            text = path.read_text(encoding="utf-8", errors="ignore").casefold()
            for phrase, reason in FORBIDDEN_RESOURCE_PHRASES.items():
                if phrase.casefold() in text:
                    relative = path.relative_to(ROOT)
                    violations.append(f"{relative}: {reason}: {phrase}")

        self.assertEqual([], violations, "\n".join(violations))


if __name__ == "__main__":
    unittest.main()
