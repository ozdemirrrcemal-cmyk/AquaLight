package com.aqua.aqualight.application.aquarium.health

import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskStatus
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.application.care.CompletedCareActivityInput
import com.aqua.aqualight.application.care.MaintenanceOperations
import com.aqua.aqualight.application.care.ManualCareTaskInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LivestockHealthContextCoordinatorTest {

    @Test
    fun contextSelectsLatestWaterAnalysisAndCompletedWaterChangeForTank() = runTest {
        val waterOperations = FakeWaterAnalysisOperations(sampleAnalyses())
        val coordinator = LivestockHealthContextCoordinator(
            waterAnalysisOperations = waterOperations,
            maintenanceOperations = FakeMaintenanceOperations(sampleTasks())
        )

        val context = coordinator.contextForTank(TARGET_TANK_ID).first()

        assertEquals(TARGET_TANK_ID, waterOperations.requestedTankId)
        assertEquals(LATEST_ANALYSIS_ID, context.latestWaterAnalysis?.id)
        assertEquals(LATEST_WATER_CHANGE_TIME, context.lastWaterChangeAtMillis)
    }

    @Test
    fun contextUsesNullsWhenTankHasNoWaterOrMaintenanceHistory() = runTest {
        val coordinator = LivestockHealthContextCoordinator(
            waterAnalysisOperations = FakeWaterAnalysisOperations(emptyList()),
            maintenanceOperations = FakeMaintenanceOperations(emptyList())
        )

        val context = coordinator.contextForTank(TARGET_TANK_ID).first()

        assertNull(context.latestWaterAnalysis)
        assertNull(context.lastWaterChangeAtMillis)
    }

    private fun sampleAnalyses(): List<WaterAnalysisSnapshot> = listOf(
        waterAnalysis(FIRST_ANALYSIS_ID, FIRST_ANALYSIS_TIME),
        waterAnalysis(LATEST_ANALYSIS_ID, LATEST_ANALYSIS_TIME)
    )

    private fun sampleTasks(): List<CareTaskSnapshot> = listOf(
        task(
            FIRST_TASK_ID, TARGET_TANK_ID, CareTaskType.WATER_CHANGE,
            CareTaskStatus.COMPLETED, FIRST_WATER_CHANGE_TIME
        ),
        task(
            LATEST_TASK_ID, TARGET_TANK_ID, CareTaskType.WATER_CHANGE,
            CareTaskStatus.COMPLETED, LATEST_WATER_CHANGE_TIME
        ),
        task(
            OTHER_TANK_TASK_ID, OTHER_TANK_ID, CareTaskType.WATER_CHANGE,
            CareTaskStatus.COMPLETED, OTHER_TANK_WATER_CHANGE_TIME
        ),
        task(
            PENDING_TASK_ID, TARGET_TANK_ID, CareTaskType.WATER_CHANGE,
            CareTaskStatus.PENDING, null
        ),
        task(
            FEEDING_TASK_ID, TARGET_TANK_ID, CareTaskType.FEEDING,
            CareTaskStatus.COMPLETED, FEEDING_TIME
        )
    )

    private fun waterAnalysis(
        id: Long,
        measuredAtMillis: Long
    ) = WaterAnalysisSnapshot(
        id = id,
        tankId = TARGET_TANK_ID,
        measuredAtMillis = measuredAtMillis,
        temperatureCelsius = null,
        temperatureSource = null,
        measurements = emptyList(),
        createdAtMillis = measuredAtMillis
    )

    private fun task(
        id: Long,
        tankId: Long,
        type: CareTaskType,
        status: CareTaskStatus,
        completedAtMillis: Long?
    ) = CareTaskSnapshot(
        id = id,
        tankId = tankId,
        title = "test",
        description = "",
        type = type,
        source = CareTaskSource.MANUAL,
        status = status,
        dueAtMillis = DUE_AT_MILLIS,
        completedAtMillis = completedAtMillis,
        repeatEnabled = false,
        repeatIntervalDays = DEFAULT_DAY_COUNT,
        reminderEnabled = false,
        missedReminderEnabled = false,
        missedReminderDays = DEFAULT_DAY_COUNT,
        waterChangePercent = null,
        note = "",
        createdAtMillis = CREATED_AT_MILLIS
    )

    private class FakeWaterAnalysisOperations(
        private val analyses: List<WaterAnalysisSnapshot>
    ) : WaterAnalysisOperations {
        var requestedTankId: Long? = null

        override fun analysesForTank(tankId: Long): Flow<List<WaterAnalysisSnapshot>> {
            requestedTankId = tankId
            return flowOf(analyses)
        }

        override fun analysis(analysisId: Long): Flow<WaterAnalysisSnapshot?> =
            flowOf(analyses.firstOrNull { analysis -> analysis.id == analysisId })

        override suspend fun saveAnalysis(input: WaterAnalysisInput): Long =
            error(UNUSED_MESSAGE)

        override suspend fun deleteAnalysis(analysisId: Long) {
            error(UNUSED_MESSAGE)
        }
    }

    private class FakeMaintenanceOperations(
        tasks: List<CareTaskSnapshot>
    ) : MaintenanceOperations {
        override val tasks: Flow<List<CareTaskSnapshot>> = flowOf(tasks)

        override fun task(taskId: Long): Flow<CareTaskSnapshot?> = flowOf(null)

        override suspend fun syncSmartCareTasks(tanks: List<AquariumTankSnapshot>) {
            error(UNUSED_MESSAGE)
        }

        override suspend fun completeTask(taskId: Long) = unused()
        override suspend fun deleteTask(taskId: Long) = unused()

        override suspend fun updateCompletedTaskDate(
            taskId: Long,
            completedAtMillis: Long
        ) = unused()

        override suspend fun addCompletedActivity(input: CompletedCareActivityInput) = unused()
        override suspend fun deleteManualTask(taskId: Long) = unused()
        override suspend fun addManualTask(input: ManualCareTaskInput) = unused()

        override suspend fun updateManualTask(
            taskId: Long,
            input: ManualCareTaskInput
        ) = unused()

        private fun unused(): Nothing = error(UNUSED_MESSAGE)
    }

    private companion object {
        const val TARGET_TANK_ID = 42L
        const val OTHER_TANK_ID = 7L
        const val FIRST_ANALYSIS_ID = 101L
        const val LATEST_ANALYSIS_ID = 102L
        const val FIRST_ANALYSIS_TIME = 1_000L
        const val LATEST_ANALYSIS_TIME = 2_000L
        const val FIRST_TASK_ID = 201L
        const val LATEST_TASK_ID = 202L
        const val OTHER_TANK_TASK_ID = 203L
        const val PENDING_TASK_ID = 204L
        const val FEEDING_TASK_ID = 205L
        const val FIRST_WATER_CHANGE_TIME = 3_000L
        const val LATEST_WATER_CHANGE_TIME = 4_000L
        const val OTHER_TANK_WATER_CHANGE_TIME = 5_000L
        const val FEEDING_TIME = 6_000L
        const val DUE_AT_MILLIS = 900L
        const val CREATED_AT_MILLIS = 800L
        const val DEFAULT_DAY_COUNT = 1
        const val UNUSED_MESSAGE = "Not used by context tests."
    }
}
