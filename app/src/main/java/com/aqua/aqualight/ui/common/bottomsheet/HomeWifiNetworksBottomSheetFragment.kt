package com.aqua.aqualight.ui.common.bottomsheet

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetHomeWifiNetworksBinding
import com.aqua.aqualight.databinding.ItemHomeWifiNetworkBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HomeWifiNetworksBottomSheetFragment : BottomSheetDialogFragment(
    R.layout.bottom_sheet_home_wifi_networks
) {

    private var _binding: BottomSheetHomeWifiNetworksBinding? = null
    private val binding get() = _binding!!

    data class HomeWifiNetworkItem(
        val ssid: String,
        val rssi: Int,
        val bssid: String = "",
        val channel: Int = 0,
        val security: String = ""
    )

    private val networks: List<HomeWifiNetworkItem>
        get() {
            val ssids = arguments?.getStringArray(ARG_SSIDS).orEmpty()
            val rssis = arguments?.getIntArray(ARG_RSSIS) ?: intArrayOf()
            val bssids = arguments?.getStringArray(ARG_BSSIDS).orEmpty()
            val channels = arguments?.getIntArray(ARG_CHANNELS) ?: intArrayOf()
            val securities = arguments?.getStringArray(ARG_SECURITIES).orEmpty()

            return ssids.mapIndexed { index, ssid ->
                HomeWifiNetworkItem(
                    ssid = ssid,
                    rssi = rssis.getOrNull(index) ?: DEFAULT_RSSI,
                    bssid = bssids.getOrNull(index).orEmpty(),
                    channel = channels.getOrNull(index) ?: 0,
                    security = securities.getOrNull(index).orEmpty()
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = BottomSheetHomeWifiNetworksBinding.bind(view)

        binding.homeWifiNetworksScroll.apply {
            isNestedScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }

        renderNetworks()
    }

    override fun onStart() {
        super.onStart()

        val bottomSheetDialog = dialog as? BottomSheetDialog ?: return

        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        bottomSheet.setBackgroundColor(Color.TRANSPARENT)

        val sheetHeight = (
            resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO
        ).toInt()

        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = sheetHeight
        }

        bottomSheet.requestLayout()

        bottomSheetDialog.behavior.apply {
            isFitToContents = true
            state = BottomSheetBehavior.STATE_EXPANDED
            peekHeight = sheetHeight
            skipCollapsed = true
            isHideable = true
            isDraggable = true
        }
    }

    private fun renderNetworks() {
        binding.homeWifiNetworksContainer.removeAllViews()

        networks.forEach { network ->
            binding.homeWifiNetworksContainer.addView(
                createNetworkRow(
                    network = network
                )
            )
        }
    }

    private fun createNetworkRow(
        network: HomeWifiNetworkItem
    ): View {
        val itemBinding = ItemHomeWifiNetworkBinding.inflate(
            layoutInflater,
            binding.homeWifiNetworksContainer,
            false
        )

        itemBinding.tvWifiSsid.text = network.ssid

        itemBinding.tvWifiSignal.text = signalLabel(
            rssi = network.rssi
        )

        itemBinding.ivWifiSignalIcon.setImageResource(
            signalIcon(
                rssi = network.rssi
            )
        )

        itemBinding.root.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_SSID to network.ssid,
                    RESULT_BSSID to network.bssid,
                    RESULT_CHANNEL to network.channel
                )
            )

            dismiss()
        }

        return itemBinding.root
    }

    private fun signalLabel(
        rssi: Int
    ): String {
        return when {
            rssi >= EXCELLENT_RSSI -> getString(R.string.wifi_signal_excellent)
            rssi >= STRONG_RSSI -> getString(R.string.wifi_signal_strong)
            rssi >= GOOD_RSSI -> getString(R.string.wifi_signal_good)
            else -> getString(R.string.wifi_signal_weak)
        }
    }

    private fun signalIcon(
        rssi: Int
    ): Int {
        return when {
            rssi >= EXCELLENT_RSSI -> R.drawable.ic_wifi_signal_4
            rssi >= STRONG_RSSI -> R.drawable.ic_wifi_signal_3
            rssi >= GOOD_RSSI -> R.drawable.ic_wifi_signal_2
            else -> R.drawable.ic_wifi_signal_1
        }
    }

    override fun onDestroyView() {
        _binding = null

        super.onDestroyView()
    }

    companion object {
        private const val TAG = "HomeWifiNetworksBottomSheetFragment"

        private const val ARG_SSIDS = "ssids"
        private const val ARG_RSSIS = "rssis"
        private const val ARG_BSSIDS = "bssids"
        private const val ARG_CHANNELS = "channels"
        private const val ARG_SECURITIES = "securities"

        private const val DEFAULT_RSSI = -100

        private const val EXCELLENT_RSSI = -55
        private const val STRONG_RSSI = -67
        private const val GOOD_RSSI = -75

        private const val SHEET_HEIGHT_RATIO = 0.92f

        const val REQUEST_KEY = "home_wifi_network_result"
        const val RESULT_SSID = "ssid"
        const val RESULT_BSSID = "bssid"
        const val RESULT_CHANNEL = "channel"

        fun show(
            fragmentManager: FragmentManager,
            networks: List<HomeWifiNetworkItem>
        ) {
            if (fragmentManager.findFragmentByTag(TAG) != null) {
                return
            }

            HomeWifiNetworksBottomSheetFragment().apply {
                arguments = bundleOf(
                    ARG_SSIDS to networks.map { network ->
                        network.ssid
                    }.toTypedArray(),
                    ARG_RSSIS to networks.map { network ->
                        network.rssi
                    }.toIntArray(),
                    ARG_BSSIDS to networks.map { network ->
                        network.bssid
                    }.toTypedArray(),
                    ARG_CHANNELS to networks.map { network ->
                        network.channel
                    }.toIntArray(),
                    ARG_SECURITIES to networks.map { network ->
                        network.security
                    }.toTypedArray()
                )
            }.show(
                fragmentManager,
                TAG
            )
        }
    }
}