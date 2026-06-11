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

    private const val DEFAULT_REFRESH_INTERVAL_MS = 4_000L

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val lock = Any()

    private val states =
        mutableMapOf<Long, MutableStateFlow<LightDeviceLiveState>>()

    private val refreshJobs =
        mutableMapOf<Long, Job>()

    private val activeConsumers =
        mutableMapOf<Long, MutableSet<String>>()

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
        ownerKey: String,
        refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS
    ) {
        if (deviceId <= 0L || ownerKey.isBlank()) {
            return
        }

        val appContext = context.applicationContext

        LightAppVisibilityMonitor.register(appContext)
        DevicePresenceMonitor.start(appContext)

        synchronized(lock) {
            val consumers = activeConsumers.getOrPut(deviceId) {
                mutableSetOf()
            }

            consumers.add(ownerKey)

            val existingJob = refreshJobs[deviceId]

            if (existingJob?.isActive == true) {
                scope.launch {
                    refreshIfForeground(
                        context = appContext,
                        deviceId = deviceId,
                        showRefreshing = true
                    )
                }
                return
            }

            refreshJobs[deviceId] = scope.launch {
                refreshIfForeground(
                    context = appContext,
                    deviceId = deviceId,
                    showRefreshing = true
                )

                while (isActive) {
                    delay(refreshIntervalMs)

                    refreshIfForeground(
                        context = appContext,
                        deviceId = deviceId,
                        showRefreshing = false
                    )
                }
            }
        }
    }

    fun stop(
        deviceId: Long,
        ownerKey: String
    ) {
        if (deviceId <= 0L || ownerKey.isBlank()) {
            return
        }

        synchronized(lock) {
            val consumers = activeConsumers[deviceId]

            consumers?.remove(ownerKey)

            if (!consumers.isNullOrEmpty()) {
                return
            }

            activeConsumers.remove(deviceId)
            refreshJobs.remove(deviceId)?.cancel()

            stateFor(deviceId).value =
                LightDeviceLiveState.initial(
                    deviceId = deviceId
                )
        }
    }

    fun stopAll() {
        synchronized(lock) {
            refreshJobs.values.forEach { job ->
                job.cancel()
            }

            refreshJobs.clear()
            activeConsumers.clear()

            states.forEach { (deviceId, state) ->
                state.value = LightDeviceLiveState.initial(
                    deviceId = deviceId
                )
            }
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

        LightAppVisibilityMonitor.register(appContext)

        scope.launch {
            refreshIfForeground(
                context = appContext,
                deviceId = deviceId,
                showRefreshing = true
            )
        }
    }

    private suspend fun refreshIfForeground(
        context: Context,
        deviceId: Long,
        showRefreshing: Boolean
    ) {
        if (!LightAppVisibilityMonitor.isForeground.value) {
            stateFor(deviceId).value =
                LightDeviceLiveState.initial(
                    deviceId = deviceId
                )
            return
        }

        refreshInternal(
            context = context,
            deviceId = deviceId,
            showRefreshing = showRefreshing
        )
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
                requireOnline = true,
                forceLiveCheck = false
            )
        ) {
            is LightDeviceAddressResolver.Result.Success -> {
                result
            }

            is LightDeviceAddressResolver.Result.Failure -> {
                stateFlow.value = LightDeviceLiveState.initial(
                    deviceId = deviceId
                ).copy(
                    errorMessage = result.message
                )
                return
            }
        }

        val reader = Esp32LightDeviceLiveReader()

        reader.read(
            ip = address.ip
        ).onSuccess { snapshot ->
            val now = System.currentTimeMillis()

            stateFlow.value = LightDeviceLiveState(
                deviceId = deviceId,
                isRefreshing = false,
                deviceTime = snapshot.deviceTime,
                deviceTimeUpdatedMillis = if (snapshot.deviceTime != null) {
                    now
                } else {
                    0L
                },
                channels = snapshot.channels,
                thermalProtection = snapshot.thermalProtection
                    ?: LightThermalProtectionState(),
                cooling = snapshot.cooling
                    ?: LightCoolingState(),
                liveDataUpdatedMillis = if (snapshot.channels.isNotEmpty()) {
                    now
                } else {
                    0L
                },
                isLiveDataFresh = snapshot.channels.isNotEmpty(),
                lastUpdatedMillis = now,
                errorMessage = snapshot.partialErrorMessage
            )
        }.onFailure { error ->
            stateFlow.value = LightDeviceLiveState.initial(
                deviceId = deviceId
            ).copy(
                errorMessage = error.message
                    ?: "Live device data could not be read"
            )
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