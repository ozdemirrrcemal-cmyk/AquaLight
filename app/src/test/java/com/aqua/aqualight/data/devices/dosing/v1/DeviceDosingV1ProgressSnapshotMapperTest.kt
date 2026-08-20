package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingScheduleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceDosingV1ProgressSnapshotMapperTest {

    @Test
    fun `firmware progress summary survives the application mapping unchanged`() {
        val status = DeviceDosingV1StatusParser.parseProgress(
            DeviceDosingV1TestFixtures.progressStatus()
        )
        val detail = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus()
        ).channel

        val mapped = DeviceDosingV1ProgressSnapshotMapper.map(status, detail)

        assertEquals(DeviceDosingScheduleState.ACTIVE, mapped.scheduleState)
        assertEquals(2_400L, mapped.scheduledAmountMicroliters)
        assertEquals(1_100L, mapped.completedAmountMicroliters)
        assertEquals(1_300L, mapped.remainingAmountMicroliters)
        assertEquals(2, mapped.totalOccurrences)
        assertEquals(1, mapped.completedOccurrences)
        assertEquals(1, mapped.resolvedOccurrences)
        assertEquals(0, mapped.pendingOccurrences)
        assertEquals(1, mapped.runningOccurrences)
        assertEquals(0, mapped.skippedOccurrences)
        assertEquals(0, mapped.uncertainOccurrences)
        assertEquals(45.833, mapped.completionPercent, 0.0)
        assertEquals(2, mapped.occurrences.size)
    }

    @Test
    fun `non-current execution keeps canonical firmware occurrences pending`() {
        val json = DeviceDosingV1TestFixtures.progressStatus()
        json.getJSONObject("progress")
            .put("completed", 0)
            .put("resolved", 0)
            .put("pending", 2)
            .put("running", 0)
            .put("skipped", 0)
            .put("uncertain", 0)
            .put("completedAmountMl", 0.0)
            .put("remainingAmountMl", 2.4)
            .put("completionPercent", 0.0)
            .put("executionCurrent", false)
        val occurrences = json.getJSONArray("occurrences")
        for (index in 0 until occurrences.length()) {
            occurrences.getJSONObject(index).put("status", "pending")
        }
        val status = DeviceDosingV1StatusParser.parseProgress(json)
        val detail = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus()
        ).channel

        val mapped = DeviceDosingV1ProgressSnapshotMapper.map(status, detail)

        assertFalse(mapped.executionCurrent)
        assertEquals(DeviceDosingScheduleState.ACTIVE, mapped.scheduleState)
        assertEquals(2, mapped.totalOccurrences)
        assertEquals(2, mapped.pendingOccurrences)
        assertEquals(2, mapped.occurrences.size)
        assertEquals(
            listOf(DeviceDosingOccurrenceState.PENDING, DeviceDosingOccurrenceState.PENDING),
            mapped.occurrences.map { occurrence -> occurrence.state }
        )
    }
}
