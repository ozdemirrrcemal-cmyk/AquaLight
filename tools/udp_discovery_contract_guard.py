#!/usr/bin/env python3
"""Protect the exact Android UDP discovery parser and datagram scanner contract."""
from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"
FILES = {
    "contract": SOURCE / "data/devices/contract/AqlDiscoveryContract.kt",
    "parser": SOURCE / "data/devices/discovery/udp/AqlDiscoveryParser.kt",
    "scanner": SOURCE / "data/devices/discovery/udp/AqlDiscoveryUdpScanner.kt",
    "tests": ROOT
    / "app/src/test/java/com/aqua/aqualight/data/devices/discovery/udp/AqlDiscoveryParserContractTest.kt",
}

errors: list[str] = []


def read(label: str) -> str:
    path = FILES[label]
    try:
        return path.read_text(encoding="utf-8", errors="strict")
    except (OSError, UnicodeError) as exc:
        errors.append(f"{path.relative_to(ROOT)} could not be read: {exc}")
        return ""


def require_tokens(label: str, tokens: tuple[str, ...]) -> None:
    source = sources[label]
    for token in tokens:
        if token not in source:
            errors.append(f"{label} token is missing: {token}")


def forbid_tokens(label: str, tokens: tuple[str, ...]) -> None:
    source = sources[label]
    for token in tokens:
        if token in source:
            errors.append(f"{label} contains forbidden discovery token: {token}")


sources = {label: read(label) for label in FILES}

require_tokens(
    "contract",
    (
        'const val SCHEMA = "aql.discovery.v1"',
        'const val TYPE_DEVICE_ANNOUNCE = "device.announce"',
        'const val TYPE_REFRESH = "refresh"',
        "const val VERSION = 1",
        "const val PORT = 10888",
        "const val MAX_PACKET_SIZE_BYTES = 768",
    ),
)
require_tokens(
    "parser",
    (
        "rawPayload.toByteArray(StandardCharsets.UTF_8).size",
        "JsonReader(StringReader(raw))",
        "reader.isLenient = false",
        "ParseError.DUPLICATE_FIELD",
        "requireExactKeys(ROOT_KEYS)",
        "DeviceFamily.fromWireExact(familyRaw)",
        "runtimeHost.isCanonicalPrivateLanIpv4()",
        "value != runtimeHost",
        "ParseError.RUNTIME_HOST_SOURCE_MISMATCH",
        "wsPath != AqlWsContract.DEFAULT_PATH",
        "wsProtocol != AqlWsContract.DEFAULT_PROTOCOL",
        "endpoint.hasWebSocketEndpoint",
        'private val NETWORK_MODES = setOf("off", "sta", "ap", "ap_sta", "unknown")',
    ),
)
forbid_tokens(
    "parser",
    (
        "optString(",
        "optBoolean(",
        "optInt(",
        "optLong(",
        "optJSONObject(",
        "DeviceFamily.fromWire(",
        "rawPayload.trim()",
        "value.toString().trim()",
        ".lowercase()",
    ),
)
require_tokens(
    "scanner",
    (
        "ByteArray(packetSizeBytes + OVERSIZE_SENTINEL_BYTES)",
        "AqlDiscoveryDatagramDecoder.decode(",
        "length > maximumBytes",
        "CodingErrorAction.REPORT",
        "ByteBuffer.wrap(data, offset, length)",
    ),
)
forbid_tokens(
    "scanner",
    (
        "String(data, offset, length, Charsets.UTF_8)",
        "ByteArray(packetSizeBytes)\n",
    ),
)
require_tokens(
    "tests",
    (
        "unknown fields duplicate keys and scalar coercion",
        "canonical private runtime host matching datagram source",
        "packet limit is measured in utf8 bytes rather than characters",
        "datagram decoder rejects oversize malformed utf8 and invalid slices",
        "ParseError.DUPLICATE_FIELD",
        "ParseError.RUNTIME_HOST_SOURCE_MISMATCH",
    ),
)

if errors:
    print("UDP discovery contract guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("UDP discovery contract guard passed.")
