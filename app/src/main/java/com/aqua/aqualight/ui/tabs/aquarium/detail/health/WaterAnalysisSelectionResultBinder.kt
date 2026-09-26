package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.fragment.app.Fragment

internal class WaterAnalysisSelectionResultBinder(
    private val fragment: Fragment,
    private val state: WaterAnalysisParameterState,
    private val tankProfile: () -> String?,
    private val renderer: () -> WaterAnalysisParameterRenderer?
) {

    fun bind() {
        bindAdditionalTestResult()
        bindMeasurementResult()
        bindMeasurementCancelResult()
    }

    private fun bindAdditionalTestResult() {
        fragment.childFragmentManager.setFragmentResultListener(
            WaterTestPickerBottomSheet.REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            val parameterId = result
                .getString(WaterTestPickerBottomSheet.RESULT_PARAMETER_ID)
                .toParameterIdOrNull()
                ?: return@setFragmentResultListener
            val profile = tankProfile() ?: return@setFragmentResultListener
            if (parameterId in WaterTestProfileUiCatalog.additionalIds(profile)) {
                state.additionalParameters.add(parameterId)
                renderer()?.render(profile)
            }
        }
    }

    private fun bindMeasurementResult() {
        fragment.childFragmentManager.setFragmentResultListener(
            WaterMeasurementMethodBottomSheet.REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            val parameterId = result
                .getString(WaterMeasurementMethodBottomSheet.RESULT_PARAMETER_ID)
                .toParameterIdOrNull()
                ?: return@setFragmentResultListener
            val method = result
                .getString(WaterMeasurementMethodBottomSheet.RESULT_METHOD)
                .toMeasurementMethodOrNull()
                ?: return@setFragmentResultListener

            state.measurementSelections[parameterId] =
                WaterMeasurementUiCatalog.normalizeSelection(
                    parameterId = parameterId,
                    selection = WaterMeasurementSelectionUi(
                        method = method,
                        testKitId = result
                            .getString(WaterMeasurementMethodBottomSheet.RESULT_TEST_KIT_ID)
                            .orEmpty(),
                        basisId = result
                            .getString(WaterMeasurementMethodBottomSheet.RESULT_BASIS_ID)
                            .orEmpty(),
                        unitId = result
                            .getString(WaterMeasurementMethodBottomSheet.RESULT_UNIT_ID)
                            .orEmpty()
                    )
                )

            if (renderer()?.clearActiveMeasurementParameter(parameterId) != true) {
                state.activeMeasurementParameterId = null
                renderer()?.render(tankProfile())
            }
        }
    }

    private fun bindMeasurementCancelResult() {
        fragment.childFragmentManager.setFragmentResultListener(
            WaterMeasurementMethodBottomSheet.CANCEL_REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            val parameterId = result
                .getString(WaterMeasurementMethodBottomSheet.RESULT_PARAMETER_ID)
                .toParameterIdOrNull()
                ?: return@setFragmentResultListener
            if (renderer()?.clearActiveMeasurementParameter(parameterId) != true) {
                state.activeMeasurementParameterId = null
                renderer()?.render(tankProfile())
            }
        }
    }

    private fun String?.toParameterIdOrNull(): WaterTestParameterId? =
        this?.let { rawId ->
            runCatching { WaterTestParameterId.valueOf(rawId) }.getOrNull()
        }

    private fun String?.toMeasurementMethodOrNull(): WaterMeasurementMethodUi? =
        this?.let { rawMethod ->
            runCatching { WaterMeasurementMethodUi.valueOf(rawMethod) }.getOrNull()
        }
}
