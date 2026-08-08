@file:Suppress("TooManyFunctions", "LongParameterList")

package com.aqua.aqualight.platform.notifications

import android.content.Context
import android.util.Log
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.notifications.AppProcessForegroundState
import com.aqua.aqualight.application.notifications.DeviceUpdateNotification
import com.aqua.aqualight.application.notifications.NotificationDispatchResult
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAvailabilityHint
import com.aqua.aqualight.data.devices.update.DeviceFirmwareAvailabilityTrust
import com.aqua.aqualight.data.notifications.DeviceUpdateNotificationLedger
import com.aqua.aqualight.data.user.UserDataScope
import java.util.concurrent.CancellationException
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

    suspend fun dismissAvailability(ownerUid: String, deviceUid: String) {
        cancelUntrustedAvailability(ownerUid, deviceUid)
    }

    suspend fun cancelUntrustedAvailability(ownerUid: String, deviceUid: String) {
        clearAvailability(ownerUid, deviceUid)
    }

    suspend fun clearDeletedDevices(
        ownerUid: String,
        deviceUids: Set<String>
    ): Set<String>

    suspend fun reconcileDevices(ownerUid: String, currentDeviceUids: Set<String>)

    suspend fun clearOwner(ownerUid: String)
}

/** Central device-update notification coordinator shared by foreground and background flows. */
internal class AndroidDeviceFirmwareUpdateNotificationPublisher(
    context: Context,
    private val dispatchUseCase: NotificationDispatchUseCase,
    private val renderer: AndroidNotificationRenderer,
    private val ledger: DeviceUpdateNotificationLedger,
    private val trust: DeviceFirmwareAvailabilityTrust,
    private val notificationFactory: DeviceFirmwareUpdateNotificationFactory =
        DeviceFirmwareUpdateNotificationFactory(context),
    private val isAppForeground: () -> Boolean = AppProcessForegroundState::isForeground
) : DeviceFirmwareUpdateNotificationOperations {

    private val deviceLocks = ConcurrentHashMap<DeviceIdentity, Mutex>()

    override suspend fun publishOtaState(
        ownerUid: String,
        state: DeviceOtaState,
        deviceName: String
    ) {
        val owner = requireOwnerUid(ownerUid)
        val notification = notificationFactory.fromOtaState(owner, state, deviceName)
        when {
            notification != null -> dispatchOtaState(owner, state, notification)
            state.clearsAvailability() -> clearAvailability(owner, state.deviceUid)
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
        if (isAppForeground()) {
            if (!operationActive) {
                renderer.cancelDeviceUpdate(owner, hint.deviceUid)
            }
            false
        } else {
            val alreadyAnnounced = ledger.isAnnounced(
                owner,
                hint.deviceUid,
                hint.targetVersion
            )
            if (operationActive || alreadyAnnounced) {
                false
            } else {
                dispatchAvailability(owner, hint)
            }
        }
    }

    override suspend fun clearAvailability(ownerUid: String, deviceUid: String) {
        withDeviceLock(ownerUid, deviceUid) {
            val owner = requireOwnerUid(ownerUid)
            if (!renderer.isDeviceUpdateOperationNotificationActive(owner, deviceUid)) {
                clearAvailabilityLocked(owner, deviceUid)
            }
        }
    }

    override suspend fun dismissAvailability(ownerUid: String, deviceUid: String) {
        cancelVisibleAvailability(ownerUid, deviceUid)
    }

    override suspend fun cancelUntrustedAvailability(
        ownerUid: String,
        deviceUid: String
    ) {
        cancelVisibleAvailability(ownerUid, deviceUid)
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun clearDeletedDevices(
        ownerUid: String,
        deviceUids: Set<String>
    ): Set<String> {
        val owner = requireOwnerUid(ownerUid)
        val failed = linkedSetOf<String>()
        deviceUids.map(String::trim)
            .filter(String::isNotBlank)
            .forEach { deviceUid ->
                try {
                    clearRemovedDevice(owner, deviceUid)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(
                        TAG,
                        "Device update notification cleanup failed; reconciliation remains pending.",
                        error
                    )
                    failed += deviceUid
                }
            }
        return failed
    }

    override suspend fun reconcileDevices(
        ownerUid: String,
        currentDeviceUids: Set<String>
    ) {
        val owner = requireOwnerUid(ownerUid)
        val current = currentDeviceUids.map(String::trim)
            .filterTo(mutableSetOf(), String::isNotBlank)
        val tracked = ledger.trackedDeviceUids(owner) +
            trust.trackedDeviceUids(owner)
        (tracked - current).forEach { deviceUid ->
            clearRemovedDevice(owner, deviceUid)
        }
    }

    override suspend fun clearOwner(ownerUid: String) {
        val owner = requireOwnerUid(ownerUid)
        renderer.cancelOwner(owner)
        ledger.clearOwner(owner)
        trust.clearOwner(owner)
        deviceLocks.keys.removeAll { identity -> identity.ownerUid == owner }
    }

    private suspend fun dispatchOtaState(
        ownerUid: String,
        state: DeviceOtaState,
        notification: DeviceUpdateNotification
    ) {
        withDeviceLock(ownerUid, state.deviceUid) {
            val result = dispatchUseCase.dispatchDeviceUpdate(notification)
            if (result == NotificationDispatchResult.POSTED) {
                ledger.trackDevice(ownerUid, state.deviceUid)
            }
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

    private suspend fun cancelVisibleAvailability(ownerUid: String, deviceUid: String) {
        withDeviceLock(ownerUid, deviceUid) {
            val owner = requireOwnerUid(ownerUid)
            if (!renderer.isDeviceUpdateOperationNotificationActive(owner, deviceUid)) {
                renderer.cancelDeviceUpdate(owner, deviceUid)
            }
        }
    }

    private suspend fun clearRemovedDevice(ownerUid: String, deviceUid: String) {
        withDeviceLock(ownerUid, deviceUid) {
            clearAvailabilityLocked(ownerUid, deviceUid)
            trust.clearDevice(ownerUid, deviceUid)
        }
    }

    private suspend fun clearAvailabilityLocked(
        ownerUid: String,
        deviceUid: String
    ) {
        renderer.cancelDeviceUpdate(ownerUid, deviceUid)
        ledger.clearDevice(ownerUid, deviceUid)
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
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
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

    private companion object {
        const val TAG = "DeviceUpdateNotify"
    }
}

private fun DeviceOtaState.clearsAvailability(): Boolean = when (this) {
    is DeviceOtaState.Idle,
    is DeviceOtaState.Unsupported,
    is DeviceOtaState.UpToDate -> true
    else -> false
}
