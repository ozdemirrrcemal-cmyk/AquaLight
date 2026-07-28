#!/usr/bin/env python3
"""Provision deterministic, non-secret Firebase fixtures for Dependabot CI."""

from __future__ import annotations

import base64
import json
import os
import re
from pathlib import Path
from typing import Mapping, MutableMapping

DEPENDABOT_ACTOR = "dependabot[bot]"
ALLOWED_REPOSITORY = "ozdemirrrcemal-cmyk/AquaLight"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
NUMBER_RE = re.compile(r"^[1-9][0-9]*$")

ENVIRONMENTS: Mapping[str, Mapping[str, str]] = {
    "AQL_FIREBASE_DEBUG_CONFIG_BASE64": {
        "name": "debug",
        "package_name": "com.aqua.aqualight.debug",
        "project_id": "aqualight-ci-debug",
        "project_number": "910000000001",
        "mobile_sdk_app_id": "1:910000000001:android:d0000000000000000000000000000001",
        "oauth_client_id": "910000000001-aqualightcidebug.apps.googleusercontent.com",
        "api_key": "AIza" + "D" * 35,
    },
    "AQL_FIREBASE_STAGING_CONFIG_BASE64": {
        "name": "staging",
        "package_name": "com.aqua.aqualight.staging",
        "project_id": "aqualight-ci-staging",
        "project_number": "910000000002",
        "mobile_sdk_app_id": "1:910000000002:android:e0000000000000000000000000000002",
        "oauth_client_id": "910000000002-aqualightcistaging.apps.googleusercontent.com",
        "api_key": "AIza" + "S" * 35,
    },
    "AQL_FIREBASE_RELEASE_SMOKE_CONFIG_BASE64": {
        "name": "releaseSmoke",
        "package_name": "com.aqua.aqualight.smoke",
        "project_id": "aqualight-ci-smoke",
        "project_number": "910000000003",
        "mobile_sdk_app_id": "1:910000000003:android:f0000000000000000000000000000003",
        "oauth_client_id": "910000000003-aqualightcismoke.apps.googleusercontent.com",
        "api_key": "AIza" + "R" * 35,
    },
}


