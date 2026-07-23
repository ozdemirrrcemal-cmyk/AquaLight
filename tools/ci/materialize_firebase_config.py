#!/usr/bin/env python3
"""Materialize and validate an environment-specific google-services.json file."""

from __future__ import annotations

import argparse
import base64
import binascii
import json
import os
import tempfile
from pathlib import Path

ENVIRONMENT_VARIABLES = {
    "debug": "FIREBASE_DEBUG_GOOGLE_SERVICES_JSON_BASE64",
    "staging": "FIREBASE_STAGING_GOOGLE_SERVICES_JSON_BASE64",
    "production": "FIREBASE_PRODUCTION_GOOGLE_SERVICES_JSON_BASE64",
}


class FirebaseConfigError(ValueError):
    pass


def decode_config(encoded: str) -> dict:
    try:
        raw = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as error:
        raise FirebaseConfigError("Firebase configuration is not valid base64") from error
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise FirebaseConfigError("Decoded Firebase configuration is not valid UTF-8 JSON") from error
    if not isinstance(value, dict):
        raise FirebaseConfigError("Firebase configuration root must be a JSON object")
    return value


def configured_packages(config: dict) -> set[str]:
    packages: set[str] = set()
    for client in config.get("client", []):
        package_name = (
            client.get("client_info", {})
            .get("android_client_info", {})
            .get("package_name")
        )
        if isinstance(package_name, str) and package_name:
            packages.add(package_name)
    return packages


def validate_config(config: dict, expected_package: str) -> None:
    project_id = config.get("project_info", {}).get("project_id")
    if not isinstance(project_id, str) or not project_id.strip():
        raise FirebaseConfigError("Firebase project_info.project_id is missing")

    packages = configured_packages(config)
    if expected_package not in packages:
        rendered = ", ".join(sorted(packages)) or "<none>"
        raise FirebaseConfigError(
            f"Expected Android package {expected_package!r}; config contains {rendered}"
        )


def atomic_write_json(destination: Path, config: dict) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        dir=destination.parent,
        prefix=f".{destination.name}.",
        delete=False,
    ) as stream:
        json.dump(config, stream, indent=2, sort_keys=True)
        stream.write("\n")
        temporary = Path(stream.name)
    temporary.chmod(0o600)
    temporary.replace(destination)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--environment", choices=sorted(ENVIRONMENT_VARIABLES), required=True)
    parser.add_argument("--expected-package", required=True)
    parser.add_argument("--output", type=Path, default=Path("app/google-services.json"))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    variable = ENVIRONMENT_VARIABLES[args.environment]
    encoded = os.environ.get(variable, "").strip()
    if not encoded:
        raise SystemExit(f"Firebase configuration secret {variable} is missing or empty")

    try:
        config = decode_config(encoded)
        validate_config(config, args.expected_package)
        atomic_write_json(args.output, config)
    except FirebaseConfigError as error:
        raise SystemExit(f"firebase config error: {error}") from error

    project_id = config["project_info"]["project_id"]
    print(
        f"Materialized validated {args.environment} Firebase configuration "
        f"for {args.expected_package} (project {project_id})."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
