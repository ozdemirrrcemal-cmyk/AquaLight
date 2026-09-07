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
    fun `end of day maps to 1440 only when explicitly enabled`() {
        assertEquals(
            1_440,
            timePickerMinutesOfDay(hour = 24, minute = 0, allowEndOfDay = true)
        )
        assertThrows(IllegalArgumentException::class.java) {
            timePickerMinutesOfDay(hour = 24, minute = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            timePickerMinutesOfDay(hour = 24, minute = 1, allowEndOfDay = true)
        }
    }

    @Test
    fun `end of day request requires exactly 24 00`() {
        AquaTimePickerBottomSheet.Request(
            title = "Select end time",
            message = "End of period",
            initialHour = 24,
            initialMinute = 0,
            allowEndOfDay = true,
            confirmText = "Apply",
            cancelText = "Cancel",
            resultTarget = AquaTimePickerBottomSheet.ResultTarget("end-time-result")
        )

        assertThrows(IllegalArgumentException::class.java) {
            AquaTimePickerBottomSheet.Request(
                title = "Select end time",
                message = "End of period",
                initialHour = 24,
                initialMinute = 5,
                allowEndOfDay = true,
                confirmText = "Apply",
                cancelText = "Cancel",
                resultTarget = AquaTimePickerBottomSheet.ResultTarget("end-time-result")
            )
        }
    }

    @Test
    fun `constrained wall clock exposes only selectable hour and minute combinations`() {
        val selectable = listOf(0, 5, 8 * 60, 8 * 60 + 15, 24 * 60)

        assertEquals(listOf(0, 8, 24), timePickerHourValues(selectable, allowEndOfDay = true))
        assertEquals(listOf(0, 5), timePickerMinuteValues(selectable, hour = 0))
        assertEquals(listOf(0, 15), timePickerMinuteValues(selectable, hour = 8))
        assertEquals(listOf(0), timePickerMinuteValues(selectable, hour = 24))
    }

    @Test
    fun `constrained request rejects an initial time that cannot be selected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AquaTimePickerBottomSheet.Request(
                title = "Select start time",
                message = "Only valid times are shown",
                initialHour = 8,
                initialMinute = 10,
                selectableMinutesOfDay = listOf(8 * 60, 8 * 60 + 5),
                confirmText = "Apply",
                cancelText = "Cancel",
                resultTarget = AquaTimePickerBottomSheet.ResultTarget("start-time-result")
            )
        }
    }

    @Test
    fun `restored constrained selection falls back to the nearest valid value`() {
        assertEquals(
            15,
            restoreTimePickerSelection(
                savedValue = 13,
                initialValue = 0,
                values = listOf(0, 5, 10, 15)
            )
        )
    }

    @Test
    fun `invalid clock values are rejected`() {
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
