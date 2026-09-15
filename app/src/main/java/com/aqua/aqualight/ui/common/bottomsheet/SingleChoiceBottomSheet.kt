package com.aqua.aqualight.ui.common.bottomsheet

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Generic process-safe selector for short, serializable ID/label option lists. */
class SingleChoiceBottomSheet : BottomSheetDialogFragment(
    R.layout.bottom_sheet_single_choice
) {

    private var resultSent = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        view.findViewById<TextView>(R.id.tvSingleChoiceTitle).text =
            args.getString(ARG_TITLE).orEmpty()

        val ids = args.getStringArrayList(ARG_OPTION_IDS).orEmpty()
        val labels = args.getStringArrayList(ARG_OPTION_LABELS).orEmpty()
        val selectedId = args.getString(ARG_SELECTED_ID).orEmpty()
        val columns = args.getInt(ARG_COLUMNS, 1).coerceAtLeast(1)
        val container = view.findViewById<GridLayout>(R.id.singleChoiceContainer).apply {
            columnCount = columns
            removeAllViews()
        }

        val count = minOf(ids.size, labels.size)
        repeat(count) { index ->
            container.addView(
                createOption(
                    id = ids[index],
                    label = labels[index],
                    selected = ids[index] == selectedId,
                    columns = columns,
                    index = index
                )
            )
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        publish(RESULT_CANCELLED, "")
        super.onCancel(dialog)
    }

    private fun createOption(
        id: String,
        label: String,
        selected: Boolean,
        columns: Int,
        index: Int
    ): TextView {
        val gap = resources.getDimensionPixelSize(R.dimen.single_choice_gap)
        val isLastColumn = index % columns == columns - 1
        return TextView(requireContext()).apply {
            text = label
            gravity = Gravity.CENTER
            isSelected = selected
            isClickable = true
            isFocusable = true
            includeFontPadding = false
            maxLines = 2
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.single_choice_text_size)
            )
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.aqua_card_text_primary
                    else R.color.aqua_card_text_secondary
                )
            )
            setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            setBackgroundResource(R.drawable.bg_aqua_selection_row_compact)
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = resources.getDimensionPixelSize(R.dimen.single_choice_row_height)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(0, 0, if (isLastColumn) 0 else gap, gap)
            }
            setPadding(
                resources.getDimensionPixelSize(R.dimen.single_choice_horizontal_padding),
                0,
                resources.getDimensionPixelSize(R.dimen.single_choice_horizontal_padding),
                0
            )
            setOnClickListener {
                publish(RESULT_SELECTED, id)
                dismiss()
            }
        }
    }

    private fun publish(result: String, selectedId: String) {
        if (resultSent) return
        resultSent = true
        val args = requireArguments()
        parentFragmentManager.setFragmentResult(
            args.getString(ARG_REQUEST_KEY).orEmpty(),
            bundleOf(
                RESULT_KEY to result,
                RESULT_SELECTED_ID to selectedId,
                RESULT_PAYLOAD_ID to args.getString(ARG_PAYLOAD_ID).orEmpty()
            )
        )
    }

    companion object {
        const val RESULT_KEY = "single_choice_result"
        const val RESULT_SELECTED_ID = "single_choice_selected_id"
        const val RESULT_PAYLOAD_ID = "single_choice_payload_id"
        const val RESULT_SELECTED = "selected"
        const val RESULT_CANCELLED = "cancelled"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_OPTION_IDS = "arg_option_ids"
        private const val ARG_OPTION_LABELS = "arg_option_labels"
        private const val ARG_SELECTED_ID = "arg_selected_id"
        private const val ARG_COLUMNS = "arg_columns"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_PAYLOAD_ID = "arg_payload_id"
        private const val TAG_PREFIX = "SingleChoiceBottomSheet:"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            options: List<Pair<String, String>>,
            selectedId: String?,
            columns: Int,
            requestKey: String,
            payloadId: String = ""
        ) {
            val tag = TAG_PREFIX + requestKey
            if (fragmentManager.findFragmentByTag(tag) != null || fragmentManager.isStateSaved) return
            SingleChoiceBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_OPTION_IDS to ArrayList(options.map { it.first }),
                    ARG_OPTION_LABELS to ArrayList(options.map { it.second }),
                    ARG_SELECTED_ID to selectedId.orEmpty(),
                    ARG_COLUMNS to columns.coerceAtLeast(1),
                    ARG_REQUEST_KEY to requestKey,
                    ARG_PAYLOAD_ID to payloadId
                )
            }.show(fragmentManager, tag)
        }
    }
}
