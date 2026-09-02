package com.aqua.aqualight.data.store

import androidx.datastore.core.DataStoreFactory
import com.aqua.aqualight.data.aquarium.store.AquariumTanksSerializer
import com.aqua.aqualight.data.aquarium.store.StoredTank
import com.aqua.aqualight.data.aquarium.store.TankStoreRules
import com.aqua.aqualight.application.aquarium.AquariumIdGenerator
import com.aqua.aqualight.data.care.CareTaskStoreRules
import com.aqua.aqualight.data.care.CareTasksCommercialSerializer
import com.aqua.aqualight.data.care.StoredCareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.user.UserPreferencesSerializer
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialStoreConcurrencyTest {

    @Test
    fun concurrentCareTaskTransactionsPersistUniqueOwnerScopedIds() = runBlocking {
        withTemporaryStore(
            fileName = "care_tasks.pb",
            serializer = CareTasksCommercialSerializer
        ) { store ->
            coroutineScope {
                repeat(WRITE_COUNT) { index ->
                    launch(Dispatchers.Default) {
                        store.updateData { current ->
                            val id = CareTaskStoreRules.nextUniqueId(
                                currentTasks = current.tasksList,
                                nowMillis = FIXED_NOW_MILLIS
                            )
                            CareTaskStoreRules.validateStore(
                                current.toBuilder()
                                    .addTasks(validCareTask(id, index))
                                    .build()
                            )
                        }
                    }
                }
            }

            val persisted = store.data.first()
            val ids = persisted.tasksList.map { task -> task.id }

            assertEquals(WRITE_COUNT, persisted.tasksCount)
            assertEquals(WRITE_COUNT, ids.toSet().size)
            assertTrue(ids.all { id -> id > 0L })
            CareTaskStoreRules.validateStore(persisted)
        }
    }

    @Test
    fun concurrentTankTransactionsPersistUniqueOwnerScopedIds() = runBlocking {
        withTemporaryStore(
            fileName = "aquarium_tanks.pb",
            serializer = AquariumTanksSerializer
        ) { store ->
            coroutineScope {
                repeat(WRITE_COUNT) { index ->
                    launch(Dispatchers.Default) {
                        store.updateData { current ->
                            val existingIds = current.tanksList
                                .mapTo(mutableSetOf()) { tank -> tank.id }
                            val id = AquariumIdGenerator.newLong(existingIds)

                            TankStoreRules.validateStore(
                                current.toBuilder()
                                    .addTanks(validTank(id, index))
                                    .build()
                            )
                        }
                    }
                }
            }

            val persisted = store.data.first()
            val ids = persisted.tanksList.map { tank -> tank.id }

            assertEquals(WRITE_COUNT, persisted.tanksCount)
            assertEquals(WRITE_COUNT, ids.toSet().size)
            assertTrue(ids.all { id -> id > 0L })
            TankStoreRules.validateStore(persisted)
        }
    }

    @Test
    fun concurrentPreferenceTransactionsDoNotLoseUpdates() = runBlocking {
        withTemporaryStore(
            fileName = "user_prefs.pb",
            serializer = UserPreferencesSerializer
        ) { store ->
            coroutineScope {
                repeat(WRITE_COUNT) {
                    launch(Dispatchers.Default) {
                        store.updateData { current ->
                            val updated = current.toBuilder()
                                .setTodayManualActionCount(
                                    current.todayManualActionCount + 1
                                )
                                .build()
                            com.aqua.aqualight.data.user.UserPreferencesStoreRules
                                .validate(updated)
                        }
                    }
                }
            }

            val persisted = store.data.first()
            assertEquals(WRITE_COUNT, persisted.todayManualActionCount)
        }
    }

    private suspend fun <T> withTemporaryStore(
        fileName: String,
        serializer: androidx.datastore.core.Serializer<T>,
        block: suspend (androidx.datastore.core.DataStore<T>) -> Unit
    ) {
        val directory = Files.createTempDirectory("aql-store-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(directory, fileName)
        val store = DataStoreFactory.create(
            serializer = serializer,
            scope = scope,
            produceFile = { file }
        )

        try {
            block(store)
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    private fun validCareTask(
        id: Long,
        index: Int
    ): StoredCareTask = StoredCareTask.newBuilder()
        .setId(id)
        .setOwnerUid(OWNER_UID)
        .setTankId(TANK_ID)
        .setTitle("Task $index")
        .setDescription("")
        .setType(CareTaskType.FILTER_MAINTENANCE.name)
        .setSource(CareTaskSource.MANUAL.name)
        .setStatus(CareTaskStatus.PENDING.name)
        .setDueAtMillis(FIXED_DUE_MILLIS)
        .setCompletedAtMillis(0L)
        .setRepeatEnabled(false)
        .setRepeatIntervalDays(CareTaskStoreRules.MIN_REPEAT_INTERVAL_DAYS)
        .setReminderEnabled(false)
        .setMissedReminderEnabled(false)
        .setMissedReminderDays(CareTaskStoreRules.MIN_MISSED_REMINDER_DAYS)
        .setWaterChangePercent(0)
        .setGeneratedRuleKey("")
        .setCreatedAtMillis(FIXED_NOW_MILLIS)
        .setUpdatedAtMillis(FIXED_NOW_MILLIS)
        .build()

    private fun validTank(
        id: Long,
        index: Int
    ): StoredTank = StoredTank.newBuilder()
        .setId(id)
        .setOwnerUid(OWNER_UID)
        .setName("Tank $index")
        .setDescription("")
        .setSetupDateEpochDay(FIXED_SETUP_EPOCH_DAY)
        .setWidthCm(60)
        .setLengthCm(40)
        .setHeightCm(40)
        .setSizeUnit("cm")
        .setVolumeUnit("L")
        .setTankType("Planted")
        .setTankStyle("Nature Aquarium")
        .setCreatedAtMillis(FIXED_NOW_MILLIS)
        .build()

    private companion object {
        const val WRITE_COUNT = 64
        const val OWNER_UID = "owner-concurrency"
        const val TANK_ID = 10L
        const val FIXED_SETUP_EPOCH_DAY = 20_454L
        const val FIXED_NOW_MILLIS = 1_767_225_600_000L
        const val FIXED_DUE_MILLIS = 1_767_312_000_000L
    }
}
