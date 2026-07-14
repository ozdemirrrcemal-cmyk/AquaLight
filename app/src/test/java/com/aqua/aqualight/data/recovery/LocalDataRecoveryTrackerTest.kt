package com.aqua.aqualight.data.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalDataRecoveryTrackerTest {

    @Before
    fun clearTracker() {
        LocalDataRecoveryTracker.consumeRecoveredAreas()
    }

    @Test
    fun `recovered areas are deduplicated and consumed once`() {
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.KNOWN_DEVICES
        )
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.KNOWN_DEVICES
        )
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.TANK_DEVICE_ASSIGNMENTS
        )
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.AQUARIUM_TANKS
        )
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.CARE_TASKS
        )

        assertEquals(
            setOf(
                LocalDataRecoveryTracker.Area.KNOWN_DEVICES,
                LocalDataRecoveryTracker.Area.TANK_DEVICE_ASSIGNMENTS,
                LocalDataRecoveryTracker.Area.AQUARIUM_TANKS,
                LocalDataRecoveryTracker.Area.CARE_TASKS
            ),
            LocalDataRecoveryTracker.consumeRecoveredAreas()
        )
        assertTrue(LocalDataRecoveryTracker.consumeRecoveredAreas().isEmpty())
    }
}
