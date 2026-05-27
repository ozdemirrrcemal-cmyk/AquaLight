package com.aqua.aqualight.data.devices.dosing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.updateData
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.dosing.proto.DosingChannelSettingsPreferences
import com.aqua.aqualight.data.devices.dosing.proto.DosingChannelSettingsRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dosingChannelSettingsDataStore:
    DataStore<DosingChannelSettingsPreferences> by dataStore(
        fileName = "dosing_channel_settings_preferences.pb",
        serializer = DosingChannelSettingsPreferencesSerializer
    )

data class DosingChannelSettingsUi(
    val deviceId: Long,
    val channelIndex: Int,
    val reservoirTrackingEnabled: Boolean,
    val containerVolumeMl: Float?,
    val missedDoseCompensationEnabled: Boolean,
    val updatedAtMillis: Long
)

class DosingChannelSettingsDataStoreManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    fun observeChannelSettings(
        deviceId: Long,
        channelIndex: Int
    ): Flow<DosingChannelSettingsUi> {
        val safeChannelIndex =
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )

        return appContext.dosingChannelSettingsDataStore.data.map { preferences ->
            preferences.recordsList
                .firstOrNull { record ->
                    record.deviceId == deviceId &&
                        record.channelIndex == safeChannelIndex
                }
                ?.toUi()
                ?: DosingChannelSettingsUi(
                    deviceId = deviceId,
                    channelIndex = safeChannelIndex,
                    reservoirTrackingEnabled = true,
                    containerVolumeMl = null,
                    missedDoseCompensationEnabled = true,
                    updatedAtMillis = 0L
                )
        }
    }

    suspend fun saveLocalChannelSettings(
        deviceId: Long,
        channelIndex: Int,
        reservoirTrackingEnabled: Boolean,
        containerVolumeMl: Float?,
        missedDoseCompensationEnabled: Boolean
    ) {
        updateChannelRecord(
            deviceId = deviceId,
            channelIndex = channelIndex
        ) {
            setReservoirTrackingEnabled(
                reservoirTrackingEnabled
            )

            if (containerVolumeMl == null) {
                setHasContainerVolumeMl(
                    false
                )

                setContainerVolumeMl(
                    0f
                )
            } else {
                setHasContainerVolumeMl(
                    true
                )

                setContainerVolumeMl(
                    containerVolumeMl.coerceAtLeast(
                        minimumValue = 0f
                    )
                )
            }

            setHasMissedDoseCompensationEnabled(
                true
            )

            setMissedDoseCompensationEnabled(
                missedDoseCompensationEnabled
            )
        }
    }

    suspend fun saveReservoirTrackingEnabled(
        deviceId: Long,
        channelIndex: Int,
        enabled: Boolean
    ) {
        updateChannelRecord(
            deviceId = deviceId,
            channelIndex = channelIndex
        ) {
            setReservoirTrackingEnabled(
                enabled
            )
        }
    }

    suspend fun saveMissedDoseCompensationEnabled(
        deviceId: Long,
        channelIndex: Int,
        enabled: Boolean
    ) {
        updateChannelRecord(
            deviceId = deviceId,
            channelIndex = channelIndex
        ) {
            setHasMissedDoseCompensationEnabled(
                true
            )

            setMissedDoseCompensationEnabled(
                enabled
            )
        }
    }

    suspend fun saveContainerVolumeMl(
        deviceId: Long,
        channelIndex: Int,
        containerVolumeMl: Float
    ) {
        val safeContainerVolumeMl =
            containerVolumeMl.coerceAtLeast(
                minimumValue = 0f
            )

        updateChannelRecord(
            deviceId = deviceId,
            channelIndex = channelIndex
        ) {
            setReservoirTrackingEnabled(
                true
            )

            setHasContainerVolumeMl(
                true
            )

            setContainerVolumeMl(
                safeContainerVolumeMl
            )
        }
    }

    suspend fun clearContainerVolume(
        deviceId: Long,
        channelIndex: Int
    ) {
        updateChannelRecord(
            deviceId = deviceId,
            channelIndex = channelIndex
        ) {
            setHasContainerVolumeMl(
                false
            )

            setContainerVolumeMl(
                0f
            )
        }
    }

    private suspend fun updateChannelRecord(
        deviceId: Long,
        channelIndex: Int,
        block: DosingChannelSettingsRecord.Builder.() -> Unit
    ) {
        val safeChannelIndex =
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )

        appContext.dosingChannelSettingsDataStore.updateData { current ->
            val existingRecord =
                current.recordsList.firstOrNull { record ->
                    record.deviceId == deviceId &&
                        record.channelIndex == safeChannelIndex
                }

            val newRecordBuilder =
                existingRecord?.toBuilder()
                    ?: DosingChannelSettingsRecord.newBuilder()
                        .setDeviceId(
                            deviceId
                        )
                        .setChannelIndex(
                            safeChannelIndex
                        )
                        .setReservoirTrackingEnabled(
                            true
                        )

            val newRecord =
                newRecordBuilder
                    .apply(
                        block
                    )
                    .setUpdatedAtMillis(
                        System.currentTimeMillis()
                    )
                    .build()

            val updatedRecords =
                current.recordsList
                    .filterNot { record ->
                        record.deviceId == deviceId &&
                            record.channelIndex == safeChannelIndex
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

    private fun DosingChannelSettingsRecord.toUi(): DosingChannelSettingsUi {
        return DosingChannelSettingsUi(
            deviceId = deviceId,
            channelIndex = channelIndex,
            reservoirTrackingEnabled = reservoirTrackingEnabled,
            containerVolumeMl = if (hasContainerVolumeMl) {
                containerVolumeMl
            } else {
                null
            },
            missedDoseCompensationEnabled = if (hasMissedDoseCompensationEnabled) {
                missedDoseCompensationEnabled
            } else {
                true
            },
            updatedAtMillis = updatedAtMillis
        )
    }
}