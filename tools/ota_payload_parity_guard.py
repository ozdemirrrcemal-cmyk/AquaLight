#!/usr/bin/env python3
"""Fail closed when Android OTA payload parsing drifts from firmware main."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
FIXTURE_PATH = ROOT / "protocol/fixtures/aql_firmware_ota_payload_v1.json"
RUNTIME_CONTRACT_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/firmware/"
    / "DeviceFirmwareRuntimeContract.kt"
)
EVENT_CONTRACT_PATH = (
    ROOT / "app/src/main/java/com/aqua/aqualight/data/devices/contract/AqlWsEventContract.kt"
)
STATUS_PARSER_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/firmware/"
    / "DeviceFirmwareStatusParser.kt"
)
READ_PARSER_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/firmware/"
    / "DeviceFirmwareReadParser.kt"
)
MODELS_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/firmware/"
    / "DeviceFirmwareModels.kt"
)
FAILURE_MAPPER_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/firmware/"
    / "DeviceOtaFailureMapper.kt"
)
MANIFEST_PARSER_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/firmware/"
    / "DeviceFirmwareManifestParser.kt"
)

EXPECTED_FIRMWARE_REPOSITORY = "ozdemirrrcemal-cmyk/AquaLight-Firmware"
EXPECTED_FIRMWARE_BRANCH = "main"
EXPECTED_FIRMWARE_COMMIT = "1bf5d94af804acd87d4a5ed25971abc0fd9f048d"


class GuardFailure(AssertionError):
    """One OTA parity requirement failed."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardFailure(message)


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="strict")
    except (OSError, UnicodeError) as error:
        raise GuardFailure(f"Unable to read {path.relative_to(ROOT)}: {error}") from error


def load_fixture() -> dict[str, Any]:
    try:
        value = json.loads(read_text(FIXTURE_PATH))
    except json.JSONDecodeError as error:
        raise GuardFailure(f"Invalid OTA payload fixture: {error}") from error
    require(isinstance(value, dict), "OTA payload fixture root must be an object")
    return value


def extract_braced(source: str, marker: str) -> str:
    marker_index = source.find(marker)
    require(marker_index >= 0, f"Missing source marker: {marker}")
    start = source.find("{", marker_index + len(marker))
    require(start >= 0, f"Missing opening brace after: {marker}")
    depth = 0
    for index in range(start, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start + 1 : index]
    raise GuardFailure(f"Unterminated source block: {marker}")


def extract_parenthesized(source: str, start: int) -> str:
    open_index = source.find("(", start)
    require(open_index >= 0, "Missing opening parenthesis")
    depth = 0
    for index in range(open_index, len(source)):
        char = source[index]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return source[open_index + 1 : index]
    raise GuardFailure("Unterminated parenthesized block")


def extract_set(source: str, name: str) -> list[str]:
    assignment = source.find(f"private val {name}")
    require(assignment >= 0, f"Missing Kotlin set: {name}")
    set_of = source.find("setOf", assignment)
    require(set_of >= 0, f"Missing setOf for: {name}")
    block = extract_parenthesized(source, set_of)
    return re.findall(r'"([^"]+)"', block)


def string_constants(block: str) -> dict[str, str]:
    return dict(re.findall(r'const\s+val\s+(\w+)\s*=\s*"([^"]*)"', block))


def verify_fixture_pins(fixture: dict[str, Any]) -> None:
    require(fixture.get("fixtureVersion") == 1, "OTA fixtureVersion must remain 1")
    require(
        fixture.get("schema") == "aql.android-firmware.ota-payload.v1",
        "OTA fixture schema drifted",
    )
    firmware = fixture.get("firmware")
    require(isinstance(firmware, dict), "OTA fixture firmware pin is missing")
    require(
        firmware.get("repository") == EXPECTED_FIRMWARE_REPOSITORY,
        "OTA fixture firmware repository drifted",
    )
    require(
        firmware.get("branch") == EXPECTED_FIRMWARE_BRANCH,
        "OTA fixture must remain pinned to firmware main",
    )
    require(
        firmware.get("commit") == EXPECTED_FIRMWARE_COMMIT,
        "OTA fixture firmware main commit drifted",
    )


