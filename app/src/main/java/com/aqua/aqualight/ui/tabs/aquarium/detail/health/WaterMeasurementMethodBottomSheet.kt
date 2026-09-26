package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
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
        selection = WaterMeasurementSelectionUi(
            method = WaterMeasurementMethodUi.valueOf(
                requireNotNull(args.getString(ARG_METHOD))
            ),
            testKitId = args.getString(ARG_TEST_KIT_ID).orEmpty(),
            basisId = args.getString(ARG_BASIS_ID).orEmpty(),
            unitId = args.getString(ARG_UNIT_ID).orEmpty()
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
            selection = when (
                result.getString(SingleChoiceBottomSheet.RESULT_PAYLOAD_ID).orEmpty()
            ) {
                PAYLOAD_TEST_KIT -> selection.copy(testKitId = selectedId)
                PAYLOAD_BASIS -> selection.copy(basisId = selectedId)
                PAYLOAD_UNIT -> selection.copy(unitId = selectedId)
                else -> selection
            }
            render()
        }

        contentBinding.cardMethodManual.setOnClickListener {
            selection = selection.copy(method = WaterMeasurementMethodUi.MANUAL)
            render()
        }
        contentBinding.cardMethodTestKit.setOnClickListener {
            selection = selection.copy(method = WaterMeasurementMethodUi.TEST_KIT)
            render()
        }
        contentBinding.cardMethodDigital.setOnClickListener {
            selection = selection.copy(method = WaterMeasurementMethodUi.DIGITAL)
            render()
        }
        contentBinding.cardMethodSensor.setOnClickListener {
            selection = selection.copy(method = WaterMeasurementMethodUi.SENSOR)
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
                options = WaterMeasurementUiCatalog.basisOptions(parameterId),
                selectedId = selection.basisId,
                payloadId = PAYLOAD_BASIS
            )
        }
        contentBinding.rowUnit.setOnClickListener {
            showChoice(
                titleRes = R.string.water_measurement_unit,
                options = WaterMeasurementUiCatalog.unitOptions(parameterId),
                selectedId = selection.unitId,
                payloadId = PAYLOAD_UNIT
            )
        }

        contentBinding.btnApply.setOnClickListener {
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
        val basisOptions = WaterMeasurementUiCatalog.basisOptions(parameterId)
        val unitOptions = WaterMeasurementUiCatalog.unitOptions(parameterId)

        contentBinding.tvTestKitValue.setText(
            WaterMeasurementUiCatalog.optionLabelRes(kitOptions, selection.testKitId)
        )
        contentBinding.tvBasisValue.setText(
            WaterMeasurementUiCatalog.optionLabelRes(basisOptions, selection.basisId)
        )
        if (unitOptions.isNotEmpty()) {
            contentBinding.tvUnitValue.setText(
                WaterMeasurementUiCatalog.optionLabelRes(unitOptions, selection.unitId)
            )
        }

        val testKitEnabled = selection.method == WaterMeasurementMethodUi.TEST_KIT
        contentBinding.rowTestKit.isEnabled = testKitEnabled
        contentBinding.rowTestKit.alpha = if (testKitEnabled) ENABLED_ALPHA else DISABLED_ALPHA

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

        card.setCardBackgroundColor(if (selected) primary else transparent)
        card.strokeColor = if (selected) primary else outline
        text.setTextColor(if (selected) selectedText else unselectedText)
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

    override fun onDestroyView() {
        _contentBinding = null
        _sheetBinding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST_KEY = "water_measurement_method_request"
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
        ) {
            if (fragmentManager.findFragmentByTag(TAG) != null || fragmentManager.isStateSaved) {
                return
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
        }
    }
}
