from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from generate_stage14_final_evidence import (
    INSTRUMENTATION_STAGE14_JSON,
    QUALITY_STAGE14_JSON,
    FinalEvidenceFailure,
    generate,
)

TAG = "v1.2.3"
VERSION = "1.2.3"
COMMIT = "c" * 40


def write(path: Path, content: bytes | str = "evidence\n") -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(content, bytes):
        path.write_bytes(content)
    else:
        path.write_text(content, encoding="utf-8")
    return path


def write_json(
    path: Path,
    *,
    commit: str | None = None,
    extra: dict[str, object] | None = None,
) -> Path:
    value: dict[str, object] = {
        "schemaVersion": 1,
        "passed": True,
    }
    if commit is not None:
        value["releaseCommit"] = commit
    if extra:
        value.update(extra)
    return write(path, json.dumps(value) + "\n")


class EvidenceFixture:
    def __init__(self, root: Path, include_apk: bool = False) -> None:
        self.release = root / "final-release"
        self.quality = self.release / "validation/quality"
        self.instrumentation = self.release / "validation/instrumentation"
        self.codeql = self.release / "supply-chain/security/codeql"
        self.blocker = self.release / "validation/release-blocker-inventory.json"
        self.manual = self.release / "validation/manual-acceptance.json"
        self.json_output = self.release / "supply-chain/final-evidence.json"
        self.markdown_output = self.release / "supply-chain/final-evidence.md"
        self.include_apk = include_apk
        self._create()

    def _create(self) -> None:
        policy_source = json.loads(
            (ROOT / "config/commercial/stage14-validation-policy.json").read_text(
                encoding="utf-8"
            )
        )
        self.policy = write_json(
            self.quality / "release-quality/stage14-policy-validation.json",
            extra={
                "policyId": policy_source["policyId"],
                "sourceSha256": "1" * 64,
                "canonicalSha256": "2" * 64,
                "requiredArtifacts": policy_source["requiredArtifacts"],
            },
        )
        write_json(self.quality / "release-quality/dependency-integrity.json")
        write_json(
            self.quality
            / "app/build/reports/stage14/detekt-policy-summary.json"
        )
        write_json(
            self.quality
            / "app/build/reports/coverage/test/debug/"
            "critical-package-thresholds.json"
        )
        for variant in ("debug", "staging", "releaseSmoke", "release"):
            write(
                self.quality
                / f"app/build/reports/lint-results-{variant}.xml",
                "<issues />\n",
            )
        write(
            self.quality / "app/build/reports/detekt/detekt.sarif",
            "{}\n",
        )
        for variant in (
            "testDebugUnitTest",
            "testStagingUnitTest",
            "testReleaseSmokeUnitTest",
            "testReleaseUnitTest",
        ):
            write(
                self.quality
                / f"app/build/test-results/{variant}/TEST-commercial.xml",
                "<testsuite tests=\"1\" failures=\"0\" />\n",
            )
        write(
            self.quality / "app/build/reports/coverage/test/debug/report.xml",
            "<report />\n",
        )
        quality_stage14 = self.quality / "release-quality/stage14-evidence"
        for name in QUALITY_STAGE14_JSON:
            write_json(quality_stage14 / name, commit=COMMIT)

        write(self.codeql / "results/commercial.sarif", "{}\n")
        write_json(self.codeql / "codeql-summary.json", commit=COMMIT)

        instrumentation_stage14 = self.instrumentation / "stage14-evidence"
        for api_level in (27, 36):
            write(
                instrumentation_stage14
                / f"junit-api-{api_level}/TEST-commercial.xml",
                "<testsuite tests=\"1\" failures=\"0\" />\n",
            )
        for name in INSTRUMENTATION_STAGE14_JSON:
            write_json(instrumentation_stage14 / name, commit=COMMIT)

        write_json(self.blocker, commit=COMMIT)
        write_json(self.manual, commit=COMMIT)

        artifacts = self.release / "artifacts"
        supply_chain = self.release / "supply-chain"
        write(artifacts / f"AquaLight-{VERSION}.aab", b"signed-aab")
        write(
            artifacts / f"AquaLight-{VERSION}.aab.sha256",
            "0" * 64 + f"  AquaLight-{VERSION}.aab\n",
        )
        write(
            artifacts / "signed-aab-verification.txt",
            "jar verified\n",
        )
        write(
            artifacts / f"AquaLight-{VERSION}-mapping.txt",
            "mapping\n",
        )
        write(artifacts / "SHA256SUMS", "0" * 64 + "  AquaLight.aab\n")
        write(
            supply_chain / f"AquaLight-{VERSION}.aab.spdx.json",
            "{}\n",
        )
        write(
            supply_chain
            / f"attestations/AquaLight-{VERSION}.aab.provenance.json",
            "{}\n",
        )
        write(
            supply_chain / f"attestations/AquaLight-{VERSION}.aab.sbom.json",
            "{}\n",
        )
        if self.include_apk:
            write(artifacts / f"AquaLight-{VERSION}.apk", b"signed-apk")
            write(
                artifacts / f"AquaLight-{VERSION}.apk.sha256",
                "0" * 64 + f"  AquaLight-{VERSION}.apk\n",
            )
            write(
                artifacts / "signed-apk-verification.txt",
                "apk verified\n",
            )
            write(
                supply_chain / f"AquaLight-{VERSION}.apk.spdx.json",
                "{}\n",
            )
            write(
                supply_chain
                / f"attestations/AquaLight-{VERSION}.apk.provenance.json",
                "{}\n",
            )
            write(
                supply_chain / f"attestations/AquaLight-{VERSION}.apk.sbom.json",
                "{}\n",
            )

    def generate(self) -> dict[str, object]:
        return generate(
            policy_path=self.policy,
            quality_root=self.quality,
            instrumentation_root=self.instrumentation,
            codeql_root=self.codeql,
            release_root=self.release,
            blocker=self.blocker,
            manual=self.manual,
            release_tag=TAG,
            release_version=VERSION,
            commit=COMMIT,
            include_apk=self.include_apk,
            json_output=self.json_output,
            markdown_output=self.markdown_output,
        )


