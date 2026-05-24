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
        val rssi: Int
    )

    private val networks: List<HomeWifiNetworkItem>
        get() {
            val ssids = arguments?.getStringArray(ARG_SSIDS).orEmpty()
            val rssis = arguments?.getIntArray(ARG_RSSIS) ?: intArrayOf()

            return ssids.mapIndexed { index, ssid ->
                HomeWifiNetworkItem(
                    ssid = ssid,
                    rssi = rssis.getOrNull(index) ?: -100
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

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
            resources.displayMetrics.heightPixels * 0.92f
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
                createNetworkRow(network)
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
        itemBinding.tvWifiSignal.text = signalLabel(network.rssi)

        itemBinding.root.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_SSID to network.ssid
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
            rssi >= -55 -> getString(R.string.wifi_signal_excellent)
            rssi >= -67 -> getString(R.string.wifi_signal_strong)
            rssi >= -75 -> getString(R.string.wifi_signal_good)
            else -> getString(R.string.wifi_signal_weak)
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

        const val REQUEST_KEY = "home_wifi_network_result"
        const val RESULT_SSID = "ssid"

        fun show(
            fragmentManager: FragmentManager,
            networks: List<HomeWifiNetworkItem>
        ) {
            if (fragmentManager.findFragmentByTag(TAG) != null) {
                return
            }

            HomeWifiNetworksBottomSheetFragment().apply {
                arguments = bundleOf(
                    ARG_SSIDS to networks.map { it.ssid }.toTypedArray(),
                    ARG_RSSIS to networks.map { it.rssi }.toIntArray()
                )
            }.show(
                fragmentManager,
                TAG
            )
        }
    }
}