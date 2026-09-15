package com.aqua.aqualight.data.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.aquarium.store.AquariumTanksSerializer
import com.aqua.aqualight.data.aquarium.store.AquariumTanksStore
import com.aqua.aqualight.data.aquarium.store.StoredTank
import com.aqua.aqualight.data.aquarium.store.TankStoreRules
import com.aqua.aqualight.data.care.CareTaskStoreRules
import com.aqua.aqualight.data.care.CareTasksCommercialSerializer
import com.aqua.aqualight.data.care.CareTasksStore
import com.aqua.aqualight.data.care.StoredCareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.devices.light.library.DeviceLightLibrarySerializer
import com.aqua.aqualight.data.devices.light.library.DeviceLightLibraryStoreData
import com.aqua.aqualight.data.devices.light.library.DeviceLightLibraryStoreRules
import com.aqua.aqualight.data.devices.light.library.StoredDeviceLightChannelValue
import com.aqua.aqualight.data.devices.light.library.StoredDeviceLightLibraryEntry
import com.aqua.aqualight.data.devices.light.library.StoredDeviceLightLibraryKind
import com.aqua.aqualight.data.devices.light.library.StoredDeviceLightManualScene
import com.aqua.aqualight.data.user.UserPreferences
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
        val lightLibrary = DeviceLightLibraryStoreRules.defaultStore().toBuilder()
            .addEntries(validLightLibraryEntry())
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
        assertEquals(
            lightLibrary,
            roundTrip(DeviceLightLibrarySerializer, lightLibrary)
        )
    }

    @Test
    fun everyEmptyUnversionedStoreFailsClosedOnReadAndWrite() {
        val tankStore = AquariumTanksStore.getDefaultInstance()
        val careStore = CareTasksStore.getDefaultInstance()
        val preferences = UserPreferences.getDefaultInstance()
        val lightLibrary = DeviceLightLibraryStoreData.getDefaultInstance()

        assertCorruption(AquariumTanksSerializer, tankStore.toByteArray())
        assertCorruption(CareTasksCommercialSerializer, careStore.toByteArray())
        assertCorruption(UserPreferencesSerializer, preferences.toByteArray())
        assertCorruption(DeviceLightLibrarySerializer, lightLibrary.toByteArray())

        assertWriteRejected(AquariumTanksSerializer, tankStore)
        assertWriteRejected(CareTasksCommercialSerializer, careStore)
        assertWriteRejected(UserPreferencesSerializer, preferences)
        assertWriteRejected(DeviceLightLibrarySerializer, lightLibrary)
    }

    @Test
    fun unversionedStoresWithDataFailClosed() {
        val tankBytes = TankStoreRules.defaultStore()
            .toBuilder()
            .setSchemaVersion(0)
            .addTanks(validTank())
            .build()
            .toByteArray()
        val careBytes = CareTaskStoreRules.defaultStore()
            .toBuilder()
            .setSchemaVersion(0)
            .addTasks(validCareTask())
            .build()
            .toByteArray()
        val preferenceBytes = UserPreferencesStoreRules.defaultPreferences()
            .toBuilder()
            .setSchemaVersion(0)
            .setUid("owner-a")
            .setIsLoggedIn(true)
            .build()
            .toByteArray()
        val lightLibraryBytes = DeviceLightLibraryStoreRules.defaultStore()
            .toBuilder()
            .setSchemaVersion(0)
            .addEntries(validLightLibraryEntry())
            .build()
            .toByteArray()

        assertCorruption(AquariumTanksSerializer, tankBytes)
        assertCorruption(CareTasksCommercialSerializer, careBytes)
        assertCorruption(UserPreferencesSerializer, preferenceBytes)
        assertCorruption(DeviceLightLibrarySerializer, lightLibraryBytes)
    }

    @Test
    fun futureStoreVersionsFailClosedUntilExplicitlySupported() {
        val tankBytes = TankStoreRules.defaultStore()
            .toBuilder()
            .setSchemaVersion(CommercialStoreSchema.AQUARIUM_TANKS_VERSION + 1)
            .addTanks(validTank())
            .build()
            .toByteArray()
        val careBytes = CareTaskStoreRules.defaultStore()
            .toBuilder()
            .setSchemaVersion(CommercialStoreSchema.CARE_TASKS_VERSION + 1)
            .addTasks(validCareTask())
            .build()
            .toByteArray()
        val preferenceBytes = UserPreferencesStoreRules.defaultPreferences()
            .toBuilder()
            .setSchemaVersion(CommercialStoreSchema.USER_PREFERENCES_VERSION + 1)
            .build()
            .toByteArray()
        val lightLibraryBytes = DeviceLightLibraryStoreRules.defaultStore()
            .toBuilder()
            .setSchemaVersion(CommercialStoreSchema.LIGHT_LIBRARY_VERSION + 1)
            .build()
            .toByteArray()

        assertCorruption(AquariumTanksSerializer, tankBytes)
        assertCorruption(CareTasksCommercialSerializer, careBytes)
        assertCorruption(UserPreferencesSerializer, preferenceBytes)
        assertCorruption(DeviceLightLibrarySerializer, lightLibraryBytes)
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

        assertCorruption(CareTasksCommercialSerializer, invalidCareBytes)
    }

    @Test
    fun duplicatePersistedIdsFailClosed() {
        val duplicateTankBytes = TankStoreRules.defaultStore()
            .toBuilder()
            .addTanks(validTank())
            .addTanks(validTank().toBuilder().setName("Duplicate").build())
            .build()
            .toByteArray()
        val duplicateTaskBytes = CareTaskStoreRules.defaultStore()
            .toBuilder()
            .addTasks(validCareTask())
            .addTasks(validCareTask().toBuilder().setTitle("Duplicate").build())
            .build()
            .toByteArray()

        assertCorruption(AquariumTanksSerializer, duplicateTankBytes)
        assertCorruption(CareTasksCommercialSerializer, duplicateTaskBytes)
    }

    private fun <T> assertCorruption(
        serializer: Serializer<T>,
        bytes: ByteArray
    ) {
        assertThrows(CorruptionException::class.java) {
            runBlocking {
                serializer.readFrom(ByteArrayInputStream(bytes))
            }
        }
    }

    private fun <T> assertWriteRejected(
        serializer: Serializer<T>,
        value: T
    ) {
        assertThrows(StoreInvariantViolation::class.java) {
            runBlocking {
                serializer.writeTo(value, ByteArrayOutputStream())
            }
        }
    }

    private suspend fun <T> roundTrip(
        serializer: Serializer<T>,
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
        .setSetupDateEpochDay(20_454L)
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

    private fun validLightLibraryEntry(): StoredDeviceLightLibraryEntry =
        StoredDeviceLightLibraryEntry.newBuilder()
            .setId("manual-1")
            .setOwnerUid("owner-a")
            .setDisplayName("Evening View")
            .setNormalizedName("evening view")
            .setKind(
                StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_MANUAL
            )
            .setProductKey("LIGHT_RGB_PRO_SLIM")
            .addAllChannelKeys(listOf("redPercent", "greenPercent", "bluePercent"))
            .setCreatedAtMillis(1_767_225_600_000L)
            .setUpdatedAtMillis(1_767_225_600_000L)
            .setManual(
                StoredDeviceLightManualScene.newBuilder()
                    .addAllChannels(
                        listOf(
                            storedChannel("redPercent", 65),
                            storedChannel("greenPercent", 45),
                            storedChannel("bluePercent", 75)
                        )
                    )
            )
            .build()

    private fun storedChannel(
        key: String,
        percent: Int
    ): StoredDeviceLightChannelValue = StoredDeviceLightChannelValue.newBuilder()
        .setChannelKey(key)
        .setPercent(percent)
        .build()
}
