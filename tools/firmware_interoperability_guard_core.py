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
DOSING_PIN_PATH = ROOT / "protocol/fixtures/aql_android_dosing_v1_pin.json"
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
FIRMWARE_COMMIT = "980b03f0d83cdeb997698fc6b207064aa709cec8"
DOSING_FIRMWARE_COMMIT = FIRMWARE_COMMIT
COMMAND_NAMES_BLOB = "8fbd5743e58f76f9123e63441313246aae91814d"
EVENT_CONTRACT_BLOB = "96bcb0b45c0d39e46dc3f586507f95dca9909640"
PRODUCT_CATALOG_EXPORT_COMMIT = FIRMWARE_COMMIT
REQUEST_CONTRACT_BLOBS = {
    "src/api/v1/commands/AqlDeviceCommands.hpp": (
        "c5dc9982d5c8894fa74cec51c53e7c7d8af16f53"
    ),
    "src/api/v1/commands/AqlNetworkCommands.hpp": (
        "913db695e24051585d8179b6f6fecbce74293a86"
    ),
    "src/api/v1/commands/AqlSecurityCommands.hpp": (
        "75f7f41e4400d28c629775dc533560319cdf8977"
    ),
    "src/api/v1/commands/AqlTimeCommands.hpp": (
        "ce33b52d75d2f1c26c08ce8bc64356be3812bcc7"
    ),
    "src/api/v1/commands/AqlLightCommands.hpp": (
        "ce59a843375913b7d315b2cd68bdf40445df55bd"
    ),
    "src/api/v1/commands/AqlLightTemperatureProtectionCommands.hpp": (
        "f48588d88bb902139d3545002a0ae8c40a5795c1"
    ),
    "src/api/v1/commands/AqlLightThermalCommands.hpp": (
        "b71a5415082b7d23deab8cb86aa81885a01c24f3"
    ),
    "src/api/v1/commands/AqlCoolingCommands.hpp": (
        "50f209416d7b97efdd69b81274205dcf09777363"
    ),
    "src/api/v1/commands/AqlTimerCommands.hpp": (
        "0e442d40293d0a68bb5016840bd52b7c001489a6"
    ),
    "src/api/v1/commands/AqlDosingCommands.hpp": (
        "c293c9317db26492ca2a7ab4251ce0b23d787fa7"
    ),
    "src/api/v1/commands/AqlDosingProgressCommands.hpp": (
        "92fd314e10c90a8d8f77434dc9d2b52ec9896341"
    ),
    "src/api/v1/commands/AqlFirmwareCommands.hpp": (
        "6cf2e4e56947a0b6b641e55765b8b6f685004fbb"
    ),
    "src/modules/timer/AqlTimerService.hpp": (
        "0e8bbb20a92afbe7b5500fba51a1d03f4dfb0064"
    ),
    "src/security/AqlSecurityService.hpp": (
        "14d3d58740c6f8e2efebdf640298288a99c78715"
    ),
}

EXPECTED_FIXTURES = {
    "aql_ws_v1_golden.json": (
        "508bd588c118a0c41b66c838c579c45fefcfa5f54b1a608c26b2c9b1ef8984fb",
        "a16e32d73a2b8aabd5989fc400df36fd9f6b5347",
        True,
    ),
    "aql_cooling_contract_v1.json": (
        "9197d06f5f2022bdeea288e8455fad98b1fab57ad3325a1f202c50b555c8ddf2",
        "823fa046921922eb97573cb01c086de0b76fb350",
        True,
    ),
    "aql_cooling_telemetry_v1.json": (
        "8c0ecc54eff1a05f3d72b9b740e6d986dbc3a7cc69c61647608aed360b621b85",
        "7ec000ec24e2ef48cd54beff3bad81b58d7cd4c4",
        True,
    ),
    "aql_light_thermal_contract_v1.json": (
        "f1c8bac58740c3250a5c2e7a172f3d49604bf0ae0a0ba628f88f156c1842d7a6",
        "acbe344c29f8fe5569ffcf3b5b1d0fda2a6b07f7",
        True,
    ),
    "aql_product_catalog_v1.json": (
        "8ed588f11c28d7ad537623082e60bf98aed973eeb1aa0b01f582eade2126f63b",
        None,
        False,
    ),
}

