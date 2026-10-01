package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TankHealthMetricPresentationContractTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun metricCardsKeepTheirSizeWhileShowingLocalizedNamesAndFormulas() {
        val layout = file("app/src/main/res/layout/item_tank_health_metric.xml")
        val turkish = file("app/src/main/res/values-tr/tank_health_analysis_strings.xml")
        val symbols = file("app/src/main/res/values/tank_health_analysis_strings.xml")

        assertTrue(layout.contains("android:layout_height=\"@dimen/aqua_size_72\""))
        assertTrue(layout.contains("android:maxLines=\"2\""))
        assertTrue(layout.contains("app:autoSizeTextType=\"uniform\""))
        assertTrue(layout.contains("aqua_text_size_health_metric_label_min"))

        assertLocalizedString(turkish, "tank_health_test_nitrate", "Nitrat")
        assertLocalizedString(turkish, "tank_health_test_nitrite", "Nitrit")
        assertLocalizedString(turkish, "tank_health_test_ammonia_ammonium", "Amonyak / Amonyum")
        assertLocalizedString(turkish, "tank_health_test_general_hardness", "Genel Sertlik")
        assertLocalizedString(turkish, "tank_health_test_carbonate_hardness", "Karbonat Sertliği")
        assertLocalizedString(turkish, "tank_health_test_phosphate", "Fosfat")
        assertSymbol(symbols, "tank_health_test_symbol_nitrate", "NO₃⁻")
        assertSymbol(symbols, "tank_health_test_symbol_nitrite", "NO₂⁻")
        assertSymbol(symbols, "tank_health_test_symbol_ammonia_ammonium", "NH₃ / NH₄⁺")
        assertSymbol(symbols, "tank_health_test_symbol_phosphate", "PO₄³⁻")

        val fragment = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "TankHealthFragment.kt"
        )
        val adapter = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "TankHealthContentAdapter.kt"
        )
        assertTrue(fragment.contains("TankHealthWaterMetricUiCatalog.models("))
        assertTrue(fragment.contains("TankHealthWaterMetricId.Temperature"))
        assertTrue(fragment.contains("WaterAnalysisPresentation.temperatureValueText("))
        assertTrue(fragment.contains("TankHealthWaterMetricAssessment.summarize("))
        assertTrue(fragment.contains("actionTankHealthFragmentToTankHealthMetricDetailFragment"))
        assertTrue(adapter.contains("TankHealthWaterMetricAssessment.statusColorRes("))
        assertFalse(fragment.contains("R.string.tank_health_analysis_recorded"))
        assertTrue(fragment.contains("AquariumTankViewModel"))
        assertTrue(adapter.contains("submitWaterMetrics("))
        assertTrue(adapter.contains("buildItems(currentMetrics, header, maintenance, system)"))
        assertTrue(adapter.contains("currentMeasuredAtMillis, waterReadStatus, currentAssessmentSummary"))
        assertTrue(adapter.contains("TankHealthContentItem.MaintenanceSection(maintenance)"))
        assertTrue(adapter.contains("TankHealthContentItem.SystemSection(system)"))
        assertTrue(adapter.contains("R.string.tank_health_last_analysis_at"))
        assertTrue(adapter.contains("context.getString(item.labelRes)"))
        assertTrue(adapter.contains("context.getString(symbolRes)"))
    }

    @Test
    fun historyAndMetricDetailUseStructuredFrozenAssessmentInsteadOfRawParagraphDump() {
        val historyDetail = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "TankHealthAnalysisDetailFragment.kt"
        )
        val metricDetail = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "TankHealthMetricDetailFragment.kt"
        )
        val metricPresentation = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "TankHealthWaterMetricDetailPresentation.kt"
        )

        assertFalse(historyDetail.contains("WaterAssessmentPresentation.detail("))
        assertTrue(historyDetail.contains("WaterAssessmentPresentation.summary("))
        assertTrue(historyDetail.contains("tvCompatibilityStatus"))
        assertTrue(
            historyDetail.contains(
                "actionTankHealthAnalysisDetailFragmentToTankHealthMetricDetailFragment"
            )
        )
        assertTrue(metricDetail.contains("TankHealthWaterMetricDetailPresentation.findings("))
        assertTrue(metricDetail.contains("tank_health_metric_detail_frozen_context"))
        assertTrue(metricPresentation.contains("record.assessment"))
        assertFalse(metricPresentation.contains("WaterQualityAssessmentEngine.assess("))
    }

    private fun file(relativePath: String): String =
        File(repositoryRoot, relativePath).readText()

    private fun assertLocalizedString(xml: String, name: String, value: String) {
        assertTrue(xml.contains("<string name=\"$name\">$value</string>"))
    }

    private fun assertSymbol(xml: String, name: String, value: String) {
        assertTrue(xml.contains("<string name=\"$name\" translatable=\"false\">$value</string>"))
    }

    private fun locateRepositoryRoot(): File {
        var candidate: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (candidate != null) {
            if (File(candidate, "app/src/main").isDirectory) return candidate
            candidate = candidate.parentFile
        }
        error("Cannot locate AquaLight repository root from user.dir.")
    }
}
