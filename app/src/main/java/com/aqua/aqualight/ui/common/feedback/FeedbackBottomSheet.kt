package com.aqua.aqualight.ui.common.feedback

import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetDeviceConfirmBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Shared process-safe confirm, info, warning and error sheet. */
class FeedbackBottomSheet : BottomSheetDialogFragment(
    R.layout.bottom_sheet_device_confirm
) {

    private var _binding: BottomSheetDeviceConfirmBinding? = null
    private val binding get() = _binding!!
    private var resultSent = false
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetDeviceConfirmBinding.bind(view)

        val args = requireArguments()
        val tone = runCatching {
            FeedbackTone.valueOf(args.getString(ARG_TONE).orEmpty())
        }.getOrDefault(FeedbackTone.INFO)
        val cancelText = args.getString(ARG_CANCEL_TEXT)

        binding.ivConfirmIcon.setImageResource(iconForTone(tone))
        binding.tvConfirmTitle.text = args.getString(ARG_TITLE).orEmpty()
        binding.tvConfirmMessage.text = args.getString(ARG_MESSAGE).orEmpty()
        val primaryText = args.getString(ARG_PRIMARY_TEXT).orEmpty()
        binding.btnConfirmPrimary.text = primaryText
        binding.btnConfirmPrimary.isVisible = primaryText.isNotBlank()
        binding.btnConfirmCancel.isVisible = cancelText != null
        binding.btnConfirmCancel.text = cancelText.orEmpty()

        binding.btnConfirmPrimary.setOnClickListener {
            sendResult(RESULT_PRIMARY)
            dismiss()
        }
        binding.btnConfirmCancel.setOnClickListener {
            sendResult(RESULT_CANCEL)
            dismiss()
        }

        scheduleAutoDismiss(args.getLong(ARG_AUTO_DISMISS_AT, NO_AUTO_DISMISS))
    }

    override fun onCancel(dialog: DialogInterface) {
        sendResult(RESULT_CANCEL)
        super.onCancel(dialog)
    }

    override fun onDestroyView() {
        autoDismissRunnable?.let(handler::removeCallbacks)
        autoDismissRunnable = null
        _binding = null
        super.onDestroyView()
    }

    private fun scheduleAutoDismiss(dismissAtMillis: Long) {
        if (dismissAtMillis == NO_AUTO_DISMISS) return
        val delay = (dismissAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        autoDismissRunnable = Runnable {
            sendResult(RESULT_PRIMARY)
            dismissAllowingStateLoss()
        }.also { handler.postDelayed(it, delay) }
    }

    private fun sendResult(result: String) {
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

    private fun iconForTone(tone: FeedbackTone): Int {
        return when (tone) {
            FeedbackTone.INFO -> R.drawable.ic_info
            FeedbackTone.SUCCESS -> R.drawable.ic_success
            FeedbackTone.WARNING,
            FeedbackTone.DANGER -> R.drawable.ic_warning
            FeedbackTone.ERROR -> R.drawable.ic_error
        }
    }

    enum class FeedbackTone {
        INFO,
        SUCCESS,
        WARNING,
        ERROR,
        DANGER
    }

    companion object {
        const val RESULT_KEY = "feedback_sheet_result"
        const val RESULT_ACTION_ID = "feedback_sheet_action_id"
        const val RESULT_PRIMARY = "primary"
        const val RESULT_CANCEL = "cancel"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"
        private const val ARG_PRIMARY_TEXT = "arg_primary_text"
        private const val ARG_CANCEL_TEXT = "arg_cancel_text"
        private const val ARG_TONE = "arg_tone"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_ACTION_ID = "arg_action_id"
        private const val ARG_AUTO_DISMISS_AT = "arg_auto_dismiss_at"
        private const val NO_AUTO_DISMISS = Long.MIN_VALUE
        private const val TAG_PREFIX = "FeedbackBottomSheet:"

        fun newInstance(
            title: String,
            message: String,
            primaryText: String,
            cancelText: String?,
            tone: FeedbackTone,
            requestKey: String,
            actionId: String,
            autoDismissMillis: Long = 0L
        ): FeedbackBottomSheet {
            return FeedbackBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_MESSAGE to message,
                    ARG_PRIMARY_TEXT to primaryText,
                    ARG_CANCEL_TEXT to cancelText,
                    ARG_TONE to tone.name,
                    ARG_REQUEST_KEY to requestKey,
                    ARG_ACTION_ID to actionId,
                    ARG_AUTO_DISMISS_AT to if (autoDismissMillis > 0L) {
                        System.currentTimeMillis() + autoDismissMillis
                    } else {
                        NO_AUTO_DISMISS
                    }
                )
            }
        }

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            message: String,
            primaryText: String,
            cancelText: String?,
            tone: FeedbackTone,
            requestKey: String,
            actionId: String,
            autoDismissMillis: Long = 0L
        ) {
            val tag = TAG_PREFIX + requestKey
            if (fragmentManager.findFragmentByTag(tag) != null || fragmentManager.isStateSaved) return

            newInstance(
                title = title,
                message = message,
                primaryText = primaryText,
                cancelText = cancelText,
                tone = tone,
                requestKey = requestKey,
                actionId = actionId,
                autoDismissMillis = autoDismissMillis
            ).show(fragmentManager, tag)
        }
    }
}
