#!/usr/bin/env python3
"""Fail CI when the protected AquaLight runtime baseline is weakened.

Stage 00 is intentionally behavior-neutral. This guard records the critical
provisioning, private-LAN WebSocket, presence and recovery invariants that must
remain intact during the firmware-to-Android contract migration.
"""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aqua/aqualight"
ERRORS: list[str] = []


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        ERRORS.append(f"missing protected baseline file: {relative}")
        return ""
    return path.read_text(encoding="utf-8", errors="strict")


def require(relative: str, text: str, tokens: tuple[str, ...]) -> None:
    for token in tokens:
        if token not in text:
            ERRORS.append(f"{relative}: missing baseline invariant: {token}")


def forbid(relative: str, text: str, tokens: tuple[str, ...]) -> None:
    for token in tokens:
        if token in text:
            ERRORS.append(f"{relative}: forbidden baseline regression: {token}")


manifest_path = "app/src/main/AndroidManifest.xml"
manifest = read(manifest_path)
require(
    manifest_path,
    manifest,
    (
        'android:usesCleartextTraffic="false"',
        'android:networkSecurityConfig="@xml/network_security_config"',
    ),
)
forbid(manifest_path, manifest, ('android:usesCleartextTraffic="true"',))

network_security_path = "app/src/main/res/xml/network_security_config.xml"
network_security = read(network_security_path)
require(
    network_security_path,
    network_security,
    (
        '<base-config cleartextTrafficPermitted="false"',
        '<domain includeSubdomains="true">device.aql.local</domain>',
    ),
)

credential_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/store/"
    "DeviceCredentialStore.kt"
)
credential = read(credential_path)
require(
    credential_path,
    credential,
    (
        "EncryptedSharedPreferences.create",
        "MasterKey.KeyScheme.AES256_GCM",
        "override suspend fun getToken",
        "suspend fun stageToken",
        "suspend fun commitStagedToken",
        "suspend fun rollbackStagedToken",
        "suspend fun discardStagedTokens",
        "AqlBleProvisioningContract.RUNTIME_TOKEN_HEX_LENGTH",
    ),
)
forbid(
    credential_path,
    credential,
    (
        "PreferenceManager.getDefaultSharedPreferences",
        "context.getSharedPreferences(",
        "Log.",
        "println(",
    ),
)

handoff_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/provisioning/repository/"
    "AqlProvisioningHandoffSaver.kt"
)
handoff = read(handoff_path)
require(
    handoff_path,
    handoff,
    (
        "require(handoff.isUsable)",
        "credentialStore.stageToken",
        "metadataResolver.resolveAndConnect",
        "credentialStore.commitStagedToken",
        "rollbackRegistration",
        "rollbackCredential",
        "credentialStore.rollbackStagedToken",
        "DeviceOnlineState.ONLINE_LAN",
        "withContext(NonCancellable)",
    ),
)
forbid(handoff_path, handoff, ("Log.", "println("))

route_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/runtime/ws/"
    "AqlPrivateLanEndpoint.kt"
)
route = read(route_path)
require(
    route_path,
    route,
    (
        'HOST_SUFFIX = ".device.aql.local"',
        "endpoint.privateLanAddressBytes()",
        'url = "ws://$host:${endpoint.wsPort}${endpoint.wsPath}"',
        "hostname != route.syntheticHostname",
        "InetAddress.getByAddress",
    ),
)

ws_client_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/runtime/ws/AqlWsClient.kt"
)
ws_client = read(ws_client_path)
require(
    ws_client_path,
    ws_client,
    (
        "AqlPrivateLanEndpoint.route",
        "AqlPrivateLanDns(route)",
        "launchHandshakeTimeout",
        "tokenProvider?.getToken",
        "tokenProvider?.clearToken",
        "override fun onMessage(webSocket: WebSocket, bytes: ByteString)",
        "AqlWsProtocolError.UNSUPPORTED_TYPE",
        "activeSecureSession",
        "override suspend fun shutdown",
    ),
)
forbid(ws_client_path, ws_client, ("sendRaw(", "Log.", "println("))

runtime_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/repository/"
    "DeviceRuntimeRepository.kt"
)
runtime = read(runtime_path)
require(
    runtime_path,
    runtime,
    (
        "DeviceRuntimeMetadataBootstrapCoordinator",
        "sendAuthenticatedBootstrap",
        "disconnectForLocalNetworkLoss",
        "reconnectAfterNetworkRestore",
        "tokenLifecycleMutex",
        "session.wsClient.shutdown()",
        "metadataBootstrapCoordinator.clear",
        "AqlWsConnectionState.Authenticated",
    ),
)

presence_path = (
    "app/src/main/java/com/aqua/aqualight/data/devices/monitor/"
    "DevicePresenceRuntimeMonitor.kt"
)
presence = read(presence_path)
require(
    presence_path,
    presence,
    (
        "fun setAppForeground",
        "disconnectForLocalNetworkLoss",
        "reconnectAfterNetworkRestore",
        "requestNetworkStatus",
        "restartScanner",
        "recoverAfterLocalNetworkRestored",
        "DeviceOnlineState.LOCAL_NETWORK_OFFLINE",
        "AUTHENTICATED_LIVENESS_PROBE_INTERVAL_MS",
        "FOREGROUND_REVALIDATION_GRACE_MS",
    ),
)

required_tests = (
    "app/src/test/java/com/aqua/aqualight/data/devices/runtime/ws/"
    "AqlPrivateLanEndpointTest.kt",
    "app/src/test/java/com/aqua/aqualight/data/devices/runtime/ws/"
    "AqlWsWireCodecGoldenTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/devices/store/"
    "DeviceCredentialStoreInstrumentedTest.kt",
    "app/src/androidTest/java/com/aqua/aqualight/data/devices/provisioning/store/"
    "ProvisioningCommitRecoveryStoreInstrumentedTest.kt",
)
for relative in required_tests:
    read(relative)

ws_dir = APP / "data/devices/runtime/ws"
if ws_dir.is_dir():
    combined = "\n".join(
        path.read_text(encoding="utf-8", errors="strict")
        for path in sorted(ws_dir.glob("*.kt"))
    )
    for token in ("sendRaw(", "AqlWsAuthManager", "AqlWsMessageParser"):
        if token in combined:
            ERRORS.append(f"runtime/ws: forbidden legacy or raw transport path: {token}")

if ERRORS:
    print("WebSocket runtime baseline guard failed:", file=sys.stderr)
    for error in ERRORS:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("WebSocket runtime baseline guard passed.")
