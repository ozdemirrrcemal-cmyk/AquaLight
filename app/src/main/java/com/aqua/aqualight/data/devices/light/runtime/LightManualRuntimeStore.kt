package com.aqua.aqualight.data.devices.light.runtime

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

object LightManualRuntimeStore {

    private const val PREFS_NAME = "light_manual_runtime"
    private const val KEY_MODE = "mode"
    private const val KEY_SCENE = "scene"
    private const val KEY_RED = "red"
    private const val KEY_GREEN = "green"
    private const val KEY_BLUE = "blue"
    private const val KEY_WHITE = "white"
    private const val KEY_POWER = "power"
    private const val KEY_UPDATED_AT = "updated_at"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun configure(context: Context) {
        if (prefs != null) {
            return
        }

        prefs = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    private val runtimeStates =
        MutableStateFlow<Map<Long, LightManualRuntimeState>>(emptyMap())

    fun observe(
        deviceId: Long
    ): Flow<LightManualRuntimeState> {
        return runtimeStates
            .map { states ->
                states[deviceId] ?: readPersistedState(deviceId) ?: LightManualRuntimeState.auto(deviceId)
            }
            .distinctUntilChanged()
    }

    fun current(
        deviceId: Long
    ): LightManualRuntimeState {
        return runtimeStates.value[deviceId]
            ?: readPersistedState(deviceId)
            ?: LightManualRuntimeState.auto(deviceId)
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
        persistState(state)

        runtimeStates.update { states ->
            states + (state.deviceId to state)
        }
    }

    private fun persistState(state: LightManualRuntimeState) {
        val preferences = prefs ?: return
        val prefix = keyPrefix(state.deviceId)

        preferences.edit()
            .putString(prefix + KEY_MODE, state.mode.name)
            .putString(prefix + KEY_SCENE, state.activeSceneName)
            .putInt(prefix + KEY_RED, state.red)
            .putInt(prefix + KEY_GREEN, state.green)
            .putInt(prefix + KEY_BLUE, state.blue)
            .putInt(prefix + KEY_WHITE, state.white)
            .putBoolean(prefix + KEY_POWER, state.isPowerOn)
            .putLong(prefix + KEY_UPDATED_AT, state.updatedAt)
            .apply()
    }

    private fun readPersistedState(deviceId: Long): LightManualRuntimeState? {
        val preferences = prefs ?: return null
        val prefix = keyPrefix(deviceId)

        val modeName = preferences.getString(prefix + KEY_MODE, null)
            ?: return null

        val mode = runCatching {
            LightControlMode.valueOf(modeName)
        }.getOrDefault(LightControlMode.AUTO)

        return LightManualRuntimeState(
            deviceId = deviceId,
            mode = mode,
            activeSceneName = preferences.getString(prefix + KEY_SCENE, null),
            red = preferences.getInt(prefix + KEY_RED, 0).coerceIn(0, 100),
            green = preferences.getInt(prefix + KEY_GREEN, 0).coerceIn(0, 100),
            blue = preferences.getInt(prefix + KEY_BLUE, 0).coerceIn(0, 100),
            white = preferences.getInt(prefix + KEY_WHITE, 0).coerceIn(0, 100),
            isPowerOn = preferences.getBoolean(prefix + KEY_POWER, false),
            updatedAt = preferences.getLong(prefix + KEY_UPDATED_AT, 0L)
        )
    }

    private fun keyPrefix(deviceId: Long): String {
        return "manual_" + deviceId + "_"
    }
}