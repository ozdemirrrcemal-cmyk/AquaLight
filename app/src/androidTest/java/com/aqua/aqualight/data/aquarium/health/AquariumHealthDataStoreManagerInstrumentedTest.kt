package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity
import com.aqua.aqualight.application.aquarium.AquariumLivestockTaxonomy
import com.aqua.aqualight.application.aquarium.health.AquariumWaterReading
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestInput
import com.aqua.aqualight.application.aquarium.health.HealthWaterParameter
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservationInput
import com.aqua.aqualight.application.aquarium.health.LivestockHealthSymptomCatalog
import com.aqua.aqualight.application.aquarium.health.ObservationIntensity
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.TankDraft
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
    fun healthRecordsAreOwnerScopedAndOrphanLivestockObservationsAreRepaired() =
        runBlocking {
            val ownerUid = "health-store-${UUID.randomUUID()}"
            val otherOwnerUid = "health-other-${UUID.randomUUID()}"
            val tankStore = AquariumTankDataStoreManager(context)
            val healthStore = AquariumHealthDataStoreManager.create(context)

            var tankId = 0L
            try {
                UserDataScope.withOwnerUid(ownerUid) {
                    tankId = tankStore.addTankFromDraft(validTankDraft())
                    tankStore.addLivestockToTank(
                        tankId = tankId,
                        livestock = validLivestock()
                    )

                    val waterTestId = healthStore.addWaterTest(
                        ownerUid = ownerUid,
                        input = waterInput(tankId),
                        nowMillis = CREATED_MILLIS
                    )
                    val observationId = healthStore.addObservation(
                        ownerUid = ownerUid,
                        input = observationInput(tankId),
                        nowMillis = CREATED_MILLIS
                    )

                    assertTrue(waterTestId > 0L)
                    assertTrue(observationId > 0L)
                    assertEquals(
                        1,
                        healthStore.waterTestsForOwnerFlow(
                            ownerUid,
                            tankId
                        ).first().size
                    )
                    assertEquals(
                        1,
                        healthStore.observationsForOwnerFlow(
                            ownerUid,
                            tankId
                        ).first().size
                    )

                    healthStore.updateWaterTest(
                        ownerUid = ownerUid,
                        testId = waterTestId,
                        input = waterInput(
                            tankId = tankId,
                            ph = 7.4
                        ),
                        nowMillis = UPDATED_MILLIS
                    )
                    assertEquals(
                        7.4,
                        healthStore.waterTestsForOwnerFlow(
                            ownerUid,
                            tankId
                        ).first().single().readings.single().value,
                        0.0
                    )

                    assertTrue(
                        healthStore.waterTestsForOwnerFlow(
                            otherOwnerUid,
                            tankId
                        ).first().isEmpty()
                    )

                    tankStore.removeLivestockFromTank(
                        tankId = tankId,
                        livestockId = LIVESTOCK_ID
                    )
                    assertEquals(
                        1,
                        healthStore.repairOrphanedRecords(ownerUid)
                    )
                    assertTrue(
                        healthStore.observationsForOwnerFlow(
                            ownerUid,
                            tankId
                        ).first().isEmpty()
                    )

                    healthStore.deleteWaterTest(
                        ownerUid = ownerUid,
                        testId = waterTestId
                    )
                    assertTrue(
                        healthStore.waterTestsForOwnerFlow(
                            ownerUid,
                            tankId
                        ).first().isEmpty()
                    )
                }

                UserDataScope.withOwnerUid(otherOwnerUid) {
                    val crossOwnerWrite = runCatching {
                        healthStore.addWaterTest(
                            ownerUid = ownerUid,
                            input = waterInput(tankId),
                            nowMillis = CREATED_MILLIS
                        )
                    }
                    assertTrue(
                        crossOwnerWrite.exceptionOrNull() is StoreInvariantViolation
                    )
                }
            } finally {
                UserDataScope.withOwnerUid(ownerUid) {
                    healthStore.clearAllRecords(ownerUid)
                    tankStore.clearAllTanks(ownerUid)
                }
                UserDataScope.withOwnerUid(otherOwnerUid) {
                    healthStore.clearAllRecords(otherOwnerUid)
                    tankStore.clearAllTanks(otherOwnerUid)
                }
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

    private fun observationInput(
        tankId: Long
    ) = LivestockHealthObservationInput(
        tankId = tankId,
        livestockId = LIVESTOCK_ID,
        categoryKey = AquariumLivestockTaxonomy.FISH,
        symptomKey = LivestockHealthSymptomCatalog.FISH_SURFACE_GASPING,
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
        plants = emptyList(),
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

    private companion object {
        const val LIVESTOCK_ID = 880_001L
        const val SETUP_EPOCH_DAY = 20_454L
        const val MEASURED_MILLIS = 1_767_225_600_000L
        const val CREATED_MILLIS = 1_767_229_200_000L
        const val UPDATED_MILLIS = 1_767_232_800_000L
    }
}
