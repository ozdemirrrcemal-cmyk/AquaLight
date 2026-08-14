package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSettings
import kotlinx.coroutines.flow.Flow

/** Routes installable fixture devices to mutable UI data while real devices remain fail-closed. */
internal class DebugFixtureDosingChannelOperations(
    private val delegate: DeviceDosingChannelOperations,
    private val fixtures: DebugDeviceFixtureCatalog,
    private val stateStore: DebugFixtureDosingStateStore
) : DeviceDosingChannelOperations {

    override fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingChannelSnapshot?> = if (fixtures.contains(deviceUid)) {
        stateStore.observeChannel(deviceUid, slotId)
    } else {
        delegate.observe(deviceUid, slotId)
    }

    override fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
        if (fixtures.contains(deviceUid)) {
            stateStore.observeChannels(deviceUid)
        } else {
            delegate.observeAll(deviceUid)
        }

    override suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = if (fixtures.contains(deviceUid)) {
        stateStore.refreshChannel(deviceUid, slotId)
    } else {
        delegate.refresh(deviceUid, slotId)
    }

    override suspend fun refreshAll(deviceUid: String): Boolean = if (fixtures.contains(deviceUid)) {
        stateStore.refreshChannels(deviceUid)
    } else {
        delegate.refreshAll(deviceUid)
    }

    override suspend fun applyProgram(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram
    ): DeviceDosingChannelOperationResult = if (fixtures.contains(deviceUid)) {
        stateStore.applyProgram(deviceUid, slotId, program)
    } else {
        delegate.applyProgram(deviceUid, slotId, program)
    }

    override suspend fun setMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult = if (fixtures.contains(deviceUid)) {
        stateStore.setMissedDoseRecoveryEnabled(deviceUid, slotId, enabled)
    } else {
        delegate.setMissedDoseRecoveryEnabled(deviceUid, slotId, enabled)
    }

    override suspend fun applyReservoirSettings(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings
    ): DeviceDosingChannelOperationResult = if (fixtures.contains(deviceUid)) {
        stateStore.applyReservoirSettings(deviceUid, slotId, settings)
    } else {
        delegate.applyReservoirSettings(deviceUid, slotId, settings)
    }

    override suspend fun refillReservoir(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = if (fixtures.contains(deviceUid)) {
        stateStore.refillReservoir(deviceUid, slotId)
    } else {
        delegate.refillReservoir(deviceUid, slotId)
    }

    override suspend fun doseNow(
        deviceUid: String,
        slotId: String,
        amountMicroliters: Long
    ): DeviceDosingChannelOperationResult = if (fixtures.contains(deviceUid)) {
        stateStore.doseNow(deviceUid, slotId, amountMicroliters)
    } else {
        delegate.doseNow(deviceUid, slotId, amountMicroliters)
    }

    override suspend fun doseStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = if (fixtures.contains(deviceUid)) {
        stateStore.doseStop(deviceUid, slotId)
    } else {
        delegate.doseStop(deviceUid, slotId)
    }

    override suspend fun reset(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = if (fixtures.contains(deviceUid)) {
        stateStore.resetChannel(deviceUid, slotId)
    } else {
        delegate.reset(deviceUid, slotId)
    }
}