def verify_event_names(
    fixture: dict[str, Any],
    runtime_contract: str,
    event_contract: str,
    status_parser: str,
    read_parser: str,
) -> None:
    event_names = fixture["eventNames"]
    envelope_actions = fixture["eventEnvelopeActions"]

    runtime_event_block = extract_braced(runtime_contract, "object Event")
    runtime_events = string_constants(runtime_event_block)
    require(
        runtime_events.get("OTA_PROGRESS") == event_names["progress"],
        "Android OTA progress payload name differs from firmware main",
    )
    require(
        runtime_events.get("OTA_COMPLETED") == event_names["completed"],
        "Android OTA completed payload name differs from firmware main",
    )

    action_constants = string_constants(event_contract)
    require(
        action_constants.get("ACTION_OTA_PROGRESS") == envelope_actions["progress"],
        "Android OTA progress envelope action drifted",
    )
    require(
        action_constants.get("ACTION_OTA_COMPLETED") == envelope_actions["completed"],
        "Android OTA completed envelope action drifted",
    )
    require(
        event_names["progress"] != envelope_actions["progress"],
        "Qualified event name must remain distinct from its envelope action",
    )
    require(
        event_names["completed"] != envelope_actions["completed"],
        "Qualified completed event name must remain distinct from its envelope action",
    )

    require(
        status_parser.count("DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS") >= 3,
        "OTA status/start parsers must validate every firmware progress metadata field",
    )
    require(
        status_parser.count("DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED") >= 2,
        "OTA status/start parsers must validate every firmware completed metadata field",
    )
    require(
        "DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS" in read_parser
        and "DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED" in read_parser,
        "firmware.status.get parser must validate qualified event names",
    )
    require('"ota.progress"' not in status_parser, "Status parser reintroduced a short payload name")
    require('"ota.completed"' not in status_parser, "Status parser reintroduced a short payload name")
    require('"ota.progress"' not in read_parser, "Read parser reintroduced a short payload name")
    require('"ota.completed"' not in read_parser, "Read parser reintroduced a short payload name")


def verify_field_matrices(
    fixture: dict[str, Any],
    runtime_contract: str,
    status_parser: str,
    models: str,
) -> None:
    require(
        extract_set(status_parser, "OTA_START_RESPONSE_KEYS")
        == fixture["otaStartResponseFields"],
        "OTA start response field matrix drifted",
    )
    require(
        extract_set(status_parser, "OTA_STATUS_RESPONSE_KEYS")
        == fixture["otaStatusResponseFields"],
        "OTA status response field matrix drifted",
    )
    require(
        extract_set(status_parser, "OTA_SNAPSHOT_KEYS") == fixture["otaSnapshotFields"],
        "OTA snapshot field matrix drifted",
    )
    require(
        extract_set(status_parser, "OTA_EVENT_KEYS") == fixture["otaEventAdditionalFields"],
        "OTA event-only field matrix drifted",
    )

    field_constants = string_constants(extract_braced(runtime_contract, "object Field"))
    to_json = extract_braced(models, "fun toJson(): JSONObject")
    serialized_constant_names = re.findall(
        r"DeviceFirmwareRuntimeContract\.Field\.(\w+)",
        to_json,
    )
    serialized_fields = [field_constants[name] for name in serialized_constant_names]
    require(
        serialized_fields == fixture["otaStartRequestFields"],
        "OTA start request serializer field matrix drifted",
    )


def verify_phase_matrix(fixture: dict[str, Any], models: str) -> None:
    phase_block = extract_braced(models, "enum class DeviceFirmwareOtaPhase")
    phases = re.findall(r'^\s*[A-Z_]+\("([^"]+)"\)', phase_block, flags=re.MULTILINE)
    phases = [phase for phase in phases if phase != "unknown"]
    require(phases == fixture["phases"], "OTA phase wire matrix drifted")


