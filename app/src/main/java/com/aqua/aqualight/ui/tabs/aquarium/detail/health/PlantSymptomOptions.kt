package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.annotation.StringRes
import com.aqua.aqualight.R

/** Actual aquatic plant photographs are illustrations; no diagnosis is inferred from them. */
internal data class PlantSymptomOption(val key: String, @StringRes val labelRes: Int, val photoUrl: String?)

internal object PlantSymptomOptions {
    private const val ANUBIAS = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d5/" +
        "Anubias_barteri_var._nana_-_Botanischer_Garten_-_Heidelberg%2C_Germany_-_DSC01278.jpg/" +
        "330px-Anubias_barteri_var._nana_-_Botanischer_Garten_-_Heidelberg%2C_Germany_-_DSC01278.jpg"
    private const val CRYPTOCORYNE = "https://thumb.wikimedia.org/wikipedia/commons/thumb/b/bb/" +
        "Cryptocoryne_x_Willisii.jpg/250px-Cryptocoryne_x_Willisii.jpg"
    private const val ECHINODORUS = "https://thumb.wikimedia.org/wikipedia/commons/thumb/6/6d/" +
        "Echinodorus_uruguayensis_-_Botanischer_Garten_-_Heidelberg%2C_Germany_-_DSC01259.jpg/" +
        "330px-Echinodorus_uruguayensis_-_Botanischer_Garten_-_Heidelberg%2C_Germany_-_DSC01259.jpg"

    val items = listOf(
        PlantSymptomOption("healthy", R.string.plant_health_symptom_healthy, ANUBIAS),
        PlantSymptomOption("yellowing", R.string.plant_health_symptom_yellowing, ECHINODORUS),
        PlantSymptomOption("melting", R.string.plant_health_symptom_melting, CRYPTOCORYNE),
        PlantSymptomOption("damage", R.string.plant_health_symptom_damage, ANUBIAS),
        PlantSymptomOption("slow_growth", R.string.plant_health_symptom_slow_growth, CRYPTOCORYNE),
        PlantSymptomOption("brown_spots", R.string.plant_health_symptom_brown_spots, ECHINODORUS),
        PlantSymptomOption("algae", R.string.plant_health_symptom_algae, CRYPTOCORYNE),
        PlantSymptomOption("deformation", R.string.plant_health_symptom_deformation, ECHINODORUS),
        PlantSymptomOption("other", R.string.plant_health_symptom_other, null)
    )
}
