package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid

/**
 * Shared production OTA orchestration boundary.
 *
 * Feature screens should call this repository instead of implementing product-specific OTA logic.
 * Product-specific behavior is decided only by firmware identity and OTA manifest compatibility.
 */
class DeviceFirmwareUpdateRepository(
    private val runtime: DeviceFirmwareRuntimeRepository,
    private val manifestSource: DeviceFirmwareManifestHttpSource = DeviceFirmwareManifestHttpSource(),
    private val planner: DeviceFirmwareUpdatePlanner = DeviceFirmwareUpdatePlanner(),
    private val signatureVerifier: DeviceFirmwareManifestSignatureVerifier = DeviceFirmwareManifestSignatureVerifier()
) {

    suspend fun fetchManifest(manifestUrl: String): Result<DeviceFirmwareManifest> {
        return manifestSource.load(manifestUrl)
    }

    fun parseManifest(rawManifest: String): Result<DeviceFirmwareManifest> {
        return signatureVerifier.verifyAndParse(rawManifest)
    }

    fun planUpdate(
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareUpdatePlan> {
        return planner.planUpdate(
            snapshot = snapshot,
            manifest = manifest,
            applyNow = applyNow
        )
    }

    suspend fun fetchAndPlanUpdate(
        snapshot: DeviceSnapshot,
        manifestUrl: String,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareUpdatePlan> {
        return runCatching {
            val manifest = fetchManifest(manifestUrl).getOrThrow()
            planUpdate(
                snapshot = snapshot,
                manifest = manifest,
                applyNow = applyNow
            ).getOrThrow()
        }
    }

    fun requestFirmwareStatus(deviceUid: DeviceUid): DeviceFirmwareCommandResult {
        return runtime.requestStatus(deviceUid)
    }

    fun requestOtaStatus(deviceUid: DeviceUid): DeviceFirmwareCommandResult {
        return runtime.requestOtaStatus(deviceUid)
    }

    fun startUpdate(plan: DeviceFirmwareUpdatePlan): DeviceFirmwareCommandResult {
        return runtime.startUpdate(plan)
    }

    fun clearOtaStatus(deviceUid: DeviceUid): DeviceFirmwareCommandResult {
        return runtime.clearOtaStatus(deviceUid)
    }
}
