package com.aqua.aqualight.ui.tabs.devices.detail.timer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.databinding.FragmentDeviceTimerBinding
import kotlinx.coroutines.launch

class DeviceTimerFragment : Fragment(R.layout.fragment_device_timer) {

    private var _binding: FragmentDeviceTimerBinding? = null
    private val binding get() = _binding!!

    private val repository = TimerDeviceRepository()

    private lateinit var devicesStore: DevicesDataStoreManager

    private var renderer: TimerDashboardRenderer? = null

    private var currentUserDeviceName: String = ""
    private var currentDisplayedTitle: String = ""

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

        _binding = FragmentDeviceTimerBinding.bind(view)

        devicesStore = DevicesDataStoreManager.create(
            requireContext()
        )

        renderer = TimerDashboardRenderer(
            binding = binding
        )

        bindStaticScreen()
        bindClicks()
        loadTimerDashboard()
    }

    private fun bindStaticScreen() {
        currentUserDeviceName = userDeviceName

        currentDisplayedTitle = deviceTitle.ifBlank {
            defaultDeviceTitle.ifBlank {
                "Timer Controller"
            }
        }

        binding.tvTimerTitle.text = currentDisplayedTitle
        binding.tvTimerSubtitle.text = "4 channel smart timer"

        binding.cardTimerDeviceSummary.isClickable =
            canEditDeviceName

        binding.cardTimerDeviceSummary.isFocusable =
            canEditDeviceName
    }

    private fun bindClicks() {
        binding.cardTimerDeviceSummary.setOnClickListener {
            if (canEditDeviceName) {
                showDeviceNameBottomSheet()
            }
        }

        binding.cardOutlet1.setOnClickListener {
            // Sonraki adım: Outlet 1 ayar bottom sheet.
        }

        binding.cardOutlet2.setOnClickListener {
            // Sonraki adım: Outlet 2 ayar bottom sheet.
        }

        binding.cardOutlet3.setOnClickListener {
            // Sonraki adım: Outlet 3 ayar bottom sheet.
        }

        binding.cardOutlet4.setOnClickListener {
            // Sonraki adım: Outlet 4 ayar bottom sheet.
        }

        binding.cardOutlet1Power.setOnClickListener {
            // Sonraki adım: Outlet 1 hızlı ON/OFF.
        }

        binding.cardOutlet2Power.setOnClickListener {
            // Sonraki adım: Outlet 2 hızlı ON/OFF.
        }

        binding.cardOutlet3Power.setOnClickListener {
            // Sonraki adım: Outlet 3 hızlı ON/OFF.
        }

        binding.cardOutlet4Power.setOnClickListener {
            // Sonraki adım: Outlet 4 hızlı ON/OFF.
        }
    }

    private fun showDeviceNameBottomSheet() {
        TimerDeviceNameBottomSheet(
            fragment = this,
            currentName = currentUserDeviceName.ifBlank {
                currentDisplayedTitle
            },
            fallbackName = defaultDeviceTitle.ifBlank {
                "Timer Controller"
            },
            onSave = {
                newName, sheet ->
                saveDeviceName(
                    newName = newName,
                    sheet = sheet
                )
            }
        ).show()
    }

    private fun saveDeviceName(
        newName: String,
        sheet: TimerDeviceNameBottomSheet
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                devicesStore.updateDevice(
                    id = deviceId,
                    name = newName
                )
            }

            if (_binding == null) {
                return@launch
            }

            result.onSuccess {
                currentUserDeviceName = newName
                currentDisplayedTitle = newName

                binding.tvTimerTitle.text = newName

                sheet.closeAfterSave()
            }.onFailure {
                sheet.showSaveError(
                    message = "Device name could not be saved."
                )
            }
        }
    }

    private fun loadTimerDashboard() {
        if (
            deviceIp.isBlank() ||
            deviceIp == "0.0.0.0"
        ) {
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                repository.fetchTimerDashboardData(
                    ipAddress = deviceIp
                )
            }

            if (_binding == null) {
                return@launch
            }

            result.onSuccess { data ->
                binding.tvTimerOnlineStatus.text = "Online"

                renderer?.render(
                    data = data
                )
            }.onFailure {
                binding.tvTimerOnlineStatus.text = "Offline"

                renderer?.clear()
            }
        }
    }

    override fun onDestroyView() {
        renderer = null
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
        ): DeviceTimerFragment {
            return DeviceTimerFragment().apply {
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

        fun newInstance(
            deviceId: Long
        ): DeviceTimerFragment {
            return newInstance(
                deviceId = deviceId,
                deviceIp = "",
                deviceTitle = "",
                canEditDeviceName = false,
                userDeviceName = "",
                defaultDeviceTitle = ""
            )
        }
    }
}