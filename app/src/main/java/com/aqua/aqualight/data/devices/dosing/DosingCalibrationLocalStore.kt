package com.aqua.aqualight.data.devices.dosing

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dosingCalibrationDataStore by preferencesDataStore(
    name = "dosing_calibration_local"
)

enum class DosingCalibrationTimeSource {
    PHONE_TIME,
    ESP_TIME
}

data class DosingChannelCalibrationUi(
    val deviceId: Long,
    val channelIndex: Int,
    val liquidName: String,
    val lastCalibratedAtMillis: Long,
    val phoneSavedAtMillis: Long,
    val timeSource: DosingCalibrationTimeSource,
    val espRawTimeText: String,
    val measuredAmountMl: Float?
)

class DosingCalibrationLocalStore(
    context: Context
) {

    private val appContext =
        context.applicationContext

    fun observeCalibration(
        deviceId: Long,
        channelIndex: Int
    ): Flow<DosingChannelCalibrationUi?> {
        val keys =
            keysOf(
                deviceId = deviceId,
                channelIndex = channelIndex
            )

        return appContext.dosingCalibrationDataStore.data.map { preferences ->
            val lastCalibratedAtMillis =
                preferences[keys.lastCalibratedAtMillis]

            if (
                lastCalibratedAtMillis == null ||
                lastCalibratedAtMillis <= 0L
            ) {
                null
            } else {
                val timeSource =
                    runCatching {
                        DosingCalibrationTimeSource.valueOf(
                            preferences[keys.timeSource]
                                ?: DosingCalibrationTimeSource.PHONE_TIME.name
                        )
                    }.getOrDefault(
                        DosingCalibrationTimeSource.PHONE_TIME
                    )

                DosingChannelCalibrationUi(
                    deviceId = deviceId,
                    channelIndex = channelIndex,
                    liquidName = preferences[keys.liquidName].orEmpty(),
                    lastCalibratedAtMillis = lastCalibratedAtMillis,
                    phoneSavedAtMillis = preferences[keys.phoneSavedAtMillis]
                        ?: lastCalibratedAtMillis,
                    timeSource = timeSource,
                    espRawTimeText = preferences[keys.espRawTimeText].orEmpty(),
                    measuredAmountMl = preferences[keys.measuredAmountMl]
                )
            }
        }
    }

    suspend fun saveCalibration(
        deviceId: Long,
        channelIndex: Int,
        liquidName: String,
        lastCalibratedAtMillis: Long,
        phoneSavedAtMillis: Long,
        timeSource: DosingCalibrationTimeSource,
        espRawTimeText: String,
        measuredAmountMl: Float?
    ) {
        val keys =
            keysOf(
                deviceId = deviceId,
                channelIndex = channelIndex
            )

        appContext.dosingCalibrationDataStore.edit { preferences ->
            preferences[keys.liquidName] =
                liquidName

            preferences[keys.lastCalibratedAtMillis] =
                lastCalibratedAtMillis

            preferences[keys.phoneSavedAtMillis] =
                phoneSavedAtMillis

            preferences[keys.timeSource] =
                timeSource.name

            preferences[keys.espRawTimeText] =
                espRawTimeText

            if (measuredAmountMl != null) {
                preferences[keys.measuredAmountMl] =
                    measuredAmountMl
            } else {
                preferences.remove(
                    keys.measuredAmountMl
                )
            }
        }
    }

    private fun keysOf(
        deviceId: Long,
        channelIndex: Int
    ): CalibrationPreferenceKeys {
        val prefix =
            "device_${deviceId}_channel_${channelIndex}_"

        return CalibrationPreferenceKeys(
            liquidName = stringPreferencesKey("${prefix}liquid_name"),
            lastCalibratedAtMillis = longPreferencesKey("${prefix}last_calibrated_at_millis"),
            phoneSavedAtMillis = longPreferencesKey("${prefix}phone_saved_at_millis"),
            timeSource = stringPreferencesKey("${prefix}time_source"),
            espRawTimeText = stringPreferencesKey("${prefix}esp_raw_time_text"),
            measuredAmountMl = floatPreferencesKey("${prefix}measured_amount_ml")
        )
    }

    private data class CalibrationPreferenceKeys(
        val liquidName: Preferences.Key<String>,
        val lastCalibratedAtMillis: Preferences.Key<Long>,
        val phoneSavedAtMillis: Preferences.Key<Long>,
        val timeSource: Preferences.Key<String>,
        val espRawTimeText: Preferences.Key<String>,
        val measuredAmountMl: Preferences.Key<Float>
    )
}