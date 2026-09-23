package com.aqua.aqualight.data.aquarium.health.integrity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.application.aquarium.health.AquariumWaterReading
import com.aqua.aqualight.application.aquarium.health.AquariumWaterTestInput
import com.aqua.aqualight.application.aquarium.health.HealthWaterParameter
import com.aqua.aqualight.data.aquarium.health.AquariumHealthDataStoreManager
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.user.UserDataScope
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TankHealthIntegrityRecoveryInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun capturedHealthSnapshotIsRestoredWhenTankStillExists() = runBlocking {
        val ownerUid = "health-recovery-${UUID.randomUUID()}"
        val tankStore = AquariumTankDataStoreManager(context)
        val healthStore = AquariumHealthDataStoreManager.create(context)
        TankHealthIntegrityJournal.initialize(context)

        try {
            UserDataScope.withOwnerUid(ownerUid) {
                TankHealthIntegrityJournal.clearOwner(ownerUid)
                val tankId = tankStore.addTankFromDraft(validTankDraft("Restore"))
                addWaterTest(healthStore, ownerUid, tankId)

                val snapshot = healthStore.integrity.snapshotForTank(ownerUid, tankId)
                TankHealthIntegrityJournal.begin(ownerUid, listOf(tankId))
                TankHealthIntegrityJournal.captureSnapshots(
                    ownerUid = ownerUid,
                    snapshotsByTank = mapOf(tankId to snapshot)
                )
                healthStore.integrity.deleteRecordsForTank(ownerUid, tankId)

                assertTrue(
                    healthStore.waterTests.observe(ownerUid, tankId)
                        .first()
                        .isEmpty()
                )

                val result = TankHealthIntegrityRecovery
                    .create(context)
                    .recover(ownerUid)

                assertEquals(1, result.restoredRecordCount)
                assertEquals(1, result.recoveredTransactionCount)
                assertEquals(
                    1,
                    healthStore.waterTests.observe(ownerUid, tankId)
                        .first()
                        .size
                )
                assertTrue(
                    TankHealthIntegrityJournal
                        .pendingForOwner(ownerUid)
                        .isEmpty()
                )
            }
        } finally {
            UserDataScope.withOwnerUid(ownerUid) {
                healthStore.integrity.clearAllRecords(ownerUid)
                tankStore.clearAllTanks(ownerUid)
                TankHealthIntegrityJournal.clearOwner(ownerUid)
            }
        }
    }

    @Test
    fun pendingHealthTransactionCompletesWhenTankWasAlreadyDeleted() = runBlocking {
        val ownerUid = "health-delete-recovery-${UUID.randomUUID()}"
        val tankStore = AquariumTankDataStoreManager(context)
        val healthStore = AquariumHealthDataStoreManager.create(context)
        TankHealthIntegrityJournal.initialize(context)

        try {
            UserDataScope.withOwnerUid(ownerUid) {
                TankHealthIntegrityJournal.clearOwner(ownerUid)
                val tankId = tankStore.addTankFromDraft(validTankDraft("Deleted"))
                addWaterTest(healthStore, ownerUid, tankId)

                val snapshot = healthStore.integrity.snapshotForTank(ownerUid, tankId)
                TankHealthIntegrityJournal.begin(ownerUid, listOf(tankId))
                TankHealthIntegrityJournal.captureSnapshots(
                    ownerUid = ownerUid,
                    snapshotsByTank = mapOf(tankId to snapshot)
                )
                healthStore.integrity.deleteRecordsForTank(ownerUid, tankId)
                tankStore.deleteTanks(listOf(tankId))

                val result = TankHealthIntegrityRecovery
                    .create(context)
                    .recover(ownerUid)

                assertEquals(1, result.recoveredTransactionCount)
                assertTrue(
                    healthStore.waterTests.observe(ownerUid, tankId)
                        .first()
                        .isEmpty()
                )
                assertTrue(
                    TankHealthIntegrityJournal
                        .pendingForOwner(ownerUid)
                        .isEmpty()
                )
            }
        } finally {
            UserDataScope.withOwnerUid(ownerUid) {
                healthStore.integrity.clearAllRecords(ownerUid)
                tankStore.clearAllTanks(ownerUid)
                TankHealthIntegrityJournal.clearOwner(ownerUid)
            }
        }
    }

    private suspend fun addWaterTest(
        healthStore: AquariumHealthDataStoreManager,
        ownerUid: String,
        tankId: Long
    ) {
        healthStore.waterTests.add(
            ownerUid = ownerUid,
            input = AquariumWaterTestInput(
                tankId = tankId,
                measuredAtMillis = MEASURED_MILLIS,
                readings = listOf(
                    AquariumWaterReading(
                        parameter = HealthWaterParameter.PH,
                        value = 7.1
                    )
                )
            ),
            nowMillis = CREATED_MILLIS
        )
    }

    private fun validTankDraft(name: String) = TankDraft(
        name = name,
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
        const val SETUP_EPOCH_DAY = 20_454L
        const val MEASURED_MILLIS = 1_767_225_600_000L
        const val CREATED_MILLIS = 1_767_229_200_000L
    }
}
