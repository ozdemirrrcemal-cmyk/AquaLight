package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.presence.DeviceConnectionStatus
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.presence.DeviceStatusState
import com.aqua.aqualight.databinding.FragmentDeviceDosingBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.navigation.fragment.navArgs

class DeviceDosingFragment : Fragment(R.layout.fragment_device_dosing) {

    private val args: DeviceDosingFragmentArgs by navArgs()


    private var _binding: FragmentDeviceDosingBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
        get() = args.deviceId

    private val deviceTitle: String
        get() = args.deviceTitle.ifBlank {
            "Dosing"
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDeviceDosingBinding.bind(view)

        setupHeader()
        observeDeviceStatus()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = deviceTitle
            )
        )
    }

    private fun observeDeviceStatus() {
        DevicePresenceMonitor.start(
            requireContext().applicationContext
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                DevicePresenceMonitor.statuses.collectLatest { statuses ->
                    bindDeviceStatus(
                        statuses[deviceId]
                    )
                }
            }
        }
    }

    private fun bindDeviceStatus(
        statusState: DeviceStatusState?
    ) {
        binding.tvEmptyTitle.text =
            deviceTitle

        binding.tvEmptyMessage.text =
            buildStatusMessage(
                statusState
            )
    }

    private fun buildStatusMessage(
        statusState: DeviceStatusState?
    ): String {
        val status =
            statusState?.status ?: DeviceConnectionStatus.UNKNOWN

        val statusText =
            when (status) {
                DeviceConnectionStatus.ONLINE -> "Online"
                DeviceConnectionStatus.CHECKING -> "Checking connection"
                DeviceConnectionStatus.STALE -> "Connection is stale"
                DeviceConnectionStatus.OFFLINE -> "Offline"
                DeviceConnectionStatus.UNKNOWN -> "Unknown"
            }

        val resolvedIp =
            statusState?.ip.orEmpty().ifBlank {
                args.deviceIp
            }

        return buildString {
            append("Device ID: ")
            append(deviceId)
            append("\n")
            append("Status: ")
            append(statusText)

            if (resolvedIp.isNotBlank()) {
                append("\nIP: ")
                append(resolvedIp)
            }

            append("\n\n")

            if (status == DeviceConnectionStatus.ONLINE) {
                append("The dosing controller screen will be built here. Live commands remain protected by the central connection guard.")
            } else {
                append("This device is not ready for live control. Keep it powered on and connected to the same network.")
            }
        }
    }

    override fun onDestroyView() {
        _binding =
            null

        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
        const val ARG_DEVICE_IP = "deviceIp"
        const val ARG_DEVICE_TITLE = "deviceTitle"
    }
}