def verify_download_diagnostic_mapping(failure_mapper: str) -> None:
    signed_http_client_codes = {
        "HTTPC_ERROR_CONNECTION_REFUSED": -1,
        "HTTPC_ERROR_SEND_HEADER_FAILED": -2,
        "HTTPC_ERROR_SEND_PAYLOAD_FAILED": -3,
        "HTTPC_ERROR_NOT_CONNECTED": -4,
        "HTTPC_ERROR_CONNECTION_LOST": -5,
        "HTTPC_ERROR_NO_STREAM": -6,
        "HTTPC_ERROR_NO_HTTP_SERVER": -7,
        "HTTPC_ERROR_TOO_LESS_RAM": -8,
        "HTTPC_ERROR_ENCODING": -9,
        "HTTPC_ERROR_STREAM_WRITE": -10,
        "HTTPC_ERROR_READ_TIMEOUT": -11,
    }
    for name, value in signed_http_client_codes.items():
        require(
            re.search(rf"{name}\s*=\s*{value}\b", failure_mapper) is not None,
            f"Android OTA failure mapper lost signed firmware diagnostic {name}={value}",
        )

    required_reasons = {
        "DOWNLOAD_CONNECTION_FAILED",
        "DOWNLOAD_SEND_FAILED",
        "DOWNLOAD_CONNECTION_LOST",
        "DOWNLOAD_STREAM_UNAVAILABLE",
        "DOWNLOAD_SERVER_NO_RESPONSE",
        "DOWNLOAD_DEVICE_MEMORY_LOW",
        "DOWNLOAD_ENCODING_UNSUPPORTED",
        "DOWNLOAD_STREAM_WRITE_FAILED",
        "DOWNLOAD_TIMEOUT",
        "DOWNLOAD_URL_OPEN_FAILED",
        "DOWNLOAD_STREAM_INTERRUPTED",
        "DOWNLOAD_SIZE_MISMATCH",
        "RELEASE_UNAVAILABLE",
        "RELEASE_ACCESS_DENIED",
        "RELEASE_RATE_LIMITED",
        "RELEASE_REDIRECT_FAILED",
        "RELEASE_REQUEST_REJECTED",
        "RELEASE_SERVER_UNAVAILABLE",
    }
    for reason in required_reasons:
        require(reason in failure_mapper, f"Android OTA failure mapper lost exact reason {reason}")


def verify_wire_semantics(
    fixture: dict[str, Any],
    status_parser: str,
    read_parser: str,
    failure_mapper: str,
    manifest_parser: str,
) -> None:
    semantics = fixture["wireSemantics"]
    require(
        f'== "{semantics["runtimeTransport"]}"' in status_parser,
        "OTA runtimeTransport parser value drifted",
    )
    require(
        f'== "{semantics["binaryTransfer"]}"' in status_parser
        and f'== "{semantics["binaryTransfer"]}"' in read_parser,
        "OTA binaryTransfer parser value drifted",
    )
    require(
        semantics.get("httpStatus") == "signed-int",
        "OTA fixture must declare signed httpStatus",
    )
    require("httpStatus >= 0" not in status_parser, "Android rejects signed HTTP diagnostics")
    verify_download_diagnostic_mapping(failure_mapper)
    require(
        semantics.get("finishedAtMsMayBeZero") is True,
        "OTA fixture must preserve zero terminal timestamp semantics",
    )
    require(
        "finishedAtMs > 0" not in status_parser,
        "Android reintroduced a non-firmware terminal timestamp assumption",
    )

    release_note_validator = extract_braced(
        manifest_parser,
        "private fun JSONObject.requiredReleaseNoteText",
    )
    require(
        semantics.get("releaseNoteRejectedControlRange") == "U+0000..U+001F",
        "OTA fixture release-note character policy drifted",
    )
    require(
        "character < ' '" in release_note_validator,
        "Release-note parser does not mirror firmware C0 policy",
    )
    require(
        "Char::isISOControl" not in release_note_validator,
        "Release-note parser rejects more signed characters than firmware",
    )
    require(
        "requiredString(key)" not in release_note_validator,
        "Release-note parser must not inherit the broader generic string policy",
    )


def verify() -> None:
    fixture = load_fixture()
    verify_fixture_pins(fixture)

    runtime_contract = read_text(RUNTIME_CONTRACT_PATH)
    event_contract = read_text(EVENT_CONTRACT_PATH)
    status_parser = read_text(STATUS_PARSER_PATH)
    read_parser = read_text(READ_PARSER_PATH)
    models = read_text(MODELS_PATH)
    failure_mapper = read_text(FAILURE_MAPPER_PATH)
    manifest_parser = read_text(MANIFEST_PARSER_PATH)

    verify_event_names(
        fixture,
        runtime_contract,
        event_contract,
        status_parser,
        read_parser,
    )
    verify_field_matrices(fixture, runtime_contract, status_parser, models)
    verify_phase_matrix(fixture, models)
    verify_wire_semantics(
        fixture,
        status_parser,
        read_parser,
        failure_mapper,
        manifest_parser,
    )


if __name__ == "__main__":
    verify()
    print("Android OTA payload contract matches pinned AquaLight-Firmware/main.")
