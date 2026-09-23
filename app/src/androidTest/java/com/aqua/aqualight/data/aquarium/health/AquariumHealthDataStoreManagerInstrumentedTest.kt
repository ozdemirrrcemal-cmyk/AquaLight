package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity
import com.aqua.aqualight.application.aquarium.AquariumLivestockTaxonomy
import com.aqua.aqualight.application.aquarium.health.AquariumAlgaeCatalog
import com.aqua.aqualight.application.aquarium.health.AquariumWaterReading
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestInput
import com.aqua.aqualight.application.aquarium.health.HealthWaterParameter
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservationInput
import com.aqua.aqualight.application.aquarium.health.LivestockHealthSymptomCatalog
import com.aqua.aqualight.application.aquarium.health.ObservationIntensity
import com.aqua.aqualight.application.aquarium.health.PlantHealthObservationInput
import com.aqua.aqualight.application.aquarium.health.PlantHealthSymptomCatalog
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AquariumHealthDataStoreManagerInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun ownerScopedCrudAndOrphanRepairCoverAllHealthRecordTypes() = runBlocking {
        val ownerUid = "health-store-" + UUID.randomUUID()
        val otherOwnerUid = "health-other-" + UUID.randomUUID()
        val tankStore = AquariumTankDataStoreManager(context)
        val healthStore = AquariumHealthDataStoreManager.create(context)

        try {
            UserDataScope.withOwnerUid(ownerUid) {
                val tankId = createPopulatedTank(tankStore)
                val ids = addHealthRecords(healthStore, ownerUid, tankId)

                verifyOwnerRecords(healthStore, ownerUid, tankId)
                updateWaterTest(healthStore, ownerUid, tankId, ids.waterTestId)
                verifyOtherOwnerCannotRead(healthStore, otherOwnerUid, tankId)

                tankStore.removeLivestockFromTank(tankId, LIVESTOCK_ID)
                tankStore.updateTankPlants(tankId, emptyList())

                assertEquals(
                    2,
                    healthStore.integrity.repairOrphanedRecords(ownerUid)
                )
                assertTrue(
                    healthStore.livestockObservations
                        .observe(ownerUid, tankId)
                        .first()
                        .isEmpty()
                )

                val remainingPlantObservations =
                    healthStore.plantObservations
                        .observe(ownerUid, tankId)
                        .first()
                assertEquals(1, remainingPlantObservations.size)
                assertEquals(null, remainingPlantObservations.single().plantId)

                healthStore.waterTests.delete(ownerUid, ids.waterTestId)
                assertTrue(
                    healthStore.waterTests.observe(ownerUid, tankId)
                        .first()
                        .isEmpty()
                )
            }

            verifyCrossOwnerWriteFails(
                healthStore = healthStore,
                ownerUid = ownerUid,
                otherOwnerUid = otherOwnerUid
            )
        } finally {
            clearOwner(healthStore, tankStore, ownerUid)
            clearOwner(healthStore, tankStore, otherOwnerUid)
        }
    }

    private suspend fun createPopulatedTank(
        tankStore: AquariumTankDataStoreManager
    ): Long {
        val tankId = tankStore.addTankFromDraft(validTankDraft())
        tankStore.addLivestockToTank(
            tankId = tankId,
            livestock = validLivestock()
        )
        return tankId
    }

    private suspend fun addHealthRecords(
        healthStore: AquariumHealthDataStoreManager,
        ownerUid: String,
        tankId: Long
    ): CreatedHealthIds {
        val waterTestId = healthStore.waterTests.add(
            ownerUid = ownerUid,
            input = waterInput(tankId),
            nowMillis = CREATED_MILLIS
        )
        healthStore.livestockObservations.add(
            ownerUid = ownerUid,
            input = livestockObservationInput(tankId),
            nowMillis = CREATED_MILLIS
        )
        healthStore.plantObservations.add(
            ownerUid = ownerUid,
            input = specificPlantObservationInput(tankId),
            nowMillis = CREATED_MILLIS
        )
        healthStore.plantObservations.add(
            ownerUid = ownerUid,
            input = tankWideAlgaeObservationInput(tankId),
            nowMillis = CREATED_MILLIS
        )
        return CreatedHealthIds(waterTestId)
    }

    private suspend fun verifyOwnerRecords(
        healthStore: AquariumHealthDataStoreManager,
        ownerUid: String,
        tankId: Long
    ) {
        assertEquals(
            1,
            healthStore.waterTests.observe(ownerUid, tankId).first().size
        )
        assertEquals(
            1,
            healthStore.livestockObservations.observe(ownerUid, tankId).first().size
        )
        assertEquals(
            2,
            healthStore.plantObservations.observe(ownerUid, tankId).first().size
        )
    }

    private suspend fun updateWaterTest(
        healthStore: AquariumHealthDataStoreManager,
        ownerUid: String,
        tankId: Long,
        waterTestId: Long
    ) {
        healthStore.waterTests.update(
            ownerUid = ownerUid,
            testId = waterTestId,
            input = waterInput(tankId = tankId, ph = 7.4),
            nowMillis = UPDATED_MILLIS
        )
        assertEquals(
            7.4,
            healthStore.waterTests.observe(ownerUid, tankId)
                .first()
                .single()
                .readings
                .single()
                .value,
            0.0
        )
    }

    private suspend fun verifyOtherOwnerCannotRead(
        healthStore: AquariumHealthDataStoreManager,
        otherOwnerUid: String,
        tankId: Long
    ) {
        assertTrue(
            healthStore.waterTests.observe(otherOwnerUid, tankId)
                .first()
                .isEmpty()
        )
    }

    private suspend fun verifyCrossOwnerWriteFails(
        healthStore: AquariumHealthDataStoreManager,
        ownerUid: String,
        otherOwnerUid: String
    ) {
        UserDataScope.withOwnerUid(otherOwnerUid) {
            val crossOwnerWrite = runCatching {
                healthStore.waterTests.add(
                    ownerUid = ownerUid,
                    input = waterInput(TANK_ID_NOT_REQUIRED_TO_EXIST),
                    nowMillis = CREATED_MILLIS
                )
            }
            assertTrue(
                crossOwnerWrite.exceptionOrNull() is StoreInvariantViolation
            )
        }
    }

    private suspend fun clearOwner(
        healthStore: AquariumHealthDataStoreManager,
        tankStore: AquariumTankDataStoreManager,
        ownerUid: String
    ) {
        UserDataScope.withOwnerUid(ownerUid) {
            healthStore.integrity.clearAllRecords(ownerUid)
            tankStore.clearAllTanks(ownerUid)
        }
    }

    private fun waterInput(
        tankId: Long,
        ph: Double = 7.2
    ) = AquariumWaterTestInput(
        tankId = tankId,
        measuredAtMillis = MEASURED_MILLIS,
        readings = listOf(
            AquariumWaterReading(
                parameter = HealthWaterParameter.PH,
                value = ph
            )
        )
    )

    private fun livestockObservationInput(
        tankId: Long
    ) = LivestockHealthObservationInput(
        tankId = tankId,
        livestockId = LIVESTOCK_ID,
        categoryKey = AquariumLivestockTaxonomy.FISH,
        symptomKey = LivestockHealthSymptomCatalog.FISH_SURFACE_GASPING,
        intensity = ObservationIntensity.MODERATE,
        observedAtMillis = MEASURED_MILLIS
    )

    private fun specificPlantObservationInput(
        tankId: Long
    ) = PlantHealthObservationInput(
        tankId = tankId,
        plantId = PLANT_ID,
        symptomKey = PlantHealthSymptomCatalog.YELLOWING,
        algaeTypeKey = null,
        intensity = ObservationIntensity.MODERATE,
        observedAtMillis = MEASURED_MILLIS
    )

    private fun tankWideAlgaeObservationInput(
        tankId: Long
    ) = PlantHealthObservationInput(
        tankId = tankId,
        plantId = null,
        symptomKey = PlantHealthSymptomCatalog.ALGAE_PRESENCE,
        algaeTypeKey = AquariumAlgaeCatalog.BLACK_BEARD_ALGAE,
        intensity = ObservationIntensity.MODERATE,
        observedAtMillis = MEASURED_MILLIS
    )

    private fun validLivestock() = SavedAquariumLivestock(
        id = LIVESTOCK_ID,
        catalogEntryId = AquariumLivestockIdentity.custom(LIVESTOCK_ID),
        name = "Custom Fish",
        category = AquariumLivestockTaxonomy.FISH,
        quantity = 2,
        addedDateEpochDay = SETUP_EPOCH_DAY,
        note = ""
    )

    private fun validTankDraft() = TankDraft(
        name = "Health Store Tank",
        description = "",
        photoUri = null,
        plants = listOf(
            TankPlantTag(
                id = PLANT_ID,
                catalogId = "plant:test",
                plantName = "Test Plant",
                category = "Foreground"
            )
        ),
        materials = emptyList(),
        setupDateEpochDay = SETUP_EPOCH_DAY,
        widthCm = 60,
        lengthCm = 40,
        heightCm = 40,
        sizeUnit = "cm",
        volumeUnit = "L",
        tankType = "Planted",
        tankStyle = ""
    )

    private data class CreatedHealthIds(
        val waterTestId: Long
    )

    private companion object {
        const val LIVESTOCK_ID = 880_001L
        const val PLANT_ID = 990_001L
        const val TANK_ID_NOT_REQUIRED_TO_EXIST = 999_999L
        const val SETUP_EPOCH_DAY = 20_454L
        const val MEASURED_MILLIS = 1_767_225_600_000L
        const val CREATED_MILLIS = 1_767_229_200_000L
        const val UPDATED_MILLIS = 1_767_232_800_000L
    }
}
