package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.annotation.StringRes

internal sealed interface TankHealthWaterMetricId {
    data object Temperature : TankHealthWaterMetricId
    data class Parameter(val value: WaterTestParameterId) : TankHealthWaterMetricId
}

internal data class TankHealthWaterMetricUiModel(
    val id: TankHealthWaterMetricId,
    @StringRes val labelRes: Int,
    @StringRes val symbolRes: Int?,
    val valueText: String? = null,
    val statusText: String? = null
)

internal object TankHealthWaterMetricUiCatalog {
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
                id = TankHealthWaterMetricId.Parameter(id),
                labelRes = base.nameRes,
                symbolRes = base.symbolRes
            )
        }
    }
}
