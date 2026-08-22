#!/usr/bin/env python3
"""Fail closed if mandatory-RTC Android behavior creates a second time contract."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TIME_ROOT = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/time"
)
CONTRACT_PATH = TIME_ROOT / "DeviceTimeRuntimeContract.kt"
MODELS_PATH = TIME_ROOT / "DeviceTimeModels.kt"
PARSER_PATH = TIME_ROOT / "DeviceTimeStatusParser.kt"
COORDINATOR_PATH = TIME_ROOT / "DeviceTimeSyncCoordinator.kt"
PARSER_TEST_PATH = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/data/devices/runtime/modules/time/"
    / "DeviceTimeStatusParserExactTest.kt"
)
COORDINATOR_TEST_PATH = (
    ROOT
    / "app/src/test/java/com/aqua/aqualight/data/devices/runtime/modules/time/"
    / "DeviceTimeSyncCoordinatorTest.kt"
)


class GuardFailure(AssertionError):
    """One exact-v1 RTC invariant failed."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GuardFailure(message)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="strict")
    except (OSError, UnicodeError) as error:
        raise GuardFailure(f"Unable to read {path.relative_to(ROOT)}: {error}") from error


def braced_block(source: str, marker: str) -> str:
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
                return source[start + 1:index]
    raise GuardFailure(f"Unterminated source block: {marker}")


def string_constants(block: str) -> set[str]:
    return set(re.findall(r'const\s+val\s+\w+\s*=\s*"([^"]*)"', block))


def kotlin_set(source: str, name: str) -> set[str]:
    marker = f"private val {name} = setOf("
    start = source.find(marker)
    require(start >= 0, f"Missing Kotlin set: {name}")
    end = source.find("\n    )", start + len(marker))
    require(end >= 0, f"Unterminated Kotlin set: {name}")
    return set(re.findall(r'"([^"]+)"', source[start:end]))


def verify() -> None:
    contract = read(CONTRACT_PATH)
    models = read(MODELS_PATH)
    parser = read(PARSER_PATH)
    coordinator = read(COORDINATOR_PATH)
    parser_test = read(PARSER_TEST_PATH)
    coordinator_test = read(COORDINATOR_TEST_PATH)

    expected_actions = {
        "status.get", "config.apply", "phone.sync", "ntp.sync", "rtc.set",
    }
    require(
        string_constants(braced_block(contract, "object Action")) == expected_actions,
        "time action matrix drifted from existing aql.ws.v1",
    )

    expected_request_fields = {
        "epochMillis", "timezoneId", "posixTimeZone", "utcOffsetMinutes", "timeZone",
        "ntpEnabled", "gadgetSyncEnabled", "ntpServerPrimary", "ntpServerSecondary",
        "save", "parts", "year", "month", "day", "weekday", "hour", "minute", "second",
    }
    require(
        string_constants(braced_block(contract, "object Field")) == expected_request_fields,
        "time request fields drifted from existing aql.ws.v1",
    )

    expected_status_keys = {
        "timeSet", "timeString", "uptime", "uptimeMs", "millisStartDay", "timeZone",
        "utcOffsetMinutes", "timezoneId", "posixTimeZone", "autoSyncNtpEnabled",
        "autoSyncGadgetEnabled", "ntpServerPrimary", "ntpServerSecondary",
        "lastSyncSource", "lastSyncEpochMillis", "lastSyncUptimeMs", "parts", "runtime",
    }
    require(
        kotlin_set(parser, "STATUS_KEYS") == expected_status_keys,
        "time.status.get exact-key parser drifted",
    )
    require("private const val MAX_YEAR = 2099" in parser, "RTC upper year must be 2099")
    require("private const val MIN_SYNCED_YEAR = 2000" in parser, "RTC lower year must be 2000")
    require("day in MIN_DAY..daysInMonth(year, month)" in parser, "calendar date validation missing")

    status_index = coordinator.find("requestStatus(deviceUid)")
    sync_index = coordinator.find("syncPhoneNow(deviceUid)", status_index + 1)
    require(status_index >= 0 and sync_index > status_index, "status must precede phone sync")
    for fragment in (
        "requestStatus = repository::requestStatus",
        "save = false",
        "status.requiresPhoneDiscipline(phoneZone)",
        "else -> DeviceTimeSyncDecision.Skipped",
        "!timeSet",
        "timezoneId != phoneZone.timezoneId",
        "utcOffsetMinutes != phoneZone.utcOffsetMinutes",
    ):
        require(fragment in coordinator, f"mandatory-RTC bootstrap policy missing: {fragment}")

    require(
        coordinator.count("data class Attempted(") == 1
        and "val outcome: DeviceRuntimeCommandOutcome<DeviceTimeMutationResult>" in coordinator,
        "existing DeviceTimeSyncDecision.Attempted(outcome) source shape drifted",
    )
    require(
        "assertTrue(first is DeviceTimeSyncDecision.Skipped)" in coordinator_test
        and "assertEquals(0, syncCalls.get())" in coordinator_test,
        "status failure must have regression evidence for fail-closed no-mutation behavior",
    )

    forbidden_production_tokens = (
        "statusSchemaVersion",
        "aql.time.status.v2",
        "requiresRtcHardware",
        "schedulerTimeValid",
        "extendedStatus",
    )
    for path in TIME_ROOT.glob("*.kt"):
        source = read(path)
        for token in forbidden_production_tokens:
            require(token not in source, f"parallel time contract token in {path.name}: {token}")

    require(
        'put("statusSchemaVersion", 2)' in parser_test,
        "exact parser needs regression evidence that a version selector is rejected",
    )
    require(
        "DeviceTimeStatus(" in models and "DevicePhoneSyncPayload(" in models,
        "existing v1 time models are missing",
    )


if __name__ == "__main__":
    try:
        verify()
    except GuardFailure as error:
        raise SystemExit(f"Mandatory RTC v1 contract guard failed: {error}") from error
    print(
        "Mandatory RTC Android guard passed: existing aql.ws.v1 shapes, "
        "status-first recovery, exact RTC years and fail-closed mutation policy are intact."
    )
