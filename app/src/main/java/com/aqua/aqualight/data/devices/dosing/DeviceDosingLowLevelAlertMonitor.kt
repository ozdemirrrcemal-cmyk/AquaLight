package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingLowLevelAlertTextResolver
import com.aqua.aqualight.application.notifications.DeviceAlertNotification
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * Bridges authoritative Dosing reservoir transitions to the one central notification dispatcher.
 *
 * No Android notification API, channel registry or parallel preference store is permitted here.
 */
internal class DeviceDosingLowLevelAlertMonitor(
    ownerUid: String,
    private val ledger: DeviceDosingLowLevelAlertLedger,
    private val notificationDispatch: NotificationDispatchUseCase,
    private val textResolver: DeviceDosingLowLevelAlertTextResolver
) {
    private val ownerUid = ownerUid.trim().also { uid -> require(uid.isNotBlank()) }

    suspend fun monitor(snapshots: Flow<List<DeviceDosingChannelSnapshot>>) {
        snapshots.collect { channels ->
            channels.forEach { snapshot -> reconcile(snapshot) }
        }
    }

    suspend fun reconcile(snapshot: DeviceDosingChannelSnapshot) {
        val reservoir = snapshot.reservoir
        val authoritativeLow = reservoir.trackingEnabled && reservoir.lowLevelActive
        val pending = ledger.observeLowLevel(
            deviceUid = snapshot.deviceUid,
            slotId = snapshot.slotId,
            lowLevelActive = authoritativeLow
        )
        if (!pending) return

        if (!reservoir.lowLevelAlertEnabled) {
            ledger.completeDispatch(snapshot.deviceUid, snapshot.slotId)
            return
        }

        val copy = textResolver.resolve(snapshot.channelTitle)
        val completed = runCatching {
            notificationDispatch.dispatchDeviceAlert(
                DeviceAlertNotification(
                    ownerUid = ownerUid,
                    deviceUid = snapshot.deviceUid,
                    title = copy.title,
                    message = copy.message
                )
            )
        }.isSuccess
        if (completed) {
            ledger.completeDispatch(snapshot.deviceUid, snapshot.slotId)
        }
    }
}
