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
        val controller = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/i18n/AppLanguageController.kt"
        ).readText()

        val attachIndex = source.indexOf("override fun attachBaseContext(base: Context)")
        val superAttachIndex = source.indexOf("super.attachBaseContext(base)")
        val cacheReadIndex = source.indexOf("StartupAppearanceCache.create(this)")
        val onCreateIndex = source.indexOf("override fun onCreate")

        assertTrue(attachIndex >= 0)
        assertTrue(superAttachIndex > attachIndex)
        assertTrue(cacheReadIndex > superAttachIndex)
        assertTrue(onCreateIndex > cacheReadIndex)
        assertTrue(source.contains("AppLanguageController.apply("))
        assertTrue(controller.contains("AppCompatDelegate.getApplicationLocales()"))
        assertTrue(controller.contains("AppCompatDelegate.setApplicationLocales("))
    }

    @Test
    fun settingsReportTheLocaleActuallyRenderingTheApplication() {
        val operations = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/data/user/DefaultUserSettingsOperations.kt"
        ).readText()
        val languageScreen = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/app/" +
                "LanguageSettingsFragment.kt"
        ).readText()

        assertTrue(operations.contains("AppLanguageController.current()"))
        assertTrue(operations.contains("AppLanguageController.apply(supportedCode)"))
        assertFalse(languageScreen.contains("AppCompatDelegate.setApplicationLocales("))
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
    fun frameworkPickersUseTheLiveActivityWindowContext() {
        listOf(
            "app/src/main/java/com/aqua/aqualight/ui/common/dialog/" +
                "AppDatePickerDialogFragment.kt",
            "app/src/main/java/com/aqua/aqualight/ui/common/dialog/" +
                "AppTimePickerDialogFragment.kt"
        ).forEach { relativePath ->
            val source = File(repositoryRoot, relativePath).readText()

            assertTrue(
                "$relativePath must obtain its window-owning Activity context.",
                source.contains("val hostActivity = requireActivity()")
            )
            assertTrue(
                "$relativePath must construct its picker from the live Activity context.",
                source.contains("DatePickerDialog(\n            hostActivity") ||
                    source.contains("TimePickerDialog(\n            hostActivity")
            )
            assertFalse(
                "$relativePath must not create a dialog from a tokenless configuration context.",
                source.contains("LocaleFormatter.localizedContext(") ||
                    source.contains("createConfigurationContext(")
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
    fun pickerBackedFormsUseTheCorrectSharedDateBoundary() {
        val livestockForm = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/" +
                "TankDetailLivestockFormFragment.kt"
        ).readText()
        val careTaskForm = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/" +
                "AddCareTaskFragment.kt"
        ).readText()

        assertTrue(livestockForm.contains("LocaleFormatter.formatDateEpochDay("))
        assertTrue(careTaskForm.contains("LocaleFormatter.formatDate("))

        listOf(livestockForm, careTaskForm).forEach { source ->
            assertFalse(source.contains("Locale.getDefault()"))
            assertFalse(source.contains("SimpleDateFormat("))
        }
    }

    @Test
    fun maintenanceDateAndTimeSurfacesUseTheSharedLocaleBoundary() {
        val taskDetail = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/TaskDetailFragment.kt"
        ).readText()
        val adapter = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/CareTaskAdapter.kt"
        ).readText()
        val viewModel = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/maintenance/MaintenanceViewModel.kt"
        ).readText()
        val resolver = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/platform/text/" +
                "AndroidMaintenanceTextResolver.kt"
        ).readText()

        assertTrue(taskDetail.contains("LocaleFormatter.formatDate(requireContext(), millis)"))
        assertTrue(taskDetail.contains("LocaleFormatter.formatTime(requireContext(), millis)"))
        assertTrue(taskDetail.contains("LocaleFormatter.formatDateTime(requireContext(), millis)"))
        assertFalse(taskDetail.contains("SimpleDateFormat("))
        assertFalse(taskDetail.contains("Locale.getDefault()"))
        assertFalse(taskDetail.contains("dd.MM.yyyy"))
        assertFalse(taskDetail.contains("HH:mm"))

        assertTrue(adapter.contains("LocaleFormatter.formatDate(context, millis)"))
        assertFalse(adapter.contains("SimpleDateFormat("))
        assertFalse(adapter.contains("Locale.getDefault()"))
        assertFalse(adapter.contains("dd.MM.yyyy"))

        assertTrue(viewModel.contains("textResolver.formatTime(millis)"))
        assertTrue(viewModel.contains("textResolver.localeChanges"))
        assertFalse(viewModel.contains("SimpleDateFormat("))
        assertFalse(viewModel.contains("Locale.getDefault()"))
        assertFalse(viewModel.contains("HH:mm"))

        assertTrue(resolver.contains("AppLanguageController.languageChanges"))
        assertTrue(resolver.contains("LocaleFormatter.localizedContext(appContext)"))
        assertTrue(resolver.contains("LocaleFormatter.formatTime(localizedContext(), timeMillis)"))
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
    fun localeFormatterUsesSupportedAppLocaleAndAndroidHourPreference() {
        val source = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/i18n/LocaleFormatter.kt"
        ).readText()

        assertTrue(source.contains("ContextCompat.getContextForLanguage(context)"))
        assertTrue(source.contains("createConfigurationContext(configuration)"))
        assertTrue(source.contains("resolveSupportedLocale(configuredLocale)"))
        assertTrue(source.contains("AndroidDateFormat.is24HourFormat(localizedContext)"))
        assertTrue(source.contains("AndroidDateFormat.getBestDateTimePattern(locale, skeleton)"))
        assertTrue(source.contains("if (is24Hour) \"Hm\" else \"hm\""))
        assertTrue(source.contains("if (is24Hour) \"yMMMdHm\" else \"yMMMdhm\""))
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
