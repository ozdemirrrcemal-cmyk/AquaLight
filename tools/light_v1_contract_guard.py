#!/usr/bin/env python3
"""Fail closed when Android Light V1 drifts from the managed-plan firmware pin."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
PIN_PATH = ROOT / "protocol/fixtures/aql_android_light_v1_pin.json"
INTEROP_PATH = ROOT / "protocol/fixtures/aql_firmware_interoperability_v1.json"
LIGHT_FIXTURE_PATH = ROOT / "protocol/fixtures/aql_light_contract_v1.json"
GRAPH_FIXTURE_PATH = ROOT / "protocol/fixtures/aql_light_graph_contract_v1.json"
WS_FIXTURE_PATH = ROOT / "protocol/fixtures/aql_ws_v1_golden.json"
ANDROID_CONTRACT_PATH = (
    ROOT
    / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/modules/light/"
    / "DeviceLightRuntimeContract.kt"
)

FIRMWARE_REPOSITORY = "ozdemirrrcemal-cmyk/AquaLight-Firmware"
FIRMWARE_BRANCH = "feature/smart-light-automation-plan-v2"
FIRMWARE_COMMIT = "9b4fbc00d09f71d9a0c8958a71650aee3cc94840"
FIRMWARE_TREE = "b9434d71e6ecdfe0cc71689a374a7a4ae554a143"
FIRMWARE_SOURCES = {
    "src/api/v1/commands/AqlLightV1Commands.hpp":
        "3a55519fc4c8acdab2aa79deadff3e1206c923b5",
    "src/api/v1/commands/names/AqlLightCommandNames.hpp":
        "49384214f3d083dff4c99c86b21e580f05973cc7",
    "src/modules/light/AqlLightProductContract.hpp":
        "c76f9c957672ffef62b96ba75f9b034a29c5fecb",
    "src/modules/light/AqlLightContractV1.hpp":
        "819082e3b2afdfc6481e775f49ae84ad89eab8e6",
    "src/modules/light/AqlLightControlService.hpp":
        "119281f0e367dedb8aee838b1d6262f35b9594c7",
    "src/modules/light/AqlLightGraphProjector.hpp":
        "374984587cc8f801816f8f700fe174721c459dc3",
    "src/modules/light/AqlLightManagedPlan.hpp":
        "d428f5d803d142d7fd92f37e221f3290e87196ac",
    "src/modules/light/AqlLightScheduleEngine.hpp":
        "15d57dd57158c13339691cb44848f0351aecf889",
    "src/modules/light/AqlLightV1Service.hpp":
        "f8cc0752880e9559132f0b3a15fa61b972c5e973",
    "docs/LIGHT_MANAGED_AUTO_PLAN_V1_CONTRACT.md":
        "3883cdaac535d6e3bce8ab1d418f787a39891ce1",
    "tools/check_light_contract_v1.py":
        "22cf84b1dfa5fe6df51ca4d8243838c432872801",
}
SHARED_FIXTURES = {
    "aql_light_contract_v1.json": (
        "836fe5cd41a2d777db7c559dc7599cbd88a63fc9e51ce0b291c3cbb27b7b6b82",
        "2f454296d12c4225d23cfb775e84295a4b935f7f",
    ),
    "aql_light_rgb_pro_slim_contract_v1.json": (
        "b067ccd11749e26b862ce99df1ccd63e91ce04ddcaf580f53bb2d1fe84fe7e96",
        "e2d878b503681507f89660ad68713b464d84f9f9",
    ),
    "aql_light_manual_control_v1.json": (
        "2f523a82eed615543bf1a3d645069627a41a45b1a62f2d1e9910914c19a4afd9",
        "ded90c6fe8b2b014c8d75356dbf99f1b3d7112fb",
    ),
    "aql_light_graph_contract_v1.json": (
        "2ea04e333b95f01b8a27c2c80969b2fa121754821e2f9bef377cca19daaae2f7",
        "686bce0c41df8749887cd7ff2b4c0fd1ffe3ed3b",
    ),
    "aql_light_thermal_contract_v1.json": (
        "1eba62b3b80101e5f799c35c2e1af4d69e1961cf331e5d6f139b5a3aab30a3cf",
        "7a6cebbddeab45802bc60ce8201b410d8c2ef851",
    ),
    "aql_ws_v1_golden.json": (
        "71e537ecd0be42833c3daa32d8abf2b8f053895c55e0cf6cfea75378ee378b74",
        "568832d8999bc6208cec2cedc7dab33c1fb8adf2",
    ),
}
PLAN_COMMANDS = {
    "light.auto.plan.get",
    "light.auto.plan.apply",
    "light.auto.plan.delete",
}


class GuardFailure(AssertionError):
    """One deterministic Light V1 parity requirement failed."""


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


def verify_pin() -> None:
    pin = load_json(PIN_PATH)
    require(
        set(pin) == {"fixtureVersion", "schema", "firmware", "contract", "sharedFixtures"},
        "Light firmware pin root fields drifted",
    )
    require(pin.get("fixtureVersion") == 1, "Light firmware pin version drifted")
    require(pin.get("schema") == "aql.android.light-v1.pin.v1", "Light firmware pin schema drifted")

    firmware = pin.get("firmware")
    require(isinstance(firmware, dict), "Light firmware identity is missing")
    require(
        set(firmware) == {"repository", "branch", "commit", "tree", "sources"},
        "Light firmware identity fields drifted",
    )
    require(firmware.get("repository") == FIRMWARE_REPOSITORY, "Light firmware repository drifted")
    require(firmware.get("branch") == FIRMWARE_BRANCH, "Light firmware branch drifted")
    require(firmware.get("commit") == FIRMWARE_COMMIT, "Light firmware commit drifted")
    require(firmware.get("tree") == FIRMWARE_TREE, "Light firmware tree drifted")
    require(firmware.get("sources") == FIRMWARE_SOURCES, "Light source blob matrix drifted")
    require(
        all(re.fullmatch(r"[0-9a-f]{40}", value) for value in FIRMWARE_SOURCES.values()),
        "Light source blob matrix contains an invalid SHA",
    )

    shared = pin.get("sharedFixtures")
    require(isinstance(shared, dict), "Light shared fixture matrix is missing")
    require(set(shared) == set(SHARED_FIXTURES), "Light shared fixture matrix drifted")
    for name, (expected_sha, expected_blob) in SHARED_FIXTURES.items():
        spec = shared[name]
        path = ROOT / "protocol/fixtures" / name
        require(spec.get("sha256") == expected_sha, f"{name} SHA pin drifted")
        require(spec.get("firmwareBlobSha") == expected_blob, f"{name} blob pin drifted")
        require(spec.get("byteIdenticalWithFirmware") is True, f"{name} sharing flag drifted")
        require(file_sha256(path) == expected_sha, f"{name} bytes drifted")
        require(git_blob_sha(path) == expected_blob, f"{name} is not byte-identical with firmware")


def verify_android_contract() -> None:
    source = ANDROID_CONTRACT_PATH.read_text(encoding="utf-8", errors="strict")
    require(FIRMWARE_REPOSITORY in source, "Android Light firmware repository constant drifted")
    require(FIRMWARE_COMMIT in source, "Android Light firmware commit constant drifted")
    for action in ("auto.plan.get", "auto.plan.apply", "auto.plan.delete"):
        require(f'"{action}"' in source, f"Android Light contract is missing {action}")


def verify_managed_plan_contract() -> None:
    fixture = load_json(LIGHT_FIXTURE_PATH)
    require(fixture.get("schema") == "aqualight.light.v1", "Light schema must remain V1")
    require(fixture.get("storageVersion") == 1, "Light storage version must remain 1")
    commands = set(fixture.get("commands", []))
    require(PLAN_COMMANDS <= commands, "Light managed-plan command matrix is incomplete")
    plan = fixture.get("managedAutoPlan")
    require(isinstance(plan, dict), "Light managedAutoPlan contract is missing")
    require(plan.get("phaseCapacity") == 8, "Light managed-plan phase capacity drifted")
    require(plan.get("overnightSupported") is False, "Managed plans must remain same-day only")
    require(plan.get("contiguousPhases") is True, "Managed plan phases must remain contiguous")
    require(
        plan.get("mutationPreconditions") == ["expectedRevision", "expectedStorageGeneration"],
        "Managed plan CAS preconditions drifted",
    )

    status = fixture.get("status")
    require(isinstance(status, dict), "Light status fixture is missing")
    require("storageGeneration" in status.get("rootFields", []), "storageGeneration is missing")
    auto_fields = set(status.get("autoFields", []))
    require(
        {
            "scheduleSource", "planRevision", "planInstalled", "planId",
            "activePlanPhaseIndex", "planRuntimeState", "planTransitionPermille",
            "nextPlanTransitionEpochDay",
        } <= auto_fields,
        "Light status managed-plan fields drifted",
    )

    graph = load_json(GRAPH_FIXTURE_PATH)
    require("planSpans" in graph.get("responseFields", []), "Light graph planSpans missing")
    require(
        graph.get("planSpanTuple") == [
            "startTimeMsWithinToday", "endTimeMsWithinToday", "planId", "phaseIndex"
        ],
        "Light graph managed-plan tuple drifted",
    )
    require("MANAGED_PLAN" in graph.get("basisValues", []), "Light graph basis drifted")

    ws = load_json(WS_FIXTURE_PATH)
    authenticated = set(ws.get("commandAccess", {}).get("authenticated", []))
    require(PLAN_COMMANDS <= authenticated, "WebSocket command access is missing managed-plan actions")


def verify_global_interop_pin() -> None:
    interop = load_json(INTEROP_PATH)
    firmware = interop.get("firmware")
    require(isinstance(firmware, dict), "Global interoperability firmware pin is missing")
    require(firmware.get("commit") == FIRMWARE_COMMIT, "Global interoperability commit drifted")


def main() -> int:
    try:
        verify_pin()
        verify_android_contract()
        verify_managed_plan_contract()
        verify_global_interop_pin()
    except (GuardFailure, OSError, UnicodeError, KeyError, TypeError, ValueError) as error:
        print(f"Light V1 contract guard failed: {error}", file=sys.stderr)
        return 1

    print(
        "Light V1 contract guard passed: firmware commit/tree/source pins, six byte-identical "
        "shared fixtures, managed AUTO plan CAS/status/graph and WebSocket command parity."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
