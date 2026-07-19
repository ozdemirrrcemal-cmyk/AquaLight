package com.aqua.aqualight.i18n

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleContextArchitectureTest {

    private val repositoryRoot: File = locateRepositoryRoot()
    private val productionJava = File(repositoryRoot, "app/src/main/java")

    @Test
    fun applicationAppliesCachedLocaleBeforeActivityAndPickerCreation() {
        val source = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/app/AquaApp.kt"
        ).readText()

        val attachIndex = source.indexOf("override fun attachBaseContext(base: Context)")
        val superAttachIndex = source.indexOf("super.attachBaseContext(base)")
        val cacheReadIndex = source.indexOf("StartupAppearanceCache.create(this)")
        val onCreateIndex = source.indexOf("override fun onCreate")

        assertTrue(attachIndex >= 0)
        assertTrue(superAttachIndex > attachIndex)
        assertTrue(cacheReadIndex > superAttachIndex)
        assertTrue(onCreateIndex > cacheReadIndex)
        assertTrue(source.contains("AppCompatDelegate.setApplicationLocales("))
    }

    @Test
    fun startupAppearanceCacheSupportsPreApplicationBaseContext() {
        val source = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/data/user/StartupAppearanceCache.kt"
        ).readText()

        assertTrue(source.contains("context.applicationContext ?: context"))
        assertTrue(source.contains("context.getSharedPreferences("))
        assertFalse(source.contains("context.applicationContext.getSharedPreferences("))
    }

    @Test
    fun frameworkPickersUseThePerAppLanguageContext() {
        listOf(
            "app/src/main/java/com/aqua/aqualight/ui/common/dialog/" +
                "AppDatePickerDialogFragment.kt",
            "app/src/main/java/com/aqua/aqualight/ui/common/dialog/" +
                "AppTimePickerDialogFragment.kt"
        ).forEach { relativePath ->
            val source = File(repositoryRoot, relativePath).readText()

            assertTrue(
                "$relativePath must obtain the AndroidX per-app language context.",
                source.contains("LocaleFormatter.localizedContext(requireContext())")
            )
            assertFalse(
                "$relativePath must not construct a picker directly from requireContext().",
                source.contains("DatePickerDialog(\n            requireContext()") ||
                    source.contains("TimePickerDialog(\n            requireContext()")
            )
            assertFalse(
                "$relativePath must not read the process or device default locale.",
                source.contains("Locale.getDefault()")
            )
        }
    }

    @Test
    fun productionCodeCannotBypassTheSharedFrameworkPickers() {
        val datePickerOwners = kotlinSourcesContaining("DatePickerDialog(")
        val timePickerOwners = kotlinSourcesContaining("TimePickerDialog(")

        assertEquals(
            setOf(
                "app/src/main/java/com/aqua/aqualight/ui/common/dialog/" +
                    "AppDatePickerDialogFragment.kt"
            ),
            datePickerOwners
        )
        assertEquals(
            setOf(
                "app/src/main/java/com/aqua/aqualight/ui/common/dialog/" +
                    "AppTimePickerDialogFragment.kt"
            ),
            timePickerOwners
        )
    }

    @Test
    fun pickerBackedFormsUseTheSharedLocaleFormatter() {
        listOf(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/" +
                "TankDetailLivestockFormFragment.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/" +
                "AddCareTaskFragment.kt"
        ).forEach { relativePath ->
            val source = File(repositoryRoot, relativePath).readText()

            assertTrue(source.contains("LocaleFormatter.formatDate("))
            assertFalse(source.contains("Locale.getDefault()"))
            assertFalse(source.contains("SimpleDateFormat("))
        }
    }

    @Test
    fun customTankDatePickerUsesTheAppCompatApplicationLocale() {
        val policyPath =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/common/" +
                "AquariumDatePolicy.kt"
        val policy = File(repositoryRoot, policyPath).readText()
        val creationForm = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/create/steps/" +
                "TankInfoFragment.kt"
        ).readText()
        val settingsForm = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/settings/" +
                "TankSettingsBasicFragment.kt"
        ).readText()

        assertTrue(policy.contains("AppCompatDelegate.getApplicationLocales()"))
        assertFalse(policy.contains("Locale.getDefault()"))
        assertTrue(creationForm.contains("AquariumDatePolicy.setupDateLocale(requireContext())"))
        assertTrue(settingsForm.contains("locale = AquariumDatePolicy.setupDateLocale"))
    }

    @Test
    fun localeFormatterUsesTheOfficialAndroidXLanguageContextApiAndSafeFallback() {
        val source = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/i18n/LocaleFormatter.kt"
        ).readText()

        assertTrue(source.contains("ContextCompat.getContextForLanguage(context)"))
        assertTrue(source.contains("createConfigurationContext(configuration)"))
        assertTrue(source.contains("resolveSupportedLocale(configuredLocale)"))
    }

    private fun kotlinSourcesContaining(token: String): Set<String> {
        return productionJava.walkTopDown()
            .filter(File::isFile)
            .filter { file -> file.extension == "kt" }
            .filter { file -> file.readText().contains(token) }
            .mapTo(linkedSetOf()) { file ->
                file.relativeTo(repositoryRoot).invariantSeparatorsPath
            }
    }

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile

        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) {
                return candidate
            }
            candidate = candidate.parentFile
        }

        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
