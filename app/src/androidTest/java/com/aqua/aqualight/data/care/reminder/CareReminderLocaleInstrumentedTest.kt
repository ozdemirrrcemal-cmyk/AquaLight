package com.aqua.aqualight.data.care.reminder

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.catalog.CareTaskTypeCatalog
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareReminderLocaleInstrumentedTest {

    private val applicationContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun standardManualReminderRelocalizesTurkishEnglishTurkish() {
        val tank = tank(name = "User Aquarium")
        val task = task(
            type = CareTaskType.WATER_CHANGE,
            source = CareTaskSource.MANUAL,
            title = "Eski kayıtlı başlık",
            description = "Eski kayıtlı açıklama",
            waterChangePercent = 20
        )

        val firstTurkish = resolveIn("tr", task, tank)
        val english = resolveIn("en", task, tank)
        val secondTurkish = resolveIn("tr", task, tank)

        assertEquals(firstTurkish, secondTurkish)
        assertNotEquals(firstTurkish.title, english.title)
        assertNotEquals(firstTurkish.message, english.message)
        assertEquals(expectedStandardTitle("tr", task), secondTurkish.title)
        assertEquals(expectedStandardMessage("tr", task, tank), secondTurkish.message)
        assertTrue(english.message.contains(tank.name))
        assertTrue(secondTurkish.message.contains(tank.name))
    }

    @Test
    fun automaticReminderRelocalizesFromSemanticRuleIdentity() {
        val tank = tank(name = "Semantic Tank")
        val task = task(
            type = CareTaskType.DEVICE_CHECK,
            source = CareTaskSource.AUTOMATIC,
            title = "Persisted Turkish title",
            description = "Persisted Turkish description",
            generatedRuleKey = "smart_11_startup_day_1_general_check_1"
        )

        val turkish = resolveIn("tr", task, tank)
        val english = resolveIn("en", task, tank)
        val turkishAgain = resolveIn("tr", task, tank)

        assertEquals(turkish, turkishAgain)
        assertNotEquals(turkish.title, english.title)
        assertNotEquals(turkish.message, english.message)
        assertEquals(
            contextFor("tr").getString(
                R.string.maintenance_smart_rule_initial_setup_check_title
            ),
            turkishAgain.title
        )
        assertTrue(english.message.contains(tank.name))
        assertTrue(turkishAgain.message.contains(tank.name))
    }

    @Test
    fun customTitleNoteAndAquariumNameRemainUserOwnedAcrossLocales() {
        val tank = tank(name = "Kullanıcı Akvaryumu")
        val task = task(
            type = CareTaskType.CUSTOM,
            source = CareTaskSource.MANUAL,
            title = "Benim özel görevim",
            description = "Persisted application description",
            note = "Test ediyorum"
        )

        val turkish = resolveIn("tr", task, tank)
        val english = resolveIn("en", task, tank)

        assertEquals(task.title, turkish.title)
        assertEquals(task.title, english.title)
        assertTrue(turkish.message.contains(task.note))
        assertTrue(english.message.contains(task.note))
        assertTrue(turkish.message.contains(tank.name))
        assertTrue(english.message.contains(tank.name))
    }

    private fun resolveIn(
        language: String,
        task: CareTask,
        tank: SavedAquariumTank
    ): CareReminderText {
        val localizedContext = contextFor(language)
        return CareReminderTextResolver(
            context = applicationContext,
            localizedContextProvider = { localizedContext }
        ).resolve(task, tank)
    }

    private fun expectedStandardTitle(language: String, task: CareTask): String {
        val context = contextFor(language)
        val definition = CareTaskTypeCatalog.get(task.type)
        val typeTitle = definition.title(context)
        val percent = requireNotNull(task.waterChangePercent)
        return context.getString(
            R.string.maintenance_task_title_with_percent,
            typeTitle,
            percent
        )
    }

    private fun expectedStandardMessage(
        language: String,
        task: CareTask,
        tank: SavedAquariumTank
    ): String {
        val context = contextFor(language)
        val description = CareTaskTypeCatalog.get(task.type).defaultDescription(context)
        return context.getString(
            R.string.maintenance_notification_message_with_aquarium,
            tank.name,
            description
        )
    }

    private fun contextFor(language: String): Context {
        val locale = Locale.forLanguageTag(language)
        val configuration = Configuration(applicationContext.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return applicationContext.createConfigurationContext(configuration)
    }

    private fun task(
        type: CareTaskType,
        source: CareTaskSource,
        title: String,
        description: String,
        note: String = "",
        waterChangePercent: Int? = null,
        generatedRuleKey: String = ""
    ): CareTask = CareTask(
        id = 7L,
        ownerUid = "owner-a",
        tankId = 11L,
        title = title,
        description = description,
        type = type,
        source = source,
        status = CareTaskStatus.PENDING,
        dueAtMillis = System.currentTimeMillis(),
        completedAtMillis = null,
        repeatEnabled = false,
        repeatIntervalDays = 1,
        reminderEnabled = true,
        missedReminderEnabled = false,
        missedReminderDays = 1,
        waterChangePercent = waterChangePercent,
        note = note,
        generatedRuleKey = generatedRuleKey,
        createdAtMillis = 1L,
        updatedAtMillis = 1L
    )

    private fun tank(name: String): SavedAquariumTank = SavedAquariumTank(
        id = 11L,
        ownerUid = "owner-a",
        name = name,
        description = "",
        photoUri = null,
        setupDateEpochDay = LocalDate.now().toEpochDay(),
        widthCm = 60,
        lengthCm = 40,
        heightCm = 40,
        sizeUnit = "cm",
        volumeUnit = "L",
        tankType = "Freshwater",
        tankStyle = "Nature",
        createdAtMillis = 1L,
        smartCareEnabled = true,
        careRemindersEnabled = true,
        plants = emptyList(),
        materials = emptyList(),
        livestock = emptyList()
    )
}
