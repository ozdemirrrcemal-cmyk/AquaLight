package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ContentSheetWaterMeasurementMethodBinding
import com.aqua.aqualight.databinding.DialogSettingsBottomSheetBinding
import com.aqua.aqualight.ui.common.bottomsheet.SingleChoiceBottomSheet
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView

internal class WaterMeasurementMethodBottomSheet : BottomSheetDialogFragment() {

    private var _sheetBinding: DialogSettingsBottomSheetBinding? = null
    private val sheetBinding get() = _sheetBinding!!

    private var _contentBinding: ContentSheetWaterMeasurementMethodBinding? = null
    private val contentBinding get() = _contentBinding!!

    private lateinit var parameterId: WaterTestParameterId
    private lateinit var selection: WaterMeasurementSelectionUi

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
        val args = requireArguments()
        parameterId = WaterTestParameterId.valueOf(
            requireNotNull(args.getString(ARG_PARAMETER_ID))
        )
        val argumentSelection = WaterMeasurementSelectionUi(
            method = WaterMeasurementMethodUi.valueOf(
                requireNotNull(args.getString(ARG_METHOD))
            ),
            testKitId = args.getString(ARG_TEST_KIT_ID).orEmpty(),
            basisId = args.getString(ARG_BASIS_ID).orEmpty(),
            unitId = args.getString(ARG_UNIT_ID).orEmpty()
        )
        val restoredSelection = savedInstanceState
            ?.getString(STATE_METHOD)
            ?.let { rawMethod ->
                runCatching { WaterMeasurementMethodUi.valueOf(rawMethod) }
                    .getOrNull()
                    ?.let { method ->
                        WaterMeasurementSelectionUi(
                            method = method,
                            testKitId = savedInstanceState
                                .getString(STATE_TEST_KIT_ID)
                                .orEmpty(),
                            basisId = savedInstanceState
                                .getString(STATE_BASIS_ID)
                                .orEmpty(),
                            unitId = savedInstanceState
                                .getString(STATE_UNIT_ID)
                                .orEmpty()
                        )
                    }
            }
        selection = WaterMeasurementUiCatalog.normalizeSelection(
            parameterId = parameterId,
            selection = restoredSelection ?: argumentSelection
        )

        sheetBinding.tvSheetTitle.setText(R.string.water_measurement_sheet_title)
        _contentBinding = ContentSheetWaterMeasurementMethodBinding.inflate(layoutInflater)
        sheetBinding.sheetContentContainer.removeAllViews()
        sheetBinding.sheetContentContainer.addView(contentBinding.root)

