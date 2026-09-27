package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.annotation.StringRes

internal data class TankHealthWaterMetricUiModel(
    val id: WaterTestParameterId,
    @StringRes val labelRes: Int,
    @StringRes val symbolRes: Int?,
    val valueText: String? = null,
    val statusText: String? = null
)

internal object TankHealthWaterMetricUiCatalog {
    const val MAX_DASHBOARD_METRICS = 8

    fun visibleParameterIds(
        tankProfile: String,
        measuredParameterIds: List<WaterTestParameterId>
    ): List<WaterTestParameterId> {
        val recommended = WaterTestProfileUiCatalog.recommendedIds(tankProfile)
        val measuredExtras = measuredParameterIds
            .distinct()
            .filterNot(recommended::contains)
            // Old marine KH readings remain in history/detail as raw, unresolved
            // results. They must not become a second alkalinity card or evidence.
            .filterNot { id ->
                id == WaterTestParameterId.KH &&
                    WaterTestParameterId.TOTAL_ALKALINITY in recommended
            }

        return (recommended + measuredExtras)
            .distinct()
            .take(MAX_DASHBOARD_METRICS)
    }

    fun models(
        tankProfile: String,
        measuredParameterIds: List<WaterTestParameterId>
    ): List<TankHealthWaterMetricUiModel> {
        val recommended = WaterTestProfileUiCatalog.recommendedIds(tankProfile).toSet()
        return visibleParameterIds(tankProfile, measuredParameterIds).map { id ->
            val base = WaterTestProfileUiCatalog.model(
                id = id,
                importance = if (id in recommended) {
                    WaterTestImportance.RECOMMENDED
                } else {
                    WaterTestImportance.ADDITIONAL
                },
                value = ""
            )
            TankHealthWaterMetricUiModel(
                id = id,
                labelRes = base.nameRes,
                symbolRes = base.symbolRes
            )
        }
    }
}
