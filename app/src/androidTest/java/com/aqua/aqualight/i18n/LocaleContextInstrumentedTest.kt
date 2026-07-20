package com.aqua.aqualight.i18n

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat as AndroidDateFormat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import com.aqua.aqualight.ui.common.feedback.Stage8DialogTestActivity
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocaleContextInstrumentedTest {

    private val applicationContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun unsupportedChineseDeviceLocaleCannotLeakIntoFormattingContext() {
        val chineseConfiguration = Configuration(
            applicationContext.resources.configuration
        ).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
            setLayoutDirection(Locale.SIMPLIFIED_CHINESE)
        }
        val chineseDeviceContext = applicationContext.createConfigurationContext(
            chineseConfiguration
        )

        val localizedContext = LocaleFormatter.localizedContext(chineseDeviceContext)
        val localizedLocale = localizedContext.resources.configuration.locales[0]
        val expectedLanguage = SupportedLocaleRegistry.deviceDefault()

        assertNotEquals("zh", localizedLocale.language)
        assertTrue(localizedLocale.language in SupportedLocaleRegistry.all)
        assertEquals(expectedLanguage, localizedLocale.language)
    }

    @Test
    fun dateAndTimePickerFragmentsAttachToLiveActivityWindow() {
        val scenario = ActivityScenario.launch(Stage8DialogTestActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val fragmentManager = activity.supportFragmentManager
                val now = System.currentTimeMillis()

                AppDatePickerDialogFragment.show(
                    fragmentManager = fragmentManager,
                    requestKey = "date-picker-window-token",
                    initialMillis = now
                )
                fragmentManager.executePendingTransactions()

                val datePicker = fragmentManager.fragments
                    .filterIsInstance<AppDatePickerDialogFragment>()
                    .single()
                assertTrue(datePicker.dialog?.isShowing == true)
                datePicker.dismissNow()
                fragmentManager.executePendingTransactions()

                AppTimePickerDialogFragment.show(
                    fragmentManager = fragmentManager,
                    requestKey = "time-picker-window-token",
                    initialMillis = now
                )
                fragmentManager.executePendingTransactions()

                val timePicker = fragmentManager.fragments
                    .filterIsInstance<AppTimePickerDialogFragment>()
                    .single()
                assertTrue(timePicker.dialog?.isShowing == true)
                timePicker.dismissNow()
                fragmentManager.executePendingTransactions()
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun timeFormattingFollowsTheAndroid12Or24HourPreference() {
        val scenario = ActivityScenario.launch(Stage8DialogTestActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val timestamp = Calendar.getInstance().apply {
                    set(2026, Calendar.JULY, 19, 22, 8, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val localizedContext = LocaleFormatter.localizedContext(activity)
                val locale = LocaleFormatter.appLocale(localizedContext)
                val systemUses24Hour = AndroidDateFormat.is24HourFormat(localizedContext)

                assertEquals(
                    LocaleFormatter.formatTime(timestamp, locale, systemUses24Hour),
                    LocaleFormatter.formatTime(activity, timestamp)
                )
                assertEquals(
                    LocaleFormatter.formatDateTime(timestamp, locale, systemUses24Hour),
                    LocaleFormatter.formatDateTime(activity, timestamp)
                )
                assertNotEquals(
                    LocaleFormatter.formatTime(timestamp, locale, false),
                    LocaleFormatter.formatTime(timestamp, locale, true)
                )
                assertNotEquals(
                    LocaleFormatter.formatDateTime(timestamp, locale, false),
                    LocaleFormatter.formatDateTime(timestamp, locale, true)
                )
            }
        } finally {
            scenario.close()
        }
    }
}
