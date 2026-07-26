#!/usr/bin/env python3
"""Validate the immutable AquaLight Stage 14 commercial release contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1
POLICY_ID = "aqualight-stage14-commercial-release"
TAG_PATTERN = r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$"
EMULATOR_API_LEVELS = [27, 37]
VISUAL_PROFILES = [
    "light",
    "dark",
    "large-font-light",
    "large-font-dark",
    "rtl-light",
    "rtl-dark",
]
PIPELINE_ORDER = [
    "guard",
    "dependency-integrity",
    "lint-detekt",
    "unit-test-coverage",
    "codeql",
    "instrumentation-api-27-37",
    "clean-install",
    "upgrade-install",
    "release-signing-build",
    "checksum",
    "sbom-provenance",
    "final-evidence",
    "publication",
]
REQUIRED_SUITES = [
    ("repository-guards", "automated"),
    ("dependency-integrity", "automated"),
    ("static-analysis", "automated"),
    ("unit-tests", "automated"),
    ("critical-package-coverage", "automated"),
    ("codeql", "automated"),
    ("instrumentation", "automated"),
    ("clean-install", "automated"),
    ("upgrade-install", "automated"),
    ("rapid-account-switch", "automated"),
    ("process-recreation-rotation-force-stop", "automated"),
    ("permission-permanent-denial", "automated"),
    ("tank-care-corruption", "automated"),
    ("websocket-account-cleanup", "automated"),
    ("accessibility-profiles", "automated"),
    ("release-blocker-inventory", "automated"),
    ("release-build", "automated"),
    ("supply-chain", "automated"),
    ("final-evidence", "automated"),
    ("physical-phone-reboot", "manual"),
    ("physical-permission-permanent-denial", "manual"),
    ("physical-network-power-interruption", "manual"),
    ("talkback", "manual"),
    ("privacy-terms-approval", "manual"),
    ("physical-device-release-candidate", "manual"),
]
REQUIRED_ARTIFACTS = [
    ("policy-validation", "json", "required"),
    ("dependency-integrity", "json", "required"),
    ("android-lint", "xml", "required"),
    ("detekt", "sarif", "required"),
    ("detekt-policy", "json", "required"),
    ("unit-tests", "junit-xml", "required"),
    ("coverage", "jacoco-xml", "required"),
    ("coverage-thresholds", "json", "required"),
    ("codeql", "sarif", "required"),
    ("instrumentation-api-27", "junit-xml", "required"),
    ("instrumentation-api-37", "junit-xml", "required"),
    ("clean-install", "json", "required"),
    ("upgrade-install", "json", "required"),
    ("rapid-account-switch", "json", "required"),
    ("process-recreation", "json", "required"),
    ("permission-denial", "json", "required"),
    ("tank-care-corruption", "json", "required"),
    ("websocket-account-cleanup", "json", "required"),
    ("accessibility", "json", "required"),
    ("release-blocker-inventory", "json", "required"),
    ("release-aab", "aab", "required"),
    ("release-apk", "apk", "when-apk-requested"),
    ("release-mapping", "text", "required"),
    ("release-checksums", "sha256-manifest", "required"),
    ("release-sbom", "spdx-json", "required"),
    ("release-provenance", "json", "required"),
    ("manual-acceptance", "json", "required"),
    ("final-summary-json", "json", "required"),
    ("final-summary-markdown", "markdown", "required"),
]


class PolicyFailure(ValueError):
    """Raised when a policy is missing, malformed or commercially weaker."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", required=True, type=Path)
    parser.add_argument("--app-gradle", required=True, type=Path)
    parser.add_argument("--emulator-workflow", required=True, type=Path)
    parser.add_argument("--release-workflow", required=True, type=Path)
    parser.add_argument("--summary", required=True, type=Path)
    return parser.parse_args()


