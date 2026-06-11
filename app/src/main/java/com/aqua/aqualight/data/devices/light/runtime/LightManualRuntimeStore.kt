package com.aqua.aqualight.data.devices.light.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory command runtime only.
 *
 * This store intentionally does not use SharedPreferences/DataStore. Manual mode is a
 * live command/session state, not a source of truth for actual device output. If the
 * process restarts or the device goes offline, the UI must rebuild from confirmed
 * device telemetry instead of resurrecting old cached manual values.
 */
object LightManualRuntimeStore {

    private val runtimeStates =
        MutableStateFlow<Map<Long, LightManualRuntimeState>>(emptyMap())

    fun observe(
        deviceId: Long
    ): Flow<LightManualRuntimeState> {
        return runtimeStates
            .map { states ->
                states[deviceId] ?: LightManualRuntimeState.auto(deviceId)
            }
            .distinctUntilChanged()
    }

    fun current(
        deviceId: Long
    ): LightManualRuntimeState {
        return runtimeStates.value[deviceId]
            ?: LightManualRuntimeState.auto(deviceId)
    }

    fun clear(
        deviceId: Long
    ) {
        if (deviceId <= 0L) {
            return
        }

        runtimeStates.update { states ->
            states - deviceId
        }
    }

    fun applyManualScene(
        deviceId: Long,
        sceneName: String,
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ) {
        val safeRed = red.coerceIn(0, 100)
        val safeGreen = green.coerceIn(0, 100)
        val safeBlue = blue.coerceIn(0, 100)
        val safeWhite = white.coerceIn(0, 100)

        val isPowerOn =
            LightOutputMath.outputPercent(
                red = safeRed,
                green = safeGreen,
                blue = safeBlue,
                white = safeWhite
            ) > 0

        updateState(
            LightManualRuntimeState(
                deviceId = deviceId,
                mode = LightControlMode.MANUAL_SCENE,
                activeSceneName = sceneName,
                red = safeRed,
                green = safeGreen,
                blue = safeBlue,
                white = safeWhite,
                isPowerOn = isPowerOn,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun updateManualOutput(
        deviceId: Long,
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ) {
        val current = current(deviceId)

        val safeRed = red.coerceIn(0, 100)
        val safeGreen = green.coerceIn(0, 100)
        val safeBlue = blue.coerceIn(0, 100)
        val safeWhite = white.coerceIn(0, 100)

        val isPowerOn =
            LightOutputMath.outputPercent(
                red = safeRed,
                green = safeGreen,
                blue = safeBlue,
                white = safeWhite
            ) > 0

        updateState(
            current.copy(
                mode = LightControlMode.MANUAL,
                activeSceneName = null,
                red = safeRed,
                green = safeGreen,
                blue = safeBlue,
                white = safeWhite,
                isPowerOn = isPowerOn,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun setPowerOn(
        deviceId: Long,
        isPowerOn: Boolean
    ) {
        val current = current(deviceId)

        updateState(
            current.copy(
                isPowerOn = isPowerOn,
                mode = if (isPowerOn) {
                    current.mode.takeIf { mode ->
                        mode == LightControlMode.MANUAL ||
                            mode == LightControlMode.MANUAL_SCENE
                    } ?: LightControlMode.MANUAL
                } else {
                    LightControlMode.MANUAL
                },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun resumeAuto(
        deviceId: Long
    ) {
        updateState(
            LightManualRuntimeState.auto(deviceId).copy(
                mode = LightControlMode.AUTO,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun updateState(
        state: LightManualRuntimeState
    ) {
        runtimeStates.update { states ->
            states + (state.deviceId to state)
        }
    }
}
