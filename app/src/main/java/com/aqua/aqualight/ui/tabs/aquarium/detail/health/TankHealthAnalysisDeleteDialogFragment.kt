package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetDeviceConfirmBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal class TankHealthAnalysisDeleteDialogFragment : DialogFragment() {

    private var resultSent: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = BottomSheetDeviceConfirmBinding.inflate(layoutInflater)

        binding.deviceConfirmSheetRoot.apply {
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

        binding.ivConfirmIcon.setImageResource(R.drawable.ic_tank_health_delete_24)
        binding.tvConfirmTitle.setText(R.string.tank_health_analysis_delete_title)
        binding.tvConfirmMessage.setText(R.string.tank_health_analysis_delete_message)
        binding.btnConfirmCancel.setText(R.string.tank_health_analysis_delete_cancel)
        binding.btnConfirmPrimary.setText(R.string.tank_health_analysis_delete_confirm)

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

    private fun publish(result: String) {
        if (resultSent) {
            return
        }
        resultSent = true
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(RESULT_KEY to result)
        )
    }

    companion object {
        const val REQUEST_KEY = "tank_health_analysis_delete_request"
        const val RESULT_KEY = "tank_health_analysis_delete_result"
        const val RESULT_CONFIRM = "confirm"
        const val RESULT_CANCEL = "cancel"

        private const val TAG = "TankHealthAnalysisDeleteDialogFragment"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) != null || fragmentManager.isStateSaved) {
                return
            }
            TankHealthAnalysisDeleteDialogFragment().show(fragmentManager, TAG)
        }
    }
}
