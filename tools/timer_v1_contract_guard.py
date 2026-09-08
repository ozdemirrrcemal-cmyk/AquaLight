#!/usr/bin/env python3
"""Fail closed when Android's Timer V1 data contract drifts from pinned firmware."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
FIXTURE_DIR = ROOT / "protocol/fixtures"
CONTRACT_PATH = FIXTURE_DIR / "aql_timer_contract_v1.json"
GOLDEN_PATH = FIXTURE_DIR / "aql_timer_wire_v1_golden.json"
PIN_PATH = FIXTURE_DIR / "aql_android_timer_v1_pin.json"
TIMER_DIR = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/timer"
)
CONTRACT_SOURCE_PATH = TIMER_DIR / "DeviceTimerRuntimeContract.kt"
EVENT_PAYLOAD_PARSER_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/events/"
    / "DeviceRuntimeEventPayloadParser.kt"
)
TIMER_APPLICATION_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/application/devices/timer"
)
TIMER_ADAPTER_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/timer/"
    / "DefaultDeviceTimerControlOperations.kt"
)
TIMER_FAILURE_MAPPER_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/timer/v1/"
    / "DeviceTimerV1FailureMapper.kt"
)
TIMER_PRESENTATION_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/timer"
)
TIMER_STRING_PATHS = (
    ROOT / "app/src/main/res/values/device_timer_strings.xml",
    ROOT / "app/src/main/res/values-tr/device_timer_strings.xml",
)

FIRMWARE_REPOSITORY = "ozdemirrrcemal-cmyk/AquaLight-Firmware"
FIRMWARE_COMMIT = "90b6597216d0c697542d5dc12e26647625806d8f"
FIRMWARE_TREE = "276c9edeffbe291d0f0a33daa0e62ecbb35ed56a"
FIRMWARE_SOURCES = {
    "src/api/v1/commands/AqlTimerCommands.hpp":
        "f01a46b6a4982879f57f0d57c19ff50ff9f709f0",
    "src/modules/timer/AqlTimerService.hpp":
        "f0b7a64487dec5a14d571966a14274223dc6c743",
    "src/modules/timer/AqlTimerEventQueue.hpp":
        "b43ea84c29d834f6508352d4002a2ce314dd777c",
    "src/modules/timer/AqlTimerScheduleMath.hpp":
        "ce11604c6d3470a359a48fde2dae6fa94c330468",
    "src/server/AqlRealtimeServer.cpp":
        "e177f1290f75de40d19f867b838c92a00dd53cb9",
    "tools/check_timer_contract_v1.py":
        "687c23b01c27fac589cf71722c32dcf65f4af645",
    "docs/ANDROID_FIRMWARE_CONTRACT.md":
        "a7850ddb7ce754c23a964a58c2ac7faedb63e231",
}
CONTRACT_SHA256 = "014704279570575925cc8b74650f3b1b14b73fde0c3452c6ed028b7de49111cc"
CONTRACT_BLOB = "541b2194001fed7abecdd61106d02c8c8a197c2f"
GOLDEN_SHA256 = "d3d43b8e0755751c1f5a291880954eb128fb570fe52e635042c7b0e884260315"
GOLDEN_BLOB = "dd104ee7f6a42462b122cd44d605920849678f4e"
STATUS_FIELDS = {
    "supported", "channelCount", "scheduleCount", "maxSchedulesPerChannel",
    "maxScheduleCount", "revision", "lockLoop", "schema", "schemaVersion",
    "rootName", "uptimeMs", "channelScoped", "schedulesIncluded",
    "selectedChannelKey", "channels", "schedules", "returnedScheduleCount", "runtime",
}
EVENT_FIELDS = {"schema", "schemaVersion", "channelKey", "revision", "publishedAtMs", "change"}
CHANGE_FIELDS = {
    "sequence", "occurredAtMs", "operatingState", "activeSlotId", "activeSlotName",
    "nextTransitionType", "nextTransitionAt", "runtimeReason", "clockReady",
    "temporaryOverrideActive", "temporaryOverrideRemainingMs",
}


class GuardFailure(AssertionError):
    """One deterministic Timer V1 parity requirement failed."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardFailure(message)


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8", errors="strict"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise GuardFailure(f"{path.relative_to(ROOT)} is unreadable: {error}") from error
    require(isinstance(value, dict), f"{path.relative_to(ROOT)} must contain one object")
    return value


