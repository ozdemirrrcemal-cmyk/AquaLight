#!/usr/bin/env python3
"""Fail closed when the pinned Cooling V1 fixture and Android contract catalogs drift."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
FIXTURE_PATH = ROOT / "protocol/fixtures/aql_cooling_contract_v1.json"
ANDROID_CONTRACT_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/cooling/v1/"
    / "DeviceCoolingV1Contract.kt"
)
FAILURE_MAPPER_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/cooling/v1/"
    / "DeviceCoolingV1FailureMapper.kt"
)
COOLING_COMMAND_PREFIX = "cooling."

# The pinned shared Cooling fixture declares its reusable top-level error catalog,
# while cooling.manual.apply additionally emits NOT_FOUND for an unknown fanKey.
# Keep that command-local firmware emission explicit so parity stays fail-closed
# without mutating the byte-identical firmware fixture.
COMMAND_LOCAL_FIRMWARE_ERRORS = frozenset({"NOT_FOUND"})
COMMAND_LOCAL_MAPPER_ROUTES = {
    "NOT_FOUND": "DeviceCoolingV1Contract.Error.NOT_FOUND -> mapNotFound(error)",
}


class GuardFailure(AssertionError):
    """One deterministic Cooling V1 parity requirement failed."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardFailure(message)


def load_json(path: Path) -> dict[str, Any]:
    try:
        parsed = json.loads(path.read_text(encoding="utf-8", errors="strict"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise GuardFailure(f"{path.relative_to(ROOT)} is unreadable: {error}") from error
    require(isinstance(parsed, dict), f"{path.relative_to(ROOT)} must contain one object")
    return parsed


def extract_object(source: str, name: str) -> str:
    marker = f"object {name} {{"
    start = source.find(marker)
    require(start >= 0, f"Android Cooling contract is missing object {name}")
    brace_start = source.find("{", start)
    depth = 0
    for index in range(brace_start, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[brace_start + 1 : index]
    raise GuardFailure(f"Android Cooling contract object {name} is unterminated")


def string_constants(source: str) -> dict[str, str]:
    return dict(
        re.findall(
            r'const\s+val\s+(\w+)\s*=\s*"([^"]*)"',
            source,
            flags=re.DOTALL,
        )
    )


def require_unique_strings(values: Any, label: str) -> list[str]:
    require(isinstance(values, list), f"Cooling fixture {label} must be an array")
    require(all(isinstance(value, str) and value for value in values),
            f"Cooling fixture {label} must contain non-empty strings")
    require(len(values) == len(set(values)), f"Cooling fixture {label} contains duplicates")
    return values


def verify_fixture_parity() -> tuple[int, int, int]:
    fixture = load_json(FIXTURE_PATH)
    contract_source = ANDROID_CONTRACT_PATH.read_text(encoding="utf-8", errors="strict")
    mapper_source = FAILURE_MAPPER_PATH.read_text(encoding="utf-8", errors="strict")

    root_constants = string_constants(contract_source)
    require(fixture.get("schema") == root_constants.get("SCHEMA"),
            "Cooling fixture schema and Android schema drifted")
    product = fixture.get("product")
    require(isinstance(product, dict), "Cooling fixture product object is missing")
    require(product.get("productKey") == root_constants.get("PRODUCT_KEY"),
            "Cooling fixture product key and Android product key drifted")

    commands = fixture.get("commands")
    require(isinstance(commands, dict), "Cooling fixture commands must be an object")
    action_constants = string_constants(extract_object(contract_source, "Action"))
    android_commands = {
        COOLING_COMMAND_PREFIX + action for action in action_constants.values()
    }
    require(len(action_constants) == len(android_commands),
            "Android Cooling action constants contain duplicate wire values")
    require(set(commands) == android_commands,
            "Cooling fixture command catalog and Android action catalog drifted")

    events = require_unique_strings(fixture.get("events"), "events")
    event_constants = string_constants(extract_object(contract_source, "Event"))
    require(len(event_constants) == len(set(event_constants.values())),
            "Android Cooling event constants contain duplicate wire values")
    require(set(events) == set(event_constants.values()),
            "Cooling fixture event catalog and Android event catalog drifted")

    errors = require_unique_strings(fixture.get("errors"), "errors")
    fixture_errors = set(errors)
    require(
        fixture_errors.isdisjoint(COMMAND_LOCAL_FIRMWARE_ERRORS),
        "Cooling command-local firmware errors unexpectedly moved into the shared fixture catalog",
    )
    require(
        set(COMMAND_LOCAL_MAPPER_ROUTES) == set(COMMAND_LOCAL_FIRMWARE_ERRORS),
        "Cooling command-local firmware error routes drifted",
    )
    expected_android_errors = fixture_errors | set(COMMAND_LOCAL_FIRMWARE_ERRORS)
    error_constants = string_constants(extract_object(contract_source, "Error"))
    require(len(error_constants) == len(set(error_constants.values())),
            "Android Cooling error constants contain duplicate wire values")
    require(set(error_constants.values()) == expected_android_errors,
            "Cooling effective firmware error catalog and Android error catalog drifted")
    for wire_value, route in COMMAND_LOCAL_MAPPER_ROUTES.items():
        require(
            route in mapper_source,
            f"Cooling failure mapper does not preserve command-local {wire_value} semantics",
        )
    for name, wire_value in error_constants.items():
        qualified_name = f"DeviceCoolingV1Contract.Error.{name}"
        require(qualified_name in mapper_source,
                f"Cooling failure mapper does not consume {qualified_name}")
        require(f'"{wire_value}"' not in mapper_source,
                f"Cooling failure mapper duplicates raw fixture error {wire_value}")

    return len(commands), len(events), len(expected_android_errors)


def main() -> int:
    try:
        command_count, event_count, error_count = verify_fixture_parity()
    except (GuardFailure, OSError, UnicodeError, KeyError, TypeError, ValueError) as error:
        print(f"Cooling V1 parity guard failed: {error}", file=sys.stderr)
        return 1

    print(
        "Cooling V1 parity guard passed: "
        f"{command_count}/{command_count} commands, "
        f"{event_count}/{event_count} events, "
        f"{error_count}/{error_count} effective firmware errors."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
