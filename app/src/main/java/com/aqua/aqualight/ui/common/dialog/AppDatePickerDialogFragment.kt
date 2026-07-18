package com.aqua.aqualight.ui.common.dialog

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import java.util.Calendar

/** Framework-recreatable date picker that returns its value through Fragment Result. */
class AppDatePickerDialogFragment : DialogFragment() {

    private var resultSent = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = args.getLong(ARG_INITIAL_MILLIS)
        }
        return DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                publish(RESULT_SELECTED, calendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            args.getLong(ARG_MIN_MILLIS, NO_BOUND)
                .takeUnless { it == NO_BOUND }
                ?.let(datePicker::setMinDate)
            args.getLong(ARG_MAX_MILLIS, NO_BOUND)
                .takeUnless { it == NO_BOUND }
                ?.let(datePicker::setMaxDate)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        publish(RESULT_CANCELLED, null)
        super.onCancel(dialog)
    }

    private fun publish(result: String, millis: Long?) {
        if (resultSent) return
        resultSent = true
        val args = requireArguments()
        val payload = bundleOf(
            RESULT_KEY to result,
            RESULT_PAYLOAD_ID to args.getString(ARG_PAYLOAD_ID).orEmpty()
        )
        millis?.let { payload.putLong(RESULT_MILLIS, it) }
        parentFragmentManager.setFragmentResult(
            args.getString(ARG_REQUEST_KEY).orEmpty(),
            payload
        )
        dismissAllowingStateLoss()
    }

    companion object {
        const val RESULT_KEY = "app_date_picker_result"
        const val RESULT_MILLIS = "app_date_picker_millis"
        const val RESULT_PAYLOAD_ID = "app_date_picker_payload_id"
        const val RESULT_SELECTED = "selected"
        const val RESULT_CANCELLED = "cancelled"

        private const val ARG_INITIAL_MILLIS = "arg_initial_millis"
        private const val ARG_MIN_MILLIS = "arg_min_millis"
        private const val ARG_MAX_MILLIS = "arg_max_millis"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_PAYLOAD_ID = "arg_payload_id"
        private const val NO_BOUND = Long.MIN_VALUE
        private const val TAG_PREFIX = "AppDatePickerDialogFragment:"

        fun show(
            fragmentManager: FragmentManager,
            requestKey: String,
            initialMillis: Long,
            payloadId: String = "",
            minMillis: Long? = null,
            maxMillis: Long? = null
        ) {
            val tag = TAG_PREFIX + requestKey
            if (fragmentManager.findFragmentByTag(tag) != null || fragmentManager.isStateSaved) return
            AppDatePickerDialogFragment().apply {
                arguments = bundleOf(
                    ARG_INITIAL_MILLIS to initialMillis,
                    ARG_MIN_MILLIS to (minMillis ?: NO_BOUND),
                    ARG_MAX_MILLIS to (maxMillis ?: NO_BOUND),
                    ARG_REQUEST_KEY to requestKey,
                    ARG_PAYLOAD_ID to payloadId
                )
            }.show(fragmentManager, tag)
        }
    }
}
