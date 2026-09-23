package com.aqua.aqualight.ui.tabs.aquarium.detail

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TankCareProfileLivestockNavigationArchitectureTest {

    private val repositoryRoot: File = locateRepositoryRoot()

    @Test
    fun careProfileLivestockActionUsesTheCatalogPickerFlow() {
        val detailSource = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/" +
                "TankDetailFragment.kt"
        )

        assertTrue(detailSource.contains("CARE_PROFILE_ACTION_LIVESTOCK"))
        assertTrue(detailSource.contains("openLivestockPickerScreen()"))
        assertTrue(
            detailSource.contains(
                "actionTankDetailFragmentToTankLivestockPickerFragment"
            )
        )
        assertFalse(detailSource.contains("openLivestockFormIfNeeded"))
        assertFalse(detailSource.contains("openLivestockFormScreen"))
        assertFalse(
            detailSource.contains(
                "actionTankDetailFragmentToTankDetailLivestockFormFragment"
            )
        )
    }

    @Test
    fun tankLifeAddActionsAndPickerKeepTheSharedCatalogFlow() {
        val lifeSource = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/" +
                "TankDetailLifeFragment.kt"
        )
        val pickerSource = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/livestock/" +
                "TankLivestockPickerFragment.kt"
        )
        val navigation = source("app/src/main/res/navigation/nav_aquarium.xml")

        assertTrue(lifeSource.contains("binding.btnAddLife.setOnClickListener"))
        assertTrue(lifeSource.contains("binding.btnEmptyAddLife.setOnClickListener"))
        assertTrue(
            lifeSource.contains(
                "actionTankDetailFragmentToTankLivestockPickerFragment"
            )
        )
        assertTrue(
            pickerSource.contains(
                "actionTankLivestockPickerFragmentToTankDetailLivestockFormFragment"
            )
        )
        assertTrue(pickerSource.contains("openedFromPicker = true"))
        assertTrue(
            navigation.contains(
                "action_tankDetailFragment_to_tankLivestockPickerFragment"
            )
        )
        assertTrue(
            navigation.contains(
                "action_tankLivestockPickerFragment_to_tankDetailLivestockFormFragment"
            )
        )
    }

    private fun source(relativePath: String): String =
        File(repositoryRoot, relativePath).readText()

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
