package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TankHealthMetricPresentationContractTest {

    private val repositoryRoot = locateRepositoryRoot()

    @Test
    fun metricCardsKeepTheirSizeWhileShowingLocalizedNamesAndFormulas() {
        val layout = file("app/src/main/res/layout/item_tank_health_metric.xml")
        val turkish = file("app/src/main/res/values-tr/tank_health_strings.xml")

        assertTrue(layout.contains("android:layout_height=\"@dimen/aqua_size_72\""))
        assertTrue(layout.contains("android:maxLines=\"2\""))
        assertTrue(layout.contains("app:autoSizeTextType=\"uniform\""))
        assertTrue(layout.contains("aqua_text_size_health_metric_label_min"))

        assertTrue(turkish.contains("Nitrat (NO₃⁻)"))
        assertTrue(turkish.contains("Nitrit (NO₂⁻)"))
        assertTrue(turkish.contains("Amonyak / Amonyum (NH₃/NH₄⁺)"))
        assertTrue(turkish.contains("Genel Sertlik (GH)"))
        assertTrue(turkish.contains("Karbonat Sertliği (KH)"))
        assertTrue(turkish.contains("Fosfat (PO₄³⁻)"))

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
        assertTrue(adapter.contains("buildItems(metrics)"))
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