def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git_blob_sha(path: Path) -> str:
    payload = path.read_bytes()
    return hashlib.sha1(f"blob {len(payload)}\0".encode("ascii") + payload).hexdigest()


def string_constants(source: str) -> dict[str, str]:
    return dict(re.findall(r'const\s+val\s+(\w+)\s*=\s*"([^"]*)"', source))


def verify_pin(contract_source: str) -> None:
    pin = load_json(PIN_PATH)
    require(set(pin) == {"fixtureVersion", "schema", "firmware", "contract", "wireGolden"},
            "Timer pin root fields drifted")
    require(pin.get("fixtureVersion") == 1, "Timer pin version drifted")
    require(pin.get("schema") == "aql.android.timer-v1.pin.v1", "Timer pin schema drifted")
    firmware = pin.get("firmware")
    require(isinstance(firmware, dict), "Timer firmware identity is missing")
    require(firmware == {
        "repository": FIRMWARE_REPOSITORY,
        "commit": FIRMWARE_COMMIT,
        "tree": FIRMWARE_TREE,
        "sources": FIRMWARE_SOURCES,
    }, "Timer firmware SHA pin drifted")

    constants = string_constants(contract_source)
    require(constants.get("FIRMWARE_REPOSITORY") == FIRMWARE_REPOSITORY,
            "Android Timer firmware repository constant drifted")
    require(constants.get("PINNED_FIRMWARE_COMMIT") == FIRMWARE_COMMIT,
            "Android Timer firmware commit constant drifted")
    require(constants.get("SCHEMA") == "aqualight.timer.v1", "Android Timer schema drifted")

    contract = pin.get("contract")
    require(contract == {
        "schema": "aqualight.timer.v1",
        "schemaVersion": 1,
        "fixture": CONTRACT_PATH.name,
        "sha256": CONTRACT_SHA256,
        "firmwareBlobSha": CONTRACT_BLOB,
        "byteIdenticalWithFirmware": True,
    }, "Timer contract fixture pin drifted")
    golden = pin.get("wireGolden")
    require(isinstance(golden, dict), "Timer wire golden pin is missing")
    require(golden.get("fixture") == GOLDEN_PATH.name, "Timer wire golden name drifted")
    require(golden.get("sha256") == GOLDEN_SHA256, "Timer wire golden SHA-256 drifted")
    require(golden.get("androidBlobSha") == GOLDEN_BLOB, "Timer wire golden blob drifted")
    require(set(golden.get("derivedFrom", [])) == {
        "src/api/v1/commands/AqlTimerCommands.hpp",
        "src/modules/timer/AqlTimerService.hpp",
        "src/server/AqlRealtimeServer.cpp",
    }, "Timer wire golden source matrix drifted")

    require(file_sha256(CONTRACT_PATH) == CONTRACT_SHA256, "Timer contract bytes drifted")
    require(git_blob_sha(CONTRACT_PATH) == CONTRACT_BLOB,
            "Timer contract is not byte-identical with firmware")
    require(file_sha256(GOLDEN_PATH) == GOLDEN_SHA256, "Timer wire golden bytes drifted")
    require(git_blob_sha(GOLDEN_PATH) == GOLDEN_BLOB, "Timer wire golden blob drifted")


