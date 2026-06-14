package com.aqua.aqualight.data.devices.runtime.light

import android.content.Context
import android.content.SharedPreferences
import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.light.LightMode
import com.aqua.aqualight.data.devices.light.math.LightPowerMath
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import org.json.JSONObject

/**
 * Persistent fallback state for legacy light firmware that accepts manual
 * output commands but does not report MANUAL / SCENE mode back in /get.
 *
 * New firmware remains the source of truth: when the runtime reports a concrete
 * MANUAL / SCENE / MOONLIGHT mode from a non-legacy source, the dashboard and
 * manual screen use that controller state. This store is only applied to
 * legacy/unknown runtime snapshots so older controllers can still show the
 * override that the app sent, including after process death / app restart.
 */
object LightLocalOverrideStore {

    private val overrides = ConcurrentHashMap<Long, LightLocalOverrideState>()

    @Volatile
    private var preferences: SharedPreferences? = null

    fun initialize(
        context: Context
    ) {
        if (preferences != null) return

        synchronized(this) {
            if (preferences == null) {
                preferences = context.applicationContext.getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                )
            }
        }
    }

    fun recordManual(
        deviceId: Long,
        channels: LightChannelValues,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (deviceId <= 0L) return

        val state = LightLocalOverrideState(
            type = LightLocalOverrideType.MANUAL,
            channels = channels.normalized(),
            sceneName = null,
            sceneSource = null,
            updatedAtMillis = nowMillis
        )

        overrides[deviceId] = state
        persist(
            deviceId = deviceId,
            state = state
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

        val state = LightLocalOverrideState(
            type = LightLocalOverrideType.SCENE,
            channels = channels.normalized(),
            sceneName = sceneName.trim().takeIf { it.isNotEmpty() },
            sceneSource = sceneSource?.trim()?.takeIf { it.isNotEmpty() },
            updatedAtMillis = nowMillis
        )

        overrides[deviceId] = state
        persist(
            deviceId = deviceId,
            state = state
        )
    }

    fun clear(
        deviceId: Long
    ) {
        if (deviceId <= 0L) return

        overrides.remove(deviceId)
        preferences?.edit()
            ?.remove(preferenceKey(deviceId))
            ?.apply()
    }

    fun current(
        deviceId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): LightLocalOverrideState? {
        if (deviceId <= 0L) return null

        val state = overrides[deviceId]
            ?: restore(deviceId)
            ?: return null

        if (state.isExpired(nowMillis)) {
            overrides.remove(deviceId, state)
            preferences?.edit()
                ?.remove(preferenceKey(deviceId))
                ?.apply()
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

    private fun persist(
        deviceId: Long,
        state: LightLocalOverrideState
    ) {
        val prefs = preferences ?: return

        prefs.edit()
            .putString(
                preferenceKey(deviceId),
                state.toJson().toString()
            )
            .apply()
    }

    private fun restore(
        deviceId: Long
    ): LightLocalOverrideState? {
        val raw = preferences
            ?.getString(preferenceKey(deviceId), null)
            ?: return null

        val state = runCatching {
            JSONObject(raw).toLightLocalOverrideState()
        }.getOrNull()

        if (state == null) {
            preferences?.edit()
                ?.remove(preferenceKey(deviceId))
                ?.apply()
            return null
        }

        overrides[deviceId] = state
        return state
    }

    private fun LightLocalOverrideState.toJson(): JSONObject {
        return JSONObject()
            .put(KEY_TYPE, type.name)
            .put(KEY_RED, channels.red.coerceIn(0, 100))
            .put(KEY_GREEN, channels.green.coerceIn(0, 100))
            .put(KEY_BLUE, channels.blue.coerceIn(0, 100))
            .put(KEY_WHITE, channels.white.coerceIn(0, 100))
            .put(KEY_SCENE_NAME, sceneName)
            .put(KEY_SCENE_SOURCE, sceneSource)
            .put(KEY_UPDATED_AT, updatedAtMillis)
    }

    private fun JSONObject.toLightLocalOverrideState(): LightLocalOverrideState? {
        val type = runCatching {
            LightLocalOverrideType.valueOf(
                optString(KEY_TYPE).uppercase()
            )
        }.getOrNull() ?: return null

        return LightLocalOverrideState(
            type = type,
            channels = LightChannelValues(
                red = optInt(KEY_RED, 0).coerceIn(0, 100),
                green = optInt(KEY_GREEN, 0).coerceIn(0, 100),
                blue = optInt(KEY_BLUE, 0).coerceIn(0, 100),
                white = optInt(KEY_WHITE, 0).coerceIn(0, 100)
            ).normalized(),
            sceneName = optString(KEY_SCENE_NAME).trim().takeIf { it.isNotEmpty() },
            sceneSource = optString(KEY_SCENE_SOURCE).trim().takeIf { it.isNotEmpty() },
            updatedAtMillis = optLong(KEY_UPDATED_AT, 0L)
        )
    }

    private fun preferenceKey(
        deviceId: Long
    ): String {
        return "$PREFERENCE_KEY_PREFIX$deviceId"
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
        return updatedAtMillis <= 0L ||
            nowMillis - updatedAtMillis > LOCAL_OVERRIDE_TTL_MILLIS
    }

    private const val PREFERENCES_NAME = "aql_light_local_override_state"
    private const val PREFERENCE_KEY_PREFIX = "device_"
    private const val KEY_TYPE = "type"
    private const val KEY_RED = "red"
    private const val KEY_GREEN = "green"
    private const val KEY_BLUE = "blue"
    private const val KEY_WHITE = "white"
    private const val KEY_SCENE_NAME = "sceneName"
    private const val KEY_SCENE_SOURCE = "sceneSource"
    private const val KEY_UPDATED_AT = "updatedAtMillis"
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
