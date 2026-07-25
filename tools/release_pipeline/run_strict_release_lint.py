#!/usr/bin/env python3
"""Run release lint with the project baseline disabled, then restore the checkout exactly."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

BASELINE_ASSIGNMENT = "        baseline = lintBaselineFile.asFile\n"
WARNINGS_AS_ERRORS_ASSIGNMENT = "        warningsAsErrors true\n"
BASELINE_DEPENDENCY_PATTERN = re.compile(
    r"\ntasks\.configureEach \{ task ->\n"
    r"    def taskName = task\.name\.toLowerCase\(\)\n"
    r"    if \(task\.name != \"prepareLintBaseline\" && taskName\.contains\(\"lint\"\)\) \{\n"
    r"        task\.dependsOn\(prepareLintBaseline\)\n"
    r"    \}\n"
    r"\}\n"
)
BASELINE_FILTER_MESSAGE = "filtered out because they are listed in the baseline"


class StrictLintFailure(RuntimeError):
    """Raised when strict lint cannot prove baseline-free execution."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-file", type=Path, default=Path("app/build.gradle"))
    parser.add_argument("--gradle", default="./gradlew")
    parser.add_argument(
        "--output-directory",
        type=Path,
        default=Path("release-quality/lint/strict-release"),
    )
    return parser.parse_args()


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def atomic_write(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", dir=str(path.parent)
    )
    try:
        with os.fdopen(descriptor, "wb") as output:
            output.write(content)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary_name, path)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)


def strict_build_script(original: str) -> str:
    if original.count(BASELINE_ASSIGNMENT) != 1:
        raise StrictLintFailure(
            "Expected exactly one Android Lint baseline assignment in app/build.gradle."
        )
    if original.count(WARNINGS_AS_ERRORS_ASSIGNMENT) != 1:
        raise StrictLintFailure(
            "Expected exactly one warningsAsErrors assignment in app/build.gradle."
        )

    transformed = original.replace(
        BASELINE_ASSIGNMENT,
        "        // Commercial strict lint runs without a baseline.\n",
        1,
    )
    transformed = transformed.replace(
        WARNINGS_AS_ERRORS_ASSIGNMENT,
        "        warningsAsErrors false\n",
        1,
    )
    transformed, dependency_replacements = BASELINE_DEPENDENCY_PATTERN.subn(
        "\n// Commercial strict lint intentionally does not materialize lint-baseline.xml.\n",
        transformed,
        count=1,
    )
    if dependency_replacements != 1:
        raise StrictLintFailure(
            "Expected exactly one prepareLintBaseline dependency block in app/build.gradle."
        )
    if "baseline = lintBaselineFile.asFile" in transformed:
        raise StrictLintFailure("Lint baseline assignment survived strict transformation.")
    if "task.dependsOn(prepareLintBaseline)" in transformed:
        raise StrictLintFailure("Lint baseline task dependency survived strict transformation.")
    if "warningsAsErrors true" in transformed:
        raise StrictLintFailure("Strict lint still promotes every warning to an error.")
    return transformed


def run_and_tee(command: list[str], log_path: Path) -> int:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        assert process.stdout is not None
        for line in process.stdout:
            sys.stdout.write(line)
            log.write(line)
        return process.wait()


def main() -> int:
    args = parse_args()
    project_file = args.project_file.resolve()
    output_directory = args.output_directory.resolve()
    output_directory.mkdir(parents=True, exist_ok=True)

    if not project_file.is_file():
        print(f"Project build file is missing: {project_file}", file=sys.stderr)
        return 1

    original_bytes = project_file.read_bytes()
    original_hash = sha256_bytes(original_bytes)
    gradle_status = 1
    verifier_status = 1
    restoration_verified = False
    transformed_hash = ""
    log_path = output_directory / "strict-release-lint.log"
    source_xml = Path("app/build/reports/lint-results-release.xml")
    source_html = Path("app/build/reports/lint-results-release.html")
    evidence_xml = output_directory / "lint-results-release-strict.xml"
    evidence_html = output_directory / "lint-results-release-strict.html"
    lint_summary = output_directory / "lint-summary.json"
    run_summary = output_directory / "strict-release-lint-run.json"

    try:
        original_text = original_bytes.decode("utf-8")
        transformed_text = strict_build_script(original_text)
        transformed_bytes = transformed_text.encode("utf-8")
        transformed_hash = sha256_bytes(transformed_bytes)
        atomic_write(project_file, transformed_bytes)

        active_text = project_file.read_text(encoding="utf-8")
        if "baseline = lintBaselineFile.asFile" in active_text:
            raise StrictLintFailure("Strict lint checkout still configures a baseline.")
        if "task.dependsOn(prepareLintBaseline)" in active_text:
            raise StrictLintFailure("Strict lint checkout still prepares the baseline.")
        if "warningsAsErrors true" in active_text:
            raise StrictLintFailure("Strict lint checkout still promotes all warnings.")

        for stale_report in (source_xml, source_html):
            stale_report.unlink(missing_ok=True)

        gradle_status = run_and_tee(
            [args.gradle, ":app:lintRelease", "--no-daemon", "--stacktrace"],
            log_path,
        )
    except (OSError, UnicodeError, StrictLintFailure) as error:
        print(f"Strict release lint setup failed: {error}", file=sys.stderr)
    finally:
        atomic_write(project_file, original_bytes)
        restoration_verified = (
            project_file.read_bytes() == original_bytes
            and sha256_bytes(project_file.read_bytes()) == original_hash
        )

    if not restoration_verified:
        print("app/build.gradle was not restored byte-for-byte.", file=sys.stderr)
        return 1

    if source_xml.is_file() and source_xml.stat().st_size > 0:
        shutil.copy2(source_xml, evidence_xml)
    if source_html.is_file() and source_html.stat().st_size > 0:
        shutil.copy2(source_html, evidence_html)

    verifier = Path(__file__).with_name("verify_android_lint_report.py")
    verifier_status = subprocess.run(
        [
            sys.executable,
            str(verifier),
            "--xml",
            str(evidence_xml),
            "--html",
            str(evidence_html),
            "--summary",
            str(lint_summary),
            "--variant",
            "release",
        ],
        check=False,
    ).returncode

    log_text = log_path.read_text(encoding="utf-8", errors="replace") if log_path.exists() else ""
    baseline_filter_detected = BASELINE_FILTER_MESSAGE in log_text.lower()
    approved = (
        gradle_status == 0
        and verifier_status == 0
        and restoration_verified
        and not baseline_filter_detected
    )
    summary = {
        "schemaVersion": 1,
        "approved": approved,
        "baselineConfiguredDuringRun": False,
        "warningsAsErrorsDuringRun": False,
        "baselineFilterMessageDetected": baseline_filter_detected,
        "projectFile": str(project_file),
        "originalBuildScriptSha256": original_hash,
        "strictBuildScriptSha256": transformed_hash,
        "restorationVerified": restoration_verified,
        "gradleExitCode": gradle_status,
        "reportVerifierExitCode": verifier_status,
        "lintXml": str(evidence_xml),
        "lintHtml": str(evidence_html),
        "lintSummary": str(lint_summary),
    }
    run_summary.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(summary, indent=2, sort_keys=True))

    if baseline_filter_detected:
        print("Android Lint reported baseline filtering during the strict run.", file=sys.stderr)
    if gradle_status != 0:
        print(f"Strict release lint Gradle task failed: {gradle_status}", file=sys.stderr)
    if verifier_status != 0:
        print("Strict release lint report verification failed.", file=sys.stderr)
    return 0 if approved else 1


if __name__ == "__main__":
    raise SystemExit(main())
