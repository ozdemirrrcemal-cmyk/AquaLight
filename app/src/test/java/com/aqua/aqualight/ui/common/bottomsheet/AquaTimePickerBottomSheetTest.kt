package com.aqua.aqualight.ui.common.bottomsheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AquaTimePickerBottomSheetTest {

    @Test
    fun `restored wheel selection wins over the original value`() {
        assertEquals(
            17,
            restoreTimePickerSelection(
                savedValue = 17,
                initialValue = 9,
                range = 0..23
            )
        )
    }

    @Test
    fun `wheel selection is clamped to its current contract`() {
        assertEquals(
            23,
            restoreTimePickerSelection(
                savedValue = 28,
                initialValue = 9,
                range = 0..23
            )
        )
        assertEquals(
            0,
            restoreTimePickerSelection(
                savedValue = null,
                initialValue = -1,
                range = 0..59
            )
        )
    }

    @Test
    fun `hour and minute map to one stable minute of day`() {
        assertEquals(0, timePickerMinutesOfDay(hour = 0, minute = 0))
        assertEquals(570, timePickerMinutesOfDay(hour = 9, minute = 30))
        assertEquals(1_439, timePickerMinutesOfDay(hour = 23, minute = 59))
    }

    @Test
    fun `invalid clock values are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            timePickerMinutesOfDay(hour = 24, minute = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            timePickerMinutesOfDay(hour = 0, minute = 60)
        }
    }

    @Test
    fun `minute of hour mode rejects a wall clock hour`() {
        assertThrows(IllegalArgumentException::class.java) {
            AquaTimePickerBottomSheet.Request(
                title = "Select minute",
                message = "Runs every hour",
                initialHour = 9,
                initialMinute = 15,
                selectionMode = AquaTimePickerBottomSheet.SelectionMode.MINUTE_OF_HOUR,
                confirmText = "Apply",
                cancelText = "Cancel",
                resultTarget = AquaTimePickerBottomSheet.ResultTarget("hourly-result")
            )
        }
    }
}
