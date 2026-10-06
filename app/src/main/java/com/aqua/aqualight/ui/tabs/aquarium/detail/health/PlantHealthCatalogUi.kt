package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantCare
import com.aqua.aqualight.application.aquarium.AquariumPlantCatalogRecord
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.tabs.aquarium.catalog.plant.AquariumPlantCatalog

internal enum class PlantHealthCatalogRange {
    TEMPERATURE,
    PH,
    KH,
    GH
}

internal object PlantHealthCatalogUi {

    fun record(context: Context, catalogId: String): AquariumPlantCatalogRecord? =
        AquariumPlantCatalog.record(context, catalogId)

    fun growth(context: Context, care: AquariumPlantCare?): String =
        care?.growthRate.toLocalized(
            context,
            mapOf(
                "SLOW" to R.string.plant_health_growth_slow,
                "SLOW_MEDIUM" to R.string.plant_health_growth_slow_medium,
                "MEDIUM" to R.string.plant_health_growth_medium,
                "FAST" to R.string.plant_health_growth_fast
            )
        )

    fun difficulty(context: Context, care: AquariumPlantCare?): String =
        care?.difficulty.toLocalized(
            context,
            mapOf(
                "VERY_EASY" to R.string.plant_health_difficulty_very_easy,
                "EASY" to R.string.plant_health_difficulty_easy,
                "EASY_MEDIUM" to R.string.plant_health_difficulty_easy_medium,
                "MEDIUM" to R.string.plant_health_difficulty_medium,
                "HARD" to R.string.plant_health_difficulty_hard
            )
        )

    fun light(context: Context, care: AquariumPlantCare?): String =
        care?.lightRequirement.toLocalized(
            context,
            mapOf(
                "LOW" to R.string.plant_health_light_low,
                "LOW_MEDIUM" to R.string.plant_health_light_low_medium,
                "MEDIUM" to R.string.plant_health_light_medium,
                "MEDIUM_HIGH" to R.string.plant_health_light_medium_high,
                "LOW_HIGH" to R.string.plant_health_light_low_high,
                "HIGH" to R.string.plant_health_light_high
            )
        )

    fun co2(context: Context, care: AquariumPlantCare?): String =
        care?.co2Requirement.toLocalized(
            context,
            mapOf(
                "NONE" to R.string.plant_health_co2_none,
                "OPTIONAL" to R.string.plant_health_co2_optional,
                "RECOMMENDED" to R.string.plant_health_co2_recommended,
                "REQUIRED" to R.string.plant_health_co2_required
            )
        )

    fun nutrientDemand(context: Context, care: AquariumPlantCare?): String =
        care?.nutrientDemand.toLocalized(
            context,
            mapOf(
                "LOW" to R.string.plant_health_nutrient_low,
                "MEDIUM" to R.string.plant_health_nutrient_medium,
                "HIGH" to R.string.plant_health_nutrient_high
            )
        )

    fun substrate(context: Context, care: AquariumPlantCare?): String =
        care?.substrateRequirement.toLocalized(
            context,
            mapOf(
                "FLEXIBLE" to R.string.plant_health_substrate_flexible,
                "NOT_APPLICABLE" to R.string.plant_health_substrate_not_applicable,
                "NUTRIENT_RICH" to R.string.plant_health_substrate_nutrient_rich
            )
        )

    fun range(
        context: Context,
        care: AquariumPlantCare?,
        range: PlantHealthCatalogRange
    ): String {
        val bounds = when (range) {
            PlantHealthCatalogRange.TEMPERATURE -> care?.temperatureMinC to care?.temperatureMaxC
            PlantHealthCatalogRange.PH -> care?.pHMin to care?.pHMax
            PlantHealthCatalogRange.KH -> care?.khMin to care?.khMax
            PlantHealthCatalogRange.GH -> care?.ghMin to care?.ghMax
        }
        val formatRes = when (range) {
            PlantHealthCatalogRange.TEMPERATURE ->
                R.string.plant_health_temperature_range_format
            PlantHealthCatalogRange.PH ->
                R.string.plant_health_range_format
            PlantHealthCatalogRange.KH ->
                R.string.plant_health_kh_range_format
            PlantHealthCatalogRange.GH ->
                R.string.plant_health_gh_range_format
        }
        return formatRange(context, bounds.first, bounds.second, formatRes)
    }

    fun placement(context: Context, record: AquariumPlantCatalogRecord?): String =
        record?.placement
            ?.mapNotNull { value -> value.toPlacementRes()?.let(context::getString) }
            ?.sorted()
            ?.joinToString(" • ")
            .orEmpty()
}

private fun String?.toLocalized(
    context: Context,
    mapping: Map<String, Int>
): String {
    val key = this?.takeIf { value -> value.isNotBlank() && value != "UNKNOWN" }
        ?: return context.getString(R.string.plant_health_catalog_unknown)
    return mapping[key]
        ?.let(context::getString)
        ?: context.getString(R.string.plant_health_catalog_unknown)
}

private fun String.toPlacementRes(): Int? = when (this) {
    "FOREGROUND" -> R.string.plant_health_placement_foreground
    "MIDGROUND" -> R.string.plant_health_placement_midground
    "BACKGROUND" -> R.string.plant_health_placement_background
    "CARPET" -> R.string.plant_health_placement_carpet
    "FLOATING" -> R.string.plant_health_placement_floating
    "EPIPHYTE" -> R.string.plant_health_placement_epiphyte
    "SURFACE" -> R.string.plant_health_placement_surface
    "SURFACE_LEAVES" -> R.string.plant_health_placement_surface_leaves
    else -> null
}

private fun formatRange(
    context: Context,
    minimum: Double?,
    maximum: Double?,
    formatRes: Int
): String {
    if (minimum == null || maximum == null) {
        return context.getString(R.string.plant_health_value_not_available)
    }
    return context.getString(
        formatRes,
        LocaleFormatter.formatDecimal(context, minimum),
        LocaleFormatter.formatDecimal(context, maximum)
    )
}
