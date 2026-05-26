package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private var primeCommandRunning: Boolean = false

    private val completedDeviceActionSteps: MutableSet<Int> =
        mutableSetOf()

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
                description = "Name this dosing liquid before calibration.",
                hint = "Example: Micro, Macro, Iron, NPK or KH buffer.",
                miniStatus = "Setup"
            ),
            CalibrationStep(
                title = "Prime the tube",
                description = "Fill the tube with liquid until no air remains inside.",
                hint = "Press and hold Prime until liquid reaches the tube outlet.",
                miniStatus = "Prime",
                deviceActionTitle = "Tube priming",
                deviceActionDescription = "Hold the button to run the selected pump. Release to stop.",
                deviceActionText = "Hold Prime"
            ),
            CalibrationStep(
                title = "Start calibration",
                description = "Place the tube outlet into a measuring cylinder.",
                hint = "The pump will dose a small calibration amount.",
                miniStatus = "Calibrate",
                deviceActionTitle = "Calibration dose",
                deviceActionDescription = "Start the calibration dosing command for this channel.",
                deviceActionText = "Start"
            ),
            CalibrationStep(
                title = "Enter measured amount",
                description = "Read the exact amount in the measuring cylinder.",
                hint = "Enter the value as accurately as possible. Recommended precision is 0.05 ml.",
                miniStatus = "Measure"
            ),
            CalibrationStep(
                title = "Dose test amount",
                description = "Empty and dry the measuring cylinder, then dose 4 ml.",
                hint = "This validates the calibration with a known amount.",
                miniStatus = "Test",
                deviceActionTitle = "Validation dose",
                deviceActionDescription = "Dose exactly 4 ml using the new calibration value.",
                deviceActionText = "Dose 4 ml"
            ),
            CalibrationStep(
                title = "Confirm result",
                description = "Was exactly 4 ml dosed into the measuring cylinder?",
                hint = "Accept if the result is between 3.95 ml and 4.05 ml.",
                miniStatus = "Result"
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

        bindSystemBack()
        bindTopBar()
        bindSelectedPumpIndicator()
        bindKeyboardMode()
        bindInputDoneActions()
        bindClicks()
        renderStep()
    }

    private fun bindSystemBack() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPressed()
                }
            }
        )
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
            if (channelIndex == 0) View.VISIBLE else View.GONE

        binding.selectedIndicatorPump2.visibility =
            if (channelIndex == 1) View.VISIBLE else View.GONE

        binding.selectedIndicatorPump3.visibility =
            if (channelIndex == 2) View.VISIBLE else View.GONE

        binding.selectedIndicatorPump4.visibility =
            if (channelIndex == 3) View.VISIBLE else View.GONE
    }

    private fun bindKeyboardMode() {
        ViewCompat.setOnApplyWindowInsetsListener(
            binding.root
        ) { _, insets ->
            val keyboardVisible =
                insets.isVisible(
                    WindowInsetsCompat.Type.ime()
                )

            renderKeyboardMode(
                keyboardVisible = keyboardVisible
            )

            insets
        }
    }

    private fun renderKeyboardMode(
        keyboardVisible: Boolean
    ) {
        val inputStep =
            currentStepIndex == 0 ||
                currentStepIndex == 3

        val compactMode =
            keyboardVisible && inputStep

        binding.pumpVisualContainer.visibility =
            if (compactMode) View.GONE else View.VISIBLE

        binding.stepProgressRow.visibility =
            if (compactMode) View.GONE else View.VISIBLE

        binding.footerContainer.visibility =
            if (compactMode) View.GONE else View.VISIBLE
    }

    private fun bindInputDoneActions() {
        binding.etLiquidName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                handlePrimaryAction()
                true
            } else {
                false
            }
        }

        binding.etMeasuredAmount.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                handlePrimaryAction()
                true
            } else {
                false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindClicks() {
        binding.btnSecondaryAction.setOnClickListener {
            handleSecondaryAction()
        }

        binding.btnPrimaryAction.setOnClickListener {
            handlePrimaryAction()
        }

        binding.btnStepDeviceAction.setOnTouchListener { view, event ->
            if (currentStepIndex != 1) {
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    startPrimeCommand()
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    stopPrimeCommand()

                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        view.performClick()
                        markStepDeviceActionCompleted()
                    }

                    true
                }

                else -> true
            }
        }

        binding.btnStepDeviceAction.setOnClickListener {
            if (currentStepIndex != 1) {
                handleStepDeviceAction()
            }
        }
    }

    private fun renderStep() {
        val step = steps[currentStepIndex]

        binding.tvStepBadge.text =
            "STEP ${currentStepIndex + 1} OF ${steps.size}"

        binding.tvStepMiniStatus.text =
            step.miniStatus

        binding.tvStepTitle.text =
            step.title

        binding.tvStepDescription.text =
            step.description

        binding.tvStepHint.text =
            step.hint

        binding.btnSecondaryAction.text =
            when {
                currentStepIndex == 0 -> "Cancel"
                currentStepIndex == steps.lastIndex -> "Recalibrate"
                else -> "Back"
            }

        binding.inputLiquidNameLayout.visibility =
            if (currentStepIndex == 0) View.VISIBLE else View.GONE

        binding.inputMeasuredAmountLayout.visibility =
            if (currentStepIndex == 3) View.VISIBLE else View.GONE

        binding.calibrationIllustrationView.stepIndex =
            currentStepIndex

        renderStepProgress()
        renderStepDeviceAction()
        refreshKeyboardMode()
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

    private fun renderStepDeviceAction() {
        val step = steps[currentStepIndex]

        val hasDeviceAction =
            step.deviceActionText != null

        binding.cardStepDeviceAction.visibility =
            if (hasDeviceAction) View.VISIBLE else View.GONE

        binding.tvDeviceActionTitle.text =
            step.deviceActionTitle.orEmpty()

        binding.tvDeviceActionDescription.text =
            step.deviceActionDescription.orEmpty()

        val deviceActionCompleted =
            completedDeviceActionSteps.contains(
                currentStepIndex
            )

        binding.btnStepDeviceAction.text =
            if (deviceActionCompleted) {
                "Done"
            } else {
                step.deviceActionText.orEmpty()
            }

        binding.btnStepDeviceAction.isEnabled =
            !deviceActionCompleted

        binding.btnStepDeviceAction.alpha =
            if (deviceActionCompleted) {
                0.55f
            } else {
                1f
            }

        val primaryEnabled =
            !hasDeviceAction || deviceActionCompleted

        binding.btnPrimaryAction.text =
            if (currentStepIndex == steps.lastIndex) {
                "Yes, save"
            } else {
                "Continue"
            }

        binding.btnPrimaryAction.isEnabled =
            primaryEnabled

        binding.btnPrimaryAction.alpha =
            if (primaryEnabled) {
                1f
            } else {
                0.45f
            }
    }

    private fun startPrimeCommand() {
        if (primeCommandRunning) {
            return
        }

        hideKeyboard()

        primeCommandRunning = true

        showComingNext(
            message = "Pump priming started for Channel $channelNumber."
        )

        // Buraya sonra cihaz komutu bağlanacak:
        // start pump / manual run / prime start
    }

    private fun stopPrimeCommand() {
        if (!primeCommandRunning) {
            return
        }

        primeCommandRunning = false

        showComingNext(
            message = "Pump priming stopped for Channel $channelNumber."
        )

        // Buraya sonra cihaz komutu bağlanacak:
        // stop pump / manual run stop / prime stop
    }

    private fun handleStepDeviceAction() {
        hideKeyboard()

        when (currentStepIndex) {
            2 -> {
                showComingNext(
                    message = "Calibration command will be connected for Channel $channelNumber."
                )

                markStepDeviceActionCompleted()
            }

            4 -> {
                showComingNext(
                    message = "Dose 4 ml command will be connected for Channel $channelNumber."
                )

                markStepDeviceActionCompleted()
            }
        }
    }

    private fun markStepDeviceActionCompleted() {
        completedDeviceActionSteps.add(
            currentStepIndex
        )

        renderStep()
    }

    private fun handlePrimaryAction() {
        when (currentStepIndex) {
            0 -> {
                val liquidName =
                    binding.etLiquidName.text
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                if (liquidName.isBlank()) {
                    showComingNext(
                        message = "Please enter a liquid name."
                    )
                    return
                }

                goToNextStep()
            }

            3 -> {
                val measuredAmountText =
                    binding.etMeasuredAmount.text
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                val measuredAmount =
                    measuredAmountText.toFloatOrNull()

                if (
                    measuredAmountText.isBlank() ||
                    measuredAmount == null ||
                    measuredAmount <= 0f
                ) {
                    showComingNext(
                        message = "Please enter a valid measured amount."
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
        hideKeyboard()

        if (currentStepIndex == 0) {
            findNavController().navigateUp()
            return
        }

        if (currentStepIndex == steps.lastIndex) {
            completedDeviceActionSteps.removeAll(
                listOf(
                    2,
                    4
                )
            )

            currentStepIndex = 2
            renderStep()
            scrollToTop()
            return
        }

        currentStepIndex =
            (currentStepIndex - 1).coerceAtLeast(
                minimumValue = 0
            )

        renderStep()
        scrollToTop()
    }

    private fun goToNextStep() {
        hideKeyboard()

        currentStepIndex =
            (currentStepIndex + 1).coerceAtMost(
                maximumValue = steps.lastIndex
            )

        renderStep()
        scrollToTop()
    }

    private fun handleBackPressed() {
        hideKeyboard()

        if (currentStepIndex == 0) {
            findNavController().navigateUp()
            return
        }

        currentStepIndex =
            (currentStepIndex - 1).coerceAtLeast(
                minimumValue = 0
            )

        renderStep()
        scrollToTop()
    }

    private fun scrollToTop() {
        binding.calibrationScrollView.post {
            binding.calibrationScrollView.smoothScrollTo(
                0,
                0
            )
        }
    }

    private fun refreshKeyboardMode() {
        val keyboardVisible =
            ViewCompat.getRootWindowInsets(
                binding.root
            )?.isVisible(
                WindowInsetsCompat.Type.ime()
            ) == true

        renderKeyboardMode(
            keyboardVisible = keyboardVisible
        )
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            requireContext().getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

        inputMethodManager.hideSoftInputFromWindow(
            binding.root.windowToken,
            0
        )

        binding.etLiquidName.clearFocus()
        binding.etMeasuredAmount.clearFocus()
        binding.root.clearFocus()
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
        if (primeCommandRunning) {
            stopPrimeCommand()
        }

        _binding = null

        super.onDestroyView()
    }

    private data class CalibrationStep(
        val title: String,
        val description: String,
        val hint: String,
        val miniStatus: String,
        val deviceActionTitle: String? = null,
        val deviceActionDescription: String? = null,
        val deviceActionText: String? = null
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