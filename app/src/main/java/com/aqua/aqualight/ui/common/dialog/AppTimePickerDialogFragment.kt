package com.aqua.aqualight.ui.common.dialog

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import java.text.DateFormat
import java.util.Calendar

/** Framework-recreatable time picker that returns its value through Fragment Result. */
class AppTimePickerDialogFragment : DialogFragment() {

    private var resultSent = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = args.getLong(ARG_INITIAL_MILLIS)
        }
        return TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                publish(RESULT_SELECTED, calendar.timeInMillis)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            DateFormat.is24HourFormat(requireContext())
        )
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
        const val RESULT_KEY = "app_time_picker_result"
        const val RESULT_MILLIS = "app_time_picker_millis"
        const val RESULT_PAYLOAD_ID = "app_time_picker_payload_id"
        const val RESULT_SELECTED = "selected"
        const val RESULT_CANCELLED = "cancelled"

        private const val ARG_INITIAL_MILLIS = "arg_initial_millis"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_PAYLOAD_ID = "arg_payload_id"
        private const val TAG_PREFIX = "AppTimePickerDialogFragment:"

        fun show(
            fragmentManager: FragmentManager,
            requestKey: String,
            initialMillis: Long,
            payloadId: String = ""
        ) {
            val tag = TAG_PREFIX + requestKey
            if (fragmentManager.findFragmentByTag(tag) != null || fragmentManager.isStateSaved) return
            AppTimePickerDialogFragment().apply {
                arguments = bundleOf(
                    ARG_INITIAL_MILLIS to initialMillis,
                    ARG_REQUEST_KEY to requestKey,
                    ARG_PAYLOAD_ID to payloadId
                )
            }.show(fragmentManager, tag)
        }
    }
}
