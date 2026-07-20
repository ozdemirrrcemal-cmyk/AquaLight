package com.aqua.aqualight.data.aquarium.store

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AquariumTankDuplicateLocaleInstrumentedTest {

    private val applicationContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun duplicateNameResourcesResolveFromNonActivityContextsInBothSupportedLocales() {
        val turkishContext = localizedContext(Locale.forLanguageTag("tr"))
        val englishContext = localizedContext(Locale.forLanguageTag("en"))

        assertEquals(
            "Kopya",
            turkishContext.getString(R.string.aquarium_duplicate_name_suffix)
        )
        assertEquals(
            "Kopya 2",
            turkishContext.getString(
                R.string.aquarium_duplicate_name_numbered_suffix,
                2
            )
        )
        assertEquals(
            "Copy",
            englishContext.getString(R.string.aquarium_duplicate_name_suffix)
        )
        assertEquals(
            "Copy 2",
            englishContext.getString(
                R.string.aquarium_duplicate_name_numbered_suffix,
                2
            )
        )
    }

    private fun localizedContext(locale: Locale): Context {
        val configuration = Configuration(applicationContext.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return applicationContext.createConfigurationContext(configuration)
    }
}
