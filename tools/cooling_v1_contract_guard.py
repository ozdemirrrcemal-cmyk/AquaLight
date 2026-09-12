#!/usr/bin/env python3
"""Fail closed when the pinned Cooling V1 fixture and Android contract catalogs drift."""

from __future__ import annotations

import json
import hashlib
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
FIXTURE_PATH = ROOT / "protocol/fixtures/aql_cooling_contract_v1.json"
TELEMETRY_FIXTURE_PATH = ROOT / "protocol/fixtures/aql_cooling_telemetry_v1.json"
STATUS_FIXTURE_PATH = ROOT / "protocol/fixtures/aql_cooling_status_v1.json"
FIRMWARE_PIN_PATH = ROOT / "protocol/fixtures/aql_android_cooling_v1_pin.json"
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

FIRMWARE_REPOSITORY = "ozdemirrrcemal-cmyk/AquaLight-Firmware"
FIRMWARE_COMMIT = "7df97ce807ebb1e90ff63cc36206d6ce479a62fc"
FIRMWARE_TREE = "5df1ba11e2d0d5c65e3c6fbb1e4aba5d47bd6c69"
FIRMWARE_SOURCES = {
    "src/api/v1/commands/AqlCoolingCommands.hpp":
        "4df542d5106fce2810e64559667685c1c2ecfd69",
    "src/api/v1/telemetry/AqlCoolingTelemetry.hpp":
        "37d38ae2d5209ba7ab28040e0249e95f9e06ec10",
    "src/hardware/channels/AqlCoolingFanProfile.hpp":
        "23dc4537b52899a6ab9a7c79fbe802ecefa4f59d",
    "src/hardware/channels/AqlCoolingSensorProfile.hpp":
        "4a5bde4586b3017381b56da7b5aa06952a9cc6ca",
    "src/modules/cooling/AqlCoolingActuationSnapshot.hpp":
        "c649cdab8cc710a6c19f8f197865d7a43137f91b",
    "src/modules/cooling/AqlCoolingAlarmModel.hpp":
        "8c22fc057aac2abbd46108426c77575551a549b1",
    "src/modules/cooling/AqlCoolingContractV1.hpp":
        "68125166a9e7a0f9defeacea516be7727615507b",
    "src/modules/cooling/AqlCoolingHistoryService.hpp":
        "10c5087a1a09d141936e812dbc203a3808db4d0c",
    "src/modules/cooling/AqlCoolingService.hpp":
        "174ba58f3daa43b8403b22beabb76bae118b8801",
}
SHARED_FIXTURES = {
    "aql_cooling_contract_v1.json": (
        "9197d06f5f2022bdeea288e8455fad98b1fab57ad3325a1f202c50b555c8ddf2",
        "823fa046921922eb97573cb01c086de0b76fb350",
    ),
    "aql_cooling_telemetry_v1.json": (
        "8257ed9ad10342d8cab2693087b2b1831f8287d6e6eede24da2875457f0075a7",
        "83778df071f32d5996a3d55946275e8b63291336",
    ),
}
STATUS_FIXTURE_SHA256 = "fb19c405ee0be60b8f52f05a307d0bcb8797a047c3bcbfcab3f0b36581060df4"
STATUS_FIXTURE_BLOB = "b7529fa20c266c8ba48a2464e11de670174b3f2e"
STATUS_ROOT_FIELDS = {
    "schema", "schemaVersion", "uptimeMs", "contract", "topology", "config",
    "program", "control", "policy", "telemetry", "alarms", "healthSummary", "history",
}

# The pinned shared Cooling fixture declares the complete effective firmware error catalog.
# No command-local exception remains outside the byte-identical fixture.
COMMAND_LOCAL_FIRMWARE_ERRORS: frozenset[str] = frozenset()
COMMAND_LOCAL_MAPPER_ROUTES: dict[str, str] = {}


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


def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git_blob_sha(path: Path) -> str:
    payload = path.read_bytes()
    header = f"blob {len(payload)}\0".encode("ascii")
    return hashlib.sha1(header + payload).hexdigest()


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


