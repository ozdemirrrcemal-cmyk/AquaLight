#!/usr/bin/env python3
"""Provision environment-specific google-services.json files without committing secrets."""

from __future__ import annotations

import argparse
import base64
import binascii
import json
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class FirebaseTarget:
    name: str
    package_name: str
    secret_env: str
    output_path: Path


TARGETS = {
    "debug": FirebaseTarget(
        "debug",
        "com.aqua.aqualight.debug",
        "FIREBASE_CONFIG_DEBUG_BASE64",
        Path("app/src/debug/google-services.json"),
    ),
    "staging": FirebaseTarget(
        "staging",
        "com.aqua.aqualight.staging",
        "FIREBASE_CONFIG_STAGING_BASE64",
        Path("app/src/staging/google-services.json"),
    ),
    "production": FirebaseTarget(
        "production",
        "com.aqua.aqualight",
        "FIREBASE_CONFIG_PRODUCTION_BASE64",
        Path("app/src/release/google-services.json"),
    ),
    "release-smoke": FirebaseTarget(
        "release-smoke",
        "com.aqua.aqualight",
        "FIREBASE_CONFIG_STAGING_BASE64",
        Path("app/src/releaseSmoke/google-services.json"),
    ),
}


def placeholder_config(target: FirebaseTarget) -> dict[str, Any]:
    environment_token = target.name.replace("-", "")
    app_id_suffix = f"{environment_token:0<16}"[-16:]
    return {
        "project_info": {
            "project_number": "000000000000",
            "project_id": f"aqualight-ci-{environment_token}",
            "storage_bucket": f"aqualight-ci-{environment_token}.appspot.com",
        },
        "client": [
            {
                "client_info": {
                    "mobilesdk_app_id": f"1:000000000000:android:{app_id_suffix}",
                    "android_client_info": {"package_name": target.package_name},
                },
                "oauth_client": [
                    {
                        "client_id": "000000000000-ci-placeholder.apps.googleusercontent.com",
                        "client_type": 3,
                    }
                ],
                "api_key": [{"current_key": "ci-placeholder-not-a-production-api-key"}],
                "services": {
                    "appinvite_service": {
                        "other_platform_oauth_client": [
                            {
                                "client_id": "000000000000-ci-placeholder.apps.googleusercontent.com",
                                "client_type": 3,
                            }
                        ]
                    }
                },
            }
        ],
        "configuration_version": "1",
    }


def decode_secret(value: str, variable_name: str) -> dict[str, Any]:
    compact = "".join(value.split())
    try:
        decoded = base64.b64decode(compact, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError(f"{variable_name} is not valid base64.") from exc
    try:
        payload = json.loads(decoded.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError(f"{variable_name} does not contain UTF-8 Firebase JSON.") from exc
    if not isinstance(payload, dict):
        raise ValueError(f"{variable_name} must decode to a JSON object.")
    return payload


def validate_config(payload: dict[str, Any], target: FirebaseTarget) -> str:
    project_info = payload.get("project_info")
    if not isinstance(project_info, dict) or not project_info.get("project_id"):
        raise ValueError(f"Firebase config for {target.name} is missing project_info.project_id.")

    clients = payload.get("client")
    if not isinstance(clients, list) or not clients:
        raise ValueError(f"Firebase config for {target.name} has no client entries.")

    matching_client: dict[str, Any] | None = None
    for candidate in clients:
        if not isinstance(candidate, dict):
            continue
        package_name = (
            candidate.get("client_info", {})
            .get("android_client_info", {})
            .get("package_name")
        )
        if package_name == target.package_name:
            matching_client = candidate
            break

    if matching_client is None:
        configured = sorted(
            {
                candidate.get("client_info", {})
                .get("android_client_info", {})
                .get("package_name")
                for candidate in clients
                if isinstance(candidate, dict)
            }
            - {None}
        )
        raise ValueError(
            f"Firebase config for {target.name} does not contain package "
            f"{target.package_name}; configured packages: {configured}"
        )

    app_id = matching_client.get("client_info", {}).get("mobilesdk_app_id")
    api_keys = matching_client.get("api_key")
    if not app_id or not isinstance(api_keys, list) or not api_keys:
        raise ValueError(f"Firebase config for {target.name} is missing app id or API key.")

    return str(project_info["project_id"])


def atomic_write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    serialized = json.dumps(payload, indent=2, sort_keys=True) + "\n"
    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", dir=path.parent, delete=False
    ) as stream:
        stream.write(serialized)
        temporary = Path(stream.name)
    os.chmod(temporary, 0o600)
    temporary.replace(path)


def provision(target: FirebaseTarget, allow_placeholder: bool) -> None:
    secret_value = os.getenv(target.secret_env, "").strip()
    if secret_value:
        payload = decode_secret(secret_value, target.secret_env)
        source = target.secret_env
    elif allow_placeholder:
        payload = placeholder_config(target)
        source = "CI placeholder"
    else:
        raise ValueError(
            f"{target.secret_env} is required for Firebase environment {target.name}."
        )

    project_id = validate_config(payload, target)
    atomic_write_json(target.output_path, payload)
    print(
        f"Prepared {target.name} Firebase config for {target.package_name} "
        f"at {target.output_path} (project={project_id}, source={source})."
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument("--environment", choices=sorted(TARGETS))
    selection.add_argument("--all", action="store_true")
    selection.add_argument("--clean", action="store_true")
    parser.add_argument(
        "--allow-placeholder",
        action="store_true",
        help="Use deterministic non-production configs when a secret is absent.",
    )
    args = parser.parse_args()

    targets = list(TARGETS.values()) if args.all or args.clean else [TARGETS[args.environment]]
    if args.clean:
        for target in targets:
            target.output_path.unlink(missing_ok=True)
        return 0

    try:
        for target in targets:
            provision(target, allow_placeholder=args.allow_placeholder)
    except ValueError as exc:
        parser.error(str(exc))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
