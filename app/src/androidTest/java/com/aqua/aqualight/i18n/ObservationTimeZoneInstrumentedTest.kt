package com.aqua.aqualight.i18n

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.view.View
import android.view.ViewGroup
import android.widget.TimePicker
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import com.aqua.aqualight.ui.common.feedback.Stage8DialogTestActivity
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObservationTimeZoneInstrumentedTest {
    private val sampleZone = TimeZone.getTimeZone("Asia/Tokyo")
    private val sampleTime = Calendar.getInstance(sampleZone).apply {
        clear()
        set(2026, Calendar.JANUARY, 2, 0, 30)
    }.timeInMillis

    @Test
    fun datePickerRetainsDraftZoneAcrossRecreationAndReturnsSelectedLocalDate() = withChangedDeviceZone {
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                AppDatePickerDialogFragment.show(activity.supportFragmentManager, "sample-date",
                    sampleTime, zone = sampleZone)
                activity.supportFragmentManager.executePendingTransactions()
            }
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            scenario.recreate()
            scenario.onActivity { activity ->
                var result: Long? = null
                val manager = activity.supportFragmentManager
                manager.setFragmentResultListener("sample-date", activity) { _, value ->
                    result = value.getLong(AppDatePickerDialogFragment.RESULT_MILLIS)
                }
                val fragment = manager.fragments.filterIsInstance<AppDatePickerDialogFragment>().single()
                val dialog = fragment.requireDialog() as DatePickerDialog
                assertEquals(2, dialog.datePicker.dayOfMonth)
                dialog.datePicker.updateDate(2026, Calendar.JANUARY, 3)
                dialog.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
                val selected = Calendar.getInstance(sampleZone).apply { timeInMillis = checkNotNull(result) }
                assertEquals(2026, selected.get(Calendar.YEAR))
                assertEquals(Calendar.JANUARY, selected.get(Calendar.MONTH))
                assertEquals(3, selected.get(Calendar.DAY_OF_MONTH))
                assertEquals(0, selected.get(Calendar.HOUR_OF_DAY))
                assertEquals(30, selected.get(Calendar.MINUTE))
            }
        }
    }

    @Test
    fun timePickerRetainsDraftZoneAcrossRecreationAndDoesNotChangeSampleDate() = withChangedDeviceZone {
        ActivityScenario.launch(Stage8DialogTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                AppTimePickerDialogFragment.show(activity.supportFragmentManager, "sample-time",
                    sampleTime, zone = sampleZone)
                activity.supportFragmentManager.executePendingTransactions()
            }
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            scenario.recreate()
            scenario.onActivity { activity ->
                var result: Long? = null
                val manager = activity.supportFragmentManager
                manager.setFragmentResultListener("sample-time", activity) { _, value ->
                    result = value.getLong(AppTimePickerDialogFragment.RESULT_MILLIS)
                }
                val fragment = manager.fragments.filterIsInstance<AppTimePickerDialogFragment>().single()
                val dialog = fragment.requireDialog() as TimePickerDialog
                val picker = checkNotNull(dialog.window).decorView.descendants().filterIsInstance<TimePicker>().single()
                assertEquals(0, picker.hour)
                assertEquals(30, picker.minute)
                picker.hour = 16
                picker.minute = 45
                dialog.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
                val selected = Calendar.getInstance(sampleZone).apply { timeInMillis = checkNotNull(result) }
                assertEquals(2, selected.get(Calendar.DAY_OF_MONTH))
                assertEquals(16, selected.get(Calendar.HOUR_OF_DAY))
                assertEquals(45, selected.get(Calendar.MINUTE))
            }
        }
    }

    @Test
    fun explicitZoneFormattingRetainsDateAndSupportsTurkishEnglishAndBothClockModes() = withChangedDeviceZone {
        assertEquals("Jan 2, 2026", LocaleFormatter.formatDate(sampleTime, Locale.US, sampleZone))
        assertNotEquals(LocaleFormatter.formatDate(sampleTime, Locale.US),
            LocaleFormatter.formatDate(sampleTime, Locale.US, sampleZone))
        for (locale in listOf(Locale.US, Locale.forLanguageTag("tr-TR"))) {
            assertEquals("00:30", LocaleFormatter.formatTime(sampleTime, locale, true, sampleZone))
            val twelveHour = LocaleFormatter.formatTime(sampleTime, locale, false, sampleZone)
            assertTrue(twelveHour.contains("12:30"))
            assertNotEquals(twelveHour, LocaleFormatter.formatTime(sampleTime, locale, true, sampleZone))
        }
    }

    private fun withChangedDeviceZone(block: () -> Unit) {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }
}
