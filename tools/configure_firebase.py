#!/usr/bin/env python3
"""Validate environment-specific Firebase configuration without logging secrets."""

from __future__ import annotations

import argparse
import base64
import binascii
import json
import os
import stat
import sys
from pathlib import Path
from typing import Any

NON_PRODUCTION_ENVIRONMENTS = {"debug", "staging", "releaseSmoke"}
ALL_ENVIRONMENTS = NON_PRODUCTION_ENVIRONMENTS | {"production"}


class FirebaseConfigError(ValueError):
    pass


def _required_mapping(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise FirebaseConfigError(f"{path} must be an object")
    return value


def _required_string(value: Any, path: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise FirebaseConfigError(f"{path} must be a non-empty string")
    return value.strip()


def validate_config(payload: dict[str, Any], *, environment: str, package_name: str) -> str:
    if environment not in ALL_ENVIRONMENTS:
        raise FirebaseConfigError(f"unsupported environment: {environment}")

    project_info = _required_mapping(payload.get("project_info"), "project_info")
    project_id = _required_string(project_info.get("project_id"), "project_info.project_id")
    _required_string(project_info.get("project_number"), "project_info.project_number")

    clients = payload.get("client")
    if not isinstance(clients, list) or not clients:
        raise FirebaseConfigError("client must contain at least one Android client")

    matching_clients: list[dict[str, Any]] = []
    for index, raw_client in enumerate(clients):
        client = _required_mapping(raw_client, f"client[{index}]")
        client_info = _required_mapping(client.get("client_info"), f"client[{index}].client_info")
        android_info = _required_mapping(
            client_info.get("android_client_info"),
            f"client[{index}].client_info.android_client_info",
        )
        candidate_package = _required_string(
            android_info.get("package_name"),
            f"client[{index}].client_info.android_client_info.package_name",
        )
        if candidate_package == package_name:
            matching_clients.append(client)

    if len(matching_clients) != 1:
        raise FirebaseConfigError(
            f"expected exactly one Firebase Android client for {package_name}; found {len(matching_clients)}"
        )

    selected = matching_clients[0]
    client_info = _required_mapping(selected.get("client_info"), "matching client.client_info")
    _required_string(client_info.get("mobilesdk_app_id"), "matching client.client_info.mobilesdk_app_id")

    api_keys = selected.get("api_key")
    if not isinstance(api_keys, list) or not api_keys:
        raise FirebaseConfigError("matching client.api_key must contain at least one key")
    first_api_key = _required_mapping(api_keys[0], "matching client.api_key[0]")
    _required_string(first_api_key.get("current_key"), "matching client.api_key[0].current_key")

    web_clients = [
        item
        for item in selected.get("oauth_client", [])
        if isinstance(item, dict) and item.get("client_type") == 3 and item.get("client_id")
    ]
    if not web_clients:
        raise FirebaseConfigError(
            "matching client.oauth_client must include a type-3 web client for Google Sign-In resources"
        )

    is_demo_project = project_id.startswith("demo-")
    if environment == "production" and is_demo_project:
        raise FirebaseConfigError("production Firebase project_id must not use the demo- namespace")
    if environment in NON_PRODUCTION_ENVIRONMENTS and not is_demo_project:
        raise FirebaseConfigError(
            f"{environment} Firebase configuration must use a demo- project in source control"
        )

    return project_id


def load_and_validate(path: Path, *, environment: str, package_name: str) -> str:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise FirebaseConfigError(f"Firebase configuration does not exist: {path}") from exc
    except json.JSONDecodeError as exc:
        raise FirebaseConfigError(f"Firebase configuration is not valid JSON: {exc}") from exc
    if not isinstance(payload, dict):
        raise FirebaseConfigError("Firebase configuration root must be an object")
    return validate_config(payload, environment=environment, package_name=package_name)


def materialize_from_environment(
    *, environment_variable: str, output: Path, environment: str, package_name: str
) -> str:
    encoded = os.environ.get(environment_variable, "")
    if not encoded.strip():
        raise FirebaseConfigError(f"required environment variable is missing or empty: {environment_variable}")
    try:
        decoded = base64.b64decode("".join(encoded.split()), validate=True)
    except (binascii.Error, ValueError) as exc:
        raise FirebaseConfigError(f"{environment_variable} is not valid base64") from exc

    try:
        payload = json.loads(decoded.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise FirebaseConfigError("decoded Firebase configuration is not valid UTF-8 JSON") from exc
    if not isinstance(payload, dict):
        raise FirebaseConfigError("decoded Firebase configuration root must be an object")

    project_id = validate_config(payload, environment=environment, package_name=package_name)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    output.chmod(stat.S_IRUSR | stat.S_IWUSR)
    return project_id


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate = subparsers.add_parser("validate")
    validate.add_argument("--environment", choices=sorted(ALL_ENVIRONMENTS), required=True)
    validate.add_argument("--package", dest="package_name", required=True)
    validate.add_argument("--input", type=Path, required=True)

    materialize = subparsers.add_parser("materialize")
    materialize.add_argument("--environment", choices=sorted(ALL_ENVIRONMENTS), required=True)
    materialize.add_argument("--package", dest="package_name", required=True)
    materialize.add_argument("--base64-env", required=True)
    materialize.add_argument("--output", type=Path, required=True)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        if args.command == "validate":
            project_id = load_and_validate(
                args.input, environment=args.environment, package_name=args.package_name
            )
        else:
            project_id = materialize_from_environment(
                environment_variable=args.base64_env,
                output=args.output,
                environment=args.environment,
                package_name=args.package_name,
            )
    except FirebaseConfigError as exc:
        print(f"Firebase configuration validation failed: {exc}", file=sys.stderr)
        return 2

    print(f"Firebase configuration validated for {args.environment}: project_id={project_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
