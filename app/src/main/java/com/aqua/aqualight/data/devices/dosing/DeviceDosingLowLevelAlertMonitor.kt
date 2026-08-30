package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingLowLevelAlertTextResolver
import com.aqua.aqualight.application.notifications.DeviceAlertNotification
import com.aqua.aqualight.application.notifications.NotificationDispatchResult
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

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

    suspend fun monitor(
        snapshots: Flow<List<DeviceDosingChannelSnapshot>>,
        deliveryRetrySignals: Flow<Unit> = emptyFlow()
    ) {
        var latestSnapshots = emptyList<DeviceDosingChannelSnapshot>()
        merge(
            snapshots.map(AlertMonitorInput::Snapshots),
            deliveryRetrySignals.map { AlertMonitorInput.RetryDelivery }
        ).collect { input ->
            when (input) {
                is AlertMonitorInput.Snapshots -> {
                    latestSnapshots = input.channels
                    input.channels.forEach { snapshot -> reconcile(snapshot) }
                }
                AlertMonitorInput.RetryDelivery -> {
                    latestSnapshots.forEach { snapshot -> reconcile(snapshot) }
                }
            }
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
        val dispatchResult = runCatching {
            notificationDispatch.dispatchDeviceAlert(
                DeviceAlertNotification(
                    ownerUid = ownerUid,
                    deviceUid = snapshot.deviceUid,
                    title = copy.title,
                    message = copy.message
                )
            )
        }.getOrNull()
        when (dispatchResult) {
            NotificationDispatchResult.POSTED,
            NotificationDispatchResult.OWNER_PREFERENCE_DISABLED -> {
                // A deliberate owner preference does not become a historical notification when
                // re-enabled. Android delivery blocks and transient failures remain retryable.
                ledger.completeDispatch(snapshot.deviceUid, snapshot.slotId)
            }
            NotificationDispatchResult.SYSTEM_BLOCKED,
            null -> Unit
        }
    }
}

private sealed interface AlertMonitorInput {
    data class Snapshots(
        val channels: List<DeviceDosingChannelSnapshot>
    ) : AlertMonitorInput

    data object RetryDelivery : AlertMonitorInput
}
