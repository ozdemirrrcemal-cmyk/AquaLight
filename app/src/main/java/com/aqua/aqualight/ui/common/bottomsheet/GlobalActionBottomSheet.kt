package com.aqua.aqualight.ui.common.bottomsheet

import android.content.DialogInterface
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

data class BottomSheetDetailRow(
    val label: String,
    val value: String
)

data class BottomSheetAction(
    val id: String,
    val text: String,
    val style: BottomSheetActionStyle
)

enum class BottomSheetActionStyle {
    PRIMARY,
    DANGER,
    NEUTRAL
}

/** Process-safe multi-action sheet. Inputs live in arguments and actions return via Fragment Result. */
class GlobalActionBottomSheet : BottomSheetDialogFragment(
    R.layout.bottom_sheet_global_action
) {

    private var resultSent = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val titleText = view.findViewById<TextView>(R.id.tvGlobalActionTitle)
        val messageText = view.findViewById<TextView>(R.id.tvGlobalActionMessage)
        val detailsContainer = view.findViewById<LinearLayout>(R.id.globalActionDetailsContainer)
        val buttonsContainer = view.findViewById<LinearLayout>(R.id.globalActionButtonsContainer)

        titleText.text = args.getString(ARG_TITLE).orEmpty()
        val message = args.getString(ARG_MESSAGE)
        messageText.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
        messageText.text = message.orEmpty()

        renderDetails(
            container = detailsContainer,
            labels = args.getStringArrayList(ARG_DETAIL_LABELS).orEmpty(),
            values = args.getStringArrayList(ARG_DETAIL_VALUES).orEmpty()
        )
        renderActions(
            container = buttonsContainer,
            ids = args.getStringArrayList(ARG_ACTION_IDS).orEmpty(),
            texts = args.getStringArrayList(ARG_ACTION_TEXTS).orEmpty(),
            styles = args.getStringArrayList(ARG_ACTION_STYLES).orEmpty()
        )
    }

    override fun onCancel(dialog: DialogInterface) {
        publishResult(RESULT_CANCELLED)
        super.onCancel(dialog)
    }

    private fun renderDetails(
        container: LinearLayout,
        labels: List<String>,
        values: List<String>
    ) {
        container.removeAllViews()
        val itemCount = minOf(labels.size, values.size)
        container.visibility = if (itemCount == 0) View.GONE else View.VISIBLE

        repeat(itemCount) { index ->
            val row = LayoutInflater.from(requireContext()).inflate(
                R.layout.item_global_bottom_sheet_detail_row,
                container,
                false
            )
            row.findViewById<TextView>(R.id.tvDetailLabel).text = labels[index]
            row.findViewById<TextView>(R.id.tvDetailValue).text = values[index]
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    topMargin = resources.getDimensionPixelSize(R.dimen.global_action_detail_gap)
                }
            }
            container.addView(row)
        }
    }

    private fun renderActions(
        container: LinearLayout,
        ids: List<String>,
        texts: List<String>,
        styles: List<String>
    ) {
        container.removeAllViews()
        val itemCount = minOf(ids.size, texts.size, styles.size)
        container.visibility = if (itemCount == 0) View.GONE else View.VISIBLE

        repeat(itemCount) { index ->
            val style = runCatching {
                BottomSheetActionStyle.valueOf(styles[index])
            }.getOrDefault(BottomSheetActionStyle.NEUTRAL)
            val button = createActionButton(
                text = texts[index],
                style = style
            ) {
                publishResult(
                    result = RESULT_ACTION,
                    actionId = ids[index]
                )
                dismiss()
            }
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.global_action_button_height)
            ).apply {
                if (index > 0) {
                    topMargin = resources.getDimensionPixelSize(R.dimen.global_action_button_gap)
                }
            }
            container.addView(button)
        }
    }

    private fun createActionButton(
        text: String,
        style: BottomSheetActionStyle,
        onClick: () -> Unit
    ): MaterialButton {
        val styleRes = when (style) {
            BottomSheetActionStyle.PRIMARY -> R.style.Widget_Aqua_BottomSheet_Button_Primary
            BottomSheetActionStyle.DANGER -> R.style.Widget_Aqua_BottomSheet_Button_Danger
            BottomSheetActionStyle.NEUTRAL -> R.style.Widget_Aqua_BottomSheet_Button_Neutral
        }
        return MaterialButton(
            ContextThemeWrapper(requireContext(), styleRes),
            null
        ).apply {
            this.text = text
            setOnClickListener { onClick() }
        }
    }

    private fun publishResult(
        result: String,
        actionId: String = ""
    ) {
        if (resultSent) return
        resultSent = true
        val args = requireArguments()
        parentFragmentManager.setFragmentResult(
            args.getString(ARG_REQUEST_KEY).orEmpty(),
            bundleOf(
                RESULT_KEY to result,
                RESULT_ACTION_ID to actionId,
                RESULT_PAYLOAD_ID to args.getString(ARG_PAYLOAD_ID).orEmpty()
            )
        )
    }

    companion object {
        const val RESULT_KEY = "global_action_result"
        const val RESULT_ACTION_ID = "global_action_id"
        const val RESULT_PAYLOAD_ID = "global_action_payload_id"
        const val RESULT_ACTION = "action"
        const val RESULT_CANCELLED = "cancelled"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"
        private const val ARG_DETAIL_LABELS = "arg_detail_labels"
        private const val ARG_DETAIL_VALUES = "arg_detail_values"
        private const val ARG_ACTION_IDS = "arg_action_ids"
        private const val ARG_ACTION_TEXTS = "arg_action_texts"
        private const val ARG_ACTION_STYLES = "arg_action_styles"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_PAYLOAD_ID = "arg_payload_id"
        private const val TAG_PREFIX = "GlobalActionBottomSheet:"

        fun newInstance(
            title: String,
            message: String?,
            details: List<BottomSheetDetailRow>,
            actions: List<BottomSheetAction>,
            requestKey: String,
            payloadId: String
        ): GlobalActionBottomSheet {
            return GlobalActionBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_MESSAGE to message,
                    ARG_DETAIL_LABELS to ArrayList(details.map(BottomSheetDetailRow::label)),
                    ARG_DETAIL_VALUES to ArrayList(details.map(BottomSheetDetailRow::value)),
                    ARG_ACTION_IDS to ArrayList(actions.map(BottomSheetAction::id)),
                    ARG_ACTION_TEXTS to ArrayList(actions.map(BottomSheetAction::text)),
                    ARG_ACTION_STYLES to ArrayList(actions.map { it.style.name }),
                    ARG_REQUEST_KEY to requestKey,
                    ARG_PAYLOAD_ID to payloadId
                )
            }
        }

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            message: String? = null,
            details: List<BottomSheetDetailRow> = emptyList(),
            actions: List<BottomSheetAction> = emptyList(),
            requestKey: String,
            payloadId: String = ""
        ) {
            val tag = TAG_PREFIX + requestKey
            if (fragmentManager.findFragmentByTag(tag) != null) return
            newInstance(
                title = title,
                message = message,
                details = details,
                actions = actions,
                requestKey = requestKey,
                payloadId = payloadId
            ).show(fragmentManager, tag)
        }
    }
}
