package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import java.io.File
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

        assertTrue(turkish.contains("<string name=\"tank_health_test_nitrate\">Nitrat</string>"))
        assertTrue(turkish.contains("<string name=\"tank_health_test_nitrite\">Nitrit</string>"))
        assertTrue(turkish.contains("<string name=\"tank_health_test_ammonia_ammonium\">Amonyak / Amonyum</string>"))
        assertTrue(turkish.contains("<string name=\"tank_health_test_general_hardness\">Genel Sertlik</string>"))
        assertTrue(turkish.contains("<string name=\"tank_health_test_carbonate_hardness\">Karbonat Sertliği</string>"))
        assertTrue(turkish.contains("<string name=\"tank_health_test_phosphate\">Fosfat</string>"))
        assertTrue(symbols.contains("<string name=\"tank_health_test_symbol_nitrate\" translatable=\"false\">NO₃⁻</string>"))
        assertTrue(symbols.contains("<string name=\"tank_health_test_symbol_nitrite\" translatable=\"false\">NO₂⁻</string>"))
        assertTrue(symbols.contains("<string name=\"tank_health_test_symbol_ammonia_ammonium\" translatable=\"false\">NH₃ / NH₄⁺</string>"))
        assertTrue(symbols.contains("<string name=\"tank_health_test_symbol_phosphate\" translatable=\"false\">PO₄³⁻</string>"))

        val fragment = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "TankHealthFragment.kt"
        )
        val adapter = file(
            "app/src/main/java/com/aqua/aqualight/ui/tabs/aquarium/detail/health/" +
                "TankHealthContentAdapter.kt"
        )
        assertTrue(fragment.contains("TankHealthWaterMetricUiCatalog.models("))
        assertTrue(fragment.contains("AquariumTankViewModel"))
        assertTrue(adapter.contains("submitWaterMetrics("))
        assertTrue(adapter.contains("buildItems(metrics, measuredAtMillis)"))
        assertTrue(adapter.contains("R.string.tank_health_last_analysis_at"))
        assertTrue(adapter.contains("context.getString(item.labelRes)"))
        assertTrue(adapter.contains("context.getString(symbolRes)"))
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
