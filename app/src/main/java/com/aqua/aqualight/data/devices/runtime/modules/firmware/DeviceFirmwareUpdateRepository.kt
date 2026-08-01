package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

/**
 * Shared production OTA data boundary.
 *
 * Family-specific Settings screens must use the common application coordinator built on this
 * repository. Product matching and OTA safety never belong to presentation code.
 */
class DeviceFirmwareUpdateRepository(
    private val runtime: DeviceFirmwareRuntimeRepository,
    private val manifestSource: DeviceFirmwareManifestHttpSource = DeviceFirmwareManifestHttpSource(),
    private val planner: DeviceFirmwareUpdatePlanner = DeviceFirmwareUpdatePlanner(),
    private val signatureVerifier: DeviceFirmwareManifestSignatureVerifier =
        DeviceFirmwareManifestSignatureVerifier()
) {

    suspend fun fetchManifest(manifestUrl: String): Result<DeviceFirmwareManifest> {
        return manifestSource.load(manifestUrl)
    }

    fun parseManifest(rawManifest: String): Result<DeviceFirmwareManifest> {
        return signatureVerifier.verifyAndParse(rawManifest)
    }

    fun evaluateUpdate(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareAvailability> = planner.evaluateUpdate(snapshot, manifest, applyNow)

    fun planUpdate(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareUpdatePlan> = planner.planUpdate(snapshot, manifest, applyNow)

    suspend fun fetchAndEvaluateUpdate(
        snapshot: DeviceSnapshot,
        manifestUrl: String,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareAvailability> {
        return runCatching {
            val manifest = fetchManifest(manifestUrl).getOrThrow()
            evaluateUpdate(snapshot, manifest, applyNow).getOrThrow()
        }
    }

    suspend fun fetchAndPlanUpdate(
        snapshot: DeviceSnapshot,
        manifestUrl: String,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareUpdatePlan> {
        return runCatching {
            val availability = fetchAndEvaluateUpdate(snapshot, manifestUrl, applyNow).getOrThrow()
            when (availability) {
                is DeviceFirmwareAvailability.UpdateAvailable -> availability.plan
                is DeviceFirmwareAvailability.UpToDate -> error(
                    "No newer compatible OTA artifact found."
                )
            }
        }
    }

    suspend fun requestFirmwareStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareStatus> = runtime.requestStatus(deviceUid)

    suspend fun requestOtaStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaSnapshot> = runtime.requestOtaStatus(deviceUid)

    fun startUpdate(plan: DeviceFirmwareUpdatePlan): DeviceFirmwareCommandResult {
        return runtime.startUpdate(plan)
    }

    fun clearOtaStatus(deviceUid: DeviceUid): DeviceFirmwareCommandResult {
        return runtime.clearOtaStatus(deviceUid)
    }
}
