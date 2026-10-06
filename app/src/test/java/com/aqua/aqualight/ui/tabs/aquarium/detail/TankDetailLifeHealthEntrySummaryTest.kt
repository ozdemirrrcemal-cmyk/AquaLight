package com.aqua.aqualight.ui.tabs.aquarium.detail

import com.aqua.aqualight.application.aquarium.health.LivestockCheckSnapshot
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TankDetailLifeHealthEntrySummaryTest {

    @Test
    fun summaryCountsOnlyActiveFollowupsAndUsesOnlyTheirChecks() {
        val summary = buildLivestockHealthEntrySummary(
            listOf(
                observation(
                    id = ACTIVE_OBSERVATION_ID,
                    closedAtMillis = null,
                    checks = listOf(check(ACTIVE_CHECK_TIME))
                ),
                observation(
                    id = CLOSED_OBSERVATION_ID,
                    closedAtMillis = CLOSED_AT_MILLIS,
                    checks = listOf(check(CLOSED_CHECK_TIME))
                )
            )
        )

        assertEquals(1, summary.activeFollowupCount)
        assertEquals(ACTIVE_CHECK_TIME, summary.lastCheckAtMillis)
    }

    @Test
    fun summaryHasNoLastCheckWhenOnlyClosedFollowupsExist() {
        val summary = buildLivestockHealthEntrySummary(
            listOf(
                observation(
                    id = CLOSED_OBSERVATION_ID,
                    closedAtMillis = CLOSED_AT_MILLIS,
                    checks = listOf(check(CLOSED_CHECK_TIME))
                )
            )
        )

        assertEquals(0, summary.activeFollowupCount)
        assertNull(summary.lastCheckAtMillis)
    }

    private fun observation(
        id: Long,
        closedAtMillis: Long?,
        checks: List<LivestockCheckSnapshot>
    ) = LivestockObservationSnapshot(
        id = id,
        tankId = TANK_ID,
        livestockId = LIVESTOCK_ID,
        symptomKeys = listOf(SYMPTOM_KEY),
        onsetKey = ONSET_KEY,
        otherObservation = "",
        note = "",
        photoUris = emptyList(),
        affectedCount = AFFECTED_COUNT,
        totalCount = TOTAL_COUNT,
        createdAtMillis = CREATED_AT_MILLIS,
        closedAtMillis = closedAtMillis,
        closeReason = closedAtMillis?.let { CLOSE_REASON },
        checks = checks,
        evaluations = emptyList()
    )

    private fun check(checkedAtMillis: Long) = LivestockCheckSnapshot(
        status = CHECK_STATUS,
        affectedCount = AFFECTED_COUNT,
        checkedAtMillis = checkedAtMillis,
        note = "",
        photoUris = emptyList()
    )

    private companion object {
        const val TANK_ID = 42L
        const val LIVESTOCK_ID = 7L
        const val ACTIVE_OBSERVATION_ID = 101L
        const val CLOSED_OBSERVATION_ID = 102L
        const val CREATED_AT_MILLIS = 1_000L
        const val ACTIVE_CHECK_TIME = 2_000L
        const val CLOSED_CHECK_TIME = 3_000L
        const val CLOSED_AT_MILLIS = 4_000L
        const val AFFECTED_COUNT = 1
        const val TOTAL_COUNT = 4
        const val SYMPTOM_KEY = "surface"
        const val ONSET_KEY = "today"
        const val CHECK_STATUS = "same"
        const val CLOSE_REASON = "manual"
    }
}
