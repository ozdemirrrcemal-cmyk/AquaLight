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
            val fixture = createFixture()
            TankHealthIntegrityJournal.initialize(context)

            try {
                verifyCompensatedRemoval(fixture)
            } finally {
                clearFixture(fixture)
            }
        }

    private fun createFixture(): CleanupFixture {
        val ownerUid =
            "livestock-health-cleanup-" + UUID.randomUUID()
        val tankStore =
            AquariumTankDataStoreManager(context)
        val healthStore =
            AquariumHealthDataStoreManager.create(context)
        return CleanupFixture(
            ownerUid = ownerUid,
            tankStore = tankStore,
            healthStore = healthStore,
            operations =
                DefaultAquariumTankContentsOperations(
                    context = context,
                    tankStore = tankStore,
                    healthStore = healthStore
                )
        )
    }

    private suspend fun verifyCompensatedRemoval(
        fixture: CleanupFixture
    ) {
        UserDataScope.withOwnerUid(fixture.ownerUid) {
            TankHealthIntegrityJournal.clearOwner(
                fixture.ownerUid
            )
            val tankId = fixture.tankStore.addTankFromDraft(
                validTankDraft()
            )
            fixture.tankStore.addLivestockToTank(
                tankId = tankId,
                livestock = validLivestock()
            )
            fixture.healthStore.livestockObservations.add(
                ownerUid = fixture.ownerUid,
                input = observationInput(tankId),
                nowMillis = CREATED_MILLIS
            )

            TankHealthIntegrityJournal.begin(
                ownerUid = fixture.ownerUid,
                tankIds = listOf(tankId)
            )

            val failure = runCatching {
                fixture.operations.removeLivestock(
                    tankId = tankId,
                    livestockId = LIVESTOCK_ID
                )
            }.exceptionOrNull()

            assertTrue(failure is StoreInvariantViolation)
            val tank = fixture.tankStore
                .tanksSnapshotForOwner(fixture.ownerUid)
                .single()
            assertEquals(
                LIVESTOCK_ID,
                tank.livestock.single().id
            )
            assertEquals(
                1,
                fixture.healthStore.livestockObservations
                    .observe(fixture.ownerUid, tankId)
                    .first()
                    .size
            )

            TankHealthIntegrityJournal.abort(
                fixture.ownerUid,
                tankId
            )
        }
    }

    private suspend fun clearFixture(
        fixture: CleanupFixture
    ) {
        UserDataScope.withOwnerUid(fixture.ownerUid) {
            TankHealthIntegrityJournal.clearOwner(
                fixture.ownerUid
            )
            fixture.healthStore.integrity
                .clearAllRecords(fixture.ownerUid)
            fixture.tankStore.clearAllTanks(
                fixture.ownerUid
            )
        }
    }

    private data class CleanupFixture(
        val ownerUid: String,
        val tankStore: AquariumTankDataStoreManager,
        val healthStore: AquariumHealthDataStoreManager,
        val operations: DefaultAquariumTankContentsOperations
    )

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
