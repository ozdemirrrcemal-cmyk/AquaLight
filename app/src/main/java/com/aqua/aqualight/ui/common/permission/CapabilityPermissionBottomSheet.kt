package com.aqua.aqualight.ui.common.permission

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetCapabilityPermissionBinding
import com.aqua.aqualight.platform.permissions.AppCapability
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Process-safe rationale/settings sheet shared by every runtime-permission flow.
 *
 * The sheet accepts arguments only and returns the selected action through Fragment
 * Result. It intentionally has no constructor parameters or callback fields. All
 * capability-specific visual content is resolved by [CapabilityPermissionUiSpecResolver].
 */
class CapabilityPermissionBottomSheet : BottomSheetDialogFragment(
    R.layout.bottom_sheet_capability_permission
) {

    private var _binding: BottomSheetCapabilityPermissionBinding? = null
    private val binding get() = _binding!!
    private var resultSent = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetCapabilityPermissionBinding.bind(view)

        val capability = requireCapability()
        val mode = requireMode()
        val uiSpec = CapabilityPermissionUiSpecResolver.resolve(capability, mode)

        binding.imgPermissionIcon.setImageResource(uiSpec.iconRes)
        binding.tvPermissionTitle.setText(uiSpec.titleRes)
        binding.tvPermissionMessage.setText(uiSpec.messageRes)
        binding.btnPermissionPrimary.setText(uiSpec.primaryActionRes)

        binding.permissionStatusBadge.isVisible = uiSpec.statusBadgeRes != null
        uiSpec.statusBadgeRes?.let(binding.imgPermissionStatus::setImageResource)

        binding.btnPermissionPrimary.setOnClickListener {
            sendResult(
                if (mode == Mode.RATIONALE) RESULT_ALLOW else RESULT_OPEN_SETTINGS
            )
            dismiss()
        }
        binding.btnPermissionCancel.setOnClickListener {
            sendResult(RESULT_CANCEL)
            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        sendResult(RESULT_CANCEL)
        super.onCancel(dialog)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun requireCapability(): AppCapability {
        return AppCapability.valueOf(requireArguments().getString(ARG_CAPABILITY).orEmpty())
    }

    private fun requireMode(): Mode {
        return Mode.valueOf(requireArguments().getString(ARG_MODE).orEmpty())
    }

    private fun sendResult(result: String) {
        if (resultSent) return
        resultSent = true
        parentFragmentManager.setFragmentResult(
            requireArguments().getString(ARG_REQUEST_KEY).orEmpty(),
            bundleOf(RESULT_KEY to result)
        )
    }

    enum class Mode {
        RATIONALE,
        OPEN_SETTINGS
    }

    companion object {
        const val RESULT_KEY = "capability_permission_result"
        const val RESULT_ALLOW = "allow"
        const val RESULT_OPEN_SETTINGS = "open_settings"
        const val RESULT_CANCEL = "cancel"

        private const val ARG_CAPABILITY = "arg_capability"
        private const val ARG_MODE = "arg_mode"
        private const val ARG_REQUEST_KEY = "arg_request_key"

        fun newInstance(
            capability: AppCapability,
            mode: Mode,
            requestKey: String
        ): CapabilityPermissionBottomSheet {
            return CapabilityPermissionBottomSheet().apply {
                arguments = bundleOf(
                    ARG_CAPABILITY to capability.name,
                    ARG_MODE to mode.name,
                    ARG_REQUEST_KEY to requestKey
                )
            }
        }
    }
}
