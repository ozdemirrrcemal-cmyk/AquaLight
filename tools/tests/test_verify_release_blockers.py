from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from verify_release_blockers import ReleaseBlockerFailure, validate

REPOSITORY = "owner/AquaLight"
COMMIT = "a" * 40


def issue(
    number: int,
    *labels: str,
    state: str = "open",
    pull_request: bool = False,
) -> dict[str, object]:
    value: dict[str, object] = {
        "number": number,
        "title": f"Issue {number}",
        "state": state,
        "html_url": f"https://github.com/{REPOSITORY}/issues/{number}",
        "repository_url": f"https://api.github.com/repos/{REPOSITORY}",
        "labels": [{"name": label} for label in labels],
    }
    if pull_request:
        value["pull_request"] = {
            "url": f"https://api.github.com/repos/{REPOSITORY}/pulls/{number}"
        }
    return value


def payload(*issues: dict[str, object]) -> bytes:
    return json.dumps([list(issues)]).encode("utf-8")


class ReleaseBlockerTest(unittest.TestCase):
    def test_empty_inventory_passes(self) -> None:
        summary = validate(payload(), REPOSITORY, COMMIT)

        self.assertTrue(summary["passed"])
        self.assertEqual(0, summary["counts"]["openIssues"])

    def test_medium_and_low_issues_pass(self) -> None:
        summary = validate(
            payload(
                issue(1, "severity:medium"),
                issue(2, "severity:low", "bug"),
            ),
            REPOSITORY,
            COMMIT,
        )

        self.assertEqual(2, summary["counts"]["openIssues"])
        self.assertEqual(0, summary["counts"]["critical"])

    def test_critical_and_high_issues_fail(self) -> None:
        for label in ("severity:critical", "severity:high"):
            with self.subTest(label=label):
                with self.assertRaisesRegex(
                    ReleaseBlockerFailure,
                    "blocking or untriaged",
                ) as raised:
                    validate(payload(issue(1, label)), REPOSITORY, COMMIT)
                self.assertFalse(raised.exception.args[1]["passed"])

    def test_explicit_release_blocker_fails(self) -> None:
        with self.assertRaises(ReleaseBlockerFailure) as raised:
            validate(
                payload(issue(1, "severity:medium", "release:blocker")),
                REPOSITORY,
                COMMIT,
            )

        self.assertEqual(
            1,
            raised.exception.args[1]["counts"]["releaseBlocker"],
        )

    def test_missing_severity_fails_closed(self) -> None:
        with self.assertRaises(ReleaseBlockerFailure) as raised:
            validate(payload(issue(1, "bug")), REPOSITORY, COMMIT)

        self.assertEqual(1, raised.exception.args[1]["counts"]["untriaged"])

    def test_multiple_severities_fail_closed(self) -> None:
        with self.assertRaises(ReleaseBlockerFailure) as raised:
            validate(
                payload(issue(1, "severity:low", "severity:high")),
                REPOSITORY,
                COMMIT,
            )

        self.assertEqual(
            1,
            raised.exception.args[1]["counts"]["ambiguousSeverity"],
        )

    def test_pull_requests_are_excluded(self) -> None:
        summary = validate(
            payload(issue(7, pull_request=True)),
            REPOSITORY,
            COMMIT,
        )

        self.assertEqual(0, summary["counts"]["openIssues"])
        self.assertEqual(1, summary["counts"]["openPullRequestsExcluded"])

    def test_closed_cross_repository_and_duplicate_items_are_rejected(self) -> None:
        invalid_repository = issue(1, "severity:low")
        invalid_repository["repository_url"] = (
            "https://api.github.com/repos/another/repository"
        )
        cases = (
            payload(issue(1, "severity:low", state="closed")),
            payload(invalid_repository),
            payload(issue(1, "severity:low"), issue(1, "severity:low")),
        )
        for raw in cases:
            with self.subTest(raw=raw):
                with self.assertRaises(ReleaseBlockerFailure):
                    validate(raw, REPOSITORY, COMMIT)

    def test_paginated_shape_and_commit_are_fail_closed(self) -> None:
        with self.assertRaisesRegex(ReleaseBlockerFailure, "page array"):
            validate(json.dumps([]).encode(), REPOSITORY, COMMIT)
        with self.assertRaisesRegex(ReleaseBlockerFailure, "40-character"):
            validate(payload(), REPOSITORY, "abc")


if __name__ == "__main__":
    unittest.main()
