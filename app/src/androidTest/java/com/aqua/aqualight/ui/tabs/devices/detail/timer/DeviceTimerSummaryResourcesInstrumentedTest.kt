package com.aqua.aqualight.ui.tabs.devices.detail.timer

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceTimerSummaryResourcesInstrumentedTest {

    private val applicationContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun activeChannelSummaryFormatsEveryPlaceholderInSupportedLocales() {
        val turkish = localizedContext(Locale.forLanguageTag("tr"))
        val english = localizedContext(Locale.forLanguageTag("en"))

        assertEquals("Açık çıkış: 1 / 4", turkish.activeSummary(active = 1, total = 4))
        assertEquals("Açık çıkış: 3 / 4", turkish.activeSummary(active = 3, total = 4))
        assertEquals("1 of 4 output active", english.activeSummary(active = 1, total = 4))
        assertEquals("3 of 4 outputs active", english.activeSummary(active = 3, total = 4))
    }

    private fun Context.activeSummary(active: Int, total: Int): String =
        resources.deviceTimerActiveSummary(active = active, total = total)

    private fun localizedContext(locale: Locale): Context {
        val configuration = Configuration(applicationContext.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return applicationContext.createConfigurationContext(configuration)
    }
}
