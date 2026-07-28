from __future__ import annotations

import base64
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = (
    Path(__file__).resolve().parents[2]
    / ".github"
    / "actions"
    / "provision-dependabot-firebase"
    / "provision.py"
)
SPEC = importlib.util.spec_from_file_location("dependabot_firebase_provision", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def trusted_environment(github_env: Path) -> dict[str, str]:
    event_path = github_env.with_name("event.json")
    event_path.write_text(
        json.dumps(
            {
                "pull_request": {
                    "base": {"ref": "main"},
                    "head": {
                        "ref": "dependabot/gradle/firebase-123",
                        "repo": {
                            "full_name": "ozdemirrrcemal-cmyk/AquaLight"
                        },
                    },
                    "user": {"login": "dependabot[bot]"},
                }
            }
        ),
        encoding="utf-8",
    )
    return {
        "GITHUB_ACTIONS": "true",
        "GITHUB_EVENT_NAME": "pull_request",
        "GITHUB_ACTOR": "dependabot[bot]",
        "GITHUB_REPOSITORY": "ozdemirrrcemal-cmyk/AquaLight",
        "GITHUB_HEAD_REF": "dependabot/gradle/firebase-123",
        "GITHUB_EVENT_PATH": str(event_path),
        "GITHUB_ENV": str(github_env),
    }


class DependabotFirebaseProvisionTest(unittest.TestCase):
    def test_provisions_three_distinct_non_production_configs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            github_env = Path(directory) / "github-env"
            provisioned = MODULE.provision(trusted_environment(github_env), github_env)

            self.assertEqual(set(provisioned), set(MODULE.ENVIRONMENTS))
            values = dict(
                line.split("=", 1)
                for line in github_env.read_text(encoding="utf-8").splitlines()
            )
            configs = []
            for variable_name, specification in MODULE.ENVIRONMENTS.items():
                decoded = base64.b64decode(values[variable_name]).decode("utf-8")
                config = json.loads(decoded)
                configs.append(config)
                client = config["client"][0]
                self.assertEqual(
                    client["client_info"]["android_client_info"]["package_name"],
                    specification["package_name"],
                )
                self.assertEqual(config["configuration_version"], "1")
                self.assertRegex(
                    client["api_key"][0]["current_key"],
                    r"^AIza[A-Za-z0-9_-]{35}$",
                )

            self.assertEqual(
                len({config["project_info"]["project_id"] for config in configs}),
                len(configs),
            )
            self.assertNotIn("AQL_FIREBASE_PRODUCTION_CONFIG_BASE64", values)
            self.assertEqual(values["AQL_FIREBASE_CONFIG_SOURCE"], "dependabot-ci-fixture")

    def test_preserves_existing_protected_input(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            github_env = Path(directory) / "github-env"
            environment = trusted_environment(github_env)
            environment["AQL_FIREBASE_DEBUG_CONFIG_BASE64"] = "protected-value"

            provisioned = MODULE.provision(environment, github_env)
            output = github_env.read_text(encoding="utf-8")

            self.assertNotIn("AQL_FIREBASE_DEBUG_CONFIG_BASE64=", output)
            self.assertNotIn("AQL_FIREBASE_DEBUG_CONFIG_BASE64", provisioned)

    def test_trusted_dispatch_overrides_repository_inputs_with_fixtures(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            github_env = Path(directory) / "github-env"
            expected_sha = "a" * 40
            environment = {
                "GITHUB_ACTIONS": "true",
                "GITHUB_EVENT_NAME": "workflow_dispatch",
                "GITHUB_ACTOR": "github-actions[bot]",
                "GITHUB_REPOSITORY": "ozdemirrrcemal-cmyk/AquaLight",
                "GITHUB_REF": "refs/heads/dependabot/gradle/junit-4.13.2",
                "GITHUB_SHA": expected_sha,
                "GITHUB_ENV": str(github_env),
                "AQL_TRUSTED_DEPENDABOT_DISPATCH": "true",
                "AQL_EXPECTED_SHA": expected_sha,
                "AQL_DEPENDABOT_PR": "200",
                "AQL_TRUST_RUN_ID": "300",
                "AQL_FIREBASE_DEBUG_CONFIG_BASE64": "repository-secret",
            }

            provisioned = MODULE.provision(environment, github_env)
            values = dict(
                line.split("=", 1)
                for line in github_env.read_text(encoding="utf-8").splitlines()
            )

            self.assertEqual(set(MODULE.ENVIRONMENTS), set(provisioned))
            self.assertNotEqual(
                "repository-secret",
                values["AQL_FIREBASE_DEBUG_CONFIG_BASE64"],
            )
            self.assertEqual(
                "dependabot-ci-fixture",
                values["AQL_FIREBASE_CONFIG_SOURCE"],
            )

    def test_rejects_non_dependabot_context(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            github_env = Path(directory) / "github-env"
            environment = trusted_environment(github_env)
            payload = json.loads(
                Path(environment["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8")
            )
            payload["pull_request"]["user"]["login"] = "untrusted-user"
            Path(environment["GITHUB_EVENT_PATH"]).write_text(
                json.dumps(payload),
                encoding="utf-8",
            )

            with self.assertRaises(SystemExit):
                MODULE.provision(environment, github_env)

    def test_reopened_dependabot_pr_does_not_depend_on_trigger_actor(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            github_env = Path(directory) / "github-env"
            environment = trusted_environment(github_env)
            environment["GITHUB_ACTOR"] = "maintainer"

            provisioned = MODULE.provision(environment, github_env)

            self.assertEqual(set(MODULE.ENVIRONMENTS), set(provisioned))

    def test_trusted_main_dispatch_uses_fixtures(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            github_env = Path(directory) / "github-env"
            environment = {
                "GITHUB_ACTIONS": "true",
                "GITHUB_EVENT_NAME": "workflow_dispatch",
                "GITHUB_REPOSITORY": "ozdemirrrcemal-cmyk/AquaLight",
                "GITHUB_REF": "refs/heads/main",
                "GITHUB_SHA": "c" * 40,
                "GITHUB_ENV": str(github_env),
                "AQL_TRUSTED_MAIN_DISPATCH": "true",
            }

            provisioned = MODULE.provision(environment, github_env)

            self.assertEqual(set(MODULE.ENVIRONMENTS), set(provisioned))

    def test_rejects_partial_trusted_dispatch_context(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            github_env = Path(directory) / "github-env"
            environment = trusted_environment(github_env)
            environment.update(
                {
                    "GITHUB_EVENT_NAME": "workflow_dispatch",
                    "GITHUB_REF": "refs/heads/dependabot/gradle/test",
                    "GITHUB_SHA": "a" * 40,
                    "AQL_TRUSTED_DEPENDABOT_DISPATCH": "true",
                    "AQL_EXPECTED_SHA": "a" * 40,
                }
            )

            with self.assertRaises(SystemExit):
                MODULE.provision(environment, github_env)


if __name__ == "__main__":
    unittest.main()
