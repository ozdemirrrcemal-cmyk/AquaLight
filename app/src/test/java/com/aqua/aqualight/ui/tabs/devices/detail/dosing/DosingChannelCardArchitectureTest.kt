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
    fun `configured card is firmware-ready without transport ownership`() {
        val card = source(DOSING_SOURCE_ROOT + "DosingChannelCard.kt")
        val models = source(DOSING_SOURCE_ROOT + "DosingChannelCardModels.kt")
        val progress = source(DOSING_SOURCE_ROOT + "DosingScheduledProgress.kt")

        assertTrue(models.contains("val dailyDoseMl: Double? = null"))
        assertTrue(models.contains("val programMode: DosingProgramModeUi? = null"))
        assertTrue(models.contains("val scheduledProgress: DosingScheduledProgressUiState? = null"))
        assertTrue(models.contains("val reservoir: DosingReservoirSummaryUiState? = null"))
        assertTrue(models.contains("val manualUsage: DosingManualUsageUiState? = null"))
        assertTrue(card.contains("DosingScheduledProgress("))
        assertTrue(progress.contains("DosingProgramModeUi.SINGLE"))
        assertTrue(progress.contains("DosingProgramModeUi.HOURLY_24"))
        assertTrue(progress.contains("DosingProgramModeUi.CUSTOM_PERIODS"))
        assertTrue(progress.contains("DosingProgramModeUi.TIMER"))
        assertFalse(card.contains("DeviceDosingStatus"))
        assertFalse(progress.contains("DeviceDosingStatus"))
    }

    @Test
    fun `scheduled progress and manual usage remain separate presentation siblings`() {
        val models = source(DOSING_SOURCE_ROOT + "DosingChannelCardModels.kt")
        val progress = source(DOSING_SOURCE_ROOT + "DosingScheduledProgress.kt")

        assertTrue(models.contains("data class DosingScheduledProgressUiState"))
        assertTrue(models.contains("data class DosingManualUsageUiState"))
        assertFalse(
            models.substringAfter("data class DosingScheduledProgressUiState")
                .substringBefore("enum class DosingReservoirLevelUiState")
                .contains("manual")
        )
        assertTrue(progress.contains("manualUsage = state.manualUsage").not())
        assertTrue(progress.contains("manualUsage: DosingManualUsageUiState?"))
        assertTrue(progress.contains("ManualDoseBadge("))
    }

    @Test
    fun `unconfigured card contains no fabricated dose or schedule value`() {
        val card = source(DOSING_SOURCE_ROOT + "DosingChannelCard.kt")
        val models = source(DOSING_SOURCE_ROOT + "DosingChannelCardModels.kt")

        assertTrue(card.contains("DosingChannelVisualState.NOT_CONFIGURED"))
        assertTrue(card.contains("DosingChannelEmptyState("))
        assertTrue(models.contains("val dailyDoseMl: Double? = null"))
        assertFalse(models.contains("val dailyDoseMl: Double = 0.0"))
        assertFalse(card.contains("0.00 ml"))
    }

    @Test
    fun `idle channel has no fake ready status and pump state follows the same presentation truth`() {
        val models = source(DOSING_SOURCE_ROOT + "DosingChannelCardModels.kt")
        val root = source(DOSING_SOURCE_ROOT + "DosingCatalogScreen.kt")

        assertTrue(models.contains("IDLE(null)"))
        assertFalse(models.contains("READY("))
        assertFalse(models.contains("SCHEDULED("))
        assertTrue(root.contains("DosingChannelVisualState.DOSING -> DosingPumpVisualState.RUNNING"))
        assertTrue(root.contains("DosingChannelVisualState.ERROR -> DosingPumpVisualState.ERROR"))
    }

    @Test
    fun `progress uses authoritative totals and occurrence statuses without local schedule reconstruction`() {
        val progress = source(DOSING_SOURCE_ROOT + "DosingScheduledProgress.kt")
        val models = source(DOSING_SOURCE_ROOT + "DosingChannelCardModels.kt")

        assertTrue(models.contains("val completionPercent: Double"))
        assertTrue(models.contains("val completedAmountMl: Double"))
        assertTrue(models.contains("val totalAmountMl: Double"))
        assertTrue(models.contains("PENDING"))
        assertTrue(models.contains("RUNNING"))
        assertTrue(models.contains("COMPLETED"))
        assertTrue(models.contains("SKIPPED"))
        assertTrue(models.contains("UNCERTAIN"))
        assertFalse(progress.contains("sumOf"))
        assertFalse(progress.contains("completedCount.toFloat() /"))
        assertFalse(progress.contains("remainingAmountMl ="))
    }

    @Test
    fun `legacy dose progress implementation stays removed`() {
        val legacyProgressBar = File(repositoryRoot, DOSING_SOURCE_ROOT + "DosingDoseProgressBar.kt")
        val legacyProgressDrawing = File(
            repositoryRoot,
            DOSING_SOURCE_ROOT + "DosingDoseProgressDrawing.kt"
        )

        assertFalse(legacyProgressBar.exists())
        assertFalse(legacyProgressDrawing.exists())
    }

    @Test
    fun `presentation does not couple channel cards to firmware runtime models`() {
        val files = listOf(
            source(DOSING_SOURCE_ROOT + "DosingChannelCardModels.kt"),
            source(DOSING_SOURCE_ROOT + "DosingChannelCard.kt"),
            source(DOSING_SOURCE_ROOT + "DosingScheduledProgress.kt")
        )

        FORBIDDEN_RUNTIME_TYPES.forEach { token ->
            files.forEach { content -> assertFalse(content.contains(token)) }
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
            DOSING_SOURCE_ROOT + "DosingScheduledProgress.kt",
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
