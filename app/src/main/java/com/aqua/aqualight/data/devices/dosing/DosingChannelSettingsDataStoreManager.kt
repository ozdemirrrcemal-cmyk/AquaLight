package com.aqua.aqualight.data.devices.dosing

import android.content.Context
import androidx.datastore.core.DataStore
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
) {
    val hasReservoirCapacity: Boolean
        get() =
            containerVolumeMl != null &&
                containerVolumeMl > 0f
}

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
                ?: createDefaultUi(
                    deviceId = deviceId,
                    channelIndex = safeChannelIndex
                )
        }
    }

    fun observeDeviceChannelSettings(
        deviceId: Long
    ): Flow<List<DosingChannelSettingsUi>> {
        return appContext.dosingChannelSettingsDataStore.data.map { preferences ->
            List(
                size = CHANNEL_COUNT
            ) { channelIndex ->
                preferences.recordsList
                    .firstOrNull { record ->
                        record.deviceId == deviceId &&
                            record.channelIndex == channelIndex
                    }
                    ?.toUi()
                    ?: createDefaultUi(
                        deviceId = deviceId,
                        channelIndex = channelIndex
                    )
            }
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

            setContainerVolumeOrClear(
                value = containerVolumeMl
            )

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
        updateChannelRecord(
            deviceId = deviceId,
            channelIndex = channelIndex
        ) {
            setContainerVolumeOrClear(
                value = containerVolumeMl
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
            clearContainerVolumeFields()
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
                        .setHasMissedDoseCompensationEnabled(
                            true
                        )
                        .setMissedDoseCompensationEnabled(
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

    private fun DosingChannelSettingsRecord.Builder.setContainerVolumeOrClear(
        value: Float?
    ) {
        val safeValue =
            value
                ?.coerceAtLeast(
                    minimumValue = 0f
                )
                ?.takeIf { volume ->
                    volume > 0f
                }

        if (safeValue == null) {
            clearContainerVolumeFields()
            return
        }

        setHasContainerVolumeMl(
            true
        )

        setContainerVolumeMl(
            safeValue
        )
    }

    private fun DosingChannelSettingsRecord.Builder.clearContainerVolumeFields() {
        setHasContainerVolumeMl(
            false
        )

        setContainerVolumeMl(
            0f
        )
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

    private fun createDefaultUi(
        deviceId: Long,
        channelIndex: Int
    ): DosingChannelSettingsUi {
        return DosingChannelSettingsUi(
            deviceId = deviceId,
            channelIndex = channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            ),
            reservoirTrackingEnabled = true,
            containerVolumeMl = null,
            missedDoseCompensationEnabled = true,
            updatedAtMillis = 0L
        )
    }

    private companion object {
        private const val CHANNEL_COUNT =
            4
    }
}