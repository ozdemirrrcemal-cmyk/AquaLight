package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingChannelCardArchitectureTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun `channel cards are materialized only from validated central catalog slots`() {
        val viewModel = source(DOSING_SOURCE_ROOT + "DeviceDosingRootViewModel.kt")

        assertTrue(viewModel.contains("catalogState == DeviceRootCatalogState.VALID"))
        assertTrue(viewModel.contains("channelSlots.dosingChannels"))
        assertTrue(viewModel.contains("slot.toInitialDosingChannelCardUiState()"))
        assertFalse(viewModel.contains("Regex("))
        assertFalse(viewModel.contains("containsMatchIn"))
    }

    @Test
    fun `root card keeps central surface and stable slot identity`() {
        val rootScreen = source(DOSING_SOURCE_ROOT + "DosingCatalogScreen.kt")
        val card = source(CARD_SOURCE_ROOT + "DosingChannelCard.kt")
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
    }

    @Test
    fun `healthy configured state has no ready pill and only exceptional states show status`() {
        val models = source(CARD_SOURCE_ROOT + "DosingChannelCardModels.kt")
        val header = source(CARD_SOURCE_ROOT + "DosingChannelCardHeader.kt")

        assertTrue(models.contains("CONFIGURED(R.string.device_dosing_channel_status_configured, false)"))
        assertTrue(models.contains("DOSING(R.string.device_dosing_channel_status_dosing, false)"))
        assertTrue(
            models.contains(
                "AUTOMATIC_DOSING_OFF(R.string.device_dosing_channel_automatic_off, false)"
            )
        )
        assertTrue(header.contains("if (state.visualState.showsStatusPill)"))
        assertFalse(models.contains("READY("))
        assertFalse(models.contains("SCHEDULED("))
    }

    @Test
    fun `calibration and missing program use closed content states`() {
        val card = source(CARD_SOURCE_ROOT + "DosingChannelCard.kt")
        val mapper = source(CARD_SOURCE_ROOT + "DosingChannelCardMapper.kt")

        assertTrue(card.contains("DosingChannelVisualState.NOT_CONFIGURED"))
        assertTrue(card.contains("DosingChannelVisualState.PROGRAM_NOT_CONFIGURED"))
        assertTrue(card.contains("device_dosing_channel_calibration_required"))
        assertTrue(card.contains("device_dosing_channel_program_empty_title"))
        assertTrue(mapper.contains("!calibrated -> DosingChannelVisualState.NOT_CONFIGURED"))
        assertTrue(mapper.contains("program == null -> DosingChannelVisualState.PROGRAM_NOT_CONFIGURED"))
        assertFalse(card.contains("0.00 ml"))
    }

    @Test
    fun `each firmware program mode owns a distinct progress renderer`() {
        val progress = source(CARD_SOURCE_ROOT + "DosingProgramProgress.kt")
        val modes = source(CARD_SOURCE_ROOT + "DosingProgramProgressModes.kt")
        val rail = source(CARD_SOURCE_ROOT + "DosingDoseRail.kt")

        assertTrue(progress.contains("DosingSingleProgramProgress"))
        assertTrue(progress.contains("DosingHourlyProgramProgress"))
        assertTrue(progress.contains("DosingCustomProgramProgress"))
        assertTrue(progress.contains("DosingTimerProgramProgress"))
        assertTrue(modes.contains("DosingDoseRail"))
        assertTrue(rail.contains("occurrence.startFraction"))
        assertTrue(rail.contains("occurrence.endFraction"))
        assertTrue(modes.contains("state.customGroupBreaks()"))
        assertTrue(modes.contains("state.hourlyGroupBreaks()"))
        assertTrue(modes.contains("gap = CUSTOM_GROUP_GAP"))
        assertTrue(modes.contains("gap = HOURLY_GROUP_GAP"))
        assertTrue(rail.contains("DosingProgressMarkerScale"))
        assertTrue(rail.contains("DosingDeliveredValueTag"))
        assertTrue(rail.contains("PROGRESS_VALUE_TAG_AREA_HEIGHT"))
        assertFalse(rail.contains("occurrence.timeFraction"))
        assertFalse(rail.contains("TIMER_NODE_RADIUS"))
        assertFalse(progress.contains("DosingDoseProgressBar"))
    }

    @Test
    fun `manual usage is separate and reservoir visibility follows firmware tracking`() {
        val progressMapper = source(CARD_SOURCE_ROOT + "DosingChannelCardProgressMapper.kt")
        val reservoirMapper = source(CARD_SOURCE_ROOT + "DosingChannelCardReservoirMapper.kt")
        val progress = source(CARD_SOURCE_ROOT + "DosingProgramProgress.kt")
        val summary = source(CARD_SOURCE_ROOT + "DosingChannelCardSummary.kt")

        assertTrue(progressMapper.contains("usageToday.manualDeliveredMicroliters"))
        assertTrue(progressMapper.contains("withDoseFractions()"))
        assertTrue(progressMapper.contains("toProgressMarkers"))
        assertTrue(progress.contains("state.manualDeliveredTodayMl > 0.0"))
        assertTrue(progress.contains("DosingManualDosePill"))
        assertTrue(progress.contains("MANUAL_PILL_HEIGHT = PROGRESS_RAIL_HEIGHT"))
        assertTrue(progress.contains("padding(top = PROGRESS_VALUE_TAG_AREA_HEIGHT)"))
        assertFalse(progress.contains("device_dosing_channel_manual_label"))
        assertTrue(reservoirMapper.contains("if (!reservoir.trackingEnabled) return null"))
        assertTrue(summary.contains("state.reservoir?.let"))
        assertFalse(progress.contains("scheduledDeliveredTodayMl +"))
    }

    @Test
    fun `automatic off replaces program summary instead of showing a header pill`() {
        val models = source(CARD_SOURCE_ROOT + "DosingChannelCardModels.kt")
        val summary = source(CARD_SOURCE_ROOT + "DosingChannelCardSummary.kt")

        assertTrue(
            models.contains(
                "AUTOMATIC_DOSING_OFF(R.string.device_dosing_channel_automatic_off, false)"
            )
        )
        assertTrue(
            summary.contains(
                "visualState == DosingChannelVisualState.AUTOMATIC_DOSING_OFF"
            )
        )
        assertTrue(summary.contains("stringResource(visualState.labelRes)"))
    }

    @Test
    fun `presentation does not couple cards to raw dosing v1 models`() {
        cardSources().forEach { content ->
            FORBIDDEN_RUNTIME_TYPES.forEach { token -> assertFalse(content.contains(token)) }
            assertFalse(content.contains("com.aqua.aqualight.data.devices"))
            assertFalse(content.contains("DeviceDosingV1"))
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

    private fun cardSources(): List<String> = File(repositoryRoot, CARD_SOURCE_ROOT)
        .walkTopDown()
        .filter { file -> file.isFile && file.extension == "kt" }
        .map(File::readText)
        .toList()

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
        const val CARD_SOURCE_ROOT = DOSING_SOURCE_ROOT + "presentation/card/"

        val FORBIDDEN_RUNTIME_TYPES = listOf(
            "DeviceDosingChannelStatus",
            "DeviceDosingStatus",
            "DeviceDosingScheduleStatus",
            "DeviceDosingRuntimeCapabilities",
            "AqlCommercialDeviceCatalog"
        )
        val SUPPRESSION_FREE_SOURCE_FILES = listOf(
            DOSING_SOURCE_ROOT + "DeviceDosingRootFragment.kt",
            DOSING_SOURCE_ROOT + "DeviceDosingRootViewModel.kt",
            DOSING_SOURCE_ROOT + "DosingCatalogScreen.kt",
            CARD_SOURCE_ROOT + "DosingChannelCard.kt",
            CARD_SOURCE_ROOT + "DosingChannelCardHeader.kt",
            CARD_SOURCE_ROOT + "DosingChannelCardMapper.kt",
            CARD_SOURCE_ROOT + "DosingChannelCardModels.kt",
            CARD_SOURCE_ROOT + "DosingChannelCardSummary.kt",
            CARD_SOURCE_ROOT + "DosingChannelCardGlyphs.kt",
            CARD_SOURCE_ROOT + "DosingProgressGeometryMapper.kt",
            CARD_SOURCE_ROOT + "DosingProgramProgress.kt",
            CARD_SOURCE_ROOT + "DosingProgramProgressModes.kt",
            CARD_SOURCE_ROOT + "DosingDoseRail.kt",
            CARD_SOURCE_ROOT + "DosingReservoirProjection.kt",
            CARD_SOURCE_ROOT + "DosingReservoirSummary.kt",
            DOSING_SOURCE_ROOT + "DosingPumpDeviceCompose.kt",
            DOSING_SOURCE_ROOT + "DosingPumpIndicatorDrawing.kt",
            DOSING_SOURCE_ROOT + "DosingPumpPalette.kt"
        )
        val FORBIDDEN_SUPPRESSION_TOKENS = listOf(
            "@file:Suppress(",
            "@Suppress(",
            "@SuppressLint("
        )
    }
}
