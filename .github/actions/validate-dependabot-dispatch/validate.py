#!/usr/bin/env python3
"""Validate a privileged workflow_dispatch against an immutable Dependabot PR."""

from __future__ import annotations

import json
import os
import re
import urllib.error
import urllib.request
from typing import Mapping

ALLOWED_REPOSITORY = "ozdemirrrcemal-cmyk/AquaLight"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
NUMBER_RE = re.compile(r"^[1-9][0-9]*$")
HEAD_REF_RE = re.compile(r"^dependabot/gradle/[A-Za-z0-9._/-]+$")
VALIDATION_ID_RE = re.compile(
    r"^dependabot-(?P<apply_run_id>[1-9][0-9]*)-(?P<head_sha>[0-9a-f]{40})$"
)
TRUST_WORKFLOW_PATH = ".github/workflows/dependabot_gradle_trust_refresh.yml"
APPLY_WORKFLOW_PATH = ".github/workflows/apply_dependabot_gradle_trust_refresh.yml"
TRUST_FILES = {
    "app/gradle.lockfile",
    "gradle/verification-metadata.xml",
}


def validate(environment: Mapping[str, str]) -> None:
    repository = environment.get("GITHUB_REPOSITORY", "")
    expected_sha = environment.get("AQL_EXPECTED_SHA", "")
    expected_base_sha = environment.get("AQL_EXPECTED_BASE_SHA", "")
    pr_number = environment.get("AQL_DEPENDABOT_PR", "")
    trust_run_id = environment.get("AQL_TRUST_RUN_ID", "")
    validation_id = environment.get("AQL_VALIDATION_ID", "")
    token = environment.get("AQL_GITHUB_TOKEN", "")
    current_run_id = environment.get("GITHUB_RUN_ID", "")
    validation_match = VALIDATION_ID_RE.fullmatch(validation_id)

    checks = {
        "GITHUB_ACTIONS": environment.get("GITHUB_ACTIONS") == "true",
        "GITHUB_EVENT_NAME": environment.get("GITHUB_EVENT_NAME")
        == "workflow_dispatch",
        "GITHUB_REPOSITORY": repository == ALLOWED_REPOSITORY,
        "expected SHA": bool(SHA_RE.fullmatch(expected_sha)),
        "expected base SHA": bool(SHA_RE.fullmatch(expected_base_sha)),
        "pull request": bool(NUMBER_RE.fullmatch(pr_number)),
        "trust run ID": bool(NUMBER_RE.fullmatch(trust_run_id)),
        "current run ID": bool(NUMBER_RE.fullmatch(current_run_id)),
        "validation ID": validation_match is not None,
        "validation head SHA": validation_match is not None
        and validation_match.group("head_sha") == expected_sha,
        "dispatch SHA": environment.get("GITHUB_SHA") == expected_sha,
        "GitHub token": bool(token.strip()),
    }
    failed = [name for name, valid in checks.items() if not valid]
    if failed:
        raise SystemExit(
            "Refusing untrusted Dependabot dispatch; failed checks: "
            + ", ".join(failed)
        )

    def api(path: str) -> dict[str, object]:
        request = urllib.request.Request(
            "https://api.github.com" + path,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {token}",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "AquaLight-Dependabot-Dispatch-Validator",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.load(response)
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise SystemExit(
                f"GitHub API GET {path} failed: {error.code}: {detail}"
            ) from error
        if not isinstance(payload, dict):
            raise SystemExit(f"GitHub API GET {path} returned an invalid payload.")
        return payload

    pull = api(f"/repos/{repository}/pulls/{pr_number}")
    head_ref = str(pull.get("head", {}).get("ref", ""))
    pull_checks = {
        "open PR": pull.get("state") == "open",
        "Dependabot author": pull.get("user", {}).get("login") == "dependabot[bot]",
        "main base": pull.get("base", {}).get("ref") == "main",
        "base SHA": pull.get("base", {}).get("sha") == expected_base_sha,
        "same-repository head": pull.get("head", {}).get("repo", {}).get("full_name")
        == repository,
        "canonical Gradle head": bool(HEAD_REF_RE.fullmatch(head_ref))
        and ".." not in head_ref
        and not head_ref.endswith("/"),
        "head SHA": pull.get("head", {}).get("sha") == expected_sha,
        "dispatch ref": environment.get("GITHUB_REF") == f"refs/heads/{head_ref}",
    }
    failed = [name for name, valid in pull_checks.items() if not valid]
    if failed:
        raise SystemExit(
            "Refusing stale or noncanonical Dependabot dispatch; failed checks: "
            + ", ".join(failed)
        )

    run = api(f"/repos/{repository}/actions/runs/{trust_run_id}")
    trust_workflow = api(
        f"/repos/{repository}/actions/workflows/{run.get('workflow_id', '')}"
    )
    pull_associations = run.get("pull_requests")
    if not isinstance(pull_associations, list):
        pull_associations = []
    association = next(
        (
            item
            for item in pull_associations
            if isinstance(item, dict) and str(item.get("number")) == pr_number
        ),
        None,
    )
    run_checks = {
        "Trust workflow": run.get("name") == "Dependabot Gradle Trust Refresh",
        "Trust conclusion": run.get("status") == "completed"
        and run.get("conclusion") == "success",
        "Trust event": run.get("event") == "pull_request",
        "Trust branch": run.get("head_branch") == head_ref,
        "Trust workflow path": trust_workflow.get("path") == TRUST_WORKFLOW_PATH,
        "Trust repository": run.get("head_repository", {}).get("full_name")
        == repository,
        "Trust PR association": association is not None,
        "Trust PR base": association is not None
        and association.get("base", {}).get("sha") == expected_base_sha,
    }
    failed = [name for name, valid in run_checks.items() if not valid]
    if failed:
        raise SystemExit(
            "Refusing dispatch without a matching successful Trust Refresh; "
            "failed checks: "
            + ", ".join(failed)
        )

    source_head_sha = str(run.get("head_sha", ""))
    if not SHA_RE.fullmatch(source_head_sha):
        raise SystemExit("Trust Refresh source head SHA is invalid.")
    if association is not None and association.get("head", {}).get("sha") != source_head_sha:
        raise SystemExit("Trust Refresh PR association head SHA is invalid.")

    if expected_sha != source_head_sha:
        final_commit = api(f"/repos/{repository}/git/commits/{expected_sha}")
        parents = final_commit.get("parents")
        if (
            not isinstance(parents, list)
            or len(parents) != 1
            or parents[0].get("sha") != source_head_sha
        ):
            raise SystemExit(
                "Final Dependabot head is not the single-parent child of Trust Refresh."
            )
        comparison = api(
            f"/repos/{repository}/compare/{source_head_sha}...{expected_sha}"
        )
        changed_files = {
            str(item.get("filename"))
            for item in comparison.get("files", [])
            if isinstance(item, dict)
        }
        if (
            comparison.get("status") != "ahead"
            or comparison.get("ahead_by") != 1
            or comparison.get("behind_by") != 0
            or changed_files != TRUST_FILES
        ):
            raise SystemExit(
                "Final Dependabot head contains changes outside the exact trust state."
            )

    assert validation_match is not None
    apply_run_id = validation_match.group("apply_run_id")
    apply_run = api(f"/repos/{repository}/actions/runs/{apply_run_id}")
    apply_workflow = api(
        f"/repos/{repository}/actions/workflows/{apply_run.get('workflow_id', '')}"
    )
    apply_checks = {
        "Apply workflow": apply_run.get("name")
        == "Apply Dependabot Gradle Trust Refresh",
        "Apply workflow path": apply_workflow.get("path") == APPLY_WORKFLOW_PATH,
        "Apply event": apply_run.get("event") == "workflow_run",
        "Apply status": apply_run.get("status") in {"queued", "in_progress"},
        "Apply repository": apply_run.get("repository", {}).get("full_name")
        == repository,
    }
    failed = [name for name, valid in apply_checks.items() if not valid]
    if failed:
        raise SystemExit(
            "Refusing dispatch without its active trusted Apply run; failed checks: "
            + ", ".join(failed)
        )

    current_run = api(f"/repos/{repository}/actions/runs/{current_run_id}")
    current_checks = {
        "current dispatch event": current_run.get("event") == "workflow_dispatch",
        "current dispatch head": current_run.get("head_sha") == expected_sha,
        "current dispatch title": current_run.get("display_title")
        == f"{environment.get('GITHUB_WORKFLOW', '')} · {validation_id}",
        "current dispatch repository": current_run.get("repository", {}).get("full_name")
        == repository,
    }
    failed = [name for name, valid in current_checks.items() if not valid]
    if failed:
        raise SystemExit(
            "Refusing dispatch without exact run correlation; failed checks: "
            + ", ".join(failed)
        )

    print(
        f"Trusted Dependabot dispatch validated for PR #{pr_number} "
        f"at {expected_sha} against main {expected_base_sha}."
    )


def main() -> None:
    validate(os.environ)


if __name__ == "__main__":
    main()
