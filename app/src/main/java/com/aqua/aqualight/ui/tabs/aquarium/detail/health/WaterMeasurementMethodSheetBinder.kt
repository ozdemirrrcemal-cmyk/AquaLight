package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.graphics.Typeface
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ContentSheetWaterMeasurementMethodBinding
import com.aqua.aqualight.ui.common.bottomsheet.SingleChoiceBottomSheet
import com.google.android.material.card.MaterialCardView

internal data class WaterMeasurementMethodSheetState(
    val parameterId: WaterTestParameterId,
    var selection: WaterMeasurementSelectionUi
)

internal class WaterMeasurementMethodSheetBinder(
    private val fragment: WaterMeasurementMethodBottomSheet,
    private val binding: ContentSheetWaterMeasurementMethodBinding,
    private val state: WaterMeasurementMethodSheetState,
    private val onApply: (WaterMeasurementSelectionUi) -> Unit
) {

    fun bind() {
        setupChoiceResult()
        setupMethodActions()
        setupOptionActions()
        binding.btnApply.setOnClickListener {
            val normalized = WaterMeasurementUiCatalog.normalizeSelection(
                state.parameterId,
                state.selection
            )
            state.selection = normalized
            if (WaterMeasurementUiCatalog.isSelectionValid(state.parameterId, normalized)) {
                onApply(normalized)
            }
        }
        render()
    }

    private fun setupChoiceResult() {
        fragment.childFragmentManager.setFragmentResultListener(
            CHOICE_REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(SingleChoiceBottomSheet.RESULT_KEY) ==
                SingleChoiceBottomSheet.RESULT_SELECTED
            ) {
                val selectedId = result
                    .getString(SingleChoiceBottomSheet.RESULT_SELECTED_ID)
                    .orEmpty()
                val updated = when (
                    result.getString(SingleChoiceBottomSheet.RESULT_PAYLOAD_ID).orEmpty()
                ) {
                    PAYLOAD_TEST_KIT -> state.selection.copy(testKitId = selectedId)
                    PAYLOAD_BASIS -> state.selection.copy(basisId = selectedId)
                    PAYLOAD_UNIT -> state.selection.copy(unitId = selectedId)
                    else -> state.selection
                }
                state.selection = WaterMeasurementUiCatalog.normalizeSelection(
                    state.parameterId,
                    updated
                )
                render()
            }
        }
    }

    private fun setupMethodActions() {
        binding.cardMethodManual.setOnClickListener {
            updateMethod(WaterMeasurementMethodUi.MANUAL)
        }
        binding.cardMethodTestKit.setOnClickListener {
            updateMethod(WaterMeasurementMethodUi.TEST_KIT)
        }
        binding.cardMethodDigital.setOnClickListener {
            updateMethod(WaterMeasurementMethodUi.DIGITAL)
        }
        binding.cardMethodSensor.isEnabled = false
        binding.cardMethodSensor.isClickable = false
        binding.cardMethodSensor.alpha = DISABLED_ALPHA
    }

    private fun setupOptionActions() {
        binding.rowTestKit.setOnClickListener {
            if (state.selection.method == WaterMeasurementMethodUi.TEST_KIT) {
                showChoice(
                    titleRes = R.string.water_measurement_test_kit,
                    options = WaterMeasurementUiCatalog.testKitOptions(state.parameterId),
                    selectedId = state.selection.testKitId,
                    payloadId = PAYLOAD_TEST_KIT
                )
            }
        }
        binding.rowBasis.setOnClickListener {
            showChoice(
                titleRes = R.string.water_measurement_basis,
                options = WaterMeasurementUiCatalog.selectableBasisOptions(
                    state.parameterId,
                    state.selection
                ),
                selectedId = state.selection.basisId,
                payloadId = PAYLOAD_BASIS
            )
        }
        binding.rowUnit.setOnClickListener {
            showChoice(
                titleRes = R.string.water_measurement_unit,
                options = WaterMeasurementUiCatalog.selectableUnitOptions(
                    state.parameterId,
                    state.selection
                ),
                selectedId = state.selection.unitId,
                payloadId = PAYLOAD_UNIT
            )
        }
    }

    private fun updateMethod(method: WaterMeasurementMethodUi) {
        state.selection = WaterMeasurementUiCatalog.normalizeSelection(
            parameterId = state.parameterId,
            selection = state.selection.copy(method = method)
        )
        render()
    }

    private fun render() {
        val selection = state.selection
        renderMethod(
            binding.cardMethodManual,
            binding.tvMethodManual,
            binding.ivMethodManual,
            selection.method == WaterMeasurementMethodUi.MANUAL
        )
        renderMethod(
            binding.cardMethodTestKit,
            binding.tvMethodTestKit,
            binding.ivMethodTestKit,
            selection.method == WaterMeasurementMethodUi.TEST_KIT
        )
        renderMethod(
            binding.cardMethodDigital,
            binding.tvMethodDigital,
            binding.ivMethodDigital,
            selection.method == WaterMeasurementMethodUi.DIGITAL
        )
        renderMethod(
            binding.cardMethodSensor,
            binding.tvMethodSensor,
            binding.ivMethodSensor,
            selection.method == WaterMeasurementMethodUi.SENSOR
        )
        renderRows()
        renderCanonicalInfo()
    }

    private fun renderRows() {
        val selection = state.selection
        val kitOptions = WaterMeasurementUiCatalog.testKitOptions(state.parameterId)
        val basisOptions = WaterMeasurementUiCatalog.selectableBasisOptions(
            state.parameterId,
            selection
        )
        val unitOptions = WaterMeasurementUiCatalog.selectableUnitOptions(
            state.parameterId,
            selection
        )

        binding.tvTestKitValue.setText(
            selectedOptionLabelRes(kitOptions, selection.testKitId)
        )
        binding.tvBasisValue.setText(
            selectedOptionLabelRes(basisOptions, selection.basisId)
        )

        val hasUnit = unitOptions.isNotEmpty()
        binding.dividerBeforeUnit.isVisible = hasUnit
        binding.rowUnit.isVisible = hasUnit
        if (hasUnit) {
            binding.tvUnitValue.setText(
                selectedOptionLabelRes(unitOptions, selection.unitId)
            )
        }

        val testKitEnabled = selection.method == WaterMeasurementMethodUi.TEST_KIT
        binding.rowTestKit.isEnabled = testKitEnabled
        binding.rowTestKit.alpha = if (testKitEnabled) ENABLED_ALPHA else DISABLED_ALPHA
        binding.btnApply.isEnabled =
            WaterMeasurementUiCatalog.isSelectionValid(state.parameterId, selection)
    }

    private fun renderCanonicalInfo() {
        binding.tvCanonicalInfo.isVisible =
            WaterMeasurementUiCatalog.hasCanonicalSemantics(state.parameterId)
        if (!binding.tvCanonicalInfo.isVisible) return
        val canonicalBasis = WaterMeasurementUiCatalog.canonicalBasis(state.parameterId)
        val canonicalUnit = WaterMeasurementUiCatalog.canonicalUnit(state.parameterId)
        binding.tvCanonicalInfo.text = if (canonicalUnit == null) {
            fragment.getString(
                R.string.water_measurement_canonical_format_without_unit,
                fragment.getString(canonicalBasis.labelRes)
            )
        } else {
            fragment.getString(
                R.string.water_measurement_canonical_format,
                fragment.getString(canonicalBasis.labelRes),
                fragment.getString(canonicalUnit.labelRes)
            )
        }
    }

    private fun renderMethod(
        card: MaterialCardView,
        text: TextView,
        icon: ImageView,
        selected: Boolean
    ) {
        val context = fragment.requireContext()
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
        if (options.isNotEmpty()) {
            SingleChoiceBottomSheet.show(
                fragmentManager = fragment.childFragmentManager,
                title = fragment.getString(titleRes),
                options = options.map { option ->
                    option.id to fragment.getString(option.labelRes)
                },
                selectedId = selectedId,
                columns = 1,
                requestKey = CHOICE_REQUEST_KEY,
                payloadId = payloadId
            )
        }
    }

    private companion object {
        const val CHOICE_REQUEST_KEY = "water_measurement_choice_request"
        const val PAYLOAD_TEST_KIT = "test_kit"
        const val PAYLOAD_BASIS = "basis"
        const val PAYLOAD_UNIT = "unit"
        const val ENABLED_ALPHA = 1f
        const val DISABLED_ALPHA = 0.5f
    }
}
