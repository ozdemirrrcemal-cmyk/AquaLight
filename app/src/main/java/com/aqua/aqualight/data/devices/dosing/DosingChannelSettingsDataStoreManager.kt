package com.aqua.aqualight.data.devices.dosing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.dosing.proto.DosingChannelSettingsPreferences
import com.aqua.aqualight.data.devices.dosing.proto.DosingChannelSettingsRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val remainingVolumeMl: Float?,
    val lastReservoirSyncAtMillis: Long,
    val lastDailyDoseMl: Float?,
    val lastManualDoseMl: Float?,
    val lastManualDoseAtMillis: Long,
    val reservoirEmpty: Boolean,
    val pausedByReservoirEmpty: Boolean,
    val missedDoseCompensationEnabled: Boolean,
    val lastRefilledAtMillis: Long,
    val updatedAtMillis: Long
) {
    val hasReservoirCapacity: Boolean
        get() =
            containerVolumeMl != null &&
                containerVolumeMl > 0f

    val hasLastManualDose: Boolean
        get() =
            lastManualDoseMl != null &&
                lastManualDoseMl > 0f &&
                lastManualDoseAtMillis > 0L

    val shouldShowReservoirProgress: Boolean
        get() =
            reservoirTrackingEnabled &&
                hasReservoirCapacity &&
                remainingVolumeMl != null &&
                !reservoirEmpty

    val shouldShowRefillAction: Boolean
        get() =
            reservoirTrackingEnabled &&
                hasReservoirCapacity &&
                reservoirEmpty

    val reservoirProgressPercent: Int
        get() {
            val capacity =
                containerVolumeMl ?: return 0

            val remaining =
                remainingVolumeMl ?: return 0

            if (capacity <= 0f) {
                return 0
            }

            return ((remaining / capacity) * 100f)
                .roundToInt()
                .coerceIn(
                    minimumValue = 0,
                    maximumValue = 100
                )
        }
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
                size = 4
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
        ) { existingRecord, nowMillis ->
            val safeContainerVolumeMl =
                containerVolumeMl
                    ?.coerceAtLeast(
                        minimumValue = 0f
                    )
                    ?.takeIf { value ->
                        value > 0f
                    }

            val previousContainerVolumeMl =
                existingRecord
                    ?.takeIf { record ->
                        record.hasContainerVolumeMl
                    }
                    ?.containerVolumeMl

            val trackingWasDisabled =
                existingRecord?.reservoirTrackingEnabled != true

            val containerChanged =
                !areFloatValuesSame(
                    currentValue = safeContainerVolumeMl,
                    savedValue = previousContainerVolumeMl
                )

            setReservoirTrackingEnabled(
                reservoirTrackingEnabled
            )

            if (safeContainerVolumeMl == null) {
                clearReservoirVolumeFields()
            } else {
                setHasContainerVolumeMl(
                    true
                )

                setContainerVolumeMl(
                    safeContainerVolumeMl
                )

                val shouldStartFullReservoir =
                    reservoirTrackingEnabled &&
                        (
                            existingRecord == null ||
                                trackingWasDisabled ||
                                containerChanged ||
                                !existingRecord.hasRemainingVolumeMl
                            )

                if (shouldStartFullReservoir) {
                    startFullReservoir(
                        containerVolumeMl = safeContainerVolumeMl,
                        nowMillis = nowMillis
                    )
                } else {
                    val existingRemaining =
                        existingRecord
                            ?.takeIf { record ->
                                record.hasRemainingVolumeMl
                            }
                            ?.remainingVolumeMl

                    if (existingRemaining != null) {
                        setHasRemainingVolumeMl(
                            true
                        )

                        setRemainingVolumeMl(
                            existingRemaining.coerceIn(
                                minimumValue = 0f,
                                maximumValue = safeContainerVolumeMl
                            )
                        )
                    }
                }
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
        ) { existingRecord, nowMillis ->
            setReservoirTrackingEnabled(
                enabled
            )

            if (
                enabled &&
                existingRecord != null &&
                existingRecord.hasContainerVolumeMl &&
                existingRecord.containerVolumeMl > 0f &&
                !existingRecord.hasRemainingVolumeMl
            ) {
                startFullReservoir(
                    containerVolumeMl = existingRecord.containerVolumeMl,
                    nowMillis = nowMillis
                )
            }
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
        ) { _, _ ->
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
        ) { _, nowMillis ->
            if (safeContainerVolumeMl <= 0f) {
                clearReservoirVolumeFields()
                return@updateChannelRecord
            }

            setReservoirTrackingEnabled(
                true
            )

            setHasContainerVolumeMl(
                true
            )

            setContainerVolumeMl(
                safeContainerVolumeMl
            )

            startFullReservoir(
                containerVolumeMl = safeContainerVolumeMl,
                nowMillis = nowMillis
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
        ) { _, _ ->
            clearReservoirVolumeFields()
        }
    }

    suspend fun refillReservoir(
        deviceId: Long,
        channelIndex: Int
    ) {
        updateChannelRecord(
            deviceId = deviceId,
            channelIndex = channelIndex
        ) { existingRecord, nowMillis ->
            if (
                existingRecord == null ||
                !existingRecord.hasContainerVolumeMl ||
                existingRecord.containerVolumeMl <= 0f
            ) {
                return@updateChannelRecord
            }

            startFullReservoir(
                containerVolumeMl = existingRecord.containerVolumeMl,
                nowMillis = nowMillis
            )
        }
    }

    suspend fun syncReservoirUsage(
        deviceId: Long,
        channelIndex: Int
    ) {
        updateChannelRecord(
            deviceId = deviceId,
            channelIndex = channelIndex
        ) { existingRecord, nowMillis ->
            applyElapsedDailyUsageIfPossible(
                existingRecord = existingRecord,
                nowMillis = nowMillis
            )
        }
    }

    suspend fun markReservoirEmptyAndPaused(
        deviceId: Long,
        channelIndex: Int
    ) {
        updateChannelRecord(
            deviceId = deviceId,
            channelIndex = channelIndex
        ) { existingRecord, nowMillis ->
            if (
                existingRecord == null ||
                !existingRecord.hasContainerVolumeMl ||
                existingRecord.containerVolumeMl <= 0f
            ) {
                return@updateChannelRecord
            }

            setHasRemainingVolumeMl(
                true
            )

            setRemainingVolumeMl(
                0f
            )

            setLastReservoirSyncAtMillis(
                nowMillis
            )

            setReservoirEmpty(
                true
            )

            setPausedByReservoirEmpty(
                true
            )
        }
    }

    suspend fun saveLastDailyDoseMl(
        deviceId: Long,
        channelIndex: Int,
        dailyDoseMl: Float
    ) {
        val safeDailyDoseMl =
            dailyDoseMl.coerceAtLeast(
                minimumValue = 0f
            )

        updateChannelRecord(
            deviceId = deviceId,
            channelIndex = channelIndex
        ) { existingRecord, nowMillis ->
            val previousDailyDoseMl =
                existingRecord
                    ?.takeIf { record ->
                        record.hasLastDailyDoseMl
                    }
                    ?.lastDailyDoseMl

            val dailyDoseChanged =
                !areFloatValuesSame(
                    currentValue = safeDailyDoseMl,
                    savedValue = previousDailyDoseMl
                )

            if (dailyDoseChanged) {
                applyElapsedDailyUsageIfPossible(
                    existingRecord = existingRecord,
                    nowMillis = nowMillis
                )
            }

            setHasLastDailyDoseMl(
                true
            )

            setLastDailyDoseMl(
                safeDailyDoseMl
            )

            if (
                lastReservoirSyncAtMillis <= 0L ||
                dailyDoseChanged
            ) {
                setLastReservoirSyncAtMillis(
                    nowMillis
                )
            }
        }
    }

    suspend fun saveLastManualDoseMl(
        deviceId: Long,
        channelIndex: Int,
        manualDoseMl: Float
    ) {
        val safeManualDoseMl =
            manualDoseMl.coerceAtLeast(
                minimumValue = 0f
            )

        updateChannelRecord(
            deviceId = deviceId,
            channelIndex = channelIndex
        ) { existingRecord, nowMillis ->
            setHasLastManualDoseMl(
                true
            )

            setLastManualDoseMl(
                safeManualDoseMl
            )

            setLastManualDoseAtMillis(
                nowMillis
            )

            consumeReservoirDoseIfPossible(
                existingRecord = existingRecord,
                doseMl = safeManualDoseMl,
                nowMillis = nowMillis
            )
        }
    }

    private suspend fun updateChannelRecord(
        deviceId: Long,
        channelIndex: Int,
        block: DosingChannelSettingsRecord.Builder.(
            existingRecord: DosingChannelSettingsRecord?,
            nowMillis: Long
        ) -> Unit
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

            val nowMillis =
                System.currentTimeMillis()

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
                    .apply {
                        block(
                            existingRecord,
                            nowMillis
                        )
                    }
                    .setUpdatedAtMillis(
                        nowMillis
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

    private fun DosingChannelSettingsRecord.Builder.startFullReservoir(
        containerVolumeMl: Float,
        nowMillis: Long
    ) {
        val safeContainerVolumeMl =
            containerVolumeMl.coerceAtLeast(
                minimumValue = 0f
            )

        setHasRemainingVolumeMl(
            true
        )

        setRemainingVolumeMl(
            safeContainerVolumeMl
        )

        setLastReservoirSyncAtMillis(
            nowMillis
        )

        setReservoirEmpty(
            false
        )

        setPausedByReservoirEmpty(
            false
        )

        setLastRefilledAtMillis(
            nowMillis
        )
    }

    private fun DosingChannelSettingsRecord.Builder.clearReservoirVolumeFields() {
        setHasContainerVolumeMl(
            false
        )

        setContainerVolumeMl(
            0f
        )

        setHasRemainingVolumeMl(
            false
        )

        setRemainingVolumeMl(
            0f
        )

        setLastReservoirSyncAtMillis(
            0L
        )

        setReservoirEmpty(
            false
        )

        setPausedByReservoirEmpty(
            false
        )

        setLastRefilledAtMillis(
            0L
        )
    }

    private fun DosingChannelSettingsRecord.Builder.applyElapsedDailyUsageIfPossible(
        existingRecord: DosingChannelSettingsRecord?,
        nowMillis: Long
    ) {
        if (
            existingRecord == null ||
            !existingRecord.reservoirTrackingEnabled ||
            !existingRecord.hasContainerVolumeMl ||
            existingRecord.containerVolumeMl <= 0f ||
            !existingRecord.hasRemainingVolumeMl
        ) {
            return
        }

        val effectiveRemainingMl =
            calculateEffectiveRemainingVolumeMl(
                record = existingRecord,
                nowMillis = nowMillis
            ) ?: return

        val safeRemainingMl =
            effectiveRemainingMl.coerceIn(
                minimumValue = 0f,
                maximumValue = existingRecord.containerVolumeMl
            )

        val isEmpty =
            safeRemainingMl <= 0f

        setHasRemainingVolumeMl(
            true
        )

        setRemainingVolumeMl(
            safeRemainingMl
        )

        setLastReservoirSyncAtMillis(
            nowMillis
        )

        setReservoirEmpty(
            isEmpty
        )

        setPausedByReservoirEmpty(
            isEmpty
        )
    }

    private fun DosingChannelSettingsRecord.Builder.consumeReservoirDoseIfPossible(
        existingRecord: DosingChannelSettingsRecord?,
        doseMl: Float,
        nowMillis: Long
    ) {
        if (
            doseMl <= 0f ||
            existingRecord == null ||
            !existingRecord.reservoirTrackingEnabled ||
            !existingRecord.hasContainerVolumeMl ||
            existingRecord.containerVolumeMl <= 0f ||
            !existingRecord.hasRemainingVolumeMl
        ) {
            return
        }

        val effectiveRemainingMl =
            calculateEffectiveRemainingVolumeMl(
                record = existingRecord,
                nowMillis = nowMillis
            ) ?: return

        val newRemainingMl =
            (effectiveRemainingMl - doseMl).coerceIn(
                minimumValue = 0f,
                maximumValue = existingRecord.containerVolumeMl
            )

        val isEmpty =
            newRemainingMl <= 0f

        setHasRemainingVolumeMl(
            true
        )

        setRemainingVolumeMl(
            newRemainingMl
        )

        setLastReservoirSyncAtMillis(
            nowMillis
        )

        setReservoirEmpty(
            isEmpty
        )

        setPausedByReservoirEmpty(
            isEmpty
        )
    }

    private fun DosingChannelSettingsRecord.toUi(): DosingChannelSettingsUi {
        val nowMillis =
            System.currentTimeMillis()

        val effectiveRemainingVolumeMl =
            calculateEffectiveRemainingVolumeMl(
                record = this,
                nowMillis = nowMillis
            )

        val hasCapacity =
            hasContainerVolumeMl &&
                containerVolumeMl > 0f

        val effectiveReservoirEmpty =
            reservoirEmpty ||
                (
                    reservoirTrackingEnabled &&
                        hasCapacity &&
                        effectiveRemainingVolumeMl != null &&
                        effectiveRemainingVolumeMl <= 0f
                    )

        return DosingChannelSettingsUi(
            deviceId = deviceId,
            channelIndex = channelIndex,
            reservoirTrackingEnabled = reservoirTrackingEnabled,
            containerVolumeMl = if (hasContainerVolumeMl) {
                containerVolumeMl
            } else {
                null
            },
            remainingVolumeMl = effectiveRemainingVolumeMl,
            lastReservoirSyncAtMillis = lastReservoirSyncAtMillis,
            lastDailyDoseMl = if (hasLastDailyDoseMl) {
                lastDailyDoseMl
            } else {
                null
            },
            lastManualDoseMl = if (hasLastManualDoseMl) {
                lastManualDoseMl
            } else {
                null
            },
            lastManualDoseAtMillis = lastManualDoseAtMillis,
            reservoirEmpty = effectiveReservoirEmpty,
            pausedByReservoirEmpty = pausedByReservoirEmpty || effectiveReservoirEmpty,
            missedDoseCompensationEnabled = if (hasMissedDoseCompensationEnabled) {
                missedDoseCompensationEnabled
            } else {
                true
            },
            lastRefilledAtMillis = lastRefilledAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    private fun createDefaultUi(
        deviceId: Long,
        channelIndex: Int
    ): DosingChannelSettingsUi {
        return DosingChannelSettingsUi(
            deviceId = deviceId,
            channelIndex = channelIndex,
            reservoirTrackingEnabled = true,
            containerVolumeMl = null,
            remainingVolumeMl = null,
            lastReservoirSyncAtMillis = 0L,
            lastDailyDoseMl = null,
            lastManualDoseMl = null,
            lastManualDoseAtMillis = 0L,
            reservoirEmpty = false,
            pausedByReservoirEmpty = false,
            missedDoseCompensationEnabled = true,
            lastRefilledAtMillis = 0L,
            updatedAtMillis = 0L
        )
    }

    private fun calculateEffectiveRemainingVolumeMl(
        record: DosingChannelSettingsRecord,
        nowMillis: Long
    ): Float? {
        if (!record.hasRemainingVolumeMl) {
            return null
        }

        if (
            !record.reservoirTrackingEnabled ||
            !record.hasContainerVolumeMl ||
            record.containerVolumeMl <= 0f
        ) {
            return record.remainingVolumeMl
        }

        if (record.reservoirEmpty) {
            return 0f
        }

        val remainingMl =
            record.remainingVolumeMl

        val dailyDoseMl =
            if (record.hasLastDailyDoseMl) {
                record.lastDailyDoseMl
            } else {
                0f
            }

        if (
            dailyDoseMl <= 0f ||
            record.lastReservoirSyncAtMillis <= 0L ||
            nowMillis <= record.lastReservoirSyncAtMillis
        ) {
            return remainingMl.coerceIn(
                minimumValue = 0f,
                maximumValue = record.containerVolumeMl
            )
        }

        val elapsedDays =
            ((nowMillis - record.lastReservoirSyncAtMillis) / DAY_IN_MILLIS)
                .coerceAtLeast(
                    minimumValue = 0L
                )

        if (elapsedDays <= 0L) {
            return remainingMl.coerceIn(
                minimumValue = 0f,
                maximumValue = record.containerVolumeMl
            )
        }

        val consumedMl =
            dailyDoseMl * elapsedDays.toFloat()

        return (remainingMl - consumedMl).coerceIn(
            minimumValue = 0f,
            maximumValue = record.containerVolumeMl
        )
    }

    private fun areFloatValuesSame(
        currentValue: Float?,
        savedValue: Float?
    ): Boolean {
        if (
            currentValue == null ||
            savedValue == null
        ) {
            return currentValue == savedValue
        }

        return abs(
            currentValue - savedValue
        ) < 0.001f
    }

    private companion object {
        private const val DAY_IN_MILLIS =
            24L * 60L * 60L * 1000L
    }
}