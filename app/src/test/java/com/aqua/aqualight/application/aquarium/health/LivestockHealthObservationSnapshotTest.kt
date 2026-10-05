package com.aqua.aqualight.application.aquarium.health

import org.junit.Assert.assertEquals
import org.junit.Test

class LivestockHealthObservationSnapshotTest {

    @Test
    fun currentAffectedCountUsesInitialObservationBeforeAnyCheck() {
        val snapshot = observation(
            affectedCount = 7,
            checks = emptyList()
        )

        assertEquals(7, snapshot.currentAffectedCount)
    }

    @Test
    fun currentAffectedCountUsesChronologicallyLatestCheck() {
        val snapshot = observation(
            affectedCount = 7,
            checks = listOf(
                check(affectedCount = 4, checkedAtMillis = 3_000L),
                check(affectedCount = 6, checkedAtMillis = 1_000L),
                check(affectedCount = 5, checkedAtMillis = 4_000L)
            )
        )

        assertEquals(5, snapshot.currentAffectedCount)
    }

    @Test
    fun evaluationBecomesStaleAfterNewCheck() {
        val initialEvaluation = evaluation(checkCount = 0, latestCheckAtMillis = null)
        val snapshot = observation(
            affectedCount = 7,
            checks = listOf(check(affectedCount = 5, checkedAtMillis = 4_000L)),
            evaluations = listOf(initialEvaluation)
        )

        assertEquals(true, snapshot.isEvaluationStale(null))
    }

    @Test
    fun evaluationBecomesStaleWhenLatestWaterAnalysisChanges() {
        val previousWater = waterAnalysis(id = 10L)
        val snapshot = observation(
            affectedCount = 7,
            checks = emptyList(),
            evaluations = listOf(
                evaluation(
                    checkCount = 0,
                    latestCheckAtMillis = null,
                    waterAnalysis = previousWater
                )
            )
        )

        assertEquals(true, snapshot.isEvaluationStale(waterAnalysis(id = 11L)))
        assertEquals(false, snapshot.isEvaluationStale(previousWater))
    }

    private fun observation(
        affectedCount: Int,
        checks: List<LivestockCheckSnapshot>,
        evaluations: List<LivestockEvaluationSnapshot> = emptyList()
    ) = LivestockObservationSnapshot(
        id = 11L,
        tankId = 22L,
        livestockId = 33L,
        symptomKeys = listOf("surface"),
        onsetKey = "today",
        otherObservation = "",
        note = "",
        photoUris = emptyList(),
        affectedCount = affectedCount,
        totalCount = 7,
        createdAtMillis = 500L,
        closedAtMillis = null,
        closeReason = null,
        checks = checks,
        evaluations = evaluations
    )

    private fun evaluation(
        checkCount: Int,
        latestCheckAtMillis: Long?,
        waterAnalysis: WaterAnalysisSnapshot? = null
    ) = LivestockEvaluationSnapshot(
        id = 90L,
        evaluatedAtMillis = 5_000L,
        trigger = LivestockEvaluationTrigger.INITIAL_OBSERVATION,
        affectedCount = 7,
        basedOnCheckCount = checkCount,
        basedOnLatestCheckAtMillis = latestCheckAtMillis,
        waterAnalysis = waterAnalysis
    )

    private fun waterAnalysis(id: Long) = WaterAnalysisSnapshot(
        id = id,
        tankId = 22L,
        measuredAtMillis = 2_000L,
        temperatureCelsius = null,
        temperatureSource = null,
        measurements = emptyList(),
        createdAtMillis = 2_500L
    )

    private fun check(
        affectedCount: Int,
        checkedAtMillis: Long
    ) = LivestockCheckSnapshot(
        status = "same",
        affectedCount = affectedCount,
        checkedAtMillis = checkedAtMillis,
        note = "",
        photoUris = emptyList()
    )
}
