package com.aqua.aqualight.ui.tabs.aquarium.detail.health

internal class WaterAnalysisParameterState {
    val parameterValues = linkedMapOf<WaterTestParameterId, String>()
    val additionalParameters = linkedSetOf<WaterTestParameterId>()
    val measurementSelections =
        linkedMapOf<WaterTestParameterId, WaterMeasurementSelectionUi>()
    var activeMeasurementParameterId: WaterTestParameterId? = null
}
