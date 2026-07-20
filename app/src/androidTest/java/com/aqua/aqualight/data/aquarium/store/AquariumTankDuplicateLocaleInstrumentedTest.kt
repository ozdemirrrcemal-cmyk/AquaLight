package com.aqua.aqualight.data.aquarium.store

import android.content.Context
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.ui.common.feedback.Stage8DialogTestActivity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AquariumTankDuplicateLocaleInstrumentedTest {

    private val applicationContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun duplicateNamesFollowActivePerAppLocaleFromApplicationContext() = runBlocking {
        val ownerUid = "tank-duplicate-locale-${UUID.randomUUID()}"
        val previousLanguageTags = AppCompatDelegate
            .getApplicationLocales()
            .toLanguageTags()
        val scenario = ActivityScenario.launch(Stage8DialogTestActivity::class.java)
        val tankStore = AquariumTankDataStoreManager(applicationContext)

        try {
            UserDataScope.withOwnerUid(ownerUid) {
                tankStore.clearAllTanks(ownerUid)
                val sourceTankId = tankStore.addTankFromDraft(validTankDraft("Display Tank"))

                applyLanguage("tr")
                val firstTurkishCopyId = tankStore.duplicateTank(sourceTankId)
                val secondTurkishCopyId = tankStore.duplicateTank(sourceTankId)
                assertEquals(
                    "Display Tank Kopya",
                    tankName(tankStore, ownerUid, firstTurkishCopyId)
                )
                assertEquals(
                    "Display Tank Kopya 2",
                    tankName(tankStore, ownerUid, secondTurkishCopyId)
                )

                applyLanguage("en")
                val firstEnglishCopyId = tankStore.duplicateTank(sourceTankId)
                val secondEnglishCopyId = tankStore.duplicateTank(sourceTankId)
                assertEquals(
                    "Display Tank Copy",
                    tankName(tankStore, ownerUid, firstEnglishCopyId)
                )
                assertEquals(
                    "Display Tank Copy 2",
                    tankName(tankStore, ownerUid, secondEnglishCopyId)
                )
            }
        } finally {
            UserDataScope.withOwnerUid(ownerUid) {
                tankStore.clearAllTanks(ownerUid)
            }
            restoreLanguage(previousLanguageTags)
            scenario.close()
        }
    }

    private suspend fun tankName(
        tankStore: AquariumTankDataStoreManager,
        ownerUid: String,
        tankId: Long
    ): String {
        val tank = tankStore.tanksSnapshotForOwner(ownerUid)
            .firstOrNull { candidate -> candidate.id == tankId }
        assertTrue("Duplicated tank must exist for the active owner.", tank != null)
        return requireNotNull(tank).name
    }

    private fun applyLanguage(languageTag: String) {
        setApplicationLocales(languageTag)

        repeat(LOCALE_SETTLE_ATTEMPTS) {
            val actualLanguage = ContextCompat
                .getContextForLanguage(applicationContext)
                .resources
                .configuration
                .locales[0]
                .language
            if (actualLanguage == languageTag) {
                return
            }
            SystemClock.sleep(LOCALE_SETTLE_DELAY_MILLIS)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }

        val actualLanguage = ContextCompat
            .getContextForLanguage(applicationContext)
            .resources
            .configuration
            .locales[0]
            .language
        assertEquals(languageTag, actualLanguage)
    }

    private fun restoreLanguage(languageTags: String) {
        setApplicationLocales(languageTags)
    }

    private fun setApplicationLocales(languageTags: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(languageTags)
            )
        }
        instrumentation.waitForIdleSync()
    }

    private fun validTankDraft(name: String): TankDraft = TankDraft(
        name = name,
        description = "",
        photoUri = null,
        plants = emptyList(),
        materials = emptyList(),
        setupDateEpochDay = SETUP_EPOCH_DAY,
        widthCm = 60,
        lengthCm = 40,
        heightCm = 40,
        sizeUnit = "cm",
        volumeUnit = "L",
        tankType = "Planted",
        tankStyle = ""
    )

    private companion object {
        const val SETUP_EPOCH_DAY = 20_454L
        const val LOCALE_SETTLE_ATTEMPTS = 40
        const val LOCALE_SETTLE_DELAY_MILLIS = 50L
    }
}
