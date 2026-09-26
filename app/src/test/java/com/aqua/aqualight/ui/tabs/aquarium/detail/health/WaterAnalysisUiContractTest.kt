package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterAnalysisUiContractTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun tankTypeSheetSeparatesEnvironmentFromProfileWithoutChangingSharedSheetHost() {
        val layout = file("app/src/main/res/layout/content_sheet_tank_type.xml")
        val editor = file(
            "app/src/main/java/com/aqua/aqualight/ui/common/bottomsheet/" +
                "TankSettingsEditorBottomSheet.kt"
        )

        assertTrue(layout.contains("optionEnvironmentFreshwater"))
        assertTrue(layout.contains("optionEnvironmentBrackish"))
        assertTrue(layout.contains("optionEnvironmentMarine"))
        assertTrue(layout.contains("optionProfile1"))
        assertTrue(editor.contains("AquariumTankTaxonomy.tankTypeCodesForEnvironment("))
        assertTrue(editor.contains("DialogSettingsBottomSheetBinding"))
    }

    @Test
    fun addAnalysisUsesProfileModelsAndHasNoFixedWaterValueFixtures() {
        val renderer = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "WaterAnalysisParameterRenderer.kt"
        )
        val waterLayout = file(
            "app/src/main/res/layout/item_tank_health_analysis_water_parameters.xml"
        )
        val values = file("app/src/main/res/values/tank_health_analysis_values.xml")
        val addTestLayout = file(
            "app/src/main/res/layout/item_tank_health_analysis_add_test.xml"
        )
        val parameterLayout = file(
            "app/src/main/res/layout/item_tank_health_analysis_parameter_input.xml"
        )

        assertTrue(renderer.contains("WaterTestProfileUiCatalog.recommendedIds("))
        assertTrue(renderer.contains("WaterTestPickerBottomSheet.show("))
        assertTrue(renderer.contains("ItemTankHealthAnalysisAddTestBinding.inflate("))
        assertTrue(waterLayout.contains("recommendedParametersContainer"))
        assertTrue(waterLayout.contains("additionalParametersContainer"))
        assertTrue(waterLayout.contains("waterParametersCard"))
        assertTrue(waterLayout.contains("additionalTestsCard"))
        assertTrue(addTestLayout.contains("btnAddTest"))
        assertTrue(addTestLayout.contains("aqua_size_110"))
        assertTrue(parameterLayout.contains("ivParameterIcon"))
        assertTrue(parameterLayout.contains("bg_water_test_symbol_chip"))
        assertTrue(parameterLayout.contains("aqua_size_110"))
        assertFalse(parameterLayout.contains("aqua_size_140"))
        assertFalse(values.contains("tank_health_analysis_input_"))
        assertFalse(waterLayout.contains("inputPh"))
        assertFalse(waterLayout.contains("inputNo3"))
    }

    @Test
    fun sensorUiCannotClaimAReadingBeforeAuthoritativeIntegration() {
        val controller = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "WaterAnalysisTemperatureUiController.kt"
        )
        val sensorLayout = file(
            "app/src/main/res/layout/item_tank_health_analysis_sensor_section.xml"
        )
        val strings = file("app/src/main/res/values/tank_health_analysis_strings.xml")

        assertTrue(
            controller.contains(
                "sensorUiState: TemperatureSensorUiState = " +
                    "TemperatureSensorUiState.Unavailable"
            )
        )
        assertTrue(controller.contains("TemperatureSource.MANUAL"))
        assertFalse(sensorLayout.contains("tank_health_analysis_temperature_value"))
        assertFalse(sensorLayout.contains("tank_health_analysis_device_name"))
        assertFalse(strings.contains("Cooling Mini v2"))
    }

    @Test
    fun returningFromHistoryRendersDynamicWaterCardsAgain() {
        val fragment = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "TankHealthAnalysisAddFragment.kt"
        )
        val screen = file(
            "app/src/main/res/layout/fragment_tank_health_analysis_add.xml"
        )

        assertTrue(fragment.contains("if (nextProfile == tankProfile)"))
        assertTrue(fragment.contains("parameterRenderer?.render(nextProfile)"))
        assertTrue(fragment.contains("return@observe"))
        assertTrue(screen.contains("android:id=\"@+id/sensorSection\""))
        assertTrue(screen.contains("android:id=\"@+id/waterParametersSection\""))
        assertTrue(screen.contains("android:layout_marginTop=\"@dimen/aqua_size_12\""))
    }

    @Test
    fun processRecreationHooksPreserveUserInputSelections() {
        val fragment = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "TankHealthAnalysisAddFragment.kt"
        )
        val measurementController = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "WaterAnalysisMeasurementTimeController.kt"
        )
        val temperatureController = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "WaterAnalysisTemperatureUiController.kt"
        )

        assertTrue(fragment.contains("override fun onSaveInstanceState(outState: Bundle)"))
        assertTrue(fragment.contains("measurementTimeController?.saveState(outState)"))
        assertTrue(fragment.contains("temperatureUiController?.saveState(outState)"))
        assertTrue(fragment.contains("STATE_ADDITIONAL_PARAMETER_IDS"))
        assertTrue(fragment.contains("STATE_PARAMETER_VALUE_IDS"))
        assertTrue(fragment.contains("STATE_PARAMETER_VALUES"))
        assertTrue(measurementController.contains("STATE_MEASUREMENT_TIME_MILLIS"))
        assertTrue(temperatureController.contains("STATE_TEMPERATURE_SOURCE"))
        assertTrue(temperatureController.contains("STATE_MANUAL_TEMPERATURE"))
    }

    @Test
    fun waterAnalysisImplementationIsSuppressionFreeAndHasSingleControllerPath() {
        val activeFiles = listOf(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/common/" +
                "AquariumTankTaxonomyText.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "TankHealthAnalysisAddFragment.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "WaterAnalysisMeasurementTimeController.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "WaterAnalysisParameterRenderer.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "WaterAnalysisTemperatureUiController.kt"
        )
        val retiredFiles = listOf(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "WaterAnalysisDraftUiState.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "WaterAnalysisParameterController.kt",
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "WaterAnalysisSensorController.kt"
        )

        activeFiles.forEach { relativePath ->
            assertFalse(
                "$relativePath must resolve static-analysis findings without suppression.",
                file(relativePath).contains("@Suppress")
            )
        }
        retiredFiles.forEach { relativePath ->
            assertFalse(
                "$relativePath is an obsolete parallel implementation.",
                File(repositoryRoot, relativePath).exists()
            )
        }
    }

    private fun file(relativePath: String): String =
        File(repositoryRoot, relativePath).readText()

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
