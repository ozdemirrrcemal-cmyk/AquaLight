package com.aqua.aqualight.data.devices.runtime.light

import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.light.LightMode
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.light.math.LightPowerMath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Live runtime session for a single Light controller.
 *
 * All Light screens bind to this session instead of polling the controller on
 * their own. Commands update the StateFlow optimistically, then the next device
 * snapshot confirms the controller state.
 */
class LightRuntimeSession internal constructor(
    val deviceId: Long,
    private val accessor: LightRuntimeDeviceAccessor,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {

    private val _state = MutableStateFlow(
        LightRuntimeState(
            deviceId = deviceId
        )
    )
    val state: StateFlow<LightRuntimeState> =
        _state.asStateFlow()

    private val consumerMutex = Mutex()
    private val refreshMutex = Mutex()
    private val activeConsumers = mutableSetOf<String>()

    private var pollingJob: Job? = null

    fun acquire(
        consumerKey: String
    ) {
        if (deviceId <= 0L) {
            setInvalidDeviceState()
            return
        }

        scope.launch {
            var startedPolling = false
            consumerMutex.withLock {
                activeConsumers.add(consumerKey)
                if (pollingJob?.isActive != true) {
                    startPollingLocked()
                    startedPolling = true
                }
            }

            if (!startedPolling) {
                refreshAsync()
            }
        }
    }

    fun release(
        consumerKey: String
    ) {
        scope.launch {
            consumerMutex.withLock {
                activeConsumers.remove(consumerKey)
                if (activeConsumers.isEmpty()) {
                    pollingJob?.cancel()
                    pollingJob = null
                }
            }
        }
    }

    fun refreshAsync(
        readProfile: LightRuntimeReadProfile = LightRuntimeReadProfile.LIVE
    ) {
        if (deviceId <= 0L) {
            setInvalidDeviceState()
            return
        }

        scope.launch {
            refreshNow(
                readProfile = readProfile
            )
        }
    }

    suspend fun refreshNow(
        readProfile: LightRuntimeReadProfile = LightRuntimeReadProfile.LIVE
    ): ApiResult<LightRuntimeSnapshot> {
        if (deviceId <= 0L) {
            setInvalidDeviceState()
            return invalidDeviceResult()
        }

        return withContext(ioDispatcher) {
            refreshMutex.withLock {
                _state.update { state ->
                    state.copy(
                        isRefreshing = true,
                        errorMessage = null
                    )
                }

                when (val result = accessor.readSnapshotWithDevice(
                    deviceId = deviceId,
                    readProfile = readProfile
                )) {
                    is ApiResult.Success -> {
                        val now = System.currentTimeMillis()
                        _state.value = LightRuntimeState(
                            deviceId = deviceId,
                            device = result.value.device,
                            snapshot = result.value.snapshot,
                            isRefreshing = false,
                            isDeviceOnline = true,
                            errorMessage = null,
                            lastSyncedAtMillis = now
                        )
                        ApiResult.success(result.value.snapshot)
                    }

                    is ApiResult.Error -> {
                        _state.update { state ->
                            state.copy(
                                isRefreshing = false,
                                isDeviceOnline = false,
                                errorMessage = result.error.message
                            )
                        }
                        result
                    }
                }
            }
        }
    }

    suspend fun setManualOutput(
        channelValues: LightChannelValues
    ): ApiResult<Unit> {
        if (deviceId <= 0L) {
            setInvalidDeviceState()
            return invalidDeviceResult()
        }

        val normalizedChannels = channelValues.normalized()
        val previousSnapshot = _state.value.snapshot
        applyOptimisticOutput(
            mode = LightMode.MANUAL,
            channels = normalizedChannels,
            sceneName = null,
            sceneSource = null
        )

        return withContext(ioDispatcher) {
            when (val result = accessor.setManualOutput(
                deviceId = deviceId,
                channelValues = normalizedChannels
            )) {
                is ApiResult.Success -> {
                    applyOptimisticOutput(
                        mode = LightMode.MANUAL,
                        channels = normalizedChannels,
                        sceneName = null,
                        sceneSource = null
                    )
                    result
                }

                is ApiResult.Error -> {
                    restoreAfterCommandError(
                        previousSnapshot = previousSnapshot,
                        message = result.error.message
                    )
                    result
                }
            }
        }
    }

    suspend fun setSceneOutput(
        channelValues: LightChannelValues,
        sceneName: String,
        sceneSource: String?
    ): ApiResult<Unit> {
        if (deviceId <= 0L) {
            setInvalidDeviceState()
            return invalidDeviceResult()
        }

        val normalizedChannels = channelValues.normalized()
        val previousSnapshot = _state.value.snapshot
        val safeSceneName = sceneName.trim()
        val safeSceneSource = sceneSource?.trim()?.takeIf { it.isNotEmpty() }

        applyOptimisticOutput(
            mode = LightMode.SCENE,
            channels = normalizedChannels,
            sceneName = safeSceneName,
            sceneSource = safeSceneSource
        )

        return withContext(ioDispatcher) {
            when (val result = accessor.setSceneOutput(
                deviceId = deviceId,
                channelValues = normalizedChannels,
                sceneName = safeSceneName,
                sceneSource = safeSceneSource
            )) {
                is ApiResult.Success -> {
                    applyOptimisticOutput(
                        mode = LightMode.SCENE,
                        channels = normalizedChannels,
                        sceneName = safeSceneName,
                        sceneSource = safeSceneSource
                    )
                    result
                }

                is ApiResult.Error -> {
                    restoreAfterCommandError(
                        previousSnapshot = previousSnapshot,
                        message = result.error.message
                    )
                    result
                }
            }
        }
    }

    suspend fun resumeAuto(): ApiResult<Unit> {
        if (deviceId <= 0L) {
            setInvalidDeviceState()
            return invalidDeviceResult()
        }

        _state.update { state ->
            state.copy(
                isRefreshing = true,
                errorMessage = null
            )
        }

        return withContext(ioDispatcher) {
            when (val result = accessor.resumeAuto(deviceId)) {
                is ApiResult.Success -> {
                    clearLocalOverrideState()
                    refreshAsync(
                        readProfile = LightRuntimeReadProfile.STANDARD
                    )
                    result
                }

                is ApiResult.Error -> {
                    _state.update { state ->
                        state.copy(
                            isRefreshing = false,
                            errorMessage = result.error.message,
                            isDeviceOnline = state.snapshot != null
                        )
                    }
                    result
                }
            }
        }
    }

    private fun startPollingLocked() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            refreshNow(
                readProfile = LightRuntimeReadProfile.LIVE
            )

            while (isActive) {
                delay(RUNTIME_REFRESH_INTERVAL_MILLIS)
                refreshNow(
                    readProfile = LightRuntimeReadProfile.LIVE
                )
            }
        }
    }

    private fun applyOptimisticOutput(
        mode: LightMode,
        channels: LightChannelValues,
        sceneName: String?,
        sceneSource: String?
    ) {
        val normalizedChannels = channels.normalized()
        val previous = _state.value.snapshot ?: LightRuntimeSnapshot()
        val localOverride = LightLocalOverrideState(
            type = when (mode) {
                LightMode.SCENE -> LightLocalOverrideType.SCENE
                else -> LightLocalOverrideType.MANUAL
            },
            channels = normalizedChannels,
            sceneName = sceneName?.takeIf { it.isNotBlank() },
            sceneSource = sceneSource?.takeIf { it.isNotBlank() },
            updatedAtMillis = System.currentTimeMillis()
        )
        val currentWatt = previous.powerCalibration?.currentWatt(
            redPercent = normalizedChannels.red,
            greenPercent = normalizedChannels.green,
            bluePercent = normalizedChannels.blue,
            whitePercent = normalizedChannels.white
        ) ?: previous.currentWatt
        val maxWatt = previous.powerCalibration?.maxWatt ?: previous.maxWatt
        val powerLoadPercent = previous.powerCalibration?.powerLoadPercent(
            redPercent = normalizedChannels.red,
            greenPercent = normalizedChannels.green,
            bluePercent = normalizedChannels.blue,
            whitePercent = normalizedChannels.white
        ) ?: LightPowerMath.powerLoadPercent(
            currentWatt = currentWatt,
            maxWatt = maxWatt
        ) ?: previous.powerLoadPercent

        _state.update { state ->
            state.copy(
                snapshot = previous.copy(
                    mode = mode,
                    isPowerOn = !normalizedChannels.isOff,
                    outputPercent = normalizedChannels.maxPercent,
                    channels = normalizedChannels,
                    currentWatt = currentWatt,
                    maxWatt = maxWatt,
                    powerLoadPercent = powerLoadPercent,
                    activeSceneName = sceneName?.takeIf { it.isNotBlank() },
                    activeSceneSource = sceneSource?.takeIf { it.isNotBlank() },
                    localOverride = localOverride
                ),
                isDeviceOnline = true,
                isRefreshing = false,
                errorMessage = null,
                lastSyncedAtMillis = state.lastSyncedAtMillis ?: System.currentTimeMillis()
            )
        }
    }

    private fun clearLocalOverrideState() {
        _state.update { state ->
            state.copy(
                snapshot = state.snapshot?.copy(
                    mode = LightMode.AUTO,
                    activeSceneName = null,
                    activeSceneSource = null,
                    localOverride = null
                ),
                isRefreshing = false,
                isDeviceOnline = state.snapshot != null,
                errorMessage = null
            )
        }
    }

    private fun restoreAfterCommandError(
        previousSnapshot: LightRuntimeSnapshot?,
        message: String
    ) {
        _state.update { state ->
            state.copy(
                snapshot = previousSnapshot,
                isDeviceOnline = previousSnapshot != null,
                isRefreshing = false,
                errorMessage = message
            )
        }
    }

    private fun setInvalidDeviceState() {
        _state.value = LightRuntimeState(
            deviceId = deviceId,
            isRefreshing = false,
            isDeviceOnline = false,
            errorMessage = "Light device id is missing"
        )
    }

    private fun invalidDeviceResult(): ApiResult<Nothing> {
        return ApiResult.failure(
            code = ApiErrorCode.INVALID_REQUEST,
            message = "Light device id is missing"
        )
    }

    private companion object {
        const val RUNTIME_REFRESH_INTERVAL_MILLIS = 5_000L
    }
}
