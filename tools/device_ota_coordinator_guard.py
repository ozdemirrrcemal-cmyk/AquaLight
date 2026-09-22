#!/usr/bin/env python3
"""Protect shared OTA state coordination and the exact firmware-owned release contract."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/aqua/aqualight"
FILES = {
    "contract": SOURCE / "application/devices/DeviceFirmwareUpdateOperations.kt",
    "runtime_contract": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareRuntimeContract.kt",
    "adapter": SOURCE / "data/devices/DefaultDeviceFirmwareUpdateOperations.kt",
    "coordinator": SOURCE / "data/devices/runtime/modules/firmware/DeviceOtaCoordinator.kt",
    "transaction_store": SOURCE / "data/devices/runtime/modules/firmware/DeviceOtaTransactionStore.kt",
    "encrypted_store": SOURCE / "data/devices/runtime/modules/firmware/SharedPreferencesDeviceOtaTransactionStore.kt",
    "runtime": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareRuntimeRepository.kt",
    "validation": SOURCE / "data/devices/runtime/modules/firmware/DeviceOtaValidation.kt",
    "planner": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareUpdatePlanner.kt",
    "repository": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareUpdateRepository.kt",
    "manifest_source": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareManifestHttpSource.kt",
    "manifest_identity": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareManifestReleaseIdentity.kt",
    "background_probe": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareBackgroundAvailabilityProbe.kt",
    "contract_registry": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareContractRegistry.kt",
    "models": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareModels.kt",
    "manifest": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareManifestParser.kt",
    "status": SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareStatusParser.kt",
    "app_gradle": ROOT / "app/build.gradle",
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
            errors.append(f"{label} contains forbidden OTA token: {token}")


sources = {label: read(label) for label in FILES}

require_tokens(
    "runtime_contract",
    (
        'const val SCHEMA = "aql.ota.product-manifest.v1"',
        '"90919c12ee269c20cff8affa4b417393126fabb6"',
        "OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX",
        "raw.githubusercontent.com/$OFFICIAL_RELEASE_REPOSITORY/main/channels/",
        'const val MAINTENANCE_SCHEMA = "aql.maintenance.v1"',
    ),
)
forbid_tokens(
    "runtime_contract",
    (
        "aql.ota.manifest.v1",
        "aql.ota.product-manifest.v2",
    ),
)

require_tokens(
    "contract",
    (
        "sealed interface DeviceOtaState",
        "data class DeviceFirmwareReleaseContent",
        "enum class DeviceOtaFailureStage",
        "AVAILABILITY_CHECK",
        "UPDATE_EXECUTION",
        "fun observe(deviceUid: String): StateFlow<DeviceOtaState>",
        "suspend fun checkAvailability(",
        "runtimeMetadataGeneration: Long",
        "releaseContent: DeviceFirmwareReleaseContent",
        "data class RolledBack(",
        "data class PostRestartTimeout(",
        "data class UnexpectedFirmware(",
        "data class ReleaseNotPublished(",
        "enum class DeviceFirmwareUpdatePolicyLevel",
        "data class DeviceFirmwareUpdatePolicy(",
        "APPLICATION_UPDATE_REQUIRED",
        "suspend fun retryPostRestartConnection(",
    ),
)
require_tokens(
    "adapter",
    (
        "DeviceOtaCoordinator(",
        "coordinator.observe(",
        "coordinator.checkAvailability(",
        "coordinator.startUpdate(plan)",
        "coordinator.requestStatus(",
        "coordinator.clearStatus(",
        "coordinator.retryPostRestartConnection(",
        "transactionStore = transactionStore",
        "recoverRuntime = devicesRepository::replaceRuntimeAfterControlFailure",
        "runtimeLifecycleEvents = devicesRepository.runtimeLifecycleEvents()",
        "runtimeTypedEvents = devicesRepository.typedRuntimeEvents()",
        ".mapNotNull { state ->",
        "failure.stage == DeviceOtaFailureStage.UPDATE_EXECUTION",
    ),
)
require_tokens(
    "coordinator",
    (
        "events.collect(::processLifecycleEvent)",
        "events.collect(::processTypedEvent)",
        "An OTA operation is already active for this device.",
        "requestFirmwareStatus(deviceUid)",
        "fetchAndEvaluateMaintenanceUpdate(",
        "planAgainstMaintenanceIdentity(",
        "verifyInstalledFirmwareFromMaintenance",
        "parseOtaProgressEventExact",
        "selected.runtimeGeneration != event.generation",
        "Firmware OTA request echo differs from the selected plan.",
        "DeviceOtaState.Recovering",
        "private val startLocks = ConcurrentHashMap<DeviceUid, Mutex>()",
        "startLock(deviceUid).withLock",
        "private suspend fun startUpdateLocked(",
        "startLocks.putIfAbsent(deviceUid, candidate)",
        "pendingVersionVerification",
        "runCatching { refreshDiscovery() }",
        "recoverRuntime(deviceUid)",
        "DeviceRuntimeLifecycleEvent.Authenticated",
        "DeviceRuntimeLifecycleEvent.Unavailable",
        "maintenance.currentVersion == selected.dataPlan.targetVersion",
        "maintenance.currentVersion == selected.dataPlan.currentVersion",
        "DeviceOtaState.RolledBack",
        "DeviceOtaState.PostRestartTimeout",
        "DeviceOtaState.UnexpectedFirmware",
        "DEFAULT_RECOVERY_ATTEMPT_DELAY_MILLIS = 30_000L",
        "DEFAULT_RECOVERY_WINDOW_MILLIS = 120_000L",
        "transactionStore.saveQuarantine(",
        "transactionStore.activeTransactions().forEach(::restoreTransaction)",
    ),
)
require_tokens(
    "transaction_store",
    (
        "data class DeviceOtaTransaction(",
        "val awaitingVersionVerification: Boolean",
        "data class DeviceOtaQuarantine(",
        "fun matches(plan: PreparedDeviceFirmwareUpdate)",
        "fun activeTransactions(): List<DeviceOtaTransaction>",
    ),
)
require_tokens(
    "encrypted_store",
    (
        "EncryptedSharedPreferences.create(",
        "MasterKey.KeyScheme.AES256_GCM",
        "PrefKeyEncryptionScheme.AES256_SIV",
        "PrefValueEncryptionScheme.AES256_GCM",
        ".commitOrThrow()",
        "fun clearOwner()",
    ),
)
require_tokens(
    "runtime",
    (
        "private val gateway: DeviceRuntimeCommandGateway",
        "gateway.execute(",
        "suspend fun startOta(",
        "suspend fun readOtaStatus(",
        "suspend fun clearOtaStatus(",
        "DeviceFirmwareStatusParser.parseOtaStartAcceptedExact",
        "DeviceFirmwareStatusParser.parseOtaClearResultExact",
    ),
)
forbid_tokens("runtime", ("AqlWsCommandClient", "sendLegacy", "LegacyOnlyGateway"))
obsolete_raw_mapper = SOURCE / "data/devices/runtime/modules/firmware/DeviceFirmwareOtaEventMapper.kt"
if obsolete_raw_mapper.exists():
    errors.append(f"obsolete raw OTA mapper remains: {obsolete_raw_mapper.relative_to(ROOT)}")
require_tokens(
    "validation",
    (
        "object DeviceOtaValidator",
        "planAgainstMaintenanceIdentity(",
        "identity.currentVersion != plan.currentVersion",
        "snapshot.targetVersion != plan.targetVersion",
        "snapshot.sha256Expected.equals(plan.firmware.sha256",
        "snapshot.contentLength != plan.firmware.size.toLong()",
        "snapshot.sha256Actual.equals(plan.firmware.sha256",
        "snapshot.bytesWritten != plan.firmware.size.toLong()",
        "object DeviceOtaStateMapper",
    ),
)
require_tokens(
    "planner",
    (
        "fun evaluateUpdate(",
        "fun evaluateMaintenanceUpdate(",
        "requireValidatedSnapshot(snapshot)",
        "manifest.hasExpectedReleaseTag()",
        "Product-scoped OTA manifest must contain exactly one artifact.",
        "val artifact = manifest.artifacts.single()",
        "validateArtifactAgainstIdentity(artifact, manifest, identity)",
        "artifact.env == expectedEnvironment",
        "artifact.compatibility.family == identity.family.wireValue",
        "version = artifact.firmware.version",
        "model = identity.model",
        "runtimeMetadataGeneration = snapshot.runtimeMetadataGeneration",
        "runtimeMetadataGeneration = 0L",
        "val releaseContent = manifest.releaseNotes",
        ".resolve(preferredLocaleTags())",
        "DeviceFirmwareContractRegistry.requireTargetCompatible(",
        "updatePolicy = artifact.updatePolicy",
    ),
)
forbid_tokens(
    "planner",
    (
        "compatibleArtifacts.first()",
        "compatible.first()",
        "exactSingleArtifactOrNull",
        "latestVersion = currentVersion",
        "AqlCommercialDeviceCatalog",
        "AqlCommercialCatalogValidation",
        "requireValidatedProduct",
        "artifact.product.capabilities == snapshot.capabilities",
        "artifact.product.limits == snapshot.limits",
        'require(compatible.isNotEmpty()) {\n            "No compatible OTA artifact found',
    ),
)
require_tokens(
    "repository",
    (
        "DeviceFirmwareManifestNotPublishedException",
        "releaseNotPublished(snapshot)",
        "DeviceFirmwareAvailability.ReleaseNotPublished(currentVersion)",
        "requestFirmwareStatus(",
        "fetchAndEvaluateMaintenanceUpdate(",
        "planner.evaluateMaintenanceUpdate(identity, manifest, applyNow)",
        "throw error",
    ),
)
require_tokens(
    "manifest_source",
    (
        "class DeviceFirmwareManifestNotPublishedException",
        "class DeviceFirmwareManifestHttpException",
        "value.code == HTTP_NOT_FOUND",
        "throw DeviceFirmwareManifestNotPublishedException",
        "throw DeviceFirmwareManifestHttpException",
        "signatureVerifier.verifyAndParse(text)",
        "PRODUCT_CHANNEL_MANIFEST_PATH",
        "PRODUCT_VERSION_MANIFEST_PATH",
        "DEVICE_FIRMWARE_PRODUCT_ENVIRONMENTS",
        "requireFirmwareManifestMatchesUrl(sourceUrl, manifest)",
        "OTA channel manifest URL and signed manifest product differ.",
        "OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX",
    ),
)
forbid_tokens(
    "manifest_source",
    (
        'error.message.contains("404")',
        'message.contains("not found")',
        "OFFICIAL_LATEST_RELEASE_URL_PREFIX",
        "releases/latest/download",
    ),
)
require_tokens(
    "manifest_identity",
    (
        'tag == "$environment-v$version"',
        '"AquaLight-${artifact.env}-v$version-ota.bin"',
        '"AquaLight-${artifact.env}-v$version-factory.zip"',
    ),
)
require_tokens(
    "background_probe",
    (
        "val artifact = manifest.artifacts.single()",
        "validateArtifact(snapshot, manifest, artifact)",
        "DeviceFirmwareContractRegistry.requireTargetCompatible(",
    ),
)
require_tokens(
    "contract_registry",
    (
        "object DeviceFirmwareContractRegistry",
        "supportedRequiredDomains",
        "contracts.requiredDomains != listOf(expectedBaseContract)",
        "contracts.maintenanceSchema != DeviceFirmwareRuntimeContract.MAINTENANCE_SCHEMA",
        "DeviceFirmwareApplicationUpdateRequiredException",
    ),
)
require_tokens(
    "models",
    (
        "val model: String",
        ".put(DeviceFirmwareRuntimeContract.Field.MODEL, model)",
        "data class DeviceFirmwareOtaStartRequestEcho",
        "data class DeviceFirmwareManifestPlatform",
        "data class DeviceFirmwareReleaseNoteItem",
        "data class DeviceFirmwareReleaseNotes",
        "val capabilities: DeviceCapabilities",
        "val limits: DeviceLimits",
        "val version: String",
        "data class DeviceFirmwareFactoryAsset",
        "data class DeviceFirmwareMaintenanceIdentity",
        "maintenanceSchema == DeviceFirmwareRuntimeContract.MAINTENANCE_SCHEMA",
        "data class DeviceFirmwareTargetContracts",
        "val maintenanceSchema: String",
        "val contracts: DeviceFirmwareTargetContracts",
        "val updatePolicy: DeviceFirmwareUpdatePolicy",
        "sealed interface DeviceFirmwareAvailability",
        "data class ReleaseNotPublished(",
    ),
)
require_tokens(
    "app_gradle",
    (
        'tasks.register(\n        "verifyReleaseOtaManifestConfiguration"',
        "AQL_OTA_MANIFEST_PUBLIC_KEY_PEM is required for production release builds.",
        'task.name == "preReleaseBuild"',
        "task.dependsOn(verifyReleaseOtaManifestConfiguration)",
    ),
)
require_tokens(
    "manifest",
    (
        "root.requireExactKeys(ROOT_KEYS, \"manifest\")",
        "parsePlatform",
        "parseReleaseNotes",
        "DeviceFirmwareReleaseNoteItem(",
        "parseCapabilities",
        "parseLimits",
        "parseContracts",
        'maintenanceSchema = json.requiredString("maintenanceSchema")',
        "parseUpdatePolicy",
        "requiredNullableObject(\"factory\")",
        "json.requireExactKeys(FIRMWARE_KEYS, label)",
        "json.requireExactKeys(FACTORY_KEYS, label)",
        "artifact.product.family == artifact.compatibility.family",
        "artifact.product.line == artifact.compatibility.line",
        "artifact.firmware.version == manifest.version",
        "DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT",
    ),
)
forbid_tokens(
    "manifest",
    (
        "releaseNotes.locales",
        "DeviceFirmwareLocalizedReleaseNotes",
        "mandatory =",
        "parseAsset(",
    ),
)
forbid_tokens(
    "coordinator",
    (
        "planAgainstSnapshot(",
        "snapshot.hasValidatedRuntimeMetadata",
        "processSnapshotUpdates",
    ),
)
forbid_tokens(
    "background_probe",
    (
        "artifact.product.capabilities == snapshot.capabilities",
        "artifact.product.limits == snapshot.limits",
    ),
)

require_tokens(
    "status",
    (
        "parseOtaStartAcceptedExact",
        "parseOtaStatusResponseExact",
        "parseOtaProgressEventExact",
        "parseOtaClearResultExact",
        "parseOtaSnapshotExact",
        'model = json.requiredExactString("model")',
        "OTA active flag differs from its exact phase.",
    ),
)

if errors:
    print("Device OTA coordinator guard failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Device OTA coordinator guard passed.")
