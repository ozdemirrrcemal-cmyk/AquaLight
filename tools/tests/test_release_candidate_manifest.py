from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from release_candidate_manifest import (
    CandidateManifestFailure,
    build_manifest,
    candidate_approval_identity,
    expected_files,
    verify_manifest,
    write_json,
)

TAG = "v1.2.3"
VERSION = "1.2.3"
COMMIT = "a" * 40
RUN_ID = "123456"
REPOSITORY = "owner/aqualight"
CERTIFICATE = "b" * 64


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class CandidateFixture:
    def __init__(self, root: Path) -> None:
        self.root = root / "candidate-release"
        self.manifest = self.root / "CANDIDATE.json"
        self._create()

    def _write(self, relative: str, content: bytes | str) -> Path:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(content, bytes):
            path.write_bytes(content)
        else:
            path.write_text(content, encoding="utf-8")
        return path

    def _create(self) -> None:
        for artifact_id, relative in expected_files(VERSION):
            if artifact_id in {"release-aab", "release-apk"}:
                self._write(relative, artifact_id.encode("utf-8"))
            elif artifact_id in {
                "stage14-policy",
                "codeql-summary",
                "candidate-blocker-inventory",
            }:
                value: dict[str, object] = {
                    "schemaVersion": 1,
                    "passed": True,
                }
                if artifact_id != "stage14-policy":
                    value["releaseCommit"] = COMMIT
                self._write(relative, json.dumps(value) + "\n")
            elif artifact_id not in {
                "release-checksums",
                "aab-checksum",
                "apk-checksum",
            }:
                self._write(relative, f"{artifact_id}\n")

        aab_name = f"AquaLight-{VERSION}.aab"
        apk_name = f"AquaLight-{VERSION}.apk"
        aab = self.root / f"artifacts/{aab_name}"
        apk = self.root / f"artifacts/{apk_name}"
        self._write(
            f"artifacts/{aab_name}.sha256",
            f"{sha256(aab)}  {aab_name}\n",
        )
        self._write(
            f"artifacts/{apk_name}.sha256",
            f"{sha256(apk)}  {apk_name}\n",
        )
        self._write(
            "artifacts/SHA256SUMS",
            f"{sha256(aab)}  {aab_name}\n{sha256(apk)}  {apk_name}\n",
        )

    def build(self) -> dict[str, object]:
        return build_manifest(
            root=self.root,
            release_tag=TAG,
            release_version=VERSION,
            release_commit=COMMIT,
            workflow_run_id=RUN_ID,
            repository=REPOSITORY,
            version_name=VERSION,
            version_code=42,
            signing_certificate_sha256=CERTIFICATE,
        )

    def write_manifest(self) -> dict[str, object]:
        manifest = self.build()
        write_json(self.manifest, manifest)
        return manifest


class ReleaseCandidateManifestTest(unittest.TestCase):
    def test_candidate_round_trip_is_verified(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = CandidateFixture(Path(temporary))
            manifest = fixture.write_manifest()
            summary = verify_manifest(
                root=fixture.root,
                manifest_path=fixture.manifest,
                release_tag=TAG,
                release_commit=COMMIT,
                workflow_run_id=RUN_ID,
                repository=REPOSITORY,
            )

            self.assertTrue(summary["passed"])
            self.assertEqual(
                manifest["artifactDigests"],
                summary["artifactDigests"],
            )
            self.assertEqual(64, len(summary["manifestSha256"]))

    def test_manual_approval_identity_binds_all_candidate_digests(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = CandidateFixture(Path(temporary))
            fixture.write_manifest()
            identity = candidate_approval_identity(
                fixture.manifest.read_bytes()
            )

            self.assertEqual(RUN_ID, identity["workflowRunId"])
            self.assertEqual(CERTIFICATE, identity["signingCertificateSha256"])
            self.assertEqual(
                sha256(fixture.root / f"artifacts/AquaLight-{VERSION}.apk"),
                identity["apkSha256"],
            )

    def test_tampered_binary_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = CandidateFixture(Path(temporary))
            fixture.write_manifest()
            (
                fixture.root / f"artifacts/AquaLight-{VERSION}.apk"
            ).write_bytes(b"tampered")

            with self.assertRaisesRegex(
                CandidateManifestFailure,
                "identity mismatch",
            ):
                verify_manifest(
                    root=fixture.root,
                    manifest_path=fixture.manifest,
                    release_tag=TAG,
                    release_commit=COMMIT,
                    workflow_run_id=RUN_ID,
                    repository=REPOSITORY,
                )

    def test_wrong_workflow_run_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = CandidateFixture(Path(temporary))
            fixture.write_manifest()

            with self.assertRaisesRegex(
                CandidateManifestFailure,
                "workflowRunId",
            ):
                verify_manifest(
                    root=fixture.root,
                    manifest_path=fixture.manifest,
                    release_tag=TAG,
                    release_commit=COMMIT,
                    workflow_run_id="999",
                    repository=REPOSITORY,
                )

    def test_apk_is_mandatory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = CandidateFixture(Path(temporary))
            (
                fixture.root / f"artifacts/AquaLight-{VERSION}.apk"
            ).unlink()

            with self.assertRaisesRegex(
                CandidateManifestFailure,
                "release-apk",
            ):
                fixture.build()

    def test_checksum_manifest_must_match_both_binaries(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = CandidateFixture(Path(temporary))
            (
                fixture.root / "artifacts/SHA256SUMS"
            ).write_text(
                "0" * 64 + f"  AquaLight-{VERSION}.aab\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                CandidateManifestFailure,
                "does not match",
            ):
                fixture.build()


if __name__ == "__main__":
    unittest.main()
