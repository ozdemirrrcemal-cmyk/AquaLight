#!/usr/bin/env python3

from __future__ import annotations

import unittest
from pathlib import Path

from tools.github_actions_pin_guard import validate_workflow_text


class GitHubActionsPinGuardTest(unittest.TestCase):
    def test_accepts_full_commit_sha_and_local_workflow(self) -> None:
        workflow = """
steps:
  - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2
job:
  uses: ./.github/workflows/android_instrumentation.yml
"""

        self.assertEqual([], validate_workflow_text(Path("workflow.yml"), workflow))

    def test_rejects_mutable_tag_and_branch(self) -> None:
        workflow = """
steps:
  - uses: actions/checkout@v4
  - uses: owner/action@main
"""

        violations = validate_workflow_text(Path("workflow.yml"), workflow)

        self.assertEqual(2, len(violations))
        self.assertIn("full 40-character commit SHA", violations[0].reason)
        self.assertIn("full 40-character commit SHA", violations[1].reason)

    def test_rejects_unpinned_container_action(self) -> None:
        workflow = """
steps:
  - uses: docker://alpine:3.20
"""

        violations = validate_workflow_text(Path("workflow.yml"), workflow)

        self.assertEqual(1, len(violations))
        self.assertIn("sha256 digest", violations[0].reason)

    def test_accepts_digest_pinned_container_action(self) -> None:
        digest = "a" * 64
        workflow = f"steps:\n  - uses: docker://alpine@sha256:{digest}\n"

        self.assertEqual([], validate_workflow_text(Path("workflow.yml"), workflow))


if __name__ == "__main__":
    unittest.main()