class FinalEvidenceTest(unittest.TestCase):
    def test_complete_evidence_generates_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = EvidenceFixture(Path(temporary))
            summary = fixture.generate()

            self.assertTrue(summary["passed"])
            self.assertEqual(29, summary["artifactContractCount"])
            self.assertEqual(29, len(summary["artifacts"]))
            self.assertTrue(fixture.json_output.is_file())
            self.assertTrue(fixture.markdown_output.is_file())

    def test_optional_apk_is_explicitly_not_requested(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = EvidenceFixture(Path(temporary))
            summary = fixture.generate()

        apk = next(
            artifact
            for artifact in summary["artifacts"]
            if artifact["id"] == "release-apk"
        )
        self.assertEqual("not-requested", apk["status"])
        self.assertFalse(apk["requiredThisRelease"])

    def test_requested_apk_requires_complete_supply_chain(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = EvidenceFixture(Path(temporary), include_apk=True)
            fixture.generate()
            (
                fixture.release
                / f"supply-chain/attestations/AquaLight-{VERSION}.apk.sbom.json"
            ).unlink()

            with self.assertRaisesRegex(FinalEvidenceFailure, "APK SBOM"):
                fixture.generate()

    def test_missing_required_artifact_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = EvidenceFixture(Path(temporary))
            (
                fixture.quality
                / "app/build/reports/lint-results-release.xml"
            ).unlink()

            with self.assertRaisesRegex(FinalEvidenceFailure, "release Android Lint"):
                fixture.generate()

    def test_unknown_stage14_json_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = EvidenceFixture(Path(temporary))
            write_json(
                fixture.instrumentation
                / "stage14-evidence/bypass-api-27.json",
                commit=COMMIT,
            )

            with self.assertRaisesRegex(FinalEvidenceFailure, "unknown"):
                fixture.generate()

    def test_unknown_release_binary_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = EvidenceFixture(Path(temporary))
            write(fixture.release / "artifacts/unreviewed.apk", b"unknown")

            with self.assertRaisesRegex(
                FinalEvidenceFailure,
                "release artifact set mismatch",
            ):
                fixture.generate()

    def test_supplemental_logs_are_hashed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = EvidenceFixture(Path(temporary))
            extra = write(
                fixture.quality / "release-quality/build.log",
                "commercial build log\n",
            )
            summary = fixture.generate()

        relative = extra.relative_to(fixture.release).as_posix()
        self.assertIn(
            relative,
            [item["path"] for item in summary["supplementalEvidence"]],
        )

    def test_cross_commit_evidence_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = EvidenceFixture(Path(temporary))
            write_json(
                fixture.instrumentation
                / "stage14-evidence/clean-install-api-27.json",
                commit="d" * 40,
            )

            with self.assertRaisesRegex(FinalEvidenceFailure, "does not belong"):
                fixture.generate()

    def test_policy_and_implementation_must_match(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = EvidenceFixture(Path(temporary))
            policy = json.loads(fixture.policy.read_text(encoding="utf-8"))
            policy["requiredArtifacts"] = policy["requiredArtifacts"][:-1]
            fixture.policy.write_text(json.dumps(policy), encoding="utf-8")

            with self.assertRaisesRegex(
                FinalEvidenceFailure,
                "policy artifacts differ",
            ):
                fixture.generate()

    def test_release_identity_is_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = EvidenceFixture(Path(temporary))
            with self.assertRaisesRegex(FinalEvidenceFailure, "do not match"):
                generate(
                    policy_path=fixture.policy,
                    quality_root=fixture.quality,
                    instrumentation_root=fixture.instrumentation,
                    codeql_root=fixture.codeql,
                    release_root=fixture.release,
                    blocker=fixture.blocker,
                    manual=fixture.manual,
                    release_tag="v1.2.4",
                    release_version=VERSION,
                    commit=COMMIT,
                    include_apk=False,
                    json_output=fixture.json_output,
                    markdown_output=fixture.markdown_output,
                )


if __name__ == "__main__":
    unittest.main()
