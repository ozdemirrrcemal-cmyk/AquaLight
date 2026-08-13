#!/usr/bin/env python3
"""Fail closed when Android drifts from the pinned commercial firmware contract."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INTEROP = ROOT / "protocol/fixtures/aql_firmware_interoperability_v1.json"
WS_FIXTURE = ROOT / "protocol/fixtures/aql_ws_v1_golden.json"
WS_CONTRACT = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices/contract/AqlWsContract.kt"
DOSING_ROOT = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/dosing"

EXPECTED_FIRMWARE_COMMIT = "b5c63b029f74d3e458acef8169060e648db43265"
EXPECTED_COMMAND_NAMES_BLOB = "9d030937518aae9fe2bb15f23938736a07e5024e"
EXPECTED_DOSING_COMMANDS_BLOB = "c18e355b1ab7553d1b5f251cf3e4662fd322ea33"
EXPECTED_AUTHENTICATED_COMMAND_COUNT = 43
EXPECTED_DOSING_COMMANDS = {
    "dosing.status.get",
    "dosing.config.apply",
    "dosing.program.apply",
    "dosing.channel.reset",
    "dosing.prime.start",
    "dosing.prime.stop",
    "dosing.calibration.start",
    "dosing.calibration.finish",
    "dosing.calibration.confirm",
    "dosing.calibration.cancel",
    "dosing.dose.now",
    "dosing.dose.stop",
    "dosing.reservoir.refill",
}


def fail(message: str) -> None:
    raise AssertionError(message)


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def git_blob_sha(path: Path) -> str:
    payload = path.read_bytes()
    header = f"blob {len(payload)}\0".encode("ascii")
    return hashlib.sha1(header + payload).hexdigest()


def verify_firmware_pin(interop: dict) -> None:
    firmware = interop["firmware"]
    if firmware.get("commit") != EXPECTED_FIRMWARE_COMMIT:
        fail("Android interoperability fixture is not pinned to the commercial Dosing firmware HEAD")
    if firmware.get("commandNames", {}).get("blobSha") != EXPECTED_COMMAND_NAMES_BLOB:
        fail("Pinned firmware command-name blob drifted")
    if firmware.get("dosingCommands", {}).get("blobSha") != EXPECTED_DOSING_COMMANDS_BLOB:
        fail("Pinned firmware Dosing command blob drifted")


def verify_byte_identical_fixtures(interop: dict) -> None:
    fixtures = interop.get("fixtures", {})
    required = {
        "aql_ws_v1_golden.json",
        "aql_dosing_calibration_v1.json",
        "aql_dosing_program_v1.json",
        "aql_dosing_scheduling_metadata_v1.json",
    }
    if set(fixtures) != required:
        fail("Commercial Dosing interoperability fixture set drifted")
    for filename, metadata in fixtures.items():
        if metadata.get("byteIdenticalWithFirmware") is not True:
            fail(f"{filename} must remain byte-identical with firmware")
        path = ROOT / "protocol/fixtures" / filename
        if not path.is_file():
            fail(f"Missing firmware fixture mirror: {filename}")
        actual_blob = git_blob_sha(path)
        expected_blob = metadata.get("firmwareBlobSha")
        if actual_blob != expected_blob:
            fail(
                f"{filename} is not byte-identical with pinned firmware: "
                f"expected {expected_blob}, got {actual_blob}"
            )


def parse_kotlin_constants(source: str) -> dict[str, str]:
    raw: dict[str, str] = {}
    for name, expression in re.findall(r"const val\s+(\w+)\s*=\s*([^\n]+)", source):
        raw[name] = expression.strip()

    resolved: dict[str, str] = {}

    def resolve(name: str, stack: set[str] | None = None) -> str:
        if name in resolved:
            return resolved[name]
        if name not in raw:
            fail(f"Unknown Kotlin contract constant: {name}")
        stack = set() if stack is None else stack
        if name in stack:
            fail(f"Recursive Kotlin contract constant: {name}")
        stack.add(name)
        expression = raw[name]
        literal = re.fullmatch(r'"([^"]*)"', expression)
        if literal:
            value = literal.group(1)
        else:
            alias = re.fullmatch(r"(\w+)", expression)
            if not alias:
                fail(f"Unsupported Kotlin contract constant expression for {name}: {expression}")
            value = resolve(alias.group(1), stack)
        resolved[name] = value
        return value

    for key in raw:
        resolve(key)
    return resolved


def verify_command_matrix() -> None:
    fixture = load_json(WS_FIXTURE)
    firmware_commands = fixture["commandAccess"]["authenticated"]
    if len(firmware_commands) != EXPECTED_AUTHENTICATED_COMMAND_COUNT:
        fail(
            "Pinned firmware WebSocket fixture must contain exactly "
            f"{EXPECTED_AUTHENTICATED_COMMAND_COUNT} authenticated commands"
        )
    if len(set(firmware_commands)) != len(firmware_commands):
        fail("Pinned firmware WebSocket fixture contains duplicate commands")

    source = WS_CONTRACT.read_text(encoding="utf-8")
    constants = parse_kotlin_constants(source)
    match = re.search(
        r"private val authenticatedCommands\s*=\s*setOf\((.*?)\n\s*\)",
        source,
        flags=re.S,
    )
    if not match:
        fail("Unable to locate Android authenticated command matrix")
    android_commands: set[str] = set()
    for module_name, action_name in re.findall(
        r"commandKey\((MODULE_\w+),\s*(ACTION_\w+)\)",
        match.group(1),
    ):
        android_commands.add(f"{constants[module_name]}.{constants[action_name]}")

    if android_commands != set(firmware_commands):
        missing = sorted(set(firmware_commands) - android_commands)
        extra = sorted(android_commands - set(firmware_commands))
        fail(f"Android/Firmware command matrix drifted. missing={missing}, extra={extra}")
    if android_commands & EXPECTED_DOSING_COMMANDS != EXPECTED_DOSING_COMMANDS:
        fail("Android authenticated command matrix is missing final Dosing commands")


def verify_dosing_contract(interop: dict) -> None:
    dosing = interop["dosing"]
    if dosing.get("schema") != "aqualight.dosing.v1":
        fail("Dosing schema must remain final in-place aqualight.dosing.v1")
    if set(dosing.get("commands", [])) != EXPECTED_DOSING_COMMANDS:
        fail("Dosing interoperability command list drifted")
    if dosing.get("legacyCompatibilityAllowed") is not False:
        fail("Legacy Dosing compatibility is forbidden for the unreleased transition")
    if dosing.get("standaloneTimerContractSeparate") is not True:
        fail("Standalone Timer must remain isolated from the Dose Pro program scheduler")

    models = (DOSING_ROOT / "models/DeviceDosingModels.kt").read_text(encoding="utf-8")
    repository = (DOSING_ROOT / "repository/DeviceDosingRuntimeRepository.kt").read_text(
        encoding="utf-8"
    )
    access = (DOSING_ROOT / "contract/DeviceDosingRuntimeAccess.kt").read_text(encoding="utf-8")
    status_parser = (DOSING_ROOT / "parsers/DeviceDosingStatusParser.kt").read_text(
        encoding="utf-8"
    )
    event_reducer = (DOSING_ROOT / "events/DeviceDosingTypedEventReducer.kt").read_text(
        encoding="utf-8"
    )

    for legacy_type in dosing.get("forbiddenLegacyAndroidTypes", []):
        if legacy_type in models or legacy_type in repository:
            fail(f"Legacy Dosing Android type remains: {legacy_type}")
    for legacy_field in dosing.get("forbiddenLegacyPublicFields", []):
        if f'"{legacy_field}"' in models:
            fail(f"Legacy Dosing public wire field remains: {legacy_field}")

    required_model_tokens = (
        "DeviceDosingProgramApplyPayload",
        "DeviceDosingChannelResetPayload",
        "DeviceDosingSchedulingMetadata",
        "DeviceDosingUsageToday",
        "expectedRevision",
        "missedDoseRecoveryEnabled",
        "CUSTOM_PERIODS",
        "HOURLY_24",
    )
    for token in required_model_tokens:
        if token not in models:
            fail(f"Missing final Dosing model token: {token}")

    required_repository_tokens = (
        "requestChannelStatus",
        "applyProgram",
        "resetChannel",
        "acceptStatusChange",
        "PROGRAM_APPLY",
        "CHANNEL_RESET",
    )
    for token in required_repository_tokens:
        if token not in repository:
            fail(f"Missing final Dosing repository behavior: {token}")
    for forbidden in ("createSchedule(", "updateSchedule(", "deleteSchedule(", "mutateSchedules("):
        if forbidden in repository:
            fail(f"Legacy Dosing schedule-list API remains: {forbidden}")

    for token in (
        "!capabilities.standaloneTimer",
        "limits.timerChannelCount == 0",
        "!modules.timerApi",
        "!modules.timerEngine",
    ):
        if token not in access:
            fail(f"Dose Pro / standalone Timer isolation guard missing: {token}")

    if "parseChannel" not in status_parser or "parseStatusChange" not in status_parser:
        fail("Dosing status parser must support channel-scoped status and slim status events")
    if "refreshStatusChange" not in event_reducer:
        fail("Slim Dosing status events must trigger authoritative channel refresh")


def verify_program_fixture_is_not_used_as_api_response_schema() -> None:
    """Firmware fixture contains service-level successFields; executable API code is authoritative."""
    program_fixture = load_json(ROOT / "protocol/fixtures/aql_dosing_program_v1.json")
    service_fields = program_fixture.get("programApply", {}).get("successFields", [])
    if service_fields != ["changed", "revision", "channel", "program", "event"]:
        fail("Pinned firmware program fixture unexpectedly changed; re-audit executable API response")
    parser = (DOSING_ROOT / "parsers/DeviceDosingMutationParser.kt").read_text(encoding="utf-8")
    for obsolete_api_field in ('"changed"', '"program"'):
        if obsolete_api_field in re.search(
            r"fun parseProgramApply\(.*?\n\s*}\n",
            parser,
            flags=re.S,
        ).group(0):
            fail("Android incorrectly adopted service-level program fixture fields as API response")


def main() -> int:
    try:
        interop = load_json(INTEROP)
        if interop.get("schema") != "aql.android-firmware.interoperability.v1":
            fail("Unexpected Android/Firmware interoperability schema")
        verify_firmware_pin(interop)
        verify_byte_identical_fixtures(interop)
        verify_command_matrix()
        verify_dosing_contract(interop)
        verify_program_fixture_is_not_used_as_api_response_schema()
    except (AssertionError, KeyError, ValueError, json.JSONDecodeError) as error:
        print(f"Firmware interoperability guard failed: {error}", file=sys.stderr)
        return 1

    print(
        "Firmware interoperability guard passed: 43/43 authenticated commands, "
        "commercial Dosing v1 fixtures and Timer isolation are locked."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
