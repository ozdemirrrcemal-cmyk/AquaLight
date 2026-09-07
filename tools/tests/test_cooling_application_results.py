from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
COOLING_APPLICATION_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight/application/devices/cooling"
COOLING_PRESENTATION_ROOT = ROOT / (
    "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/cooling/presentation"
)

PRESENTATION_FORBIDDEN_TOKENS = {
    "DeviceRuntimeCommandOutcome": "runtime outcomes must be mapped in data adapters",
    "FirmwareError": "firmware failures must not leak into presentation",
    "ProtocolError": "protocol failures must not leak into presentation",
    "UnsupportedOperationException": "presentation must consume typed application failures",
    ".exceptionOrNull(": "presentation must not inspect exception instances",
    ".getOrThrow(": "presentation must not unwrap transport/application exceptions",
}


class CoolingApplicationResultBoundaryTest(unittest.TestCase):

    def test_cooling_application_contracts_do_not_expose_kotlin_result(self) -> None:
        violations: list[str] = []
        for path in COOLING_APPLICATION_ROOT.rglob("*.kt"):
            text = path.read_text(encoding="utf-8", errors="ignore")
            if "Result<" in text:
                violations.append(str(path.relative_to(ROOT)))

        self.assertEqual([], violations, "\n".join(violations))

    def test_cooling_presentation_does_not_depend_on_runtime_failure_details(self) -> None:
        violations: list[str] = []
        for path in COOLING_PRESENTATION_ROOT.rglob("*.kt"):
            text = path.read_text(encoding="utf-8", errors="ignore")
            for token, reason in PRESENTATION_FORBIDDEN_TOKENS.items():
                if token in text:
                    relative = path.relative_to(ROOT)
                    violations.append(f"{relative}: {reason}: {token}")

        self.assertEqual([], violations, "\n".join(violations))


if __name__ == "__main__":
    unittest.main()
