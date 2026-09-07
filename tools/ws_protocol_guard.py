#!/usr/bin/env python3
"""Fail CI when the commercial Android WebSocket security contract drifts."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]
FIXTURE_PATH = ROOT / "protocol/fixtures/aql_ws_v1_golden.json"
FIXTURE_SHA256 = "508bd588c118a0c41b66c838c579c45fefcfa5f54b1a608c26b2c9b1ef8984fb"
errors: list[str] = []


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        errors.append(f"{relative}: required protocol file is missing")
        return ""
    return path.read_text(encoding="utf-8", errors="strict")


def require(relative: str, text: str, token: str, reason: str) -> None:
    if token not in text:
        errors.append(f"{relative}: {reason}: missing {token}")


def forbid(relative: str, text: str, token: str, reason: str) -> None:
    if token in text:
        errors.append(f"{relative}: {reason}: forbidden {token}")


manifest_path = "app/src/main/AndroidManifest.xml"
manifest = read(manifest_path)
require(manifest_path, manifest, 'android:usesCleartextTraffic="false"', "global cleartext must be disabled")
require(manifest_path, manifest, 'android:networkSecurityConfig="@xml/network_security_config"', "LAN exception must be explicit")
forbid(manifest_path, manifest, 'android:usesCleartextTraffic="true"', "global cleartext is forbidden")

network_config_path = "app/src/main/res/xml/network_security_config.xml"
network_config = read(network_config_path)
require(network_config_path, network_config, '<base-config cleartextTrafficPermitted="false"', "base policy must fail closed")
require(network_config_path, network_config, '<domain includeSubdomains="true">device.aql.local</domain>', "only the app-owned LAN hostname tree may use ws")

contract_path = "app/src/main/java/com/aqua/aqualight/data/devices/contract/AqlWsContract.kt"
contract = read(contract_path)
for token, reason in (
    ('const val SCHEMA = "aql.ws.v1"', "first commercial schema must remain explicit"),
    ('const val DEFAULT_PATH = "/aql/v1/ws"', "commercial socket path must remain explicit"),
    ('const val PROTOCOL_VERSION = 1', "protocol version must remain explicit"),
    ('const val AUTH_SCHEME = "hmac-sha256"', "challenge-response algorithm must remain explicit"),
    ('const val MESSAGE_BYTES = 8_192', "wire size limit must remain explicit"),
    ('const val DATA_BYTES = 4_096', "data size limit must remain explicit"),
    ('commandKey(MODULE_NETWORK, ACTION_NETWORK_STATUS_GET)', "network status must be registered"),
):
    require(contract_path, contract, token, reason)
for token in ('"aql.ws.v2"', '"/aql/v2/ws"', 'const val TOKEN = "token"'):
    forbid(contract_path, contract, token, "pre-release version drift/plain-token compatibility is forbidden")

ws_dir = ROOT / "app/src/main/java/com/aqua/aqualight/data/devices/runtime/ws"
ws_sources = "\n".join(path.read_text(encoding="utf-8") for path in sorted(ws_dir.glob("*.kt")))
for token, reason in (
    ("sendRaw(", "raw WebSocket bypasses are forbidden"),
    ("toJsonString(", "message models must not serialize themselves"),
    ("AqlWsMessageParser", "the legacy permissive parser is forbidden"),
    ("AqlWsAuthManager", "authentication must be owned by the connection handshake"),
    ("AqlWsOutgoingMessage.Auth", "plaintext auth frames are forbidden"),
    ("Log.", "credentials and raw protocol failures must not reach Android logs"),
    ("println(", "credentials and raw protocol failures must not reach stdout"),
):
    if token in ws_sources:
        errors.append(f"runtime/ws: {reason}: forbidden {token}")

codec_path = "app/src/main/java/com/aqua/aqualight/data/devices/runtime/ws/AqlWsWireCodec.kt"
codec = read(codec_path)
for token, reason in (
    ("requireExactKeys", "wire envelopes must reject unknown or missing fields"),
    ("rejectDuplicateKeys", "duplicate JSON fields must fail closed"),
    ("verifyDevice", "runtime frames must verify MAC and sequence"),
    ("MESSAGE_TOO_LARGE", "wire messages must enforce a byte limit"),
    ("DEVICE_IDENTITY_MISMATCH", "hello must bind the selected device identity"),
):
    require(codec_path, codec, token, reason)

route_path = "app/src/main/java/com/aqua/aqualight/data/devices/runtime/ws/AqlPrivateLanEndpoint.kt"
route = read(route_path)
for token, reason in (
    ('private const val HOST_SUFFIX = ".device.aql.local"', "LAN routing must use the controlled hostname tree"),
    ("endpoint.privateLanAddressBytes()", "route creation must validate a private IP literal"),
    ("hostname != route.syntheticHostname", "custom DNS must reject every unrelated hostname"),
):
    require(route_path, route, token, reason)


def canonical(label: str, fields: list[str]) -> bytes:
    body = "AQL-WS-V1\n" + label + "\n"
    for value in fields:
        body += f"{len(value.encode('utf-8'))}:{value}\n"
    return body.encode("utf-8")


def digest_hex(key: bytes, label: str, fields: list[str]) -> str:
    return hmac.new(key, canonical(label, fields), hashlib.sha256).hexdigest()


def message_fields(direction: str, session_id: str, sequence: int, frame: dict) -> list[str]:
    ok = frame.get("ok")
    error = frame.get("error", {})
    return [
        direction,
        session_id,
        str(sequence),
        frame["id"],
        frame["type"],
        frame["module"],
        frame["action"],
        frame["data"],
        str(frame.get("status", "")),
        "1" if ok is True else "0" if ok is False else "",
        error.get("code", ""),
        error.get("field", ""),
        error.get("message", ""),
    ]


try:
    fixture_bytes = FIXTURE_PATH.read_bytes()
    if hashlib.sha256(fixture_bytes).hexdigest() != FIXTURE_SHA256:
        errors.append("golden fixture checksum drifted; update Android and firmware atomically")
    fixture = json.loads(fixture_bytes)
    contract_fixture = fixture["contract"]
    expected_contract = {
        "schema": "aql.ws.v1",
        "schemaVersion": 1,
        "protocolVersion": 1,
        "path": "/aql/v1/ws",
        "authScheme": "hmac-sha256",
        "maxMessageBytes": 8192,
        "maxDataBytes": 4096,
    }
    if contract_fixture != expected_contract:
        errors.append("golden fixture contract metadata is incompatible")

    inputs = fixture["testInputs"]
    handshake = fixture["handshake"]
    token = inputs["runtimeToken"].encode("ascii")
    credential_key = hashlib.sha256(token).digest()
    if credential_key.hex() != inputs["credentialKeySha256"]:
        errors.append("golden fixture credential key is invalid")
    auth_fields = [
        inputs["deviceUid"],
        inputs["sessionId"],
        inputs["serverNonce"],
        inputs["clientNonce"],
        inputs["authRequestId"],
    ]
    client_proof = digest_hex(credential_key, "client-auth", auth_fields)
    server_proof = digest_hex(credential_key, "server-auth", auth_fields)
    session_key = hmac.new(
        credential_key,
        canonical("session-key", auth_fields),
        hashlib.sha256,
    ).digest()
    if client_proof != handshake["expectedClientProof"]:
        errors.append("golden fixture client proof is invalid")
    if server_proof != handshake["expectedServerProof"]:
        errors.append("golden fixture server proof is invalid")
    if session_key.hex() != handshake["expectedSessionKey"]:
        errors.append("golden fixture session key is invalid")
    if handshake["authRequest"]["data"]["clientProof"] != client_proof:
        errors.append("golden auth request does not carry the expected proof")
    if handshake["authResponse"]["data"]["serverProof"] != server_proof:
        errors.append("golden auth response does not carry the expected proof")
    if "token" in json.dumps(handshake, separators=(",", ":")).lower():
        errors.append("golden wire handshake must never serialize a token field or value")

    runtime = fixture["runtime"]
    signed_frames = (
        ("c2d", runtime["clientCommand"]),
        ("d2c", runtime["deviceResponse"]),
        ("d2c", runtime["deviceEvent"]),
        ("d2c", runtime["deviceError"]),
    )
    for direction, frame in signed_frames:
        security = frame["security"]
        expected_mac = digest_hex(
            session_key,
            "message",
            message_fields(direction, inputs["sessionId"], security["seq"], frame),
        )
        if security["sessionId"] != inputs["sessionId"] or security["mac"] != expected_mac:
            errors.append(f"golden {frame['type']} frame MAC/session is invalid")
        padding = "=" * (-len(frame["data"]) % 4)
        decoded_data = base64.urlsafe_b64decode(frame["data"] + padding)
        if len(decoded_data) > expected_contract["maxDataBytes"]:
            errors.append(f"golden {frame['type']} data exceeds the contract limit")
        if not isinstance(json.loads(decoded_data), dict):
            errors.append(f"golden {frame['type']} data must decode to a JSON object")
        if len(json.dumps(frame, separators=(",", ":")).encode("utf-8")) > expected_contract["maxMessageBytes"]:
            errors.append(f"golden {frame['type']} frame exceeds the contract limit")

    if fixture["invalid"]["fakeDeviceHello"]["data"]["deviceUid"] == inputs["deviceUid"]:
        errors.append("fake-device fixture must use a contradictory device identity")
    access = fixture["commandAccess"]
    if access["public"]:
        errors.append("WebSocket must not expose unauthenticated application commands")
    if len(access["authenticated"]) != 50 or len(set(access["authenticated"])) != 50:
        errors.append("golden authenticated command matrix must contain 50 unique commands")
except (KeyError, TypeError, ValueError, UnicodeError, json.JSONDecodeError) as exc:
    errors.append(f"golden fixture could not be validated: {exc}")

if errors:
    print("WebSocket protocol guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("WebSocket protocol guard passed.")
