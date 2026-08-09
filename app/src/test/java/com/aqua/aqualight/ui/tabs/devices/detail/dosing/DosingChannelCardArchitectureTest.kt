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
    fun `main card summary is daily dose and selected days only`() {
        val card = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCard.kt"
        )
        val models = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCardModels.kt"
        )

        assertTrue(card.contains("device_dosing_channel_daily_dose_format"))
        assertTrue(card.contains("scheduleDays.summaryLabel()"))
        assertFalse(card.contains("CALIBRATION"))
        assertFalse(card.contains("DosingSetupUiState"))
        assertFalse(models.contains("DosingCalibrationUiState"))
        assertFalse(models.contains("DosingSetupUiState"))
        assertFalse(models.contains("SETUP_REQUIRED"))
    }

    @Test
    fun `dose progress is volume based and contains no time axis contract`() {
        val models = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingChannelCardModels.kt"
        )
        val progressBar = source(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingDoseProgressBar.kt"
        )
        val removedTimeline = File(
            repositoryRoot,
            "app/src/main/java/com/aqua/aqualight/ui/tabs/devices/detail/dosing/" +
                "DosingScheduleTimeline.kt"
        )

        assertTrue(models.contains("dailyDoseMl"))
        assertTrue(models.contains("deliveredTodayMl"))
        assertTrue(models.contains("doseMilestonesMl"))
        assertTrue(progressBar.contains("deliveredTodayMl / dailyDoseMl"))
        assertFalse(models.contains("dailyTargetMl"))
        assertFalse(models.contains("deliveredMl"))
        assertFalse(models.contains("doseCheckpointsMl"))
        assertFalse(models.contains("fractionOfDay"))
        assertFalse(models.contains("DosingTimeline"))
        assertFalse(progressBar.contains("24-hour"))
        assertFalse(progressBar.contains("TIMELINE"))
        assertFalse(removedTimeline.exists())
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
        val FORBIDDEN_RUNTIME_TYPES = listOf(
            "DeviceDosingChannelStatus",
            "DeviceDosingStatus",
            "DeviceDosingScheduleStatus",
            "DeviceDosingRuntimeCapabilities",
            "AqlCommercialDeviceCatalog"
        )
    }
}
