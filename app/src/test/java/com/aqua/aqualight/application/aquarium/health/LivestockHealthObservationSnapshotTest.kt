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

    private fun observation(
        affectedCount: Int,
        checks: List<LivestockCheckSnapshot>
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
        checks = checks
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
