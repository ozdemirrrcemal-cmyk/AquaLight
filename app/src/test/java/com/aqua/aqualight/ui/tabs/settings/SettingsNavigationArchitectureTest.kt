package com.aqua.aqualight.ui.tabs.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun settingsOwnsLegalAndAboutAsSeparateNavigationCenters() {
        val settingsLayout = source("app/src/main/res/layout/fragment_settings.xml")
        val settingsSource = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/SettingsFragment.kt"
        )
        val navigation = source("app/src/main/res/navigation/nav_settings.xml")

        assertInOrder(
            settingsLayout,
            "@+id/rowFeedback",
            "@+id/rowLegal",
            "@+id/rowAbout",
            "@+id/rowLogout"
        )
        assertTrue(settingsSource.contains("actionSettingsFragmentToLegalCenterFragment"))
        assertTrue(settingsSource.contains("actionSettingsFragmentToAboutAppFragment"))
        assertTrue(navigation.contains("@+id/legalCenterFragment"))
        assertTrue(navigation.contains("settings.legal.PrivacyFragment"))
        assertTrue(navigation.contains("settings.legal.TermsOfUseFragment"))
        assertTrue(navigation.contains("settings.legal.ThirdPartyLicensesFragment"))
        assertTrue(navigation.contains("action_legalCenterFragment_to_privacyFragment"))
        assertTrue(navigation.contains("action_legalCenterFragment_to_termsOfUseFragment"))
        assertTrue(navigation.contains("action_legalCenterFragment_to_thirdPartyLicensesFragment"))
    }

    @Test
    fun legalDocumentsAreNotDuplicatedInAppSettingsOrAbout() {
        val appSettingsLayout = source("app/src/main/res/layout/fragment_app_settings.xml")
        val appSettingsSource = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/app/AppSettingsFragment.kt"
        )
        val aboutLayout = source("app/src/main/res/layout/fragment_about_app.xml")
        val aboutSource = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/app/AboutAppFragment.kt"
        )
        val legalLayout = source("app/src/main/res/layout/fragment_legal_center.xml")

        assertFalse(appSettingsLayout.contains("@+id/cardAbout"))
        assertFalse(appSettingsSource.contains("actionAppSettingsFragmentToAboutAppFragment"))
        assertFalse(aboutLayout.contains("@+id/rowPrivacy"))
        assertFalse(aboutLayout.contains("@+id/rowTerms"))
        assertFalse(aboutLayout.contains("@+id/rowLicenses"))
        assertFalse(aboutSource.contains("actionAboutAppFragmentToOpenSourceLicensesFragment"))
        assertTrue(legalLayout.contains("@+id/rowPrivacyNotice"))
        assertTrue(legalLayout.contains("@+id/rowTermsOfUse"))
        assertTrue(legalLayout.contains("@+id/rowThirdPartyLicenses"))
    }

    @Test
    fun aboutFooterUsesTheCurrentYear() {
        val settingsSource = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/SettingsFragment.kt"
        )
        val aboutLayout = source("app/src/main/res/layout/fragment_about_app.xml")
        val aboutSource = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/settings/app/AboutAppFragment.kt"
        )
        val englishStrings = source("app/src/main/res/values/strings.xml")
        val turkishStrings = source("app/src/main/res/values-tr/strings.xml")

        assertFalse(aboutLayout.contains("© 2025"))
        assertTrue(settingsSource.contains("Calendar.getInstance().get(Calendar.YEAR)"))
        assertTrue(aboutSource.contains("Calendar.getInstance().get(Calendar.YEAR)"))
        assertFalse(englishStrings.contains("© 2025"))
        assertFalse(turkishStrings.contains("© 2025"))
        assertTrue(englishStrings.contains("name=\"about_app_footer_format\""))
        assertTrue(turkishStrings.contains("name=\"about_app_footer_format\""))
    }

    private fun assertInOrder(source: String, vararg tokens: String) {
        val positions = tokens.map(source::indexOf)
        assertTrue("Missing navigation row: $positions", positions.all { it >= 0 })
        assertTrue("Settings rows are out of order: $positions", positions.zipWithNext().all {
            (left, right) -> left < right
        })
    }

    private fun source(relativePath: String): String =
        File(repositoryRoot, relativePath).readText()

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
