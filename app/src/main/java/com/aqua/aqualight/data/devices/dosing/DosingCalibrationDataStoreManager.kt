package com.aqua.aqualight.data.devices.dosing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.dosing.proto.DosingCalibrationPreferences
import com.aqua.aqualight.data.devices.dosing.proto.DosingCalibrationTimeSourceProto
import com.aqua.aqualight.data.devices.dosing.proto.DosingChannelCalibrationRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dosingCalibrationDataStore:
    DataStore<DosingCalibrationPreferences> by dataStore(
        fileName = "dosing_calibration_preferences.pb",
        serializer = DosingCalibrationPreferencesSerializer
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

class DosingCalibrationDataStoreManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    fun observeCalibration(
        deviceId: Long,
        channelIndex: Int
    ): Flow<DosingChannelCalibrationUi?> {
        return appContext.dosingCalibrationDataStore.data.map { preferences ->
            preferences.recordsList
                .firstOrNull { record ->
                    record.deviceId == deviceId &&
                        record.channelIndex == channelIndex
                }
                ?.toUi()
        }
    }

    suspend fun saveCalibration(
    deviceId: Long,
    channelIndex: Int,
    lastCalibratedAtMillis: Long
) {
    appContext.dosingCalibrationDataStore.updateData { current ->
        val newRecord =
            DosingChannelCalibrationRecord.newBuilder()
                .setDeviceId(
                    deviceId
                )
                .setChannelIndex(
                    channelIndex.coerceIn(
                        minimumValue = 0,
                        maximumValue = 3
                    )
                )
                .setLastCalibratedAtMillis(
                    lastCalibratedAtMillis
                )
                .build()

        val updatedRecords =
            current.recordsList
                .filterNot { record ->
                    record.deviceId == deviceId &&
                        record.channelIndex == channelIndex
                }
                .plus(
                    newRecord
                )

        current.toBuilder()
            .clearRecords()
            .addAllRecords(
                updatedRecords
            )
            .build()
    }
}

    suspend fun clearCalibration(
        deviceId: Long,
        channelIndex: Int
    ) {
        appContext.dosingCalibrationDataStore.updateData { current ->
            val updatedRecords =
                current.recordsList.filterNot { record ->
                    record.deviceId == deviceId &&
                        record.channelIndex == channelIndex
                }

            current.toBuilder()
                .clearRecords()
                .addAllRecords(
                    updatedRecords
                )
                .build()
        }
    }

    private fun DosingChannelCalibrationRecord.toUi():
        DosingChannelCalibrationUi {
        return DosingChannelCalibrationUi(
            deviceId = deviceId,
            channelIndex = channelIndex,
            liquidName = liquidName,
            lastCalibratedAtMillis = lastCalibratedAtMillis,
            phoneSavedAtMillis = phoneSavedAtMillis,
            timeSource = timeSource.toDomain(),
            espRawTimeText = espRawTimeText,
            measuredAmountMl = if (hasMeasuredAmountMl) {
                measuredAmountMl
            } else {
                null
            }
        )
    }

    private fun DosingCalibrationTimeSource.toProto():
        DosingCalibrationTimeSourceProto {
        return when (this) {
            DosingCalibrationTimeSource.PHONE_TIME ->
                DosingCalibrationTimeSourceProto.DCTS_PHONE_TIME

            DosingCalibrationTimeSource.ESP_TIME ->
                DosingCalibrationTimeSourceProto.DCTS_ESP_TIME
        }
    }

    private fun DosingCalibrationTimeSourceProto.toDomain():
        DosingCalibrationTimeSource {
        return when (this) {
            DosingCalibrationTimeSourceProto.DCTS_ESP_TIME ->
                DosingCalibrationTimeSource.ESP_TIME

            DosingCalibrationTimeSourceProto.DCTS_PHONE_TIME,
            DosingCalibrationTimeSourceProto.DCTS_UNSPECIFIED,
            DosingCalibrationTimeSourceProto.UNRECOGNIZED ->
                DosingCalibrationTimeSource.PHONE_TIME
        }
    }
}