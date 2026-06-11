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
    private const val DEVICE_TIME_KEEP_MS = 30_000L

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

        LightManualRuntimeStore.configure(appContext)
        LightDeviceSnapshotCache.configure(appContext)
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

            stateFor(deviceId).update { state ->
                state.copy(
                    isRefreshing = false
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

        LightManualRuntimeStore.configure(appContext)
        LightDeviceSnapshotCache.configure(appContext)
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
            stateFor(deviceId).update { state ->
                state.copy(
                    isRefreshing = false
                )
            }
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
                requireOnline = true
            )
        ) {
            is LightDeviceAddressResolver.Result.Success -> {
                result
            }

            is LightDeviceAddressResolver.Result.Failure -> {
                stateFlow.update { state ->
                    cachedFallbackState(
                        deviceId = deviceId,
                        currentState = state,
                        message = result.message
                    )
                }
                return
            }
        }

        val reader = Esp32LightDeviceLiveReader()

        reader.read(
            ip = address.ip
        ).onSuccess { snapshot ->
            val now = System.currentTimeMillis()

            var nextState: LightDeviceLiveState? = null

            stateFlow.update { state ->
                val cachedState = if (snapshot.channels.isEmpty()) {
                    LightDeviceSnapshotCache.read(
                        deviceId = deviceId
                    )
                } else {
                    null
                }

                val hasAuthoritativeChannels = snapshot.channels.isNotEmpty()

                val displayChannels = when {
                    hasAuthoritativeChannels -> {
                        snapshot.channels
                    }

                    cachedState?.channels?.isNotEmpty() == true -> {
                        cachedState?.channels.orEmpty()
                    }

                    state.channels.isNotEmpty() -> {
                        state.channels
                    }

                    else -> {
                        emptyList()
                    }
                }

                val resolvedDeviceTime = snapshot.deviceTime
                    ?: state.deviceTime.takeIf {
                        state.deviceTimeUpdatedMillis > 0L &&
                            now - state.deviceTimeUpdatedMillis <= DEVICE_TIME_KEEP_MS
                    }
                    ?: cachedState?.deviceTime

                state.copy(
                    isRefreshing = false,
                    deviceTime = resolvedDeviceTime,
                    deviceTimeUpdatedMillis = when {
                        snapshot.deviceTime != null -> {
                            now
                        }

                        resolvedDeviceTime != null && resolvedDeviceTime == cachedState?.deviceTime -> {
                            cachedState?.deviceTimeUpdatedMillis ?: 0L
                        }

                        resolvedDeviceTime != null -> {
                            state.deviceTimeUpdatedMillis
                        }

                        else -> {
                            0L
                        }
                    },
                    channels = displayChannels,
                    thermalProtection = snapshot.thermalProtection
                        ?: cachedState?.thermalProtection
                        ?: state.thermalProtection.takeIf { current -> current.hasData }
                        ?: LightThermalProtectionState(),
                    cooling = snapshot.cooling
                        ?: cachedState?.cooling
                        ?: state.cooling.takeIf { current -> current.hasData }
                        ?: LightCoolingState(),
                    liveDataUpdatedMillis = if (hasAuthoritativeChannels) {
                        now
                    } else {
                        cachedState?.liveDataUpdatedMillis
                            ?: state.liveDataUpdatedMillis
                    },
                    isLiveDataFresh = hasAuthoritativeChannels,
                    lastUpdatedMillis = now,
                    errorMessage = snapshot.partialErrorMessage,
                    dataSource = when {
                        hasAuthoritativeChannels -> {
                            LightLiveDataSource.LIVE
                        }

                        displayChannels.isNotEmpty() -> {
                            LightLiveDataSource.CACHE
                        }

                        else -> {
                            LightLiveDataSource.EMPTY
                        }
                    }
                ).also { state ->
                    nextState = state
                }
            }

            nextState?.let { state ->
                LightDeviceSnapshotCache.save(
                    deviceId = deviceId,
                    liveState = state
                )

                LightManualRuntimeStore.syncFromLiveState(
                    deviceId = deviceId,
                    liveState = state
                )
            }
        }.onFailure { error ->
            val message = error.message
                ?: "Live device data could not be read"

            stateFlow.update { state ->
                cachedFallbackState(
                    deviceId = deviceId,
                    currentState = state,
                    message = message
                )
            }
        }
    }

    private fun cachedFallbackState(
        deviceId: Long,
        currentState: LightDeviceLiveState,
        message: String
    ): LightDeviceLiveState {
        val cachedState = LightDeviceSnapshotCache.read(
            deviceId = deviceId
        )

        if (cachedState != null) {
            return cachedState.copy(
                isRefreshing = false,
                errorMessage = message
            )
        }

        if (currentState.hasDisplayChannels) {
            return currentState.copy(
                isRefreshing = false,
                isLiveDataFresh = false,
                errorMessage = message,
                dataSource = LightLiveDataSource.CACHE
            )
        }

        return currentState.copy(
            isRefreshing = false,
            channels = emptyList(),
            thermalProtection = LightThermalProtectionState(),
            cooling = LightCoolingState(),
            liveDataUpdatedMillis = 0L,
            isLiveDataFresh = false,
            errorMessage = message,
            dataSource = LightLiveDataSource.EMPTY
        )
    }

    private fun stateFor(
        deviceId: Long
    ): MutableStateFlow<LightDeviceLiveState> {
        return synchronized(lock) {
            states.getOrPut(deviceId) {
                MutableStateFlow(
                    LightDeviceSnapshotCache.read(
                        deviceId = deviceId
                    ) ?: LightDeviceLiveState.initial(
                        deviceId = deviceId
                    )
                )
            }
        }
    }
}