def verify_firmware_pin(contract_source: str) -> None:
    pin = load_json(FIRMWARE_PIN_PATH)
    require(set(pin) == {
        "fixtureVersion", "schema", "firmware", "contract", "sharedFixtures",
        "derivedFixtures",
    }, "Cooling firmware pin root fields drifted")
    require(pin.get("fixtureVersion") == 1, "Cooling firmware pin version drifted")
    require(pin.get("schema") == "aql.android.cooling-v1.pin.v1",
            "Cooling firmware pin schema drifted")

    firmware = pin.get("firmware")
    require(isinstance(firmware, dict), "Cooling firmware identity is missing")
    require(set(firmware) == {"repository", "commit", "tree", "sources"},
            "Cooling firmware identity fields drifted")
    require(firmware.get("repository") == FIRMWARE_REPOSITORY,
            "Cooling firmware repository pin drifted")
    require(firmware.get("commit") == FIRMWARE_COMMIT,
            "Cooling firmware commit pin drifted")
    require(firmware.get("tree") == FIRMWARE_TREE,
            "Cooling firmware tree pin drifted")
    require(firmware.get("sources") == FIRMWARE_SOURCES,
            "Cooling firmware source blob matrix drifted")
    require(all(re.fullmatch(r"[0-9a-f]{40}", blob) for blob in FIRMWARE_SOURCES.values()),
            "Cooling firmware source blob matrix contains an invalid SHA")

    root_constants = string_constants(contract_source)
    require(root_constants.get("FIRMWARE_REPOSITORY") == FIRMWARE_REPOSITORY,
            "Android Cooling firmware repository constant drifted")
    require(root_constants.get("PINNED_FIRMWARE_COMMIT") == FIRMWARE_COMMIT,
            "Android Cooling firmware commit constant drifted")
    contract = pin.get("contract")
    require(isinstance(contract, dict), "Cooling pinned contract identity is missing")
    require(contract == {
        "schema": root_constants.get("SCHEMA"),
        "schemaVersion": 1,
        "catalogSha256": root_constants.get("CATALOG_SHA256"),
        "productKey": root_constants.get("PRODUCT_KEY"),
    }, "Cooling pinned contract identity drifted")

    shared = pin.get("sharedFixtures")
    require(isinstance(shared, dict) and set(shared) == set(SHARED_FIXTURES),
            "Cooling shared fixture pin set drifted")
    for name, (expected_sha256, expected_blob) in SHARED_FIXTURES.items():
        spec = shared.get(name)
        require(spec == {
            "sha256": expected_sha256,
            "firmwareBlobSha": expected_blob,
            "byteIdenticalWithFirmware": True,
        }, f"{name} firmware sharing pin drifted")
        path = ROOT / "protocol/fixtures" / name
        require(file_sha256(path) == expected_sha256, f"{name} bytes drifted")
        require(git_blob_sha(path) == expected_blob,
                f"{name} is not byte-identical with the pinned firmware blob")

    derived = pin.get("derivedFixtures")
    require(isinstance(derived, dict) and set(derived) == {STATUS_FIXTURE_PATH.name},
            "Cooling derived fixture pin set drifted")
    status_spec = derived[STATUS_FIXTURE_PATH.name]
    require(status_spec.get("sha256") == STATUS_FIXTURE_SHA256,
            "Cooling status golden SHA-256 pin drifted")
    require(status_spec.get("androidBlobSha") == STATUS_FIXTURE_BLOB,
            "Cooling status golden Git blob pin drifted")
    require(status_spec.get("firmwareCommit") == FIRMWARE_COMMIT,
            "Cooling status golden firmware revision drifted")
    require(set(status_spec.get("derivedFrom", [])) == {
        "src/api/v1/commands/AqlCoolingCommands.hpp",
        "src/modules/cooling/AqlCoolingAlarmModel.hpp",
        "src/modules/cooling/AqlCoolingContractV1.hpp",
        "src/modules/cooling/AqlCoolingHistoryService.hpp",
    }, "Cooling status golden source set drifted")
    require(set(status_spec.get("rootFields", [])) == STATUS_ROOT_FIELDS,
            "Cooling status golden root field pin drifted")
    require(file_sha256(STATUS_FIXTURE_PATH) == STATUS_FIXTURE_SHA256,
            "Cooling status golden bytes drifted")
    require(git_blob_sha(STATUS_FIXTURE_PATH) == STATUS_FIXTURE_BLOB,
            "Cooling status golden Git blob drifted")
    require(set(load_json(STATUS_FIXTURE_PATH)) == STATUS_ROOT_FIELDS,
            "Cooling status golden root does not match the firmware writer")


def verify_fixture_parity() -> tuple[int, int, int]:
    fixture = load_json(FIXTURE_PATH)
    contract_source = ANDROID_CONTRACT_PATH.read_text(encoding="utf-8", errors="strict")
    mapper_source = FAILURE_MAPPER_PATH.read_text(encoding="utf-8", errors="strict")

    root_constants = string_constants(contract_source)
    verify_firmware_pin(contract_source)
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