EXPECTED_EVENTS = {
    "device.status.changed",
    "network.state.changed",
    "light.status.changed",
    "light.thermal.status.changed",
    "light.thermal.telemetry.changed",
    "cooling.status.changed",
    "cooling.telemetry.changed",
    "timer.status.changed",
    "dosing.status.changed",
    "time.status.changed",
    "firmware.ota.progress",
    "firmware.ota.completed",
    "system.restarting",
}
EXPECTED_DISCONNECTED_ANDROID_MODULES: set[str] = set()
EXPECTED_DOSING_ACTION_COUNT = 14


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


def git_blob_sha_bytes(content: bytes) -> str:
    """Return Git's content-addressed blob id; SHA-1 here is a protocol identifier."""
    header = f"blob {len(content)}\0".encode("ascii")
    return hashlib.sha1(header + content, usedforsecurity=False).hexdigest()


def git_blob_sha(path: Path) -> str:
    try:
        return git_blob_sha_bytes(path.read_bytes())
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
            require(
                git_blob_sha(fixture_path) == firmware_blob,
                f"{fixture_name} is not byte-identical with the pinned firmware blob",
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
    require(len(authenticated) == 50, "firmware fixture must contain 50 commands")
    require(len(command_set) == 50, "firmware fixture command names must be unique")

    ws_source = WS_CONTRACT_PATH.read_text(encoding="utf-8", errors="strict")
    event_source = EVENT_CONTRACT_PATH.read_text(encoding="utf-8", errors="strict")
    require(android_commands(ws_source) == command_set, "Android 50-command matrix drifted")

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
    core_coverage = set(payloadless) | set(payload_commands)

    dosing_pin = load_json(DOSING_PIN_PATH)
    dosing_firmware = dosing_pin.get("firmware")
    dosing_contract = dosing_pin.get("contract")
    require(isinstance(dosing_firmware, dict), "Dosing firmware pin is missing")
    require(isinstance(dosing_contract, dict), "Dosing contract pin is missing")
    require(
        dosing_firmware.get("repository") == FIRMWARE_REPOSITORY,
        "Dosing firmware repository drifted",
    )
    require(
        dosing_firmware.get("commit") == DOSING_FIRMWARE_COMMIT,
        "Dosing feature firmware revision drifted",
    )
    require(
        dosing_firmware.get("coreInteroperabilityCommit") == FIRMWARE_COMMIT,
        "Dosing feature pin must declare its reviewed core interoperability revision",
    )
    require(
        dosing_contract.get("productionWiring") is True,
        "Dosing production wiring must remain enabled",
    )
    dosing_actions = dosing_contract.get("authenticatedActions")
    require(isinstance(dosing_actions, list), "Dosing authenticated action matrix is missing")
    dosing_action_set = set(dosing_actions)
    require(
        len(dosing_actions) == EXPECTED_DOSING_ACTION_COUNT,
        "Dosing pin must contain 14 authenticated actions",
    )
    require(
        len(dosing_action_set) == EXPECTED_DOSING_ACTION_COUNT,
        "Dosing authenticated actions must be unique",
    )
    require(
        all(
            isinstance(action, str) and action.startswith("dosing.")
            for action in dosing_actions
        ),
        "Dosing authenticated actions must use exact dosing.* command names",
    )
    require(
        dosing_action_set == {command for command in command_set if command.startswith("dosing.")},
        "global firmware fixture and Dosing v1 pin command matrices drifted",
    )
    require(
        core_coverage.isdisjoint(dosing_action_set),
        "core request coverage must not duplicate feature-owned Dosing v1 commands",
    )
    require(
        core_coverage | dosing_action_set == command_set,
        "request coverage plus Dosing v1 pin does not exactly classify all 50 commands",
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
    require(len(products) == 7, "product catalog must contain exactly seven SKUs")
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
        "Firmware interoperability guard passed: 50 command names, 50 connected "
        "Android commands (36 core + 14 feature-owned Dosing v1), 13/13 events, "
        "core request serializers, byte-identical shared fixtures and 7/7 SKUs."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
