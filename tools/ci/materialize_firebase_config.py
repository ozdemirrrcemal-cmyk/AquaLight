#!/usr/bin/env python3
"""Materialize and validate one flavor-scoped google-services.json without logging secrets."""

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

PACKAGES = {
    "development": "com.aqua.aqualight.debug.local",
    "staging": "com.aqua.aqualight.staging",
    "production": "com.aqua.aqualight",
}


def fixture(environment: str, package_name: str) -> dict[str, Any]:
    project_number = {"development": "100000000001", "staging": "100000000002"}[environment]
    app_hash = "d" * 32 if environment == "development" else "5" * 32
    return {
        "project_info": {
            "project_number": project_number,
            "project_id": f"aqualight-ci-{environment}",
            "storage_bucket": f"aqualight-ci-{environment}.firebasestorage.app",
        },
        "client": [
            {
                "client_info": {
                    "mobilesdk_app_id": f"1:{project_number}:android:{app_hash}",
                    "android_client_info": {"package_name": package_name},
                },
                "api_key": [{"current_key": "AIzaSyCIOnlyConfigurationNotForRuntimeUse000"}],
            }
        ],
        "configuration_version": "1",
    }


def decode_config(raw: str) -> dict[str, Any]:
    try:
        decoded = base64.b64decode(raw, validate=True).decode("utf-8")
        value = json.loads(decoded)
    except (binascii.Error, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("Firebase configuration secret is not valid base64-encoded JSON") from error
    if not isinstance(value, dict):
        raise ValueError("Firebase configuration root must be a JSON object")
    return value


def configured_packages(config: dict[str, Any]) -> set[str]:
    packages: set[str] = set()
    clients = config.get("client", [])
    if not isinstance(clients, list):
        return packages
    for client in clients:
        try:
            packages.add(client["client_info"]["android_client_info"]["package_name"])
        except (KeyError, TypeError):
            continue
    return packages


def validate(config: dict[str, Any], expected_package: str) -> None:
    project_info = config.get("project_info")
    if not isinstance(project_info, dict) or not project_info.get("project_id"):
        raise ValueError("Firebase configuration has no project_info.project_id")
    if expected_package not in configured_packages(config):
        raise ValueError(f"Firebase configuration has no client for {expected_package}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--environment", required=True, choices=sorted(PACKAGES))
    parser.add_argument(
        "--allow-ci-fixture",
        action="store_true",
        help="Allow a non-production compile-only fixture when CI=true and no secret is available",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    environment = args.environment
    expected_package = PACKAGES[environment]
    variable = f"FIREBASE_{environment.upper()}_GOOGLE_SERVICES_JSON_BASE64"
    encoded = os.environ.get(variable, "").strip()

    try:
        if encoded:
            config = decode_config(encoded)
            source = "secret"
        elif (
            args.allow_ci_fixture
            and environment != "production"
            and os.environ.get("CI", "").lower() == "true"
        ):
            config = fixture(environment, expected_package)
            source = "CI compile-only fixture"
        else:
            raise ValueError(f"required environment variable {variable} is missing")
        validate(config, expected_package)
    except ValueError as error:
        print(f"Firebase configuration rejected: {error}", file=sys.stderr)
        return 2

    destination = Path("app") / "src" / environment / "google-services.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(config, separators=(",", ":")), encoding="utf-8")
    temporary.chmod(stat.S_IRUSR | stat.S_IWUSR)
    temporary.replace(destination)
    destination.chmod(stat.S_IRUSR | stat.S_IWUSR)
    print(f"Materialized validated {environment} Firebase configuration from {source}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
