package com.aqua.aqualight.i18n

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocaleContextInstrumentedTest {

    private val applicationContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun unsupportedChineseDeviceLocaleCannotLeakIntoFrameworkPickers() {
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

        assertNotEquals("zh", localizedLocale.language)
        assertEquals(
            SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG,
            localizedLocale.language
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val dateDialog = DatePickerDialog(localizedContext)
            val timeDialog = TimePickerDialog(
                localizedContext,
                null,
                12,
                0,
                true
            )

            assertEquals(
                SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG,
                dateDialog.context.resources.configuration.locales[0].language
            )
            assertEquals(
                SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG,
                timeDialog.context.resources.configuration.locales[0].language
            )

            dateDialog.dismiss()
            timeDialog.dismiss()
        }
    }
}
