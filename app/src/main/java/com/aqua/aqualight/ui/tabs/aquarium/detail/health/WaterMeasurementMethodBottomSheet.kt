package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ContentSheetWaterMeasurementMethodBinding
import com.aqua.aqualight.databinding.DialogSettingsBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

internal class WaterMeasurementMethodBottomSheet : BottomSheetDialogFragment() {

    private var _sheetBinding: DialogSettingsBottomSheetBinding? = null
    private val sheetBinding get() = _sheetBinding!!

    private var _contentBinding: ContentSheetWaterMeasurementMethodBinding? = null
    private val contentBinding get() = _contentBinding!!

    private lateinit var sheetState: WaterMeasurementMethodSheetState

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _sheetBinding = DialogSettingsBottomSheetBinding.inflate(inflater, container, false)
        return sheetBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val parameterId = WaterTestParameterId.valueOf(
            requireNotNull(requireArguments().getString(ARG_PARAMETER_ID))
        )
        sheetState = WaterMeasurementMethodSheetState(
            parameterId = parameterId,
            selection = restoreSelection(parameterId, savedInstanceState)
        )

        sheetBinding.tvSheetTitle.setText(R.string.water_measurement_sheet_title)
        _contentBinding = ContentSheetWaterMeasurementMethodBinding.inflate(layoutInflater)
        sheetBinding.sheetContentContainer.removeAllViews()
        sheetBinding.sheetContentContainer.addView(contentBinding.root)

        WaterMeasurementMethodSheetBinder(
            fragment = this,
            binding = contentBinding,
            state = sheetState,
            onApply = { selection ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    selection.toResultBundle(parameterId)
                )
                dismiss()
            }
        ).bind()
    }

    private fun restoreSelection(
        parameterId: WaterTestParameterId,
        savedInstanceState: Bundle?
    ): WaterMeasurementSelectionUi {
        val source = savedInstanceState ?: requireArguments()
        val methodKey = if (savedInstanceState == null) ARG_METHOD else STATE_METHOD
        val kitKey = if (savedInstanceState == null) ARG_TEST_KIT_ID else STATE_TEST_KIT_ID
        val basisKey = if (savedInstanceState == null) ARG_BASIS_ID else STATE_BASIS_ID
        val unitKey = if (savedInstanceState == null) ARG_UNIT_ID else STATE_UNIT_ID
        val method = source.getString(methodKey)
            ?.let { raw -> runCatching { WaterMeasurementMethodUi.valueOf(raw) }.getOrNull() }
            ?: WaterMeasurementMethodUi.MANUAL

        return WaterMeasurementUiCatalog.normalizeSelection(
            parameterId = parameterId,
            selection = WaterMeasurementSelectionUi(
                method = method,
                testKitId = source.getString(kitKey).orEmpty(),
                basisId = source.getString(basisKey).orEmpty(),
                unitId = source.getString(unitKey).orEmpty()
            )
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::sheetState.isInitialized) {
            val selection = sheetState.selection
            outState.putString(STATE_METHOD, selection.method.name)
            outState.putString(STATE_TEST_KIT_ID, selection.testKitId)
            outState.putString(STATE_BASIS_ID, selection.basisId)
            outState.putString(STATE_UNIT_ID, selection.unitId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onCancel(dialog: DialogInterface) {
        if (::sheetState.isInitialized) {
            parentFragmentManager.setFragmentResult(
                CANCEL_REQUEST_KEY,
                bundleOf(RESULT_PARAMETER_ID to sheetState.parameterId.name)
            )
        }
        super.onCancel(dialog)
    }

    override fun onDestroyView() {
        _contentBinding = null
        _sheetBinding = null
        super.onDestroyView()
    }

    private fun WaterMeasurementSelectionUi.toResultBundle(
        parameterId: WaterTestParameterId
    ): Bundle = bundleOf(
        RESULT_PARAMETER_ID to parameterId.name,
        RESULT_METHOD to method.name,
        RESULT_TEST_KIT_ID to testKitId,
        RESULT_BASIS_ID to basisId,
        RESULT_UNIT_ID to unitId
    )

    companion object {
        const val REQUEST_KEY = "water_measurement_method_request"
        const val CANCEL_REQUEST_KEY = "water_measurement_method_cancel_request"
        const val RESULT_PARAMETER_ID = "water_measurement_parameter_id"
        const val RESULT_METHOD = "water_measurement_method"
        const val RESULT_TEST_KIT_ID = "water_measurement_test_kit_id"
        const val RESULT_BASIS_ID = "water_measurement_basis_id"
        const val RESULT_UNIT_ID = "water_measurement_unit_id"

        private const val ARG_PARAMETER_ID = "parameter_id"
        private const val ARG_METHOD = "method"
        private const val ARG_TEST_KIT_ID = "test_kit_id"
        private const val ARG_BASIS_ID = "basis_id"
        private const val ARG_UNIT_ID = "unit_id"
        private const val STATE_METHOD = "state_method"
        private const val STATE_TEST_KIT_ID = "state_test_kit_id"
        private const val STATE_BASIS_ID = "state_basis_id"
        private const val STATE_UNIT_ID = "state_unit_id"
        private const val TAG = "WaterMeasurementMethodBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            parameterId: WaterTestParameterId,
            selection: WaterMeasurementSelectionUi
        ): Boolean {
            val canShow =
                fragmentManager.findFragmentByTag(TAG) == null &&
                    !fragmentManager.isStateSaved
            if (canShow) {
                WaterMeasurementMethodBottomSheet().apply {
                    arguments = bundleOf(
                        ARG_PARAMETER_ID to parameterId.name,
                        ARG_METHOD to selection.method.name,
                        ARG_TEST_KIT_ID to selection.testKitId,
                        ARG_BASIS_ID to selection.basisId,
                        ARG_UNIT_ID to selection.unitId
                    )
                }.show(fragmentManager, TAG)
            }
            return canShow
        }
    }
}
