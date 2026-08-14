package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingChannelCardArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `channel cards are materialized only from validated central catalog slots`() {
        val viewModel = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DeviceDosingRootViewModel.kt"
        )

        assertTrue(viewModel.contains("catalogState == DeviceRootCatalogState.VALID"))
        assertTrue(viewModel.contains("channelSlots.dosingChannels"))
        assertTrue(viewModel.contains("slot.toInitialDosingChannelCardUiState()"))
        assertFalse(viewModel.contains("Regex("))
        assertFalse(viewModel.contains("containsMatchIn"))
        assertFalse(viewModel.contains("modelLabel ="))
    }

    @Test
    fun `final root uses stable slot ids and shared central card style`() {
        val rootScreen = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingCatalogScreen.kt"
        )
        val card = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCard.kt"
        )
        val centralStyle = source(
            "app/src/main/java/com/aqua/aqualight/ui/common/devicecard/" +
                "AquaDeviceCardComposeStyle.kt"
        )

        assertTrue(rootScreen.contains("key = DosingChannelCardUiState::slotId"))
        assertTrue(rootScreen.contains("exactDosingPumpCountOrNull"))
        assertTrue(card.contains("AquaDeviceCardSurface"))
        assertTrue(centralStyle.contains("R.color.aqua_card_device_surface"))
        assertTrue(centralStyle.contains("R.color.aqua_card_device_outline"))
        assertTrue(centralStyle.contains("R.color.aqua_card_text_primary"))
        assertTrue(centralStyle.contains("R.color.aqua_card_text_secondary"))
    }

    @Test
    fun `configured card summary is daily dose and selected days only`() {
        val card = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCard.kt"
        )
        val models = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCardModels.kt"
        )

        assertTrue(card.contains("device_dosing_channel_daily_dose_format"))
        assertTrue(card.contains("state.dailyDoseMl"))
        assertTrue(card.contains("scheduleDays.summaryLabel()"))
        assertFalse(card.contains("CALIBRATION"))
        assertFalse(card.contains("DosingSetupUiState"))
        assertFalse(models.contains("DosingCalibrationUiState"))
        assertFalse(models.contains("DosingSetupUiState"))
        assertFalse(models.contains("SETUP_REQUIRED"))
    }

    @Test
    fun `unconfigured card uses an empty state without legacy progress dependency`() {
        val card = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCard.kt"
        )

        assertTrue(card.contains("state.visualState == DosingChannelVisualState.NOT_CONFIGURED"))
        assertTrue(card.contains("DosingChannelEmptyState("))
        assertTrue(card.contains("device_dosing_channel_empty_title"))
        assertTrue(card.contains("device_dosing_channel_empty_description"))
        assertFalse(card.contains("DosingDoseProgressBar("))
        assertFalse(card.contains("doseProgress"))
        assertFalse(card.contains("0.00 ml"))
    }

    @Test
    fun `empty state strings are owned only by dosing string resources`() {
        val defaultDosingStrings = source("app/src/main/res/values/device_dosing_strings.xml")
        val turkishDosingStrings = source("app/src/main/res/values-tr/device_dosing_strings.xml")

        EMPTY_STATE_STRING_NAMES.forEach { name ->
            val declaration = "name=\"$name\""
            assertTrue(defaultDosingStrings.contains(declaration))
            assertTrue(turkishDosingStrings.contains(declaration))
        }

        val misplacedFiles = File(repositoryRoot, "app/src/main/res")
            .walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "xml" &&
                    file.name != DOSING_STRINGS_FILE_NAME
            }
            .filter { file ->
                val content = file.readText()
                EMPTY_STATE_STRING_NAMES.any { name -> content.contains("name=\"$name\"") }
            }
            .map(File::getPath)
            .toList()

        assertTrue(
            "Dosing empty-state strings must not leak into other resource files: $misplacedFiles",
            misplacedFiles.isEmpty()
        )
    }

    @Test
    fun `legacy dose progress implementation is removed before canonical replacement`() {
        val models = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCardModels.kt"
        )
        val card = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCard.kt"
        )
        val legacyProgressBar = File(repositoryRoot, DOSING_SOURCE_ROOT + "DosingDoseProgressBar.kt")
        val legacyProgressDrawing = File(
            repositoryRoot,
            DOSING_SOURCE_ROOT + "DosingDoseProgressDrawing.kt"
        )

        assertFalse(legacyProgressBar.exists())
        assertFalse(legacyProgressDrawing.exists())
        assertFalse(models.contains("DosingDoseProgressUiState"))
        assertFalse(models.contains("DosingDoseProgressVisualState"))
        assertFalse(models.contains("doseProgress"))
        assertFalse(models.contains("deliveredTodayMl"))
        assertFalse(models.contains("doseMilestonesMl"))
        assertFalse(card.contains("DosingDoseProgressBar"))
        assertFalse(card.contains("progressFraction"))
    }

    @Test
    fun `presentation does not couple channel cards to firmware runtime models`() {
        val cardModels = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCardModels.kt"
        )
        val card = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCard.kt"
        )

        FORBIDDEN_RUNTIME_TYPES.forEach { token ->
            assertFalse(cardModels.contains(token))
            assertFalse(card.contains(token))
        }
    }

    @Test
    fun `dose pro compose implementation contains no suppression annotations`() {
        SUPPRESSION_FREE_SOURCE_FILES.forEach { relativePath ->
            val content = source(relativePath)
            FORBIDDEN_SUPPRESSION_TOKENS.forEach { token ->
                assertFalse(
                    "$relativePath must not suppress static analysis with $token",
                    content.contains(token)
                )
            }
        }
    }

    private fun source(relativePath: String): String = File(repositoryRoot, relativePath).readText()

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }

    private companion object {
        const val DOSING_SOURCE_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/"
        const val DEVICE_CARD_SOURCE_ROOT =
            "app/src/main/java/com/aqua/aqualight/ui/common/devicecard/"
        const val DOSING_STRINGS_FILE_NAME = "device_dosing_strings.xml"

        val EMPTY_STATE_STRING_NAMES = listOf(
            "device_dosing_channel_empty_title",
            "device_dosing_channel_empty_description"
        )
        val FORBIDDEN_RUNTIME_TYPES = listOf(
            "DeviceDosingChannelStatus",
            "DeviceDosingStatus",
            "DeviceDosingScheduleStatus",
            "DeviceDosingRuntimeCapabilities",
            "AqlCommercialDeviceCatalog"
        )
        val SUPPRESSION_FREE_SOURCE_FILES = listOf(
            DEVICE_CARD_SOURCE_ROOT + "AquaDeviceCardComposeStyle.kt",
            DOSING_SOURCE_ROOT + "DeviceDosingRootFragment.kt",
            DOSING_SOURCE_ROOT + "DeviceDosingRootViewModel.kt",
            DOSING_SOURCE_ROOT + "DosingCatalogScreen.kt",
            DOSING_SOURCE_ROOT + "DosingChannelCard.kt",
            DOSING_SOURCE_ROOT + "DosingChannelCardModels.kt",
            DOSING_SOURCE_ROOT + "DosingChannelGlyph.kt",
            DOSING_SOURCE_ROOT + "DosingPumpDeviceCompose.kt",
            DOSING_SOURCE_ROOT + "DosingPumpIndicatorDrawing.kt",
            DOSING_SOURCE_ROOT + "DosingPumpPalette.kt",
            DOSING_SOURCE_ROOT + "channel/common/DeviceDosingChannelDestinationFragment.kt",
            DOSING_SOURCE_ROOT +
                "channel/calibration/DeviceDosingChannelCalibrationFragment.kt",
            DOSING_SOURCE_ROOT + "channel/detail/DeviceDosingChannelDetailFragment.kt"
        )
        val FORBIDDEN_SUPPRESSION_TOKENS = listOf(
            "@file:Suppress(",
            "@Suppress(",
            "@SuppressLint("
        )
    }
}
