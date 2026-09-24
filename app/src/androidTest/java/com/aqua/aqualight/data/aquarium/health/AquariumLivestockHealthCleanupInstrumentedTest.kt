package com.aqua.aqualight.data.aquarium.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity
import com.aqua.aqualight.application.aquarium.AquariumLivestockTaxonomy
import com.aqua.aqualight.application.aquarium.health.LivestockHealthObservationInput
import com.aqua.aqualight.application.aquarium.health.LivestockHealthSymptomCatalog
import com.aqua.aqualight.application.aquarium.health.ObservationIntensity
import com.aqua.aqualight.data.aquarium.DefaultAquariumTankContentsOperations
import com.aqua.aqualight.data.aquarium.health.integrity.TankHealthIntegrityJournal
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
class AquariumLivestockHealthCleanupInstrumentedTest {

    private val context =
        ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun healthCleanupFailureRestoresRemovedLivestockAndKeepsObservation() =
        runBlocking {
            val ownerUid =
                "livestock-health-cleanup-" + UUID.randomUUID()
            val tankStore =
                AquariumTankDataStoreManager(context)
            val healthStore =
                AquariumHealthDataStoreManager.create(context)
            val operations =
                DefaultAquariumTankContentsOperations(
                    context = context,
                    tankStore = tankStore,
                    healthStore = healthStore
                )

            TankHealthIntegrityJournal.initialize(context)

            try {
                UserDataScope.withOwnerUid(ownerUid) {
                    TankHealthIntegrityJournal.clearOwner(ownerUid)

                    val tankId = tankStore.addTankFromDraft(
                        validTankDraft()
                    )
                    tankStore.addLivestockToTank(
                        tankId = tankId,
                        livestock = validLivestock()
                    )
                    healthStore.livestockObservations.add(
                        ownerUid = ownerUid,
                        input = observationInput(tankId),
                        nowMillis = CREATED_MILLIS
                    )

                    TankHealthIntegrityJournal.begin(
                        ownerUid = ownerUid,
                        tankIds = listOf(tankId)
                    )

                    val failure = runCatching {
                        operations.removeLivestock(
                            tankId = tankId,
                            livestockId = LIVESTOCK_ID
                        )
                    }.exceptionOrNull()

                    assertTrue(
                        failure is StoreInvariantViolation
                    )
                    val tank = tankStore
                        .tanksSnapshotForOwner(ownerUid)
                        .single()
                    assertEquals(
                        LIVESTOCK_ID,
                        tank.livestock.single().id
                    )
                    assertEquals(
                        1,
                        healthStore.livestockObservations
                            .observe(ownerUid, tankId)
                            .first()
                            .size
                    )

                    TankHealthIntegrityJournal.abort(
                        ownerUid,
                        tankId
                    )
                }
            } finally {
                UserDataScope.withOwnerUid(ownerUid) {
                    TankHealthIntegrityJournal.clearOwner(ownerUid)
                    healthStore.integrity
                        .clearAllRecords(ownerUid)
                    tankStore.clearAllTanks(ownerUid)
                }
            }
        }

    private fun observationInput(
        tankId: Long
    ) = LivestockHealthObservationInput(
        tankId = tankId,
        livestockId = LIVESTOCK_ID,
        categoryKey = AquariumLivestockTaxonomy.FISH,
        symptomKey =
            LivestockHealthSymptomCatalog.FISH_SURFACE_GASPING,
        intensity = ObservationIntensity.MODERATE,
        observedAtMillis = OBSERVED_MILLIS
    )

    private fun validLivestock() =
        SavedAquariumLivestock(
            id = LIVESTOCK_ID,
            catalogEntryId =
                AquariumLivestockIdentity.custom(LIVESTOCK_ID),
            name = "Cleanup Fish",
            category = AquariumLivestockTaxonomy.FISH,
            quantity = 2,
            addedDateEpochDay = SETUP_EPOCH_DAY,
            note = ""
        )

    private fun validTankDraft() = TankDraft(
        name = "Cleanup Tank",
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
        const val LIVESTOCK_ID = 880_101L
        const val SETUP_EPOCH_DAY = 20_454L
        const val OBSERVED_MILLIS =
            1_767_225_600_000L
        const val CREATED_MILLIS =
            1_767_229_200_000L
    }
}
