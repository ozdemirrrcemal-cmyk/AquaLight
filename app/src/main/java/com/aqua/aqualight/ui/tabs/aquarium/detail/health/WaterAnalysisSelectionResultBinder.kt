package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.utils.DialogType

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
        bindSourceChangeConfirmation()
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

            val candidate = WaterMeasurementUiCatalog.normalizeSelection(
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
            val previous = state.measurementSelections[parameterId]
                ?: WaterMeasurementUiCatalog.defaultSelection(parameterId)
            if (state.parameterValues[parameterId]?.isNotBlank() == true && previous != candidate) {
                ConfirmDialogFragment.show(
                    fragmentManager = fragment.childFragmentManager,
                    request = ConfirmDialogFragment.Request(
                        title = fragment.getString(R.string.water_measurement_change_source_title),
                        message = fragment.getString(R.string.water_measurement_change_source_message),
                        confirmText = fragment.getString(R.string.confirm),
                        cancelText = fragment.getString(R.string.cancel),
                        presentation = ConfirmDialogFragment.Presentation(DialogType.WARNING),
                        resultTarget = ConfirmDialogFragment.ResultTarget(
                            requestKey = SOURCE_CHANGE_REQUEST_KEY,
                            actionId = WaterSourceChangeCandidateCodec.encode(
                                parameterId,
                                candidate
                            )
                        )
                    )
                )
                return@setFragmentResultListener
            }
            applySelection(parameterId, candidate)
        }
    }

    private fun bindSourceChangeConfirmation() {
        fragment.childFragmentManager.setFragmentResultListener(
            SOURCE_CHANGE_REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            val decoded = result.getString(ConfirmDialogFragment.RESULT_ACTION_ID)
                ?.let(WaterSourceChangeCandidateCodec::decode)
            if (
                result.getString(ConfirmDialogFragment.RESULT_KEY) ==
                ConfirmDialogFragment.RESULT_CONFIRM && decoded != null
            ) {
                applySelection(decoded.first, decoded.second)
            } else {
                state.activeMeasurementParameterId = null
                renderer()?.render(tankProfile())
            }
        }
    }

    private fun applySelection(
        parameterId: WaterTestParameterId,
        selection: WaterMeasurementSelectionUi
    ) {
        state.measurementSelections[parameterId] = selection

        if (renderer()?.clearActiveMeasurementParameter(parameterId) != true) {
            state.activeMeasurementParameterId = null
            renderer()?.render(tankProfile())
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

    private companion object {
        const val SOURCE_CHANGE_REQUEST_KEY = "water_measurement_source_change"
    }
}
