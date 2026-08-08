package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
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
        val manifestResult = fetchManifest(manifestUrl)
        val manifest = manifestResult.getOrElse { error ->
            return if (error is DeviceFirmwareManifestNotPublishedException) {
                noPublishedRelease(snapshot)
            } else {
                Result.failure(error)
            }
        }
        return evaluateUpdate(snapshot, manifest, applyNow)
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

    suspend fun requestOtaStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaSnapshot> = runtime.readOtaStatus(deviceUid)

    suspend fun startUpdate(
        plan: DeviceFirmwareUpdatePlan
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStartAccepted> {
        return runtime.startUpdate(plan)
    }

    suspend fun clearOtaStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaClearResult> {
        return runtime.clearOtaStatus(deviceUid)
    }

    private fun noPublishedRelease(
        snapshot: DeviceSnapshot
    ): Result<DeviceFirmwareAvailability> = runCatching {
        val currentVersion = snapshot.firmwareVersion.trim()
        require(currentVersion.isNotBlank()) { "Current firmware version is not known." }
        DeviceFirmwareAvailability.UpToDate(
            currentVersion = currentVersion,
            latestVersion = currentVersion,
            releaseContent = DeviceFirmwareReleaseContent.EMPTY
        )
    }
}
