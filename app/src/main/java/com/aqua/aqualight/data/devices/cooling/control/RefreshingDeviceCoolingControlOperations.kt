package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Cooling preparation adapter that mirrors Dosing's active authoritative refresh boundary.
 *
 * Only the pre-navigation surface preparation receives this decorator. Normal presentation
 * observers keep using the passive central-state adapter and never start firmware reads.
 */
internal class RefreshingDeviceCoolingControlOperations(
    private val delegate: DeviceCoolingControlOperations
) : DeviceCoolingControlOperations by delegate {

    override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> = flow {
        emit(delegate.refreshControl(deviceUid))
        emitAll(delegate.observeControl(deviceUid))
    }
}