def _read_pull_request_event(environment: Mapping[str, str]) -> dict[str, object]:
    raw_path = environment.get("GITHUB_EVENT_PATH", "").strip()
    if not raw_path:
        raise SystemExit("Refusing Dependabot fixture provisioning without event payload.")
    try:
        payload = json.loads(Path(raw_path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise SystemExit(
            f"Cannot read Dependabot pull-request event payload: {error}"
        ) from error
    if not isinstance(payload, dict):
        raise SystemExit("Dependabot pull-request event payload must be an object.")
    return payload


def _require_trusted_context(environment: Mapping[str, str]) -> bool:
    trusted_dispatch = (
        environment.get("AQL_TRUSTED_DEPENDABOT_DISPATCH") == "true"
    )
    trusted_main_dispatch = (
        environment.get("AQL_TRUSTED_MAIN_DISPATCH") == "true"
    )
    if trusted_dispatch and trusted_main_dispatch:
        raise SystemExit("Dependabot and main dispatch fixture modes are mutually exclusive.")
    common_checks = {
        "GITHUB_ACTIONS": environment.get("GITHUB_ACTIONS") == "true",
        "GITHUB_REPOSITORY": environment.get("GITHUB_REPOSITORY")
        == ALLOWED_REPOSITORY,
    }
    if trusted_dispatch:
        expected_sha = environment.get("AQL_EXPECTED_SHA", "")
        checks = {
            **common_checks,
            "GITHUB_EVENT_NAME": environment.get("GITHUB_EVENT_NAME")
            == "workflow_dispatch",
            "expected SHA": bool(SHA_RE.fullmatch(expected_sha)),
            "dispatch SHA": environment.get("GITHUB_SHA") == expected_sha,
            "pull request": bool(
                NUMBER_RE.fullmatch(environment.get("AQL_DEPENDABOT_PR", ""))
            ),
            "trust run ID": bool(
                NUMBER_RE.fullmatch(environment.get("AQL_TRUST_RUN_ID", ""))
            ),
            "GITHUB_REF": environment.get("GITHUB_REF", "").startswith(
                "refs/heads/dependabot/gradle/"
            ),
        }
    elif trusted_main_dispatch:
        checks = {
            **common_checks,
            "GITHUB_EVENT_NAME": environment.get("GITHUB_EVENT_NAME")
            == "workflow_dispatch",
            "GITHUB_REF": environment.get("GITHUB_REF") == "refs/heads/main",
            "main SHA": bool(
                SHA_RE.fullmatch(environment.get("GITHUB_SHA", ""))
            ),
        }
    else:
        payload = _read_pull_request_event(environment)
        pull = payload.get("pull_request")
        if not isinstance(pull, dict):
            raise SystemExit("Event payload has no pull_request object.")
        head = pull.get("head")
        base = pull.get("base")
        user = pull.get("user")
        if not isinstance(head, dict) or not isinstance(base, dict) or not isinstance(user, dict):
            raise SystemExit("Event payload pull-request identity is incomplete.")
        head_repo = head.get("repo")
        if not isinstance(head_repo, dict):
            raise SystemExit("Event payload pull-request head repository is missing.")
        checks = {
            **common_checks,
            "GITHUB_EVENT_NAME": environment.get("GITHUB_EVENT_NAME")
            == "pull_request",
            "Dependabot author": user.get("login") == DEPENDABOT_ACTOR,
            "same repository": head_repo.get("full_name") == ALLOWED_REPOSITORY,
            "main base": base.get("ref") == "main",
            "Dependabot head": isinstance(head.get("ref"), str)
            and head["ref"].startswith("dependabot/"),
            "GITHUB_HEAD_REF": environment.get("GITHUB_HEAD_REF") == head.get("ref"),
        }
    failed = [name for name, valid in checks.items() if not valid]
    if failed:
        raise SystemExit(
            "Refusing to provision Dependabot Firebase fixtures outside the trusted "
            f"context; failed checks: {', '.join(failed)}"
        )
    return trusted_dispatch or trusted_main_dispatch


def build_config(specification: Mapping[str, str]) -> dict[str, object]:
    web_oauth_client = {
        "client_id": specification["oauth_client_id"],
        "client_type": 3,
    }
    return {
        "project_info": {
            "project_number": specification["project_number"],
            "project_id": specification["project_id"],
            "storage_bucket": f'{specification["project_id"]}.appspot.com',
        },
        "client": [
            {
                "client_info": {
                    "mobilesdk_app_id": specification["mobile_sdk_app_id"],
                    "android_client_info": {
                        "package_name": specification["package_name"],
                    },
                },
                "oauth_client": [web_oauth_client],
                "api_key": [{"current_key": specification["api_key"]}],
                "services": {
                    "appinvite_service": {
                        "other_platform_oauth_client": [web_oauth_client],
                    },
                },
            },
        ],
        "configuration_version": "1",
    }


def encode_config(specification: Mapping[str, str]) -> str:
    payload = json.dumps(
        build_config(specification),
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return base64.b64encode(payload).decode("ascii")


def provision(
    environment: MutableMapping[str, str] | None = None,
    github_env_path: Path | None = None,
) -> list[str]:
    active_environment = os.environ if environment is None else environment
    trusted_dispatch = _require_trusted_context(active_environment)

    if github_env_path is None:
        raw_path = active_environment.get("GITHUB_ENV", "").strip()
        if not raw_path:
            raise SystemExit("GITHUB_ENV is missing.")
        github_env_path = Path(raw_path)

    github_env_path.parent.mkdir(parents=True, exist_ok=True)
    provisioned: list[str] = []
    with github_env_path.open("a", encoding="utf-8", newline="\n") as handle:
        for variable_name, specification in ENVIRONMENTS.items():
            if (
                not trusted_dispatch
                and active_environment.get(variable_name, "").strip()
            ):
                continue
            handle.write(f"{variable_name}={encode_config(specification)}\n")
            provisioned.append(variable_name)
        handle.write("AQL_FIREBASE_CONFIG_SOURCE=dependabot-ci-fixture\n")

    print(
        "Provisioned deterministic non-production Firebase fixtures: "
        + (", ".join(provisioned) if provisioned else "existing protected inputs preserved")
    )
    return provisioned


def main() -> None:
    provision()


if __name__ == "__main__":
    main()
