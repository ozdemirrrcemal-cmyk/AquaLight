package com.aqua.aqualight.data.store

import androidx.datastore.core.CorruptionException
import com.aqua.aqualight.data.aquarium.store.AquariumTanksSerializer
import com.aqua.aqualight.data.aquarium.store.StoredTank
import com.aqua.aqualight.data.aquarium.store.TankStoreRules
import com.aqua.aqualight.data.care.CareTaskStoreRules
import com.aqua.aqualight.data.care.CareTasksCommercialSerializer
import com.aqua.aqualight.data.care.StoredCareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.user.UserPreferencesSerializer
import com.aqua.aqualight.data.user.UserPreferencesStoreRules
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommercialStoreSerializerTest {

    @Test
    fun allCommercialSerializersRoundTripVersionedStores() = runBlocking {
        val tankStore = TankStoreRules.defaultStore().toBuilder()
            .addTanks(validTank())
            .build()
        val careStore = CareTaskStoreRules.defaultStore().toBuilder()
            .addTasks(validCareTask())
            .build()
        val preferences = UserPreferencesStoreRules.defaultPreferences()
            .toBuilder()
            .setUid("owner-a")
            .setIsLoggedIn(true)
            .build()

        assertEquals(
            tankStore,
            roundTrip(AquariumTanksSerializer, tankStore)
        )
        assertEquals(
            careStore,
            roundTrip(CareTasksCommercialSerializer, careStore)
        )
        assertEquals(
            preferences,
            roundTrip(UserPreferencesSerializer, preferences)
        )
    }

    @Test
    fun unsupportedSchemaVersionFailsClosed() {
        val unversionedTankBytes = TankStoreRules.defaultStore()
            .toBuilder()
            .setSchemaVersion(0)
            .addTanks(validTank())
            .build()
            .toByteArray()

        assertThrows(CorruptionException::class.java) {
            runBlocking {
                AquariumTanksSerializer.readFrom(
                    ByteArrayInputStream(unversionedTankBytes)
                )
            }
        }
    }

    @Test
    fun invalidPersistedEnumFailsClosed() {
        val invalidCareBytes = CareTaskStoreRules.defaultStore()
            .toBuilder()
            .addTasks(
                validCareTask().toBuilder()
                    .setType("INVALID_TYPE")
                    .build()
            )
            .build()
            .toByteArray()

        assertThrows(CorruptionException::class.java) {
            runBlocking {
                CareTasksCommercialSerializer.readFrom(
                    ByteArrayInputStream(invalidCareBytes)
                )
            }
        }
    }

    private suspend fun <T> roundTrip(
        serializer: androidx.datastore.core.Serializer<T>,
        value: T
    ): T {
        val output = ByteArrayOutputStream()
        serializer.writeTo(value, output)
        return serializer.readFrom(
            ByteArrayInputStream(output.toByteArray())
        )
    }

    private fun validTank(): StoredTank = StoredTank.newBuilder()
        .setId(10L)
        .setOwnerUid("owner-a")
        .setName("Display Tank")
        .setDescription("")
        .setSetupDateMillis(1_767_225_600_000L)
        .setWidthCm(60)
        .setLengthCm(40)
        .setHeightCm(40)
        .setSizeUnit("cm")
        .setVolumeUnit("L")
        .setTankType("Planted")
        .setTankStyle("Nature Aquarium")
        .setCreatedAtMillis(1_767_225_600_000L)
        .build()

    private fun validCareTask(): StoredCareTask = StoredCareTask.newBuilder()
        .setId(20L)
        .setOwnerUid("owner-a")
        .setTankId(10L)
        .setTitle("Inspect filter")
        .setDescription("")
        .setType(CareTaskType.FILTER_MAINTENANCE.name)
        .setSource(CareTaskSource.MANUAL.name)
        .setStatus(CareTaskStatus.PENDING.name)
        .setDueAtMillis(1_767_312_000_000L)
        .setCompletedAtMillis(0L)
        .setRepeatEnabled(false)
        .setRepeatIntervalDays(1)
        .setReminderEnabled(false)
        .setMissedReminderEnabled(false)
        .setMissedReminderDays(1)
        .setWaterChangePercent(0)
        .setGeneratedRuleKey("")
        .setCreatedAtMillis(1_767_225_600_000L)
        .setUpdatedAtMillis(1_767_225_600_000L)
        .build()
}
