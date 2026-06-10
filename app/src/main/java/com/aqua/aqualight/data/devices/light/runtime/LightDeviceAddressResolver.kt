package com.aqua.aqualight.data.devices.light.runtime

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import kotlinx.coroutines.flow.first

class LightDeviceAddressResolver(
    private val context: Context
) {

    private val appContext =
        context.applicationContext

    private val devicesDataStoreManager =
        DevicesDataStoreManager.create(appContext)

    suspend fun resolve(
        deviceId: Long,
        requireOnline: Boolean = false
    ): Result {
        if (deviceId <= 0L) {
            return Result.Failure("Device information is missing")
        }

        val presenceState = DevicePresenceMonitor.statuses.value[deviceId]

        if (
            presenceState != null &&
            presenceState.ip.isNotBlank() &&
            (!requireOnline || presenceState.isOnline)
        ) {
            return Result.Success(
                deviceId = deviceId,
                ip = presenceState.ip,
                isOnline = presenceState.isOnline
            )
        }

        val savedDevice = devicesDataStoreManager.devicesFlow
            .first()
            .firstOrNull { device ->
                device.id == deviceId
            }

        if (savedDevice == null) {
            return Result.Failure("Light device could not be found")
        }

        val savedIp = savedDevice.ip

        if (savedIp.isBlank()) {
            return Result.Failure("Device IP address is missing")
        }

        if (!requireOnline) {
            return Result.Success(
                deviceId = deviceId,
                ip = savedIp,
                isOnline = presenceState?.isOnline ?: false
            )
        }

        val checkedState = DevicePresenceMonitor.checkDeviceNow(
            context = appContext,
            deviceId = deviceId,
            knownIp = savedIp
        )

        if (checkedState == null) {
            return Result.Failure("Device status could not be checked")
        }

        if (!checkedState.isOnline) {
            return Result.Failure("Device is offline")
        }

        if (checkedState.ip.isBlank()) {
            return Result.Failure("Device IP address is missing")
        }

        return Result.Success(
            deviceId = deviceId,
            ip = checkedState.ip,
            isOnline = true
        )
    }

    sealed class Result {

        data class Success(
            val deviceId: Long,
            val ip: String,
            val isOnline: Boolean
        ) : Result()

        data class Failure(
            val message: String
        ) : Result()
    }
}