def verify_contract() -> tuple[int, int]:
    contract = load_json(CONTRACT_PATH)
    commands = contract.get("commands")
    require(contract.get("schema") == "aqualight.timer.v1", "Timer fixture schema drifted")
    require(contract.get("schemaVersion") == 1, "Timer fixture version drifted")
    require(isinstance(commands, dict), "Timer command catalog is missing")
    require(set(commands) == {
        "timer.status.get", "timer.config.apply", "timer.channel.set"
    }, "Timer command catalog drifted")
    config = commands["timer.config.apply"]
    require(config.get("scope") == "CHANNEL_REPLACE", "Timer config scope drifted")
    require(config.get("revisionProtected") is True, "Timer config lost revision CAS")
    require(config.get("scheduleChannelKeyInheritedFromRequest") is True,
            "Timer schedule ownership drifted")
    require(config.get("scheduleFields") == [
        "slotId", "enabled", "name", "weekdays", "startTimeMs", "endTimeMs",
        "spansMidnight",
    ], "Timer schedule fields drifted")
    time = contract.get("time")
    require(isinstance(time, dict), "Timer time policy is missing")
    require(time.get("scheduleBoundaryUiPrecision") == "HOUR_MINUTE",
            "Timer UI precision drifted")
    require(time.get("scheduleBoundaryGranularityMs") == 60_000,
            "Timer whole-minute boundary drifted")
    event = contract.get("events")
    require(isinstance(event, dict), "Timer event policy is missing")
    require(event.get("shape") == "DIRECT_CHANNEL_RUNTIME_CHANGE",
            "Timer event shape drifted")
    require(event.get("sequenceScope") == "GLOBAL_PUBLISH_ORDER",
            "Timer event sequence scope drifted")
    require(event.get("retryPolicy") == "SAME_SEQUENCE_UNTIL_ACKNOWLEDGED",
            "Timer retry sequence policy drifted")
    return len(commands), len(event.get("changeFields", []))


def verify_android_sources() -> None:
    contract_source = CONTRACT_SOURCE_PATH.read_text(encoding="utf-8", errors="strict")
    verify_pin(contract_source)
    all_source = "\n".join(
        path.read_text(encoding="utf-8", errors="strict")
        for path in sorted(TIMER_DIR.glob("*.kt"))
    )
    for obsolete in (
        "DeviceTimerChannelConfig", "intervalOnMs", "intervalOffMs", "repeatCount",
    ):
        require(obsolete not in all_source, f"obsolete Timer field/type remains: {obsolete}")
    for required in (
        "DeviceTimerStatusGetPayload", "expectedRevision", "slotId", "endTimeMs",
        "spansMidnight", "DeviceTimerStatusChangedEventParser", "channelDetails",
        "lastEventSequence", "SCHEDULE_BOUNDARY_GRANULARITY_MS",
    ):
        require(required in all_source, f"Android Timer contract is missing {required}")
    event_parser = EVENT_PAYLOAD_PARSER_PATH.read_text(encoding="utf-8", errors="strict")
    require("COMMAND_EVENT_DISCRIMINATOR_FIELDS" in event_parser,
            "direct Timer events are not separated from command-result envelopes")
    require("COMMAND_EVENT_FIELDS -\n        FIELD_PUBLISHED_AT_MS" in event_parser,
            "publishedAtMs still misclassifies direct Timer events")
    verify_failure_boundary()


