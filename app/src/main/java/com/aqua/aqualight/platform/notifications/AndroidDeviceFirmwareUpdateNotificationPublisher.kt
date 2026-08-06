package com.aqua.aqualight.platform.notifications

import android.content.Context
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.notifications.NotificationDispatchResult
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAvailabilityHint
import com.aqua.aqualight.data.notifications.DeviceUpdateNotificationLedger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface DeviceFirmwareUpdateNotificationOperations {
    suspend fun publishOtaState(
        ownerUid: String,
        state: DeviceOtaState,
        deviceName: String
    )

    suspend fun publishAvailabilityHint(
        ownerUid: String,
        hint: DeviceFirmwareAvailabilityHint.UpdateAvailable
    ): Boolean

    suspend fun clearAvailability(ownerUid: String, deviceUid: String)

    suspend fun reconcileDevices(ownerUid: String, currentDeviceUids: Set<String>)

    suspend fun clearOwner(ownerUid: String)
}

/** Central device-update notification coordinator shared by foreground and background flows. */
internal class AndroidDeviceFirmwareUpdateNotificationPublisher(
    context: Context,
    private val dispatchUseCase: NotificationDispatchUseCase,
    private val renderer: AndroidNotificationRenderer,
    private val ledger: DeviceUpdateNotificationLedger,
    private val notificationFactory: DeviceFirmwareUpdateNotificationFactory =
        DeviceFirmwareUpdateNotificationFactory(context)
) : DeviceFirmwareUpdateNotificationOperations {

    private val deviceLocks = ConcurrentHashMap<DeviceIdentity, Mutex>()

    override suspend fun publishOtaState(
        ownerUid: String,
        state: DeviceOtaState,
        deviceName: String
    ) {
        val owner = requireOwnerUid(ownerUid)
        val notification = notificationFactory.fromOtaState(owner, state, deviceName) ?: return
        withDeviceLock(owner, state.deviceUid) {
            dispatchUseCase.dispatchDeviceUpdate(notification)
        }
    }

    override suspend fun publishAvailabilityHint(
        ownerUid: String,
        hint: DeviceFirmwareAvailabilityHint.UpdateAvailable
    ): Boolean = withDeviceLock(ownerUid, hint.deviceUid) {
        val owner = requireOwnerUid(ownerUid)
        val operationActive = renderer.isDeviceUpdateOperationNotificationActive(
            owner,
            hint.deviceUid
        )
        val alreadyAnnounced = ledger.isAnnounced(
            owner,
            hint.deviceUid,
            hint.targetVersion
        )
        val eligible = !operationActive && !alreadyAnnounced
        if (eligible) {
            dispatchAvailability(owner, hint)
        } else {
            false
        }
    }

    private suspend fun dispatchAvailability(
        ownerUid: String,
        hint: DeviceFirmwareAvailabilityHint.UpdateAvailable
    ): Boolean {
        val notification = notificationFactory.availability(ownerUid, hint)
        val result = dispatchUseCase.dispatchDeviceUpdate(notification)
        val posted = result == NotificationDispatchResult.POSTED
        if (posted) {
            ledger.markAnnounced(ownerUid, hint.deviceUid, hint.targetVersion)
        }
        return posted
    }

    override suspend fun clearAvailability(ownerUid: String, deviceUid: String) {
        withDeviceLock(ownerUid, deviceUid) {
            val owner = requireOwnerUid(ownerUid)
            if (renderer.isDeviceUpdateOperationNotificationActive(owner, deviceUid)) {
                return@withDeviceLock
            }
            renderer.cancelDeviceUpdate(owner, deviceUid)
            ledger.clearDevice(owner, deviceUid)
        }
    }

    override suspend fun reconcileDevices(
        ownerUid: String,
        currentDeviceUids: Set<String>
    ) {
        val owner = requireOwnerUid(ownerUid)
        val removedDeviceUids = ledger.trackedDeviceUids(owner) - currentDeviceUids
        removedDeviceUids.forEach { deviceUid ->
            clearRemovedDevice(owner, deviceUid)
        }
    }

    override suspend fun clearOwner(ownerUid: String) {
        val owner = requireOwnerUid(ownerUid)
        renderer.cancelOwner(owner)
        ledger.clearOwner(owner)
    }

    private suspend fun clearRemovedDevice(ownerUid: String, deviceUid: String) {
        withDeviceLock(ownerUid, deviceUid) {
            renderer.cancelDeviceUpdate(ownerUid, deviceUid)
            ledger.clearDevice(ownerUid, deviceUid)
        }
    }

    private suspend fun <T> withDeviceLock(
        ownerUid: String,
        deviceUid: String,
        block: suspend () -> T
    ): T {
        val identity = DeviceIdentity(
            ownerUid = requireOwnerUid(ownerUid),
            deviceUid = requireDeviceUid(deviceUid)
        )
        val lock = deviceLocks.computeIfAbsent(identity) { Mutex() }
        return lock.withLock { block() }
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return ownerUid.trim().also { normalized ->
            require(normalized.isNotBlank()) { "ownerUid must not be blank" }
        }
    }

    private fun requireDeviceUid(deviceUid: String): String {
        return deviceUid.trim().also { normalized ->
            require(normalized.isNotBlank()) { "deviceUid must not be blank" }
        }
    }

    private data class DeviceIdentity(
        val ownerUid: String,
        val deviceUid: String
    )
}
