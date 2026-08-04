package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareChannel
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

/**
 * Shared production OTA data boundary.
 *
 * Family-specific Settings screens must use the common application coordinator built on this
 * repository. Product matching, channel resolution and OTA safety never belong to presentation
 * code or caller-provided URLs.
 */
internal class DeviceFirmwareUpdateRepository(
    private val runtime: DeviceFirmwareRuntimeRepository,
    private val manifestSource: DeviceFirmwareManifestHttpSource = DeviceFirmwareManifestHttpSource(),
    private val planner: DeviceFirmwareUpdatePlanner = DeviceFirmwareUpdatePlanner(),
    private val signatureVerifier: DeviceFirmwareManifestSignatureVerifier =
        DeviceFirmwareManifestSignatureVerifier(),
    private val channelManifestResolver: DeviceFirmwareChannelManifestResolver =
        DeviceFirmwareChannelManifestResolver()
) {

    suspend fun fetchManifest(
        snapshot: DeviceSnapshot,
        channel: DeviceFirmwareChannel
    ): Result<DeviceFirmwareManifest> = runCatching {
        val location = channelManifestResolver.resolve(snapshot, channel)
        manifestSource.load(location).getOrThrow()
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
        channel: DeviceFirmwareChannel,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareAvailability> = runCatching {
        val manifest = fetchManifest(snapshot, channel).getOrThrow()
        evaluateUpdate(snapshot, manifest, applyNow).getOrThrow()
    }

    suspend fun fetchAndPlanUpdate(
        snapshot: DeviceSnapshot,
        channel: DeviceFirmwareChannel,
        applyNow: Boolean = true
    ): Result<DeviceFirmwareUpdatePlan> = runCatching {
        when (val availability = fetchAndEvaluateUpdate(snapshot, channel, applyNow).getOrThrow()) {
            is DeviceFirmwareAvailability.UpdateAvailable -> availability.plan
            is DeviceFirmwareAvailability.UpToDate -> error("No newer compatible OTA artifact found.")
        }
    }

    suspend fun requestOtaStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaSnapshot> = runtime.readOtaStatus(deviceUid)

    suspend fun startUpdate(
        plan: DeviceFirmwareUpdatePlan
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStartAccepted> = runtime.startUpdate(plan)

    suspend fun clearOtaStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaClearResult> =
        runtime.clearOtaStatus(deviceUid)
}
