package com.aqua.aqualight.ui.common.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isNotEmpty
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetDeviceConfirmBinding
import com.aqua.aqualight.utils.DialogType
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Shared process-safe confirmation dialog.
 *
 * Inputs live entirely in arguments and user decisions are returned through Fragment Result so
 * the dialog remains recreatable after configuration changes or process restoration.
 */
class ConfirmDialogFragment : DialogFragment() {

    data class Request(
        val title: String,
        val message: String,
        val confirmText: String,
        val cancelText: String,
        val presentation: Presentation,
        val resultTarget: ResultTarget
    )

    data class Presentation(
        val type: DialogType,
        val destructive: Boolean = false
    )

    data class ResultTarget(
        val requestKey: String,
        val actionId: String = ""
    )

    private var resultSent = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = BottomSheetDeviceConfirmBinding.inflate(layoutInflater)
        val request = requestFromArguments(requireArguments())

        configureContent(
            binding = binding,
            request = request
        )

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
            .also { dialog ->
                dialog.setCanceledOnTouchOutside(true)
                binding.btnConfirmCancel.setOnClickListener {
                    publish(RESULT_CANCEL)
                    dialog.dismiss()
                }
                binding.btnConfirmPrimary.setOnClickListener {
                    publish(RESULT_CONFIRM)
                    dialog.dismiss()
                }
            }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onCancel(dialog: DialogInterface) {
        publish(RESULT_CANCEL)
        super.onCancel(dialog)
    }

    private fun configureContent(
        binding: BottomSheetDeviceConfirmBinding,
        request: Request
    ) {
        binding.root.apply {
            if (isNotEmpty()) {
                removeViewAt(0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.aqua_bottom_sheet_surface
                    )
                )
                cornerRadius = resources.getDimension(R.dimen.aqua_size_28)
                setStroke(
                    resources.getDimensionPixelSize(R.dimen.aqua_size_1),
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.aqua_bottom_sheet_sheet_border
                    )
                )
            }
        }
        binding.ivConfirmIcon.setImageResource(iconForType(request.presentation.type))
        binding.tvConfirmTitle.text = request.title
        binding.tvConfirmMessage.text = request.message
        binding.btnConfirmPrimary.text = request.confirmText
        binding.btnConfirmCancel.text = request.cancelText

        if (!request.presentation.destructive) {
            binding.btnConfirmPrimary.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.aqua_bottom_sheet_primary)
            )
            binding.btnConfirmPrimary.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.aqua_bottom_sheet_on_primary)
            )
            binding.btnConfirmPrimary.strokeWidth = 0
        }
    }

    private fun requestFromArguments(args: Bundle): Request {
        val type = runCatching {
            DialogType.valueOf(args.getString(ARG_TYPE).orEmpty())
        }.getOrDefault(DialogType.INFO)
        return Request(
            title = args.getString(ARG_TITLE).orEmpty(),
            message = args.getString(ARG_MESSAGE).orEmpty(),
            confirmText = args.getString(ARG_CONFIRM_TEXT).orEmpty(),
            cancelText = args.getString(ARG_CANCEL_TEXT).orEmpty(),
            presentation = Presentation(
                type = type,
                destructive = args.getBoolean(ARG_DESTRUCTIVE)
            ),
            resultTarget = ResultTarget(
                requestKey = args.getString(ARG_REQUEST_KEY).orEmpty(),
                actionId = args.getString(ARG_ACTION_ID).orEmpty()
            )
        )
    }

    private fun publish(result: String) {
        if (resultSent) return
        resultSent = true

        val args = requireArguments()
        parentFragmentManager.setFragmentResult(
            args.getString(ARG_REQUEST_KEY).orEmpty(),
            bundleOf(
                RESULT_KEY to result,
                RESULT_ACTION_ID to args.getString(ARG_ACTION_ID).orEmpty()
            )
        )
    }

    private fun iconForType(type: DialogType): Int {
        return when (type) {
            DialogType.INFO -> R.drawable.ic_info
            DialogType.ERROR -> R.drawable.ic_error
            DialogType.SUCCESS -> R.drawable.ic_success
            DialogType.WARNING -> R.drawable.ic_warning
        }
    }

    companion object {
        const val RESULT_KEY = "confirm_dialog_result"
        const val RESULT_ACTION_ID = "confirm_dialog_action_id"
        const val RESULT_CONFIRM = "confirm"
        const val RESULT_CANCEL = "cancel"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"
        private const val ARG_CONFIRM_TEXT = "arg_confirm_text"
        private const val ARG_CANCEL_TEXT = "arg_cancel_text"
        private const val ARG_TYPE = "arg_type"
        private const val ARG_DESTRUCTIVE = "arg_destructive"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_ACTION_ID = "arg_action_id"
        private const val TAG_PREFIX = "ConfirmDialogFragment:"

        fun newInstance(request: Request): ConfirmDialogFragment {
            return ConfirmDialogFragment().apply {
                arguments = bundleOf(
                    ARG_TITLE to request.title,
                    ARG_MESSAGE to request.message,
                    ARG_CONFIRM_TEXT to request.confirmText,
                    ARG_CANCEL_TEXT to request.cancelText,
                    ARG_TYPE to request.presentation.type.name,
                    ARG_DESTRUCTIVE to request.presentation.destructive,
                    ARG_REQUEST_KEY to request.resultTarget.requestKey,
                    ARG_ACTION_ID to request.resultTarget.actionId
                )
            }
        }

        fun show(
            fragmentManager: FragmentManager,
            request: Request
        ) {
            val tag = TAG_PREFIX + request.resultTarget.requestKey
            if (fragmentManager.findFragmentByTag(tag) != null || fragmentManager.isStateSaved) {
                return
            }

            newInstance(request).show(fragmentManager, tag)
        }
    }
}
