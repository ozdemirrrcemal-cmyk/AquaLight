package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentDeviceDosingCalibrationBinding

class DeviceDosingCalibrationFragment :
    Fragment(R.layout.fragment_device_dosing_calibration) {

    private var _binding: FragmentDeviceDosingCalibrationBinding? = null
    private val binding get() = _binding!!

    private var currentStepIndex: Int = 0

    private val channelIndex: Int
        get() = requireArguments().getInt(
            ARG_CHANNEL_INDEX,
            0
        ).coerceIn(
            minimumValue = 0,
            maximumValue = 3
        )

    private val channelNumber: Int
        get() = channelIndex + 1

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceIp: String
        get() = requireArguments().getString(ARG_DEVICE_IP).orEmpty()

    private val deviceTitle: String
        get() = requireArguments().getString(ARG_DEVICE_TITLE).orEmpty()

    private val steps: List<CalibrationStep> =
        listOf(
            CalibrationStep(
                title = "Name the liquid",
                description = "Give this dosing liquid a clear name before calibration.",
                hint = "Example: Micro, Macro, Iron, NPK or KH buffer.",
                primaryAction = "Continue"
            ),
            CalibrationStep(
                title = "Prime the tube",
                description = "Fill the tube with the dosing liquid until there is no air inside.",
                hint = "Later this step will run the selected pump while the button is held.",
                primaryAction = "Tube is filled"
            ),
            CalibrationStep(
                title = "Start calibration",
                description = "Place the tube outlet into a measuring cylinder, then start calibration.",
                hint = "The pump will dose a small amount. This may take a few seconds.",
                primaryAction = "Start calibration"
            ),
            CalibrationStep(
                title = "Enter measured amount",
                description = "Read the exact amount in the measuring cylinder and enter it below.",
                hint = "Use the value as accurately as possible. Recommended precision is 0.05 ml.",
                primaryAction = "Continue"
            ),
            CalibrationStep(
                title = "Dose test amount",
                description = "Make sure the measuring cylinder is empty and dry, then dose 4 ml.",
                hint = "This validates the calibration with a known amount.",
                primaryAction = "Dose 4 ml"
            ),
            CalibrationStep(
                title = "Confirm result",
                description = "Was exactly 4 ml dosed into the measuring cylinder?",
                hint = "Accept if the result is between 3.95 ml and 4.05 ml.",
                primaryAction = "Yes, save"
            )
        )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceDosingCalibrationBinding.bind(
            view
        )

        bindTopBar()
        bindSelectedPumpIndicator()
        bindClicks()
        renderStep()
    }

    private fun bindTopBar() {
        binding.tvTitle.text =
            "Channel $channelNumber Calibration"

        binding.btnBack.setOnClickListener {
            handleBackPressed()
        }
    }

    private fun bindSelectedPumpIndicator() {
        binding.selectedIndicatorPump1.visibility =
            if (channelIndex == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.selectedIndicatorPump2.visibility =
            if (channelIndex == 1) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.selectedIndicatorPump3.visibility =
            if (channelIndex == 2) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.selectedIndicatorPump4.visibility =
            if (channelIndex == 3) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun bindClicks() {
        binding.btnSecondaryAction.setOnClickListener {
            handleSecondaryAction()
        }

        binding.btnPrimaryAction.setOnClickListener {
            handlePrimaryAction()
        }
    }

    private fun renderStep() {
        val step = steps[currentStepIndex]

        binding.tvStepBadge.text =
            "STEP ${currentStepIndex + 1} OF ${steps.size}"

        binding.tvStepTitle.text =
            step.title

        binding.tvStepDescription.text =
            step.description

        binding.tvStepHint.text =
            step.hint

        binding.btnPrimaryAction.text =
            step.primaryAction

        binding.btnSecondaryAction.text =
            if (currentStepIndex == 0) {
                "Cancel"
            } else if (currentStepIndex == steps.lastIndex) {
                "Recalibrate"
            } else {
                "Back"
            }

        binding.inputLiquidNameLayout.visibility =
            if (currentStepIndex == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.inputMeasuredAmountLayout.visibility =
            if (currentStepIndex == 3) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.calibrationIllustrationView.stepIndex =
            currentStepIndex

        renderStepProgress()
    }

    private fun renderStepProgress() {
        val progressViews = listOf(
            binding.stepProgress1,
            binding.stepProgress2,
            binding.stepProgress3,
            binding.stepProgress4,
            binding.stepProgress5,
            binding.stepProgress6
        )

        progressViews.forEachIndexed { index, view ->
            view.setBackgroundColor(
                if (index <= currentStepIndex) {
                    Color.parseColor("#38BDF8")
                } else {
                    Color.parseColor("#24314F")
                }
            )
        }
    }

    private fun handlePrimaryAction() {
        when (currentStepIndex) {
            0 -> {
                val liquidName =
                    binding.etLiquidName.text?.toString()?.trim().orEmpty()

                if (liquidName.isBlank()) {
                    showComingNext(
                        message = "Please enter a liquid name."
                    )
                    return
                }

                goToNextStep()
            }

            3 -> {
                val measuredAmount =
                    binding.etMeasuredAmount.text?.toString()?.trim().orEmpty()

                if (measuredAmount.isBlank()) {
                    showComingNext(
                        message = "Please enter the measured amount."
                    )
                    return
                }

                goToNextStep()
            }

            steps.lastIndex -> {
                showComingNext(
                    message = "Calibration will be saved after device commands are connected."
                )

                findNavController().navigateUp()
            }

            else -> {
                goToNextStep()
            }
        }
    }

    private fun handleSecondaryAction() {
        if (currentStepIndex == 0) {
            findNavController().navigateUp()
            return
        }

        if (currentStepIndex == steps.lastIndex) {
            currentStepIndex = 2
            renderStep()
            return
        }

        currentStepIndex =
            (currentStepIndex - 1).coerceAtLeast(
                minimumValue = 0
            )

        renderStep()
    }

    private fun goToNextStep() {
        currentStepIndex =
            (currentStepIndex + 1).coerceAtMost(
                maximumValue = steps.lastIndex
            )

        renderStep()

        binding.calibrationScrollView.post {
            binding.calibrationScrollView.smoothScrollTo(
                0,
                0
            )
        }
    }

    private fun handleBackPressed() {
        if (currentStepIndex == 0) {
            findNavController().navigateUp()
            return
        }

        currentStepIndex =
            (currentStepIndex - 1).coerceAtLeast(
                minimumValue = 0
            )

        renderStep()
    }

    private fun showComingNext(
        message: String
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = BaseActivity.SnackType.NORMAL
        )
    }

    override fun onDestroyView() {
        _binding = null

        super.onDestroyView()
    }

    private data class CalibrationStep(
        val title: String,
        val description: String,
        val hint: String,
        val primaryAction: String
    )

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_CHANNEL_INDEX = "channelIndex"

        fun newInstance(
            deviceId: Long,
            deviceIp: String,
            deviceTitle: String,
            channelIndex: Int
        ): DeviceDosingCalibrationFragment {
            return DeviceDosingCalibrationFragment().apply {
                arguments = Bundle().apply {
                    putLong(
                        ARG_DEVICE_ID,
                        deviceId
                    )

                    putString(
                        ARG_DEVICE_IP,
                        deviceIp
                    )

                    putString(
                        ARG_DEVICE_TITLE,
                        deviceTitle
                    )

                    putInt(
                        ARG_CHANNEL_INDEX,
                        channelIndex.coerceIn(
                            minimumValue = 0,
                            maximumValue = 3
                        )
                    )
                }
            }
        }
    }
}