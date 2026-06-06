package com.aqua.aqualight.data.devices.light.runtime

import android.content.Context
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object LightDeviceLiveRefreshManager {

    private const val DEFAULT_REFRESH_INTERVAL_MS = 5_000L

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val lock = Any()

    private val states =
        mutableMapOf<Long, MutableStateFlow<LightDeviceLiveState>>()

    private val refreshJobs =
        mutableMapOf<Long, Job>()

    fun observe(
        deviceId: Long
    ): StateFlow<LightDeviceLiveState> {
        return stateFor(
            deviceId = deviceId
        ).asStateFlow()
    }

    fun start(
        context: Context,
        deviceId: Long,
        refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS
    ) {
        if (deviceId <= 0L) {
            return
        }

        val appContext = context.applicationContext

        DevicePresenceMonitor.start(appContext)

        synchronized(lock) {
            val existingJob = refreshJobs[deviceId]

            if (existingJob?.isActive == true) {
                return
            }

            refreshJobs[deviceId] = scope.launch {
                refreshInternal(
                    context = appContext,
                    deviceId = deviceId,
                    showRefreshing = true
                )

                while (isActive) {
                    delay(refreshIntervalMs)

                    refreshInternal(
                        context = appContext,
                        deviceId = deviceId,
                        showRefreshing = false
                    )
                }
            }
        }
    }

    fun stop(
        deviceId: Long
    ) {
        synchronized(lock) {
            refreshJobs.remove(deviceId)?.cancel()
        }
    }

    fun refreshNow(
        context: Context,
        deviceId: Long
    ) {
        if (deviceId <= 0L) {
            return
        }

        val appContext = context.applicationContext

        scope.launch {
            refreshInternal(
                context = appContext,
                deviceId = deviceId,
                showRefreshing = true
            )
        }
    }

    private suspend fun refreshInternal(
        context: Context,
        deviceId: Long,
        showRefreshing: Boolean
    ) {
        val stateFlow = stateFor(
            deviceId = deviceId
        )

        if (showRefreshing) {
            stateFlow.update { state ->
                state.copy(
                    isRefreshing = true,
                    errorMessage = null
                )
            }
        }

        val addressResolver = LightDeviceAddressResolver(
            context = context
        )

        val address = when (
            val result = addressResolver.resolve(
                deviceId = deviceId,
                requireOnline = false
            )
        ) {
            is LightDeviceAddressResolver.Result.Success -> {
                result
            }

            is LightDeviceAddressResolver.Result.Failure -> {
                stateFlow.update { state ->
                    state.copy(
                        isRefreshing = false,
                        errorMessage = result.message
                    )
                }
                return
            }
        }

        val reader = Esp32LightDeviceLiveReader()

        reader.read(
            ip = address.ip
        ).onSuccess { snapshot ->
            stateFlow.update { state ->
                state.copy(
                    isRefreshing = false,
                    deviceTime = snapshot.deviceTime ?: state.deviceTime,
                    channels = if (snapshot.channels.isNotEmpty()) {
                        snapshot.channels
                    } else {
                        state.channels
                    },
                    lastUpdatedMillis = System.currentTimeMillis(),
                    errorMessage = snapshot.partialErrorMessage
                )
            }
        }.onFailure { error ->
            stateFlow.update { state ->
                state.copy(
                    isRefreshing = false,
                    errorMessage = error.message ?: "Live device data could not be read"
                )
            }
        }
    }

    private fun stateFor(
        deviceId: Long
    ): MutableStateFlow<LightDeviceLiveState> {
        return synchronized(lock) {
            states.getOrPut(deviceId) {
                MutableStateFlow(
                    LightDeviceLiveState.initial(
                        deviceId = deviceId
                    )
                )
            }
        }
    }
}