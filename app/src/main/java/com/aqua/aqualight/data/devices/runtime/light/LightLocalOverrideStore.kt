package com.aqua.aqualight.data.devices.runtime.light

import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.light.LightMode
import com.aqua.aqualight.data.devices.light.math.LightPowerMath
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Process-local fallback state for legacy light firmware that accepts manual
 * output commands but does not report MANUAL / SCENE mode back in /get.
 *
 * New firmware remains the source of truth: when the runtime reports a concrete
 * MANUAL / SCENE / MOONLIGHT mode, the dashboard and manual screen use that
 * controller state. This store is only applied to legacy/unknown runtime
 * snapshots so older controllers can still show the override that the app just
 * sent without leaking legacy checks into UI code.
 */
object LightLocalOverrideStore {

    private val overrides = ConcurrentHashMap<Long, LightLocalOverrideState>()

    fun recordManual(
        deviceId: Long,
        channels: LightChannelValues,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (deviceId <= 0L) return

        overrides[deviceId] = LightLocalOverrideState(
            type = LightLocalOverrideType.MANUAL,
            channels = channels.normalized(),
            sceneName = null,
            sceneSource = null,
            updatedAtMillis = nowMillis
        )
    }

    fun recordScene(
        deviceId: Long,
        sceneName: String,
        sceneSource: String?,
        channels: LightChannelValues,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (deviceId <= 0L) return

        overrides[deviceId] = LightLocalOverrideState(
            type = LightLocalOverrideType.SCENE,
            channels = channels.normalized(),
            sceneName = sceneName.trim().takeIf { it.isNotEmpty() },
            sceneSource = sceneSource?.trim()?.takeIf { it.isNotEmpty() },
            updatedAtMillis = nowMillis
        )
    }

    fun clear(
        deviceId: Long
    ) {
        if (deviceId <= 0L) return
        overrides.remove(deviceId)
    }

    fun current(
        deviceId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): LightLocalOverrideState? {
        if (deviceId <= 0L) return null

        val state = overrides[deviceId] ?: return null
        if (state.isExpired(nowMillis)) {
            overrides.remove(deviceId, state)
            return null
        }

        return state
    }

    fun applyToSnapshot(
        deviceId: Long,
        snapshot: LightRuntimeSnapshot,
        nowMillis: Long = System.currentTimeMillis()
    ): LightRuntimeSnapshot {
        val state = current(
            deviceId = deviceId,
            nowMillis = nowMillis
        ) ?: return snapshot

        return when (snapshot.mode) {
            LightMode.SCENE -> snapshot.withSceneLabelFrom(state)
            LightMode.MANUAL,
            LightMode.MOONLIGHT -> snapshot
            LightMode.AUTO,
            LightMode.IDLE,
            LightMode.UNKNOWN -> {
                if (snapshot.source != LightRuntimeSource.LEGACY && snapshot.mode != LightMode.UNKNOWN) {
                    return snapshot
                }

                snapshot.withAppliedLocalOverride(state)
            }
        }
    }

    private fun LightRuntimeSnapshot.withSceneLabelFrom(
        state: LightLocalOverrideState
    ): LightRuntimeSnapshot {
        if (state.type != LightLocalOverrideType.SCENE) return this

        return copy(
            activeSceneName = activeSceneName ?: state.sceneName,
            activeSceneSource = activeSceneSource ?: state.sceneSource,
            localOverride = state
        )
    }

    private fun LightRuntimeSnapshot.withAppliedLocalOverride(
        state: LightLocalOverrideState
    ): LightRuntimeSnapshot {
        val deviceChannels = channels.normalized()
        val channels = if (state.channels.matches(deviceChannels)) {
            deviceChannels
        } else {
            state.channels.normalized()
        }
        val currentWatt = powerCalibration?.currentWatt(
            redPercent = channels.red,
            greenPercent = channels.green,
            bluePercent = channels.blue,
            whitePercent = channels.white
        ) ?: currentWatt
        val maxWatt = powerCalibration?.maxWatt ?: maxWatt
        val powerLoadPercent = powerCalibration?.powerLoadPercent(
            redPercent = channels.red,
            greenPercent = channels.green,
            bluePercent = channels.blue,
            whitePercent = channels.white
        ) ?: LightPowerMath.powerLoadPercent(
            currentWatt = currentWatt,
            maxWatt = maxWatt
        ) ?: powerLoadPercent

        return copy(
            mode = when (state.type) {
                LightLocalOverrideType.MANUAL -> LightMode.MANUAL
                LightLocalOverrideType.SCENE -> LightMode.SCENE
            },
            isPowerOn = !channels.isOff,
            outputPercent = channels.maxPercent,
            channels = channels,
            currentWatt = currentWatt,
            maxWatt = maxWatt,
            powerLoadPercent = powerLoadPercent,
            activeSceneName = state.sceneName.takeIf { state.type == LightLocalOverrideType.SCENE },
            activeSceneSource = state.sceneSource.takeIf { state.type == LightLocalOverrideType.SCENE },
            localOverride = state
        )
    }

    private fun LightChannelValues.matches(
        other: LightChannelValues
    ): Boolean {
        val left = normalized()
        val right = other.normalized()

        return abs(left.red - right.red) <= CHANNEL_MATCH_TOLERANCE_PERCENT &&
            abs(left.green - right.green) <= CHANNEL_MATCH_TOLERANCE_PERCENT &&
            abs(left.blue - right.blue) <= CHANNEL_MATCH_TOLERANCE_PERCENT &&
            abs(left.white - right.white) <= CHANNEL_MATCH_TOLERANCE_PERCENT
    }

    private fun LightLocalOverrideState.isExpired(
        nowMillis: Long
    ): Boolean {
        return nowMillis - updatedAtMillis > LOCAL_OVERRIDE_TTL_MILLIS
    }

    private const val CHANNEL_MATCH_TOLERANCE_PERCENT = 2
    private const val LOCAL_OVERRIDE_TTL_MILLIS = 24L * 60L * 60L * 1000L
}

data class LightLocalOverrideState(
    val type: LightLocalOverrideType,
    val channels: LightChannelValues,
    val sceneName: String?,
    val sceneSource: String?,
    val updatedAtMillis: Long
)

enum class LightLocalOverrideType {
    MANUAL,
    SCENE
}
