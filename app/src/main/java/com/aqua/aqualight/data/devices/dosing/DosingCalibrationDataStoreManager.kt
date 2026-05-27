package com.aqua.aqualight.data.devices.dosing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.dosing.proto.DosingCalibrationPreferences
import com.aqua.aqualight.data.devices.dosing.proto.DosingChannelCalibrationRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dosingCalibrationDataStore:
    DataStore<DosingCalibrationPreferences> by dataStore(
        fileName = "dosing_calibration_preferences.pb",
        serializer = DosingCalibrationPreferencesSerializer
    )

data class DosingChannelCalibrationUi(
    val deviceId: Long,
    val channelIndex: Int,
    val lastCalibratedAtMillis: Long
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
            lastCalibratedAtMillis = lastCalibratedAtMillis
        )
    }
}