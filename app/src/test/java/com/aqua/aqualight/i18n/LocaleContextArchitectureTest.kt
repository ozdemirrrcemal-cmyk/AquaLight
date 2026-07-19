package com.aqua.aqualight.i18n

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleContextArchitectureTest {

    private val repositoryRoot: File = locateRepositoryRoot()

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
        }
    }

    @Test
    fun livestockDateUsesTheSharedLocaleFormatter() {
        val relativePath =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/" +
                "TankDetailLivestockFormFragment.kt"
        val source = File(repositoryRoot, relativePath).readText()

        assertTrue(source.contains("LocaleFormatter.formatDate("))
        assertTrue(source.contains("LocaleFormatter.formatInteger("))
        assertFalse(source.contains("Locale.getDefault()"))
        assertFalse(source.contains("SimpleDateFormat("))
    }

    @Test
    fun localeFormatterUsesTheOfficialAndroidXLanguageContextApi() {
        val source = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/i18n/LocaleFormatter.kt"
        ).readText()

        assertTrue(source.contains("ContextCompat.getContextForLanguage(context)"))
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
