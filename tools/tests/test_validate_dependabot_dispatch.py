from __future__ import annotations

import importlib.util
import io
import json
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE_PATH = (
    Path(__file__).resolve().parents[2]
    / ".github"
    / "actions"
    / "validate-dependabot-dispatch"
    / "validate.py"
)
SPEC = importlib.util.spec_from_file_location(
    "validate_dependabot_dispatch",
    MODULE_PATH,
)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def response(payload: dict[str, object]) -> io.BytesIO:
    return io.BytesIO(json.dumps(payload).encode("utf-8"))


class ValidateDependabotDispatchTest(unittest.TestCase):
    def environment(self) -> dict[str, str]:
        return {
            "GITHUB_ACTIONS": "true",
            "GITHUB_EVENT_NAME": "workflow_dispatch",
            "GITHUB_REPOSITORY": "ozdemirrrcemal-cmyk/AquaLight",
            "GITHUB_REF": "refs/heads/dependabot/gradle/junit-4.13.2",
            "GITHUB_SHA": "a" * 40,
            "AQL_EXPECTED_SHA": "a" * 40,
            "AQL_EXPECTED_BASE_SHA": "b" * 40,
            "AQL_DEPENDABOT_PR": "200",
            "AQL_TRUST_RUN_ID": "300",
            "AQL_VALIDATION_ID": f"dependabot-400-{'a' * 40}",
            "AQL_GITHUB_TOKEN": "token",
            "GITHUB_RUN_ID": "500",
            "GITHUB_WORKFLOW": "Android CI",
        }

    def pull(self) -> dict[str, object]:
        return {
            "state": "open",
            "user": {"login": "dependabot[bot]"},
            "base": {"ref": "main", "sha": "b" * 40},
            "head": {
                "ref": "dependabot/gradle/junit-4.13.2",
                "sha": "a" * 40,
                "repo": {"full_name": "ozdemirrrcemal-cmyk/AquaLight"},
            },
        }

    def workflow_run(self) -> dict[str, object]:
        return {
            "name": "Dependabot Gradle Trust Refresh",
            "status": "completed",
            "conclusion": "success",
            "event": "pull_request",
            "head_branch": "dependabot/gradle/junit-4.13.2",
            "head_sha": "a" * 40,
            "workflow_id": 10,
            "pull_requests": [
                {
                    "number": 200,
                    "base": {"sha": "b" * 40},
                    "head": {"sha": "a" * 40},
                }
            ],
            "head_repository": {
                "full_name": "ozdemirrrcemal-cmyk/AquaLight"
            },
        }

    def api_payloads(self) -> dict[str, dict[str, object]]:
        return {
            "/pulls/200": self.pull(),
            "/actions/runs/300": self.workflow_run(),
            "/actions/workflows/10": {
                "path": ".github/workflows/dependabot_gradle_trust_refresh.yml"
            },
            "/actions/runs/400": {
                "name": "Apply Dependabot Gradle Trust Refresh",
                "event": "workflow_run",
                "status": "in_progress",
                "workflow_id": 11,
                "repository": {
                    "full_name": "ozdemirrrcemal-cmyk/AquaLight"
                },
            },
            "/actions/workflows/11": {
                "path": ".github/workflows/apply_dependabot_gradle_trust_refresh.yml"
            },
            "/actions/runs/500": {
                "event": "workflow_dispatch",
                "head_sha": "a" * 40,
                "display_title": f"Android CI · dependabot-400-{'a' * 40}",
                "repository": {
                    "full_name": "ozdemirrrcemal-cmyk/AquaLight"
                },
            },
        }

    def urlopen(self, payloads: dict[str, dict[str, object]]):
        def open_request(request, timeout=30):
            del timeout
            for suffix, payload in payloads.items():
                if request.full_url.endswith(suffix):
                    return response(payload)
            raise AssertionError(f"Unexpected API request: {request.full_url}")

        return open_request

    def test_accepts_exact_successful_trust_dispatch(self) -> None:
        with patch.object(
            MODULE.urllib.request,
            "urlopen",
            side_effect=self.urlopen(self.api_payloads()),
        ):
            MODULE.validate(self.environment())

    def test_rejects_invalid_sha_before_api_access(self) -> None:
        environment = self.environment()
        environment["AQL_EXPECTED_SHA"] = "not-a-sha"

        with patch.object(MODULE.urllib.request, "urlopen") as urlopen:
            with self.assertRaisesRegex(SystemExit, "expected SHA"):
                MODULE.validate(environment)
            urlopen.assert_not_called()

    def test_rejects_head_movement(self) -> None:
        pull = self.pull()
        pull["head"]["sha"] = "c" * 40
        payloads = self.api_payloads()
        payloads["/pulls/200"] = pull

        with patch.object(
            MODULE.urllib.request,
            "urlopen",
            side_effect=self.urlopen(payloads),
        ):
            with self.assertRaisesRegex(SystemExit, "head SHA"):
                MODULE.validate(self.environment())

    def test_rejects_unrelated_successful_trust_run(self) -> None:
        payloads = self.api_payloads()
        stale_run = self.workflow_run()
        stale_run["head_sha"] = "c" * 40
        stale_run["pull_requests"][0]["head"]["sha"] = "c" * 40
        payloads["/actions/runs/300"] = stale_run
        payloads[f"/git/commits/{'a' * 40}"] = {
            "parents": [{"sha": "d" * 40}]
        }

        with patch.object(
            MODULE.urllib.request,
            "urlopen",
            side_effect=self.urlopen(payloads),
        ):
            with self.assertRaisesRegex(SystemExit, "single-parent child"):
                MODULE.validate(self.environment())

    def test_accepts_single_trust_state_child_commit(self) -> None:
        payloads = self.api_payloads()
        source_sha = "c" * 40
        source_run = self.workflow_run()
        source_run["head_sha"] = source_sha
        source_run["pull_requests"][0]["head"]["sha"] = source_sha
        payloads["/actions/runs/300"] = source_run
        payloads[f"/git/commits/{'a' * 40}"] = {
            "parents": [{"sha": source_sha}]
        }
        payloads[f"/compare/{source_sha}...{'a' * 40}"] = {
            "status": "ahead",
            "ahead_by": 1,
            "behind_by": 0,
            "files": [
                {"filename": "app/gradle.lockfile"},
                {"filename": "gradle/verification-metadata.xml"},
            ],
        }

        with patch.object(
            MODULE.urllib.request,
            "urlopen",
            side_effect=self.urlopen(payloads),
        ):
            MODULE.validate(self.environment())


if __name__ == "__main__":
    unittest.main()