        childFragmentManager.setFragmentResultListener(
            CHOICE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(SingleChoiceBottomSheet.RESULT_KEY) !=
                SingleChoiceBottomSheet.RESULT_SELECTED
            ) {
                return@setFragmentResultListener
            }
            val selectedId = result
                .getString(SingleChoiceBottomSheet.RESULT_SELECTED_ID)
                .orEmpty()
            val updatedSelection = when (
                result.getString(SingleChoiceBottomSheet.RESULT_PAYLOAD_ID).orEmpty()
            ) {
                PAYLOAD_TEST_KIT -> selection.copy(testKitId = selectedId)
                PAYLOAD_BASIS -> selection.copy(basisId = selectedId)
                PAYLOAD_UNIT -> selection.copy(unitId = selectedId)
                else -> selection
            }
            selection = WaterMeasurementUiCatalog.normalizeSelection(
                parameterId = parameterId,
                selection = updatedSelection
            )
            render()
        }

        contentBinding.cardMethodManual.setOnClickListener {
            selection = WaterMeasurementUiCatalog.normalizeSelection(
                parameterId = parameterId,
                selection = selection.copy(method = WaterMeasurementMethodUi.MANUAL)
            )
            render()
        }
        contentBinding.cardMethodTestKit.setOnClickListener {
            selection = WaterMeasurementUiCatalog.normalizeSelection(
                parameterId = parameterId,
                selection = selection.copy(method = WaterMeasurementMethodUi.TEST_KIT)
            )
            render()
        }
        contentBinding.cardMethodDigital.setOnClickListener {
            selection = WaterMeasurementUiCatalog.normalizeSelection(
                parameterId = parameterId,
                selection = selection.copy(method = WaterMeasurementMethodUi.DIGITAL)
            )
            render()
        }
        contentBinding.cardMethodSensor.setOnClickListener {
            selection = WaterMeasurementUiCatalog.normalizeSelection(
                parameterId = parameterId,
                selection = selection.copy(method = WaterMeasurementMethodUi.SENSOR)
            )
            render()
        }

        contentBinding.rowTestKit.setOnClickListener {
            if (selection.method == WaterMeasurementMethodUi.TEST_KIT) {
                showChoice(
                    titleRes = R.string.water_measurement_test_kit,
                    options = WaterMeasurementUiCatalog.testKitOptions(parameterId),
                    selectedId = selection.testKitId,
                    payloadId = PAYLOAD_TEST_KIT
                )
            }
        }
        contentBinding.rowBasis.setOnClickListener {
            showChoice(
                titleRes = R.string.water_measurement_basis,
                options = WaterMeasurementUiCatalog.selectableBasisOptions(
                    parameterId,
                    selection
                ),
                selectedId = selection.basisId,
                payloadId = PAYLOAD_BASIS
            )
        }
        contentBinding.rowUnit.setOnClickListener {
            showChoice(
                titleRes = R.string.water_measurement_unit,
                options = WaterMeasurementUiCatalog.selectableUnitOptions(
                    parameterId,
                    selection
                ),
                selectedId = selection.unitId,
                payloadId = PAYLOAD_UNIT
            )
        }

        contentBinding.btnApply.setOnClickListener {
            selection = WaterMeasurementUiCatalog.normalizeSelection(parameterId, selection)
            if (!WaterMeasurementUiCatalog.isSelectionValid(parameterId, selection)) {
                return@setOnClickListener
            }
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_PARAMETER_ID to parameterId.name,
                    RESULT_METHOD to selection.method.name,
                    RESULT_TEST_KIT_ID to selection.testKitId,
                    RESULT_BASIS_ID to selection.basisId,
                    RESULT_UNIT_ID to selection.unitId
                )
            )
            dismiss()
        }

        render()
    }

    private fun render() {
        renderMethod(
            contentBinding.cardMethodManual,
            contentBinding.tvMethodManual,
            contentBinding.ivMethodManual,
            selection.method == WaterMeasurementMethodUi.MANUAL
        )
        renderMethod(
            contentBinding.cardMethodTestKit,
            contentBinding.tvMethodTestKit,
            contentBinding.ivMethodTestKit,
            selection.method == WaterMeasurementMethodUi.TEST_KIT
        )
        renderMethod(
            contentBinding.cardMethodDigital,
            contentBinding.tvMethodDigital,
            contentBinding.ivMethodDigital,
            selection.method == WaterMeasurementMethodUi.DIGITAL
        )
        renderMethod(
            contentBinding.cardMethodSensor,
            contentBinding.tvMethodSensor,
            contentBinding.ivMethodSensor,
            selection.method == WaterMeasurementMethodUi.SENSOR
        )

        val kitOptions = WaterMeasurementUiCatalog.testKitOptions(parameterId)
        val basisOptions = WaterMeasurementUiCatalog.selectableBasisOptions(
            parameterId,
            selection
        )
        val unitOptions = WaterMeasurementUiCatalog.selectableUnitOptions(
            parameterId,
            selection
        )

        contentBinding.tvTestKitValue.setText(
            WaterMeasurementUiCatalog.optionLabelRes(kitOptions, selection.testKitId)
        )
        contentBinding.tvBasisValue.setText(
            WaterMeasurementUiCatalog.optionLabelRes(basisOptions, selection.basisId)
        )
        val hasUnit = unitOptions.isNotEmpty()
        contentBinding.dividerBeforeUnit.isVisible = hasUnit
        contentBinding.rowUnit.isVisible = hasUnit
        if (hasUnit) {
            contentBinding.tvUnitValue.setText(
                WaterMeasurementUiCatalog.optionLabelRes(unitOptions, selection.unitId)
            )
        }

        val testKitEnabled = selection.method == WaterMeasurementMethodUi.TEST_KIT
        contentBinding.rowTestKit.isEnabled = testKitEnabled
        contentBinding.rowTestKit.alpha = if (testKitEnabled) ENABLED_ALPHA else DISABLED_ALPHA
        contentBinding.btnApply.isEnabled =
            WaterMeasurementUiCatalog.isSelectionValid(parameterId, selection)

        val canonicalBasis = WaterMeasurementUiCatalog.canonicalBasis(parameterId)
        val canonicalUnit = WaterMeasurementUiCatalog.canonicalUnit(parameterId)
        contentBinding.tvCanonicalInfo.text = if (canonicalUnit == null) {
            getString(
                R.string.water_measurement_canonical_format_without_unit,
                getString(canonicalBasis.labelRes)
            )
        } else {
            getString(
                R.string.water_measurement_canonical_format,
                getString(canonicalBasis.labelRes),
                getString(canonicalUnit.labelRes)
            )
        }
    }

    private fun renderMethod(
        card: MaterialCardView,
        text: TextView,
        icon: ImageView,
        selected: Boolean
    ) {
        val context = requireContext()
        val primary = ContextCompat.getColor(context, R.color.aqua_accent_primary)
        val transparent = ContextCompat.getColor(context, R.color.aqua_color_transparent)
        val outline = ContextCompat.getColor(context, R.color.aqua_card_metric_outline)
        val selectedText = ContextCompat.getColor(context, R.color.aqua_content_on_dark)
        val unselectedText = ContextCompat.getColor(context, R.color.aqua_card_text_secondary)

        card.isCheckable = true
        card.isChecked = selected
        card.setCardBackgroundColor(if (selected) primary else transparent)
        card.strokeColor = if (selected) primary else outline
        text.setTextColor(if (selected) selectedText else unselectedText)
        text.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        icon.imageTintList = android.content.res.ColorStateList.valueOf(
            if (selected) selectedText else unselectedText
        )
    }

    private fun showChoice(
        titleRes: Int,
        options: List<WaterMeasurementOptionUi>,
        selectedId: String,
        payloadId: String
    ) {
        if (options.isEmpty()) return
        SingleChoiceBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(titleRes),
            options = options.map { option -> option.id to getString(option.labelRes) },
            selectedId = selectedId,
            columns = 1,
            requestKey = CHOICE_REQUEST_KEY,
            payloadId = payloadId
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_METHOD, selection.method.name)
        outState.putString(STATE_TEST_KIT_ID, selection.testKitId)
        outState.putString(STATE_BASIS_ID, selection.basisId)
        outState.putString(STATE_UNIT_ID, selection.unitId)
        super.onSaveInstanceState(outState)
    }

    override fun onCancel(dialog: DialogInterface) {
        if (::parameterId.isInitialized) {
            parentFragmentManager.setFragmentResult(
                CANCEL_REQUEST_KEY,
                bundleOf(RESULT_PARAMETER_ID to parameterId.name)
            )
        }
        super.onCancel(dialog)
    }

    override fun onDestroyView() {
        _contentBinding = null
        _sheetBinding = null
        super.onDestroyView()
    }

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
        private const val CHOICE_REQUEST_KEY = "water_measurement_choice_request"
        private const val PAYLOAD_TEST_KIT = "test_kit"
        private const val PAYLOAD_BASIS = "basis"
        private const val PAYLOAD_UNIT = "unit"
        private const val ENABLED_ALPHA = 1f
        private const val DISABLED_ALPHA = 0.5f
        private const val TAG = "WaterMeasurementMethodBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            parameterId: WaterTestParameterId,
            selection: WaterMeasurementSelectionUi
        ): Boolean {
            if (fragmentManager.findFragmentByTag(TAG) != null || fragmentManager.isStateSaved) {
                return false
            }
            WaterMeasurementMethodBottomSheet().apply {
                arguments = bundleOf(
                    ARG_PARAMETER_ID to parameterId.name,
                    ARG_METHOD to selection.method.name,
                    ARG_TEST_KIT_ID to selection.testKitId,
                    ARG_BASIS_ID to selection.basisId,
                    ARG_UNIT_ID to selection.unitId
                )
            }.show(fragmentManager, TAG)
            return true
        }
    }
}