def require_object(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise PolicyFailure(f"{path} must be an object")
    return value


def require_exact_keys(value: dict[str, Any], expected: set[str], path: str) -> None:
    actual = set(value)
    missing = sorted(expected - actual)
    unknown = sorted(actual - expected)
    if missing or unknown:
        details = []
        if missing:
            details.append("missing " + ", ".join(missing))
        if unknown:
            details.append("unknown " + ", ".join(unknown))
        raise PolicyFailure(f"{path} has invalid fields: {'; '.join(details)}")


def require_exact(value: Any, expected: Any, path: str) -> None:
    if type(value) is not type(expected) or value != expected:
        raise PolicyFailure(f"{path} must be {expected!r}, got {value!r}")


def validate_release(value: Any) -> None:
    release = require_object(value, "release")
    require_exact_keys(
        release,
        {
            "baseBranch",
            "tagPattern",
            "applicationId",
            "candidateVariant",
            "smokeVariant",
            "minifiedCandidateRequired",
            "signedAabRequired",
            "apkMode",
        },
        "release",
    )
    expected = {
        "baseBranch": "main",
        "tagPattern": TAG_PATTERN,
        "applicationId": "com.aqua.aqualight",
        "candidateVariant": "release",
        "smokeVariant": "releaseSmoke",
        "minifiedCandidateRequired": True,
        "signedAabRequired": True,
        "apkMode": "optional-on-request",
    }
    for key, expected_value in expected.items():
        require_exact(release[key], expected_value, f"release.{key}")
    try:
        compiled = re.compile(release["tagPattern"])
    except re.error as error:
        raise PolicyFailure(f"release.tagPattern is invalid: {error}") from error
    if not compiled.fullmatch("v1.2.3") or compiled.fullmatch("v01.2.3"):
        raise PolicyFailure("release.tagPattern does not enforce canonical semantic tags")


def validate_android_workflows(
    emulator_workflow_text: str,
    release_workflow_text: str,
) -> None:
    for token, label in (
        (
            'cmdline-tools-version: "15859902"',
            "reviewed Android command-line tools pin",
        ),
        (
            'readlink -f "$(command -v sdkmanager)"',
            "resolved Android SDK manager binding",
        ),
        (
            "system-images;android-37.0;google_apis_ps16k;x86_64",
            "Android 17 16 KB system image installation",
        ),
    ):
        if token not in emulator_workflow_text:
            raise PolicyFailure(f"emulator workflow is missing {label}")
        if token not in release_workflow_text:
            raise PolicyFailure(f"release workflow is missing {label}")

    if (
        "system-images/android-37.0/google_apis_ps16k/x86_64/package.xml"
        not in emulator_workflow_text
    ):
        raise PolicyFailure(
            "emulator workflow is missing installed system-image verification"
        )
    if (
        "system-images/android-37.0/google_apis_ps16k/x86_64/package.xml"
        not in release_workflow_text
    ):
        raise PolicyFailure(
            "release workflow is missing installed Android 17 image verification"
        )
    require_exact(
        emulator_workflow_text.count(
            "channel: ${{ matrix.api-level == 37 && 'canary' || 'stable' }}"
        ),
        1,
        "emulator workflow API 37 canary-channel binding count",
    )
    require_exact(
        emulator_workflow_text.count("--channel=3"),
        2,
        "emulator workflow API 37 catalog and install binding count",
    )
    require_exact(
        release_workflow_text.count("channel: canary"),
        1,
        "release workflow API 37 canary-channel binding count",
    )
    require_exact(
        release_workflow_text.count("target: google_apis_ps16k"),
        1,
        "release workflow API 37 image-target binding count",
    )
    require_exact(
        release_workflow_text.count("--channel=3"),
        2,
        "release workflow API 37 preview-package install count",
    )
    for workflow_name, workflow_text in (
        ("emulator", emulator_workflow_text),
        ("release", release_workflow_text),
    ):
        if "channel: beta" in workflow_text or "channel: dev" in workflow_text:
            raise PolicyFailure(
                f"{workflow_name} workflow must not use undeclared beta or dev SDK channels"
            )

    require_exact(
        emulator_workflow_text.count('cmdline-tools-version: "15859902"'),
        1,
        "emulator workflow command-line tools pin count",
    )
    require_exact(
        release_workflow_text.count('cmdline-tools-version: "15859902"'),
        4,
        "release workflow command-line tools pin count",
    )
    for workflow_name, workflow_text in (
        ("emulator", emulator_workflow_text),
        ("release", release_workflow_text),
    ):
        if "cmdline-tools;latest" in workflow_text:
            raise PolicyFailure(
                f"{workflow_name} workflow uses mutable cmdline-tools;latest"
            )
        if "sdkmanager --update" in workflow_text:
            raise PolicyFailure(
                f"{workflow_name} workflow uses an unbounded SDK update"
            )

    matrix_matches = re.findall(
        r"(?m)^\s*api-level:\s*\[([^\]]+)\]\s*$",
        emulator_workflow_text,
    )
    if len(matrix_matches) != 1:
        raise PolicyFailure(
            "emulator workflow must define exactly one literal api-level matrix"
        )
    try:
        emulator_levels = [
            int(value.strip()) for value in matrix_matches[0].split(",")
        ]
    except ValueError as error:
        raise PolicyFailure("emulator workflow API matrix is not numeric") from error
    require_exact(
        emulator_levels,
        EMULATOR_API_LEVELS,
        "emulator workflow API matrix",
    )
    require_exact(
        emulator_workflow_text.count(
            "api-level: ${{ matrix.api-level == 37 && '37.0' || matrix.api-level }}"
        ),
        1,
        "emulator workflow matrix runner binding count",
    )
    require_exact(
        emulator_workflow_text.count(
            "target: ${{ matrix.api-level == 37 && 'google_apis_ps16k' || 'default' }}"
        ),
        1,
        "emulator workflow matrix image-target binding count",
    )
    require_exact(
        emulator_workflow_text.count(
            'script: bash tools/run_release_smoke.sh "${{ matrix.api-level }}"'
        ),
        1,
        "emulator workflow smoke runner binding count",
    )

    release_levels = re.findall(
        r"(?m)^\s*api-level:\s*(27|37\.0)\s*$",
        release_workflow_text,
    )
    require_exact(
        release_levels,
        ["27", "37.0"],
        "release workflow runner API identities",
    )
    release_smoke_levels = [
        int(value)
        for value in re.findall(
            r"(?m)^\s*script:\s*bash tools/run_release_smoke\.sh ([0-9]+)\s*$",
            release_workflow_text,
        )
    ]
    require_exact(
        release_smoke_levels,
        EMULATOR_API_LEVELS,
        "release workflow smoke API levels",
    )


def validate_android(
    value: Any,
    app_gradle_text: str,
    emulator_workflow_text: str,
    release_workflow_text: str,
) -> None:
    android = require_object(value, "android")
    require_exact_keys(
        android,
        {
            "minSdk",
            "targetSdk",
            "emulatorApiLevels",
            "visualProfiles",
            "largeFontScale",
        },
        "android",
    )
    require_exact(android["minSdk"], 27, "android.minSdk")
    require_exact(android["targetSdk"], 36, "android.targetSdk")
    require_exact(
        android["emulatorApiLevels"],
        EMULATOR_API_LEVELS,
        "android.emulatorApiLevels",
    )
    require_exact(android["visualProfiles"], VISUAL_PROFILES, "android.visualProfiles")
    require_exact(android["largeFontScale"], 2.0, "android.largeFontScale")

    for key in ("minSdk", "targetSdk"):
        matches = re.findall(rf"(?m)^\s*{key}\s+([0-9]+)\s*$", app_gradle_text)
        if len(matches) != 1:
            raise PolicyFailure(
                f"app Gradle must define exactly one literal {key}; found {matches}"
            )
        actual = int(matches[0])
        if actual != android[key]:
            raise PolicyFailure(
                f"android.{key}={android[key]} does not match app Gradle {key}={actual}"
            )
    validate_android_workflows(emulator_workflow_text, release_workflow_text)


def validate_blockers(value: Any) -> None:
    blockers = require_object(value, "blockerThresholds")
    require_exact_keys(
        blockers,
        {"androidLint", "codeql", "knownDefects"},
        "blockerThresholds",
    )
    expected_fields = {
        "androidLint": {"fatal", "error"},
        "codeql": {"critical", "high"},
        "knownDefects": {"critical", "high"},
    }
    for gate, fields in expected_fields.items():
        threshold = require_object(blockers[gate], f"blockerThresholds.{gate}")
        require_exact_keys(threshold, fields, f"blockerThresholds.{gate}")
        for severity in fields:
            require_exact(
                threshold[severity],
                0,
                f"blockerThresholds.{gate}.{severity}",
            )


def validate_suites(value: Any) -> None:
    if not isinstance(value, list):
        raise PolicyFailure("requiredSuites must be an array")
    normalized: list[tuple[str, str]] = []
    for index, raw in enumerate(value):
        path = f"requiredSuites[{index}]"
        suite = require_object(raw, path)
        require_exact_keys(suite, {"id", "mode", "required"}, path)
        if not isinstance(suite["id"], str) or not suite["id"]:
            raise PolicyFailure(f"{path}.id must be a non-empty string")
        if suite["mode"] not in {"automated", "manual"}:
            raise PolicyFailure(f"{path}.mode must be automated or manual")
        require_exact(suite["required"], True, f"{path}.required")
        normalized.append((suite["id"], suite["mode"]))
    if normalized != REQUIRED_SUITES:
        raise PolicyFailure(
            "requiredSuites must contain the complete ordered commercial suite contract"
        )


def validate_artifacts(value: Any) -> None:
    if not isinstance(value, list):
        raise PolicyFailure("requiredArtifacts must be an array")
    normalized: list[tuple[str, str, str]] = []
    for index, raw in enumerate(value):
        path = f"requiredArtifacts[{index}]"
        artifact = require_object(raw, path)
        require_exact_keys(artifact, {"id", "format", "requirement"}, path)
        for key in ("id", "format", "requirement"):
            if not isinstance(artifact[key], str) or not artifact[key]:
                raise PolicyFailure(f"{path}.{key} must be a non-empty string")
        normalized.append(
            (artifact["id"], artifact["format"], artifact["requirement"])
        )
    if normalized != REQUIRED_ARTIFACTS:
        raise PolicyFailure(
            "requiredArtifacts must contain the complete ordered evidence contract"
        )


def validate_completion(value: Any) -> None:
    completion = require_object(value, "completion")
    keys = {
        "sameCommitEvidenceRequired",
        "failClosed",
        "manualGatesMustBeApproved",
        "publicationRequiresAllRequiredArtifacts",
    }
    require_exact_keys(completion, keys, "completion")
    for key in keys:
        require_exact(completion[key], True, f"completion.{key}")


def validate_policy(
    policy: Any,
    app_gradle_text: str,
    emulator_workflow_text: str,
    release_workflow_text: str,
) -> dict[str, Any]:
    root = require_object(policy, "policy")
    require_exact_keys(
        root,
        {
            "schemaVersion",
            "policyId",
            "release",
            "android",
            "blockerThresholds",
            "requiredSuites",
            "requiredArtifacts",
            "pipelineOrder",
            "completion",
        },
        "policy",
    )
    require_exact(root["schemaVersion"], SCHEMA_VERSION, "schemaVersion")
    require_exact(root["policyId"], POLICY_ID, "policyId")
    validate_release(root["release"])
    validate_android(
        root["android"],
        app_gradle_text,
        emulator_workflow_text,
        release_workflow_text,
    )
    validate_blockers(root["blockerThresholds"])
    validate_suites(root["requiredSuites"])
    validate_artifacts(root["requiredArtifacts"])
    require_exact(root["pipelineOrder"], PIPELINE_ORDER, "pipelineOrder")
    validate_completion(root["completion"])
    return root


def build_summary(policy: dict[str, Any], raw_policy: bytes) -> dict[str, Any]:
    canonical = json.dumps(
        policy,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "passed": True,
        "policyId": policy["policyId"],
        "sourceSha256": hashlib.sha256(raw_policy).hexdigest(),
        "canonicalSha256": hashlib.sha256(canonical).hexdigest(),
        "release": policy["release"],
        "android": policy["android"],
        "blockerThresholds": policy["blockerThresholds"],
        "requiredSuiteIds": [suite["id"] for suite in policy["requiredSuites"]],
        "requiredArtifacts": policy["requiredArtifacts"],
        "pipelineOrder": policy["pipelineOrder"],
        "completion": policy["completion"],
    }


def write_summary(path: Path, summary: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    args = parse_args()
    try:
        raw_policy = args.policy.read_bytes()
        app_gradle_text = args.app_gradle.read_text(encoding="utf-8")
        emulator_workflow_text = args.emulator_workflow.read_text(encoding="utf-8")
        release_workflow_text = args.release_workflow.read_text(encoding="utf-8")
        try:
            parsed = json.loads(raw_policy)
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise PolicyFailure(f"policy is not valid UTF-8 JSON: {error}") from error
        policy = validate_policy(
            parsed,
            app_gradle_text,
            emulator_workflow_text,
            release_workflow_text,
        )
        summary = build_summary(policy, raw_policy)
        write_summary(args.summary, summary)
    except (OSError, PolicyFailure) as error:
        write_summary(
            args.summary,
            {
                "schemaVersion": SCHEMA_VERSION,
                "passed": False,
                "policyId": POLICY_ID,
                "failure": str(error),
            },
        )
        print(f"Stage 14 validation policy failed: {error}", file=sys.stderr)
        return 1

    print(
        "Stage 14 validation policy passed: "
        f"{len(REQUIRED_SUITES)} suites, {len(REQUIRED_ARTIFACTS)} artifacts, "
        f"API levels {EMULATOR_API_LEVELS}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
