package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankHealthAnalysisDetailBinding
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.utils.DialogType

class TankHealthAnalysisDetailFragment :
    Fragment(R.layout.fragment_tank_health_analysis_detail) {

    private var _binding: FragmentTankHealthAnalysisDetailBinding? = null
    private val binding get() = _binding!!

    private val tankId: Long
        get() = requireArguments().getLong(ARG_TANK_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(tankId > 0L) {
            "TankHealthAnalysisDetailFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthAnalysisDetailBinding.bind(view)

        setupHeader()
        setupDeleteResult()
        setupActions()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health_analysis_detail),
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun setupDeleteResult() {
        parentFragmentManager.setFragmentResultListener(
            DELETE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(ConfirmDialogFragment.RESULT_KEY) ==
                ConfirmDialogFragment.RESULT_CONFIRM
            ) {
                // Persistence is intentionally deferred to the data-integration stage.
                findNavController().navigateUp()
            }
        }
    }

    private fun setupActions() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnDeleteRecord.setOnClickListener {
            ConfirmDialogFragment.show(
                fragmentManager = parentFragmentManager,
                request = ConfirmDialogFragment.Request(
                    title = getString(R.string.tank_health_analysis_delete_title),
                    message = getString(R.string.tank_health_analysis_delete_message),
                    confirmText = getString(R.string.tank_health_analysis_delete_confirm),
                    cancelText = getString(R.string.tank_health_analysis_delete_cancel),
                    presentation = ConfirmDialogFragment.Presentation(
                        type = DialogType.ERROR,
                        destructive = true
                    ),
                    resultTarget = ConfirmDialogFragment.ResultTarget(
                        requestKey = DELETE_REQUEST_KEY,
                        actionId = DELETE_ACTION_ID
                    )
                )
            )
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ARG_TANK_ID = "tankId"
        const val DELETE_REQUEST_KEY = "tank_health_analysis_delete_request"
        const val DELETE_ACTION_ID = "delete_analysis_record"
    }
}
