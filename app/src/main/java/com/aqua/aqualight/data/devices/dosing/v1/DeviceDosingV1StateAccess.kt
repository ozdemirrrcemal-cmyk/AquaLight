package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.flow.Flow

/** Stateless address and read access over the single authoritative state owner. */
internal class DeviceDosingV1StateAccess(
    private val stateOwner: DeviceDosingV1StateOwner
) {
    fun observeChannel(deviceUid: String, slotId: String): Flow<DeviceDosingChannelSnapshot?> {
        val address = dosingV1Address(deviceUid, slotId)
        return stateOwner.reads.observeChannel(address.deviceUid, address.channelKey)
    }

    fun observeCalibration(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingCalibrationSnapshot?> {
        val address = dosingV1Address(deviceUid, slotId)
        return stateOwner.reads.observeCalibration(address.deviceUid, address.channelKey)
    }

    fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
        stateOwner.reads.observeAll(DeviceUid(deviceUid.trim()))

    fun currentChannel(deviceUid: String, slotId: String): DeviceDosingChannelSnapshot? {
        val address = dosingV1Address(deviceUid, slotId)
        return stateOwner.reads.currentChannel(address.deviceUid, address.channelKey)
    }

    fun currentValidatedPresentationChannel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelSnapshot? {
        val address = dosingV1Address(deviceUid, slotId)
        return stateOwner.reads.currentValidatedPresentationChannel(
            address.deviceUid,
            address.channelKey
        )
    }

    fun currentNavigationChannel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelSnapshot? {
        val address = dosingV1Address(deviceUid, slotId)
        return stateOwner.reads.currentNavigationChannel(
            address.deviceUid,
            address.channelKey
        )
    }

    fun currentCalibration(deviceUid: String, slotId: String): DeviceDosingCalibrationSnapshot? {
        val address = dosingV1Address(deviceUid, slotId)
        return stateOwner.reads.currentCalibration(address.deviceUid, address.channelKey)
    }

    fun currentState(address: DeviceDosingV1Address): DeviceDosingV1AuthoritativeState? =
        stateOwner.reads.currentChannel(address.deviceUid, address.channelKey)?.let { channel ->
            stateOwner.reads.currentCalibration(address.deviceUid, address.channelKey)
                ?.let { calibration -> DeviceDosingV1AuthoritativeState(channel, calibration) }
        }

    /** Mutation-only continuation from a durable ACK; never exposed as authoritative UI state. */
    fun committedMutationContinuation(
        address: DeviceDosingV1Address
    ): DeviceDosingV1AuthoritativeState? = stateOwner.reads.committedMutationContinuation(
        address.deviceUid,
        address.channelKey
    )?.let { continuation ->
        DeviceDosingV1AuthoritativeState(
            channel = continuation.channel,
            calibration = continuation.calibration
        )
    }

    fun authoritativeRevision(address: DeviceDosingV1Address): Long? =
        stateOwner.reads.authoritativeRevision(address.deviceUid, address.channelKey)

    fun setLowLevelAlertIntent(deviceUid: String, slotId: String, enabled: Boolean) {
        val address = dosingV1Address(deviceUid, slotId)
        stateOwner.setLowLevelAlertIntent(address.deviceUid, address.channelKey, enabled)
    }

    fun invalidateAll(deviceUid: DeviceUid) {
        stateOwner.invalidateAll(deviceUid)
    }
}

internal fun dosingV1Address(deviceUid: String, slotId: String): DeviceDosingV1Address =
    DeviceDosingV1Address(
        deviceUid = DeviceUid(deviceUid.trim()),
        channelKey = DeviceDosingV1SlotKeyMapper.channelKey(slotId.trim())
    )

internal data class DeviceDosingV1Address(
    val deviceUid: DeviceUid,
    val channelKey: DeviceDosingV1ChannelKey
)
