package com.aqua.aqualight.ui.tabs.devices.setup

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import com.aqua.aqualight.data.devices.discovery.DeviceDiscoveryService
import com.aqua.aqualight.data.devices.discovery.DeviceScanReason
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import com.aqua.aqualight.data.devices.setup.DeviceSetupWifiConnector
import com.aqua.aqualight.data.devices.setup.LegacyDeviceSetupClient
import com.aqua.aqualight.databinding.FragmentDeviceSetupBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DeviceSetupFragment : Fragment(R.layout.fragment_device_setup) {

    private var _binding: FragmentDeviceSetupBinding? = null
    private val binding get() = _binding!!

    private lateinit var devicesStore: DevicesDataStoreManager
    private lateinit var wifiConnector: DeviceSetupWifiConnector
    private lateinit var setupClient: LegacyDeviceSetupClient

    private var setupSsid: String = ""
    private var displayName: String = "Device"
    private var familyName: String = "Aqua device"
    private var expectedDeviceType: AquaDeviceType = AquaDeviceType.UNKNOWN

    private var isSettingUp: Boolean = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceSetupBinding.bind(view)

        devicesStore = DevicesDataStoreManager.create(requireContext())
        wifiConnector = DeviceSetupWifiConnector(requireContext())
        setupClient = LegacyDeviceSetupClient()

        readArgs()
        renderInitialState()
        setupClickListeners()
    }

    private fun readArgs() {
        displayName = requireArguments().getString(
            "displayName",
            "Device"
        )

        familyName = requireArguments().getString(
            "familyName",
            "Aqua device"
        )

        setupSsid = requireArguments().getString(
            "setupSsid",
            ""
        )

        val deviceTypeKey = requireArguments().getString(
            "deviceType",
            ""
        )

        expectedDeviceType = AquaDeviceType.entries.firstOrNull { type ->
            type.storageKey == deviceTypeKey
        } ?: AquaDeviceType.UNKNOWN
    }

    private fun renderInitialState() {
        binding.tvTitle.text = displayName
        binding.tvSubtitle.text = familyName
        binding.tvSetupSsid.text = setupSsid

        binding.tvDescription.text =
            "Enter your home Wi-Fi details. AquaLight will connect to the device, send the network settings, then find it on your home network."

        binding.tvStatus.text = "Ready to connect."
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            if (!isSettingUp) {
                findNavController().popBackStack()
            }
        }

        binding.btnStartSetup.setOnClickListener {
            startSetup()
        }
    }

    private fun startSetup() {
        if (isSettingUp) {
            return
        }

        val homeSsid = binding.etHomeWifiSsid.text
            ?.toString()
            ?.trim()
            .orEmpty()

        val homePassword = binding.etHomeWifiPassword.text
            ?.toString()
            .orEmpty()

        if (setupSsid.isBlank()) {
            showError("Device setup network is missing.")
            return
        }

        if (homeSsid.isBlank()) {
            binding.inputHomeWifiSsid.error = "Enter your home Wi-Fi name."
            return
        } else {
            binding.inputHomeWifiSsid.error = null
        }

        if (homePassword.isBlank()) {
            binding.inputHomeWifiPassword.error = "Enter your home Wi-Fi password."
            return
        } else {
            binding.inputHomeWifiPassword.error = null
        }

        isSettingUp = true
        setBusy(
            busy = true,
            status = "Connecting to $setupSsid..."
        )

        viewLifecycleOwner.lifecycleScope.launch {
            var setupConnection: DeviceSetupWifiConnector.SetupConnection? = null

            try {
                setupConnection = wifiConnector.connectToSetupNetwork(
                    ssid = setupSsid,
                    timeoutMs = 30_000L
                )

                if (_binding == null) {
                    return@launch
                }

                setBusy(
                    busy = true,
                    status = "Sending home Wi-Fi settings..."
                )

                val setupResult = setupClient.sendHomeWifiCredentials(
                    network = setupConnection.network,
                    homeSsid = homeSsid,
                    homePassword = homePassword,
                    disableSetupAccessPoint = true
                )

                setupConnection.close()
                setupConnection = null

                if (!setupResult.success) {
                    throw IllegalStateException(
                        setupResult.errorMessage
                            ?: "Device did not accept Wi-Fi settings."
                    )
                }

                if (_binding == null) {
                    return@launch
                }

                setBusy(
                    busy = true,
                    status = "Waiting for device on your home network..."
                )

                val discoveredDevice = waitForDeviceOnHomeNetwork()

                if (discoveredDevice == null) {
                    throw IllegalStateException(
                        "Device setup was sent, but the device was not found on your home network."
                    )
                }

                saveDiscoveredDevice(discoveredDevice)

                if (_binding == null) {
                    return@launch
                }

                setBusy(
                    busy = true,
                    status = "Device added successfully."
                )

                delay(600L)

                openDeviceMenu(discoveredDevice.id)
            } catch (exception: Exception) {
                exception.printStackTrace()

                if (_binding != null) {
                    showError(
                        exception.message ?: "Device setup failed."
                    )

                    setBusy(
                        busy = false,
                        status = "Setup failed. Check your Wi-Fi details and try again."
                    )
                }
            } finally {
                setupConnection?.close()
                isSettingUp = false

                if (_binding != null) {
                    setBusyControlsEnabled(true)
                }
            }
        }
    }

    private suspend fun waitForDeviceOnHomeNetwork(): DiscoveredAquaDevice? {
        val setupShortId = setupSsid
            .substringAfterLast(
                delimiter = "-",
                missingDelimiterValue = ""
            )
            .trim()

        repeat(10) {
            val result = DeviceDiscoveryService.scan(
                context = requireContext(),
                timeoutMs = 3_000L,
                reason = DeviceScanReason.MANUAL_SCAN
            )

            val match = result.devices.firstOrNull { device ->
                isExpectedDevice(
                    device = device,
                    setupShortId = setupShortId
                )
            }

            if (match != null) {
                return match
            }

            delay(2_000L)
        }

        return null
    }

    private fun isExpectedDevice(
        device: DiscoveredAquaDevice,
        setupShortId: String
    ): Boolean {
        val typeMatches = expectedDeviceType == AquaDeviceType.UNKNOWN ||
            device.deviceType == expectedDeviceType

        if (!typeMatches) {
            return false
        }

        if (setupShortId.isBlank()) {
            return true
        }

        val normalizedShortId = setupShortId.trimStart('0')
        val deviceIdText = device.id.toString()

        return deviceIdText.endsWith(setupShortId) ||
            (
                normalizedShortId.isNotBlank() &&
                    deviceIdText.endsWith(normalizedShortId)
                )
    }

    private suspend fun saveDiscoveredDevice(
        device: DiscoveredAquaDevice
    ) {
        val alreadyExists = devicesStore.deviceExists(
            id = device.id
        )

        if (alreadyExists) {
            devicesStore.updateDevicesLastSeen(
                discovered = listOf(
                    device.toLastSeenUpdate()
                )
            )
            return
        }

        val definition = AquaDeviceCatalog.findByType(
            type = device.deviceType
        ) ?: error("Unsupported device")

        val savedAquaName = definition.family.displayName
        val savedName = definition.displayName

        val serial = buildSerial(
            aquaName = savedAquaName,
            name = savedName,
            id = device.id
        )

        devicesStore.addDevice(
            id = device.id,
            aquaName = savedAquaName,
            name = savedName,
            ip = device.ip,
            serial = serial,
            firmwareBuild = device.firmwareBuild,

            deviceType = device.deviceType,

            udpVersion = device.udpVersion,
            tabLight = device.tabLight,
            tabTimer = device.tabTimer,
            tabTemperature = device.tabTemperature,

            productId = device.productId.orEmpty(),
            productFamily = device.productFamily.orEmpty(),
            productModel = device.productModel.orEmpty(),
            hardwareRevision = device.hardwareRevision.orEmpty(),
            firmwareVersion = device.firmwareVersion.orEmpty(),
            apiVersion = device.apiVersion,

            channelCount = device.channelCount,
            sensorCount = device.sensorCount,

            supportedFeatures = device.supportedFeatures,
            supportedScreens = device.supportedScreens
        )

        devicesStore.updateDevicesLastSeen(
            discovered = listOf(
                device.toLastSeenUpdate()
            )
        )
    }

    private fun DiscoveredAquaDevice.toLastSeenUpdate(): DevicesDataStoreManager.DeviceLastSeenUpdate {
        return DevicesDataStoreManager.DeviceLastSeenUpdate(
            id = id,
            ip = ip,
            firmwareBuild = firmwareBuild,

            deviceType = deviceType,

            udpVersion = udpVersion,
            tabLight = tabLight,
            tabTimer = tabTimer,
            tabTemperature = tabTemperature,

            productId = productId,
            productFamily = productFamily,
            productModel = productModel,
            hardwareRevision = hardwareRevision,
            firmwareVersion = firmwareVersion,
            apiVersion = apiVersion,

            channelCount = channelCount,
            sensorCount = sensorCount,

            supportedFeatures = supportedFeatures,
            supportedScreens = supportedScreens
        )
    }

    private fun buildSerial(
        aquaName: String,
        name: String,
        id: Long
    ): String {
        val aquaInitial = aquaName.firstOrNull()
            ?.uppercaseChar()
            ?: 'X'

        val nameInitial = name.firstOrNull()
            ?.uppercaseChar()
            ?: 'X'

        return "$aquaInitial$nameInitial-$id"
    }

    private fun openDeviceMenu(
        deviceId: Long
    ) {
        val args = Bundle().apply {
            putLong("deviceId", deviceId)
        }

        findNavController().navigate(
            R.id.deviceMenuFragment,
            args
        )
    }

    private fun setBusy(
        busy: Boolean,
        status: String
    ) {
        binding.progressSetup.isVisible = busy
        binding.tvStatus.text = status
        setBusyControlsEnabled(!busy)
    }

    private fun setBusyControlsEnabled(
        enabled: Boolean
    ) {
        binding.etHomeWifiSsid.isEnabled = enabled
        binding.etHomeWifiPassword.isEnabled = enabled
        binding.btnStartSetup.isEnabled = enabled
        binding.btnBack.isEnabled = enabled

        binding.btnStartSetup.alpha = if (enabled) {
            1f
        } else {
            0.55f
        }
    }

    private fun showError(
        message: String
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = BaseActivity.SnackType.ERROR
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}