package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentDeviceDosingBinding
import com.aqua.aqualight.databinding.ItemDosingChannelCardBinding
import com.google.android.material.card.MaterialCardView

class DeviceDosingFragment : Fragment(R.layout.fragment_device_dosing) {

    private var _binding: FragmentDeviceDosingBinding? = null
    private val binding get() = _binding!!

    private var selectedPumpIndex: Int = 0

    private val runningPumpIndexes: MutableSet<Int> =
        mutableSetOf()

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceIp: String
        get() = requireArguments().getString(ARG_DEVICE_IP).orEmpty()

    private val deviceTitle: String
        get() = requireArguments().getString(ARG_DEVICE_TITLE).orEmpty()

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

        bindDefaultChannelCards()
        bindClicks()

        selectPump(
            pumpIndex = 0
        )

        renderPumpRunningIndicators()
    }

    private fun bindDefaultChannelCards() {
        bindEmptyChannelCard(
            cardBinding = binding.channelCard1,
            channelName = "Channel 1"
        )

        bindEmptyChannelCard(
            cardBinding = binding.channelCard2,
            channelName = "Channel 2"
        )

        bindEmptyChannelCard(
            cardBinding = binding.channelCard3,
            channelName = "Channel 3"
        )

        bindEmptyChannelCard(
            cardBinding = binding.channelCard4,
            channelName = "Channel 4"
        )
    }

    private fun bindEmptyChannelCard(
        cardBinding: ItemDosingChannelCardBinding,
        channelName: String
    ) {
        cardBinding.tvChannelName.text =
            channelName

        cardBinding.tvChannelState.text =
            "Not set"

        cardBinding.tvChannelHint.text =
            "Tap to configure this channel"

        cardBinding.tvChannelHint.visibility =
            View.VISIBLE

        cardBinding.tvChannelDose.text =
            "0.0 ml"

        cardBinding.tvChannelSchedule.text =
            "Every day"

        cardBinding.tvChannelStatus.text =
            "Not set up"

        cardBinding.tvChannelReservoir.text =
            "0 ml · 0d"

        cardBinding.tvChannelProgressValue.text =
            "0.00 / 0.00 ml"

        cardBinding.progressChannelDose.progress =
            0

        cardBinding.channelMetricsContainer.visibility =
            View.GONE

        cardBinding.channelProgressSection.visibility =
            View.GONE

        cardBinding.btnChannelQuickDose.visibility =
            View.GONE
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

        binding.channelCard1.cardChannelRoot.setOnClickListener {
            handlePumpClick(
                pumpIndex = 0
            )
        }

        binding.channelCard2.cardChannelRoot.setOnClickListener {
            handlePumpClick(
                pumpIndex = 1
            )
        }

        binding.channelCard3.cardChannelRoot.setOnClickListener {
            handlePumpClick(
                pumpIndex = 2
            )
        }

        binding.channelCard4.cardChannelRoot.setOnClickListener {
            handlePumpClick(
                pumpIndex = 3
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
            card = binding.channelCard1.cardChannelRoot,
            selected = selectedPumpIndex == 0
        )

        applyChannelCardSelection(
            card = binding.channelCard2.cardChannelRoot,
            selected = selectedPumpIndex == 1
        )

        applyChannelCardSelection(
            card = binding.channelCard3.cardChannelRoot,
            selected = selectedPumpIndex == 2
        )

        applyChannelCardSelection(
            card = binding.channelCard4.cardChannelRoot,
            selected = selectedPumpIndex == 3
        )
    }

    private fun applyChannelCardSelection(
        card: MaterialCardView,
        selected: Boolean
    ) {
        if (selected) {
            card.setStrokeColor(
                Color.parseColor("#315B7A")
            )

            card.setCardBackgroundColor(
                Color.parseColor("#111A35")
            )

            card.strokeWidth =
                dpToPx(1)
        } else {
            card.setStrokeColor(
                Color.parseColor("#24314F")
            )

            card.setCardBackgroundColor(
                Color.parseColor("#101426")
            )

            card.strokeWidth =
                dpToPx(1)
        }
    }

    private fun renderPumpRunningIndicators() {
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

    private fun openSelectedPumpSettings() {
        showComingNext(
            message = "Channel ${selectedPumpIndex + 1} settings will open here."
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