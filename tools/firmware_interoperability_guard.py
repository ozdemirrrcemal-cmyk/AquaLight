#!/usr/bin/env python3
"""Fail closed when the final Android/firmware interoperability matrix drifts."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
INTEROPERABILITY_PATH = (
    ROOT / "protocol/fixtures/aql_firmware_interoperability_v1.json"
)
WEBSOCKET_PATH = ROOT / "protocol/fixtures/aql_ws_v1_golden.json"
PRODUCT_CATALOG_PATH = ROOT / "protocol/fixtures/aql_product_catalog_v1.json"
WS_CONTRACT_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/contract/AqlWsContract.kt"
)
EVENT_CONTRACT_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/contract/AqlWsEventContract.kt"
)
INTEROPERABILITY_TEST_PATH = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/data/devices/contract/"
    / "AqlFirmwareInteroperabilityTest.kt"
)

FIRMWARE_REPOSITORY = "ozdemirrrcemal-cmyk/AquaLight-Firmware"
FIRMWARE_COMMIT = "38e8812c1bcecf948ebab85979bff21a24f4b79c"
COMMAND_NAMES_BLOB = "3f93db851c8c6c31bec2284bc295fabde0d87220"
EVENT_CONTRACT_BLOB = "f71cbe76679fd425d6697c89800975e00e9edee5"
PRODUCT_CATALOG_EXPORT_COMMIT = "cf2222e58e6c69a729071a5d1205497b3fceaa70"
REQUEST_CONTRACT_BLOBS = {
    "src/api/v1/commands/AqlDeviceCommands.hpp": (
        "a78d6355555afea780fdb62809bc9107d7122698"
    ),
    "src/api/v1/commands/AqlNetworkCommands.hpp": (
        "529a3b341e81a48d33b9036343dbb0b9f2844fb6"
    ),
    "src/api/v1/commands/AqlSecurityCommands.hpp": (
        "1c16c3e7c6d1456b1802f494d91c104347ad09aa"
    ),
    "src/api/v1/commands/AqlTimeCommands.hpp": (
        "d8d82bf9edd1a2669d4a2fb0eaf7e6f105e3fc8a"
    ),
    "src/api/v1/commands/AqlLightCommands.hpp": (
        "57e576a2e02a5fc8347fca16be6b0cbf0e540a1b"
    ),
    "src/api/v1/commands/AqlLightTemperatureProtectionCommands.hpp": (
        "e3d44b0c1cad994378b12ce5b9003fd7a94d4f44"
    ),
    "src/api/v1/commands/AqlCoolingCommands.hpp": (
        "a4b6c390e4af6f28f2c844cef1d7429eb0206718"
    ),
    "src/api/v1/commands/AqlTimerCommands.hpp": (
        "77e5299511fe67e07d7cc1ffbd0114ff7316677b"
    ),
    "src/api/v1/commands/AqlDosingCommands.hpp": (
        "1d84bc0eaadb77f9041978c2ce46c7042c158009"
    ),
    "src/api/v1/commands/AqlFirmwareCommands.hpp": (
        "8b1107d159ca3ff026754c8a06bd1e75fb608c37"
    ),
    "src/modules/timer/AqlTimerService.hpp": (
        "ca37e6722e4e9d214e5efd6fc089d5e64db2490a"
    ),
    "src/security/AqlSecurityService.hpp": (
        "484906dbdd833d6ad7505ae1755748d239fc0805"
    ),
}

EXPECTED_FIXTURES = {
    "aql_ws_v1_golden.json": (
        "765cd113b848d4b17c173e513b714b806466ec994483a34c19970e7a1b984591",
        "d6d62d76970d7a17bcaceec9d4b308c3deb47b8b",
        True,
    ),
    "aql_cooling_temperature_telemetry_v1.json": (
        "020ff7e2b2fe94ad6aa7795e549cac5a926a40ac372855b475e32aecf7685213",
        "a44b8639576a56bbcad9175b36c1a61676357879",
        True,
    ),
    "aql_product_catalog_v1.json": (
        "333a3192c5212c277bced7a891f8e492511b8804a4484412c17a7124b8752716",
        None,
        False,
    ),
}

EXPECTED_EVENTS = {
    "device.status.changed",
    "network.state.changed",
    "light.status.changed",
    "cooling.status.changed",
    "timer.status.changed",
    "dosing.status.changed",
    "temperature.changed",
    "time.status.changed",
    "firmware.ota.progress",
    "firmware.ota.completed",
    "system.restarting",
}
EXPECTED_DISCONNECTED_ANDROID_MODULES = {"dosing"}


class GuardFailure(AssertionError):
    """One deterministic interoperability requirement failed."""


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
    try:
        return hashlib.sha256(path.read_bytes()).hexdigest()
    except OSError as error:
        raise GuardFailure(f"{path.relative_to(ROOT)} is unreadable: {error}") from error


def kotlin_constants(source: str) -> tuple[dict[str, str], dict[str, str]]:
    strings = dict(
        re.findall(
            r'const\s+val\s+(\w+)\s*=\s*"([^"]*)"',
            source,
            flags=re.DOTALL,
        )
    )
    aliases = dict(
        re.findall(
            r"const\s+val\s+(\w+)\s*=\s*([A-Z][A-Z0-9_]*)\b",
            source,
            flags=re.DOTALL,
        )
    )
    return strings, aliases


def resolve_constant(
    name: str,
    strings: dict[str, str],
    aliases: dict[str, str],
    trail: tuple[str, ...] = (),
) -> str:
    if name in strings:
        return strings[name]
    require(name not in trail, f"cyclic Kotlin constant alias: {' -> '.join(trail + (name,))}")
    target = aliases.get(name)
    require(target is not None, f"unresolved Kotlin string constant: {name}")
    return resolve_constant(target, strings, aliases, trail + (name,))


def extract_block(source: str, start: str) -> str:
    start_index = source.find(start)
    require(start_index >= 0, f"missing Kotlin block: {start}")
    body_start = start_index + len(start)
    end_index = source.find("\n    )", body_start)
    require(end_index >= 0, f"unterminated Kotlin block: {start}")
    return source[body_start:end_index]


def android_commands(source: str) -> set[str]:
    strings, aliases = kotlin_constants(source)
    block = extract_block(source, "private val authenticatedCommands = setOf(")
    pairs = re.findall(r"commandKey\(\s*(\w+)\s*,\s*(\w+)\s*\)", block)
    commands = {
        f"{resolve_constant(module, strings, aliases)}."
        f"{resolve_constant(action, strings, aliases)}"
        for module, action in pairs
    }
    require(len(pairs) == len(commands), "Android command registry contains duplicates")
    require(
        "private val publicCommands = emptySet<String>()" in source,
        "Android must expose zero public WebSocket commands",
    )
    require(
        '"${module.trim()}.${action.trim()}"' not in source,
        "command registration must not normalize whitespace",
    )
    require(
        '"$module.$action"' in source,
        "command registration must preserve exact module/action bytes",
    )
    return commands


def android_events(ws_source: str, event_source: str) -> set[str]:
    ws_strings, ws_aliases = kotlin_constants(ws_source)
    event_strings, event_aliases = kotlin_constants(event_source)
    block = extract_block(event_source, "private val registeredEvents = linkedSetOf(")
    definitions = re.findall(r"Definition\(\s*([^,]+),\s*([^)]+)\)", block)

    def resolve(expression: str) -> str:
        name = expression.strip().split(".")[-1]
        if expression.strip().startswith("AqlWsContract."):
            return resolve_constant(name, ws_strings, ws_aliases)
        return resolve_constant(name, event_strings, event_aliases)

    events = {f"{resolve(module)}.{resolve(action)}" for module, action in definitions}
    require(len(definitions) == len(events), "Android event registry contains duplicates")
    require(
        "Definition(module.trim(), action.trim())" not in event_source,
        "event registration must not normalize whitespace",
    )
    require(
        "Definition(module, action)" in event_source,
        "event registration must preserve exact module/action bytes",
    )
    return events


def verify_firmware_pin(interoperability: dict[str, Any]) -> None:
    require(interoperability.get("fixtureVersion") == 1, "fixtureVersion must remain 1")
    require(
        interoperability.get("schema") == "aql.android-firmware.interoperability.v1",
        "interoperability schema drifted",
    )
    firmware = interoperability.get("firmware")
    require(isinstance(firmware, dict), "firmware pin is missing")
    require(firmware.get("repository") == FIRMWARE_REPOSITORY, "firmware repository drifted")
    require(firmware.get("commit") == FIRMWARE_COMMIT, "firmware commit drifted")
    command_names = firmware.get("commandNames", {})
    event_contract = firmware.get("eventContract", {})
    require(command_names.get("blobSha") == COMMAND_NAMES_BLOB, "command-name blob drifted")
    require(event_contract.get("blobSha") == EVENT_CONTRACT_BLOB, "event-contract blob drifted")
    require(
        firmware.get("requestContractBlobs") == REQUEST_CONTRACT_BLOBS,
        "firmware request-contract blob matrix drifted",
    )


def verify_fixtures(interoperability: dict[str, Any]) -> None:
    fixture_specs = interoperability.get("fixtures")
    require(isinstance(fixture_specs, dict), "fixture checksum matrix is missing")
    require(set(fixture_specs) == set(EXPECTED_FIXTURES), "fixture checksum matrix drifted")

    for fixture_name, expected in EXPECTED_FIXTURES.items():
        expected_sha, firmware_blob, byte_identical = expected
        spec = fixture_specs[fixture_name]
        fixture_path = ROOT / "protocol/fixtures" / fixture_name
        require(file_sha256(fixture_path) == expected_sha, f"{fixture_name} bytes drifted")
        require(spec.get("sha256") == expected_sha, f"{fixture_name} SHA pin drifted")
        require(
            spec.get("byteIdenticalWithFirmware") is byte_identical,
            f"{fixture_name} firmware-sharing declaration drifted",
        )
        if firmware_blob is not None:
            require(
                spec.get("firmwareBlobSha") == firmware_blob,
                f"{fixture_name} firmware blob pin drifted",
            )

    product_spec = fixture_specs["aql_product_catalog_v1.json"]
    require(
        product_spec.get("firmwareExportCommit") == PRODUCT_CATALOG_EXPORT_COMMIT,
        "product-catalog export commit drifted",
    )


def verify_command_and_event_coverage(interoperability: dict[str, Any]) -> None:
    websocket = load_json(WEBSOCKET_PATH)
    access = websocket.get("commandAccess")
    require(isinstance(access, dict), "WebSocket commandAccess is missing")
    public = access.get("public")
    authenticated = access.get("authenticated")
    require(public == [], "WebSocket public command matrix must be empty")
    require(isinstance(authenticated, list), "authenticated command matrix is missing")
    command_set = set(authenticated)
    require(len(authenticated) == 41, "firmware fixture must contain 41 commands")
    require(len(command_set) == 41, "firmware fixture command names must be unique")

    ws_source = WS_CONTRACT_PATH.read_text(encoding="utf-8", errors="strict")
    event_source = EVENT_CONTRACT_PATH.read_text(encoding="utf-8", errors="strict")
    require(android_commands(ws_source) == command_set, "Android 41-command matrix drifted")

    disconnected_modules = interoperability.get("androidDisconnectedModules")
    require(
        isinstance(disconnected_modules, list),
        "disconnected Android module matrix is missing",
    )
    require(
        set(disconnected_modules) == EXPECTED_DISCONNECTED_ANDROID_MODULES,
        "disconnected Android module matrix drifted",
    )
    require(
        len(disconnected_modules) == len(EXPECTED_DISCONNECTED_ANDROID_MODULES),
        "disconnected Android modules contain duplicates",
    )
    connected_command_set = {
        command
        for command in command_set
        if command.split(".", 1)[0] not in EXPECTED_DISCONNECTED_ANDROID_MODULES
    }

    declared_events = interoperability.get("events")
    require(isinstance(declared_events, list), "event matrix is missing")
    require(set(declared_events) == EXPECTED_EVENTS, "pinned firmware event matrix drifted")
    require(len(declared_events) == len(EXPECTED_EVENTS), "firmware event names must be unique")
    require(
        android_events(ws_source, event_source) == EXPECTED_EVENTS,
        "Android typed event matrix drifted",
    )

    coverage = interoperability.get("requestCoverage")
    require(isinstance(coverage, dict), "request coverage matrix is missing")
    payloadless = coverage.get("payloadlessCommands")
    payload_commands = coverage.get("payloadCommands")
    require(isinstance(payloadless, list), "payloadless command matrix is missing")
    require(isinstance(payload_commands, dict), "payload command matrix is missing")
    require(len(payloadless) == len(set(payloadless)), "payloadless commands contain duplicates")
    require(
        set(payloadless).isdisjoint(payload_commands),
        "commands cannot be both payload-bearing and payloadless",
    )
    require(
        set(payloadless) | set(payload_commands) == connected_command_set,
        "request coverage does not exactly classify connected Android commands",
    )


def verify_serializer_and_hardware_ownership(interoperability: dict[str, Any]) -> None:
    coverage = interoperability["requestCoverage"]
    payload_commands = coverage["payloadCommands"]
    serializers = interoperability.get("serializers")
    require(isinstance(serializers, dict), "serializer matrix is missing")
    referenced = {
        serializer
        for command_serializers in payload_commands.values()
        for serializer in command_serializers
    }
    require(referenced == set(serializers), "serializer matrix has missing or orphan entries")

    all_fields: set[str] = set()
    test_source = INTEROPERABILITY_TEST_PATH.read_text(encoding="utf-8", errors="strict")
    for serializer_name, spec in serializers.items():
        require(isinstance(spec, dict), f"{serializer_name} spec must be an object")
        fields = spec.get("fields")
        source = spec.get("source")
        require(isinstance(fields, list) and fields, f"{serializer_name} fields are missing")
        require(len(fields) == len(set(fields)), f"{serializer_name} fields contain duplicates")
        require(isinstance(source, str), f"{serializer_name} source is missing")
        source_path = ROOT / source
        require(source_path.is_file(), f"{serializer_name} source does not exist: {source}")
        base_name = serializer_name.split(".", 1)[0]
        require(base_name in source_path.read_text(encoding="utf-8"), f"{base_name} is not in {source}")
        require(serializer_name in test_source, f"{serializer_name} lacks runtime serializer evidence")
        all_fields.update(fields)

    hardware_owned = set(interoperability.get("hardwareOwnedForbiddenRequestFields", []))
    require(hardware_owned, "hardware-owned field denylist is missing")
    require(
        all_fields.isdisjoint(hardware_owned),
        f"hardware-owned fields became request-editable: {sorted(all_fields & hardware_owned)}",
    )

    immutable_identity = set(interoperability.get("immutableIdentityRequestFields", []))
    allowed_ota_echo = set(interoperability.get("allowedOtaIdentityEchoFields", []))
    require(immutable_identity, "immutable identity field list is missing")
    require(allowed_ota_echo, "OTA identity echo allowlist is missing")
    for serializer_name, spec in serializers.items():
        identity_fields = set(spec["fields"]) & immutable_identity
        expected = allowed_ota_echo if serializer_name == "DeviceFirmwareOtaStartPayload" else set()
        require(
            identity_fields == expected,
            f"{serializer_name} immutable identity ownership drifted: {sorted(identity_fields)}",
        )


def verify_product_matrix() -> None:
    catalog = load_json(PRODUCT_CATALOG_PATH)
    source = catalog.get("source")
    products = catalog.get("products")
    require(isinstance(source, dict), "product catalog source pin is missing")
    require(source.get("commit") == PRODUCT_CATALOG_EXPORT_COMMIT, "catalog source commit drifted")
    require(isinstance(products, list), "product catalog product matrix is missing")
    require(len(products) == 9, "product catalog must contain exactly nine SKUs")
    require(
        {product.get("family") for product in products} == {"light", "timer", "dosing", "cooling"},
        "product catalog must contain all four commercial families",
    )
    for key in ("productKey", "productId", "model", "skuId", "skuCode"):
        values = [product.get(key) for product in products]
        require(all(isinstance(value, str) and value for value in values), f"missing product {key}")
        require(len(values) == len(set(values)), f"duplicate product {key}")
    require(
        all(product.get("hardwareRevision") == "2.0" for product in products),
        "commercial hardware revision matrix drifted",
    )

    result = subprocess.run(
        [sys.executable, str(ROOT / "tools/generate_android_commercial_catalog.py"), "--check"],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    require(
        result.returncode == 0,
        "generated Android catalog drifted:\n" + result.stdout + result.stderr,
    )


def main() -> int:
    try:
        interoperability = load_json(INTEROPERABILITY_PATH)
        verify_firmware_pin(interoperability)
        verify_fixtures(interoperability)
        verify_command_and_event_coverage(interoperability)
        verify_serializer_and_hardware_ownership(interoperability)
        verify_product_matrix()
    except (GuardFailure, OSError, UnicodeError, KeyError, TypeError, ValueError) as error:
        print(f"Firmware interoperability guard failed: {error}", file=sys.stderr)
        return 1

    print(
        "Firmware interoperability guard passed: 41 command names, 30 connected "
        "Android commands, 11/11 events, all connected request serializers, "
        "byte-identical shared fixtures and 9/9 SKUs."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
