package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Stable OTA presentation state for fixture UIDs; real devices retain the production updater. */
internal class DebugFixtureFirmwareUpdateOperations(
    private val delegate: DeviceFirmwareUpdateOperations,
    fixtures: DebugDeviceFixtureCatalog
) : DeviceFirmwareUpdateOperations {

    private val fixtureStates: Map<String, MutableStateFlow<DeviceOtaState>> =
        fixtures.snapshots.associate { snapshot ->
            snapshot.deviceUid.value to MutableStateFlow<DeviceOtaState>(
                DeviceOtaState.UpToDate(
                    deviceUid = snapshot.deviceUid.value,
                    currentVersion = snapshot.firmwareVersion,
                    latestVersion = snapshot.firmwareVersion,
                    releaseContent = DeviceFirmwareReleaseContent.EMPTY
                )
            )
        }

    override fun observe(deviceUid: String): StateFlow<DeviceOtaState> =
        fixtureStates[deviceUid.trim()] ?: delegate.observe(deviceUid)

    override suspend fun refreshAvailabilityIfStale(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> = fixtureState(deviceUid)
        ?.let { state -> Result.success(state) }
        ?: delegate.refreshAvailabilityIfStale(deviceUid, manifestUrl, applyNow)

    override suspend fun checkAvailability(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> = fixtureState(deviceUid)
        ?.let { state -> Result.success(state) }
        ?: delegate.checkAvailability(deviceUid, manifestUrl, applyNow)

    override suspend fun prepareUpdate(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<PreparedDeviceFirmwareUpdate> = if (isFixture(deviceUid)) {
        Result.failure(
            UnsupportedOperationException(
                "Firmware installation is disabled for debug fixtures."
            )
        )
    } else {
        delegate.prepareUpdate(deviceUid, manifestUrl, applyNow)
    }

    override suspend fun startUpdate(plan: PreparedDeviceFirmwareUpdate): DeviceFirmwareCommandResult =
        if (isFixture(plan.deviceUid)) {
            DeviceFirmwareCommandResult(sent = false)
        } else {
            delegate.startUpdate(plan)
        }

    override suspend fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult =
        if (isFixture(deviceUid)) {
            DeviceFirmwareCommandResult(sent = true)
        } else {
            delegate.requestStatus(deviceUid)
        }

    override suspend fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult =
        if (isFixture(deviceUid)) {
            DeviceFirmwareCommandResult(sent = true)
        } else {
            delegate.clearStatus(deviceUid)
        }

    override fun close() = delegate.close()

    private fun fixtureState(deviceUid: String): DeviceOtaState? =
        fixtureStates[deviceUid.trim()]?.value

    private fun isFixture(deviceUid: String): Boolean = deviceUid.trim() in fixtureStates
}
