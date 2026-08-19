package com.aqua.aqualight.data.devices.dosing.v1

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceDosingV1ProgressScheduleStateTest {

    @Test
    fun `active schedule may be authoritative before execution checkpoint becomes current`() {
        val status = DeviceDosingV1StatusParser.parseProgress(
            DeviceDosingV1TestFixtures.progressStatus()
        )
        val detail = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus()
        ).channel
        val activeBeforeCheckpoint = status.copy(
            progress = status.progress.copy(
                scheduleState = DeviceDosingV1WireValue("active"),
                executionCurrent = false,
                programDayDate = "2026-08-19"
            )
        )

        val mapped = DeviceDosingV1ProgressSnapshotMapper.map(activeBeforeCheckpoint, detail)

        assertFalse(mapped.executionCurrent)
        assertEquals(LocalDate.of(2026, 8, 19), mapped.programDayDate)
        assertEquals(status.occurrences.size, mapped.occurrences.size)
    }

    @Test
    fun `schedule state and firmware program day must remain coherent`() {
        val status = DeviceDosingV1StatusParser.parseProgress(
            DeviceDosingV1TestFixtures.progressStatus()
        )
        val detail = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus()
        ).channel
        val inconsistent = status.copy(
            progress = status.progress.copy(
                scheduleState = DeviceDosingV1WireValue("noSchedule"),
                executionCurrent = false,
                programDayDate = "2026-08-19"
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1ProgressSnapshotMapper.map(inconsistent, detail)
        }
    }

    @Test
    fun `execution current cannot exist without firmware active program day`() {
        val status = DeviceDosingV1StatusParser.parseProgress(
            DeviceDosingV1TestFixtures.progressStatus()
        )
        val detail = DeviceDosingV1StatusParser.parseChannel(
            DeviceDosingV1TestFixtures.channelStatus()
        ).channel
        val inconsistent = status.copy(
            progress = status.progress.copy(
                scheduleState = DeviceDosingV1WireValue("noSchedule"),
                executionCurrent = true,
                programDayDate = null
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            DeviceDosingV1ProgressSnapshotMapper.map(inconsistent, detail)
        }
    }
}
