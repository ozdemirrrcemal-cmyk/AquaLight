package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentDeviceDosingBinding
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class DeviceDosingFragment : Fragment(R.layout.fragment_device_dosing) {

    private var _binding: FragmentDeviceDosingBinding? = null
    private val binding get() = _binding!!

    private var selectedPumpIndex: Int = 0
    private var selectedCalibrationPumpIndex: Int = 0

    private val calibrationNudgeStep: Float = 0.003f

    private val runningPumpIndexes: MutableSet<Int> =
        mutableSetOf()

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceIp: String
        get() = requireArguments().getString(ARG_DEVICE_IP).orEmpty()

    private val deviceTitle: String
        get() = requireArguments().getString(ARG_DEVICE_TITLE).orEmpty()

    private val canEditDeviceName: Boolean
        get() = requireArguments().getBoolean(
            ARG_CAN_EDIT_DEVICE_NAME,
            false
        )

    private val userDeviceName: String
        get() = requireArguments().getString(ARG_USER_DEVICE_NAME).orEmpty()

    private val defaultDeviceTitle: String
        get() = requireArguments().getString(ARG_DEFAULT_DEVICE_TITLE).orEmpty()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceDosingBinding.bind(
            view
        )

        bindStaticScreen()
        bindClicks()

        selectPump(
            pumpIndex = 0
        )

        renderPumpRunningIndicators()

        if (PUMP_CALIBRATION_MODE) {
            bindPumpIndicatorCalibrationForPhone()
        }
    }

    private fun bindStaticScreen() {
        val title = deviceTitle.ifBlank {
            userDeviceName.ifBlank {
                defaultDeviceTitle.ifBlank {
                    "DosePro 4"
                }
            }
        }

        binding.tvDosingTitle.text = title
        binding.tvDosingSubtitle.text = "4 channel smart dosing pump"

        binding.tvConnectionStatus.text = if (deviceIp.isBlank()) {
            "Offline"
        } else {
            "Ready"
        }

        binding.cardDosingSummary.isClickable =
            canEditDeviceName

        binding.cardDosingSummary.isFocusable =
            canEditDeviceName
    }

    private fun bindClicks() {
        binding.hotspotPump1.setOnClickListener {
            handlePumpClick(
                pumpIndex = 0
            )
        }

        binding.hotspotPump2.setOnClickListener {
            handlePumpClick(
                pumpIndex = 1
            )
        }

        binding.hotspotPump3.setOnClickListener {
            handlePumpClick(
                pumpIndex = 2
            )
        }

        binding.hotspotPump4.setOnClickListener {
            handlePumpClick(
                pumpIndex = 3
            )
        }

        binding.cardChannel1.setOnClickListener {
            handlePumpClick(
                pumpIndex = 0
            )
        }

        binding.cardChannel2.setOnClickListener {
            handlePumpClick(
                pumpIndex = 1
            )
        }

        binding.cardChannel3.setOnClickListener {
            handlePumpClick(
                pumpIndex = 2
            )
        }

        binding.cardChannel4.setOnClickListener {
            handlePumpClick(
                pumpIndex = 3
            )
        }

        binding.btnManualRun.setOnClickListener {
            toggleSelectedPumpRunningForPreview()
        }

        binding.btnCalibration.setOnClickListener {
            showComingNext(
                message = "Calibration will be added for Pump ${selectedPumpIndex + 1}."
            )
        }

        binding.btnSchedule.setOnClickListener {
            showComingNext(
                message = "Dosing schedule will be added for Pump ${selectedPumpIndex + 1}."
            )
        }
    }

    private fun handlePumpClick(
        pumpIndex: Int
    ) {
        selectPump(
            pumpIndex = pumpIndex
        )

        openSelectedPumpSettings()
    }

    private fun selectPump(
        pumpIndex: Int
    ) {
        selectedPumpIndex = pumpIndex.coerceIn(
            minimumValue = 0,
            maximumValue = 3
        )

        renderSelectedChannelCard()
    }

    private fun renderSelectedChannelCard() {
        applyChannelCardSelection(
            card = binding.cardChannel1,
            selected = selectedPumpIndex == 0
        )

        applyChannelCardSelection(
            card = binding.cardChannel2,
            selected = selectedPumpIndex == 1
        )

        applyChannelCardSelection(
            card = binding.cardChannel3,
            selected = selectedPumpIndex == 2
        )

        applyChannelCardSelection(
            card = binding.cardChannel4,
            selected = selectedPumpIndex == 3
        )
    }

    private fun applyChannelCardSelection(
        card: MaterialCardView,
        selected: Boolean
    ) {
        if (selected) {
            card.setStrokeColor(
                Color.parseColor("#38BDF8")
            )

            card.strokeWidth =
                dpToPx(2)
        } else {
            card.setStrokeColor(
                Color.parseColor("#24314F")
            )

            card.strokeWidth =
                dpToPx(1)
        }
    }

    private fun toggleSelectedPumpRunningForPreview() {
        val isRunning = runningPumpIndexes.contains(
            selectedPumpIndex
        )

        setPumpRunning(
            pumpIndex = selectedPumpIndex,
            running = !isRunning
        )

        selectPump(
            pumpIndex = selectedPumpIndex
        )
    }

    private fun setPumpRunning(
        pumpIndex: Int,
        running: Boolean
    ) {
        val safePumpIndex = pumpIndex.coerceIn(
            minimumValue = 0,
            maximumValue = 3
        )

        if (running) {
            runningPumpIndexes.add(
                safePumpIndex
            )
        } else {
            runningPumpIndexes.remove(
                safePumpIndex
            )
        }

        renderPumpRunningIndicators()
    }

    private fun renderPumpRunningIndicators() {
        if (PUMP_CALIBRATION_MODE) {
            binding.indicatorPump1.visibility = View.VISIBLE
            binding.indicatorPump2.visibility = View.VISIBLE
            binding.indicatorPump3.visibility = View.VISIBLE
            binding.indicatorPump4.visibility = View.VISIBLE
            return
        }

        binding.indicatorPump1.visibility =
            if (runningPumpIndexes.contains(0)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.indicatorPump2.visibility =
            if (runningPumpIndexes.contains(1)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.indicatorPump3.visibility =
            if (runningPumpIndexes.contains(2)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.indicatorPump4.visibility =
            if (runningPumpIndexes.contains(3)) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindPumpIndicatorCalibrationForPhone() {
        binding.tvPumpCalibrationValues.visibility =
            View.VISIBLE

        binding.pumpCalibrationControls.visibility =
            View.VISIBLE

        binding.indicatorPump1.visibility =
            View.VISIBLE

        binding.indicatorPump2.visibility =
            View.VISIBLE

        binding.indicatorPump3.visibility =
            View.VISIBLE

        binding.indicatorPump4.visibility =
            View.VISIBLE

        binding.indicatorPump1.bringToFront()
        binding.indicatorPump2.bringToFront()
        binding.indicatorPump3.bringToFront()
        binding.indicatorPump4.bringToFront()

        bindDraggablePumpIndicator(
            pumpIndex = 0,
            indicator = binding.indicatorPump1,
            xGuide = binding.guidePump1Center
        )

        bindDraggablePumpIndicator(
            pumpIndex = 1,
            indicator = binding.indicatorPump2,
            xGuide = binding.guidePump2Center
        )

        bindDraggablePumpIndicator(
            pumpIndex = 2,
            indicator = binding.indicatorPump3,
            xGuide = binding.guidePump3Center
        )

        bindDraggablePumpIndicator(
            pumpIndex = 3,
            indicator = binding.indicatorPump4,
            xGuide = binding.guidePump4Center
        )

        binding.btnPumpXMinus.setOnClickListener {
            nudgeSelectedPumpX(
                delta = -calibrationNudgeStep
            )
        }

        binding.btnPumpXPlus.setOnClickListener {
            nudgeSelectedPumpX(
                delta = calibrationNudgeStep
            )
        }

        binding.btnPumpYMinus.setOnClickListener {
            nudgeAllPumpY(
                delta = -calibrationNudgeStep
            )
        }

        binding.btnPumpYPlus.setOnClickListener {
            nudgeAllPumpY(
                delta = calibrationNudgeStep
            )
        }

        updatePumpCalibrationText()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindDraggablePumpIndicator(
        pumpIndex: Int,
        indicator: View,
        xGuide: View
    ) {
        var touchOffsetFromCenterX = 0f
        var touchOffsetFromCenterY = 0f

        indicator.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    selectedCalibrationPumpIndex =
                        pumpIndex

                    view.parent.requestDisallowInterceptTouchEvent(
                        true
                    )

                    val containerLocation = IntArray(2)

                    binding.pumpVisualContainer.getLocationOnScreen(
                        containerLocation
                    )

                    val touchXInContainer =
                        event.rawX - containerLocation[0]

                    val touchYInContainer =
                        event.rawY - containerLocation[1]

                    val currentCenterX =
                        view.x + view.width / 2f

                    val currentCenterY =
                        view.y + view.height / 2f

                    touchOffsetFromCenterX =
                        touchXInContainer - currentCenterX

                    touchOffsetFromCenterY =
                        touchYInContainer - currentCenterY

                    updatePumpCalibrationText()

                    true
                }

                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP -> {
                    view.parent.requestDisallowInterceptTouchEvent(
                        true
                    )

                    val containerLocation = IntArray(2)

                    binding.pumpVisualContainer.getLocationOnScreen(
                        containerLocation
                    )

                    val touchXInContainer =
                        event.rawX - containerLocation[0]

                    val touchYInContainer =
                        event.rawY - containerLocation[1]

                    val centerX =
                        touchXInContainer - touchOffsetFromCenterX

                    val centerY =
                        touchYInContainer - touchOffsetFromCenterY

                    val xPercent =
                        (centerX / binding.pumpVisualContainer.width)
                            .coerceIn(
                                minimumValue = 0f,
                                maximumValue = 1f
                            )

                    val yPercent =
                        (centerY / binding.pumpVisualContainer.height)
                            .coerceIn(
                                minimumValue = 0f,
                                maximumValue = 1f
                            )

                    setGuidelinePercent(
                        guideline = xGuide,
                        percent = xPercent
                    )

                    setGuidelinePercent(
                        guideline = binding.guidePumpIndicatorY,
                        percent = yPercent
                    )

                    updatePumpCalibrationText()

                    true
                }

                else -> false
            }
        }
    }

    private fun nudgeSelectedPumpX(
        delta: Float
    ) {
        val guide = when (selectedCalibrationPumpIndex) {
            0 -> binding.guidePump1Center
            1 -> binding.guidePump2Center
            2 -> binding.guidePump3Center
            else -> binding.guidePump4Center
        }

        val params =
            guide.layoutParams as ConstraintLayout.LayoutParams

        setGuidelinePercent(
            guideline = guide,
            percent = params.guidePercent + delta
        )

        updatePumpCalibrationText()
    }

    private fun nudgeAllPumpY(
        delta: Float
    ) {
        val params =
            binding.guidePumpIndicatorY.layoutParams as ConstraintLayout.LayoutParams

        setGuidelinePercent(
            guideline = binding.guidePumpIndicatorY,
            percent = params.guidePercent + delta
        )

        updatePumpCalibrationText()
    }

    private fun setGuidelinePercent(
        guideline: View,
        percent: Float
    ) {
        val params =
            guideline.layoutParams as ConstraintLayout.LayoutParams

        params.guidePercent =
            percent.coerceIn(
                minimumValue = 0f,
                maximumValue = 1f
            )

        guideline.layoutParams = params
    }

    private fun updatePumpCalibrationText() {
        binding.tvPumpCalibrationValues.text =
            "Selected=CH${selectedCalibrationPumpIndex + 1}  " +
                "Y=${formatGuidePercent(binding.guidePumpIndicatorY)}  " +
                "CH1=${formatGuidePercent(binding.guidePump1Center)}  " +
                "CH2=${formatGuidePercent(binding.guidePump2Center)}  " +
                "CH3=${formatGuidePercent(binding.guidePump3Center)}  " +
                "CH4=${formatGuidePercent(binding.guidePump4Center)}"
    }

    private fun formatGuidePercent(
        guideline: View
    ): String {
        val params =
            guideline.layoutParams as ConstraintLayout.LayoutParams

        return String.format(
            Locale.US,
            "%.3f",
            params.guidePercent
        )
    }

    private fun openSelectedPumpSettings() {
        showComingNext(
            message = "Pump ${selectedPumpIndex + 1} settings will open here."
        )
    }

    private fun showComingNext(
        message: String
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = BaseActivity.SnackType.NORMAL
        )
    }

    private fun dpToPx(
        value: Int
    ): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    override fun onDestroyView() {
        _binding = null

        super.onDestroyView()
    }

    companion object {
        private const val PUMP_CALIBRATION_MODE = true

        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_CAN_EDIT_DEVICE_NAME = "canEditDeviceName"
        private const val ARG_USER_DEVICE_NAME = "userDeviceName"
        private const val ARG_DEFAULT_DEVICE_TITLE = "defaultDeviceTitle"

        fun newInstance(
            deviceId: Long,
            deviceIp: String,
            deviceTitle: String,
            canEditDeviceName: Boolean,
            userDeviceName: String,
            defaultDeviceTitle: String
        ): DeviceDosingFragment {
            return DeviceDosingFragment().apply {
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

                    putBoolean(
                        ARG_CAN_EDIT_DEVICE_NAME,
                        canEditDeviceName
                    )

                    putString(
                        ARG_USER_DEVICE_NAME,
                        userDeviceName
                    )

                    putString(
                        ARG_DEFAULT_DEVICE_TITLE,
                        defaultDeviceTitle
                    )
                }
            }
        }
    }
}