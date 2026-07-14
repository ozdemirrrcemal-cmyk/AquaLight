package com.aqua.aqualight.data.recovery

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists one-shot notices for local stores that had to be reset after corruption.
 *
 * The recovery marker intentionally contains no user or device data. It survives a
 * process restart so the next foreground activity can explain the recovery instead
 * of silently presenting an empty store.
 */
object LocalDataRecoveryTracker {

    enum class Area {
        AQUARIUM_TANKS,
        CARE_TASKS,
        KNOWN_DEVICES,
        TANK_DEVICE_ASSIGNMENTS
    }

    private val lock = Any()
    private val inMemoryAreas = linkedSetOf<Area>()

    @Volatile
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            if (preferences != null) return

            preferences = context.applicationContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
        }
    }

    fun markRecovered(area: Area) {
        synchronized(lock) {
            inMemoryAreas += area

            val currentPreferences = preferences ?: return
            val stored = currentPreferences
                .getStringSet(KEY_RECOVERED_AREAS, emptySet())
                .orEmpty()
                .toMutableSet()

            stored += area.name
            currentPreferences.edit()
                .putStringSet(KEY_RECOVERED_AREAS, stored)
                .commit()
        }
    }

    fun consumeRecoveredAreas(): Set<Area> {
        return synchronized(lock) {
            val persistedAreas = preferences
                ?.getStringSet(KEY_RECOVERED_AREAS, emptySet())
                .orEmpty()
                .mapNotNull { raw ->
                    runCatching { Area.valueOf(raw) }.getOrNull()
                }

            val recovered = (inMemoryAreas + persistedAreas).toSet()

            if (recovered.isNotEmpty()) {
                val cleared = preferences
                    ?.edit()
                    ?.remove(KEY_RECOVERED_AREAS)
                    ?.commit()

                if (cleared != false) {
                    inMemoryAreas.clear()
                }
            }

            recovered
        }
    }

    private const val PREFERENCES_NAME = "local_data_recovery"
    private const val KEY_RECOVERED_AREAS = "recovered_areas"
}