def verify_failure_boundary() -> None:
    application_source = "\n".join(
        path.read_text(encoding="utf-8", errors="strict")
        for path in sorted(TIMER_APPLICATION_PATH.glob("*.kt"))
    )
    adapter_source = TIMER_ADAPTER_PATH.read_text(encoding="utf-8", errors="strict")
    mapper_source = TIMER_FAILURE_MAPPER_PATH.read_text(encoding="utf-8", errors="strict")
    presentation_source = "\n".join(
        path.read_text(encoding="utf-8", errors="strict")
        for path in sorted(TIMER_PRESENTATION_PATH.glob("*.kt"))
    )

    for semantic in (
        "CONFLICT", "INVALID_REQUEST", "INVALID_CONFIGURATION", "CHANNEL_UNAVAILABLE",
        "RESOURCE_UNAVAILABLE", "HARDWARE_FAILURE", "STORAGE_FAILURE", "RUNTIME_LOCKED",
        "PROTOCOL_ERROR", "UNKNOWN_REJECTION",
    ):
        require(semantic in application_source,
                f"Timer application failure catalog is missing {semantic}")
    require("data class Rejected(" in application_source,
            "Timer control rejection does not retain its application reason")
    require("DeviceTimerV1FailureMapper.map(this)" in adapter_source,
            "Timer firmware rejection bypasses the V1 failure mapper")
    require("statusCode == expectedStatus" in mapper_source,
            "Timer failure mapper does not validate firmware status codes")
    require("DeviceTimerCommercialErrorResolver" in presentation_source,
            "Timer presentation has no commercial error resolver")
    require("DeviceTimerStatusNotice.CLOCK_UNAVAILABLE" in presentation_source and
            "channels.any { channel -> !channel.clockReady }" in presentation_source,
            "clockReady=false is not derived as a Timer commercial status")
    require("CLOCK_UNSYNCED" not in mapper_source,
            "Timer clock state must not be modeled as a firmware command error")

    localized_names = []
    for string_path in TIMER_STRING_PATHS:
        string_source = string_path.read_text(encoding="utf-8", errors="strict")
        names = set(re.findall(r'<string\s+name="([^"]+)"', string_source))
        localized_names.append(names)
        for required_name in (
            "device_timer_error_conflict_message",
            "device_timer_error_invalid_request_message",
            "device_timer_error_invalid_configuration_message",
            "device_timer_error_channel_unavailable_message",
            "device_timer_error_resource_unavailable_message",
            "device_timer_error_hardware_failure_message",
            "device_timer_error_storage_failure_message",
            "device_timer_error_runtime_locked_message",
            "device_timer_error_protocol_message",
            "device_timer_error_rejected_message",
            "device_timer_status_clock_unavailable_message",
        ):
            require(required_name in names,
                    f"{string_path.relative_to(ROOT)} is missing {required_name}")
    require(localized_names[0] == localized_names[1],
            "Timer EN/TR string catalogs must remain structurally identical")


def verify_golden() -> None:
    golden = load_json(GOLDEN_PATH)
    require(golden.get("fixtureVersion") == 1, "Timer wire golden version drifted")
    require(golden.get("firmwareCommit") == FIRMWARE_COMMIT,
            "Timer wire golden firmware commit drifted")
    global_status = golden.get("statusGlobal")
    scoped_status = golden.get("statusChannel")
    require(isinstance(global_status, dict) and isinstance(scoped_status, dict),
            "Timer status goldens are missing")
    require(set(global_status) == STATUS_FIELDS - {"selectedChannelKey"},
            "Timer global status fields drifted")
    require(set(scoped_status) == STATUS_FIELDS, "Timer scoped status fields drifted")
    require(global_status.get("schedules") == [], "global Timer status leaked schedules")
    require(scoped_status.get("selectedChannelKey") == "channel1",
            "scoped Timer channel key drifted")
    schedule = scoped_status["schedules"][0]
    require(schedule.get("startTimeMs") == 43_200_000 and schedule.get("startTime") == "12:00",
            "12:00 Timer golden encoding drifted")
    require(set(golden.get("statusChanged", {})) == EVENT_FIELDS,
            "Timer direct event root drifted")
    require(set(golden["statusChanged"].get("change", {})) == CHANGE_FIELDS,
            "Timer direct event change fields drifted")


def main() -> int:
    try:
        command_count, change_field_count = verify_contract()
        verify_android_sources()
        verify_golden()
    except (GuardFailure, OSError, UnicodeError, KeyError, TypeError, ValueError) as error:
        print(f"Timer V1 parity guard failed: {error}", file=sys.stderr)
        return 1
    print(
        "Timer V1 parity guard passed: "
        f"{command_count}/{command_count} commands and "
        f"{change_field_count}/{change_field_count} direct-event fields."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
