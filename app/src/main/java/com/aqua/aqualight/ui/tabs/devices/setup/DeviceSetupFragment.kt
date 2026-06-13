package com.aqua.aqualight.ui.tabs.devices.setup

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.DeviceIdentityMatcher
import com.aqua.aqualight.data.devices.DeviceStoreWriter
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaProductKey
import com.aqua.aqualight.data.devices.catalog.AquaSetupSsid
import com.aqua.aqualight.data.devices.discovery.DeviceDiscoveryService
import com.aqua.aqualight.data.devices.discovery.DeviceScanReason
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import com.aqua.aqualight.data.devices.setup.DeviceSetupWifiConnector
import com.aqua.aqualight.data.devices.setup.HomeWifiConnectionWaiter
import com.aqua.aqualight.data.devices.setup.AquaDeviceSetupClient
import com.aqua.aqualight.databinding.FragmentDeviceSetupBinding
import com.aqua.aqualight.ui.common.bottomsheet.HomeWifiNetworksBottomSheetFragment
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper
import com.aqua.aqualight.ui.tabs.devices.navigation.DeviceRouterBackStackMode
import com.aqua.aqualight.ui.tabs.devices.navigation.navigateToDeviceRouter
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.navigation.fragment.navArgs

class DeviceSetupFragment : Fragment(R.layout.fragment_device_setup) {

    private val args: DeviceSetupFragmentArgs by navArgs()

    private var _binding: FragmentDeviceSetupBinding? = null
    private val binding get() = _binding!!

    private lateinit var wifiConnector: DeviceSetupWifiConnector
    private lateinit var setupClient: AquaDeviceSetupClient
    private lateinit var homeWifiWaiter: HomeWifiConnectionWaiter
    private lateinit var deviceStoreWriter: DeviceStoreWriter

    private var setupConnection: DeviceSetupWifiConnector.SetupConnection? = null

    private var setupSsid: String = ""
    private var displayName: String = "Device"
    private var familyName: String = "Aqua device"
    private var expectedProductId: String = ""
    private var expectedProductKey: AquaProductKey = AquaProductKey.UNKNOWN
    private var expectedCategory: AquaDeviceCategory = AquaDeviceCategory.UNKNOWN
    private var expectedSetupCode: String = ""
    private var setupShortId: String = ""
    private var setupContractValid: Boolean = false
    private var selectedHomeSsid: String = ""

    private var isSettingUp = false
    private var isScanningNetworks = false

    private data class HomeWifiInput(
        val ssid: String,
        val password: String
    )

    private enum class SetupUiStep {
        WIFI,
        CONNECT,
        DONE
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceSetupBinding.bind(view)

        val devicesStore = DevicesDataStoreManager.create(
            requireContext()
        )

        wifiConnector = DeviceSetupWifiConnector(requireContext())
        setupClient = AquaDeviceSetupClient()
        homeWifiWaiter = HomeWifiConnectionWaiter(requireContext())
        deviceStoreWriter = DeviceStoreWriter(devicesStore)

        readArgs()
        setupHeader()
        renderInitialState()
        setupClickListeners()
        setupHomeWifiBottomSheetResultListener()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = displayName,
                onBackClick = {
                    if (!isSettingUp && !isScanningNetworks) {
                        findNavController().popBackStack()
                    }
                }
            )
        )
    }

    private fun readArgs() {
        displayName = args.displayName

        familyName = args.familyName

        val rawSetupSsid = args.setupSsid.trim()

        val parsedSetupSsid = AquaSetupSsid.parse(
            ssid = rawSetupSsid
        )

        setupSsid = parsedSetupSsid?.rawSsid ?: rawSetupSsid

        expectedProductId = args.productId

        expectedProductKey = AquaProductKey.fromStorageKey(
            value = args.productKey
        )

        expectedCategory = AquaDeviceCategory.fromStorageKey(
            value = args.category
        )

        expectedSetupCode = args.setupCode.ifBlank {
            parsedSetupSsid?.setupCode.orEmpty()
        }.trim()

        setupShortId = args.setupShortId.ifBlank {
            parsedSetupSsid?.shortId.orEmpty()
        }.trim()

        val definition = AquaDeviceCatalog.findDefinition(
            productId = expectedProductId,
            productKey = expectedProductKey,
            category = expectedCategory
        ) ?: AquaDeviceCatalog.findBySetupCode(
            setupCode = expectedSetupCode
        )

        if (definition != null) {
            expectedProductId = definition.productId
            expectedProductKey = definition.productKey
            expectedCategory = definition.category
            expectedSetupCode = definition.setupCode

            if (displayName.isBlank() || displayName == DEFAULT_DEVICE_NAME) {
                displayName = definition.displayName
            }

            if (familyName.isBlank() || familyName == DEFAULT_FAMILY_NAME) {
                familyName = definition.productFamily
            }
        }

        setupContractValid = parsedSetupSsid != null &&
            definition != null &&
            expectedSetupCode.equals(
                other = parsedSetupSsid.setupCode,
                ignoreCase = true
            ) &&
            setupShortId.equals(
                other = parsedSetupSsid.shortId,
                ignoreCase = true
            )
    }

    private fun renderInitialState() {
        binding.tvTitle.text = displayName
        binding.tvSubtitle.text = familyName
        binding.tvSetupSsid.text = setupSsid
        binding.tvSetupNetworkMeta.text = getString(
            R.string.device_setup_network_meta,
            expectedSetupCode.ifBlank { "—" },
            setupShortId.ifBlank { "—" }
        )
        binding.tvStatus.text = if (setupContractValid) {
            getString(R.string.device_setup_choose_home_wifi)
        } else {
            getString(R.string.device_setup_invalid_setup_network)
        }
        binding.etHomeWifiSsid.setText("")

        binding.ivDeviceImage.setImageResource(
            DeviceIconMapper.iconFor(expectedCategory)
        )

        binding.ivDeviceImage.contentDescription = displayName

        renderSetupProgress(
            activeStep = SetupUiStep.WIFI
        )

        if (!setupContractValid) {
            setSetupInputControlsEnabled(false)
        }
    }

    private fun setupClickListeners() {
        binding.inputHomeWifiSsid.setEndIconOnClickListener {
            scanHomeNetworks()
        }

        binding.etHomeWifiSsid.setOnClickListener {
            scanHomeNetworks()
        }

        binding.btnStartSetup.setOnClickListener {
            startSetup()
        }
    }

    private fun setupHomeWifiBottomSheetResultListener() {
        parentFragmentManager.setFragmentResultListener(
            HomeWifiNetworksBottomSheetFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val ssid = result.getString(
                HomeWifiNetworksBottomSheetFragment.RESULT_SSID
            ).orEmpty()

            if (ssid.isBlank()) {
                return@setFragmentResultListener
            }

            selectedHomeSsid = ssid

            binding.etHomeWifiSsid.setText(ssid)
            binding.inputHomeWifiSsid.error = null

            binding.tvStatus.text = getString(
                R.string.device_setup_selected_network,
                ssid
            )

            renderSetupProgress(
                activeStep = SetupUiStep.WIFI
            )
        }
    }

    private fun scanHomeNetworks() {
        if (isScanningNetworks || isSettingUp) {
            return
        }

        if (!ensureSetupContractReady()) {
            return
        }

        isScanningNetworks = true

        renderSetupProgress(
            activeStep = SetupUiStep.WIFI
        )

        setBusy(
            busy = true,
            status = getString(
                R.string.device_setup_connecting_to_setup,
                setupSsid
            )
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val connection = getOrCreateSetupConnection()

                if (!isViewReady()) {
                    return@launch
                }

                setBusy(
                    busy = true,
                    status = getString(R.string.device_setup_scanning_home_networks)
                )

                val networks = setupClient.scanHomeWifiNetworks(
                    network = connection.network
                )

                if (!isViewReady()) {
                    return@launch
                }

                if (networks.isEmpty()) {
                    showError(
                        getString(R.string.device_setup_no_networks_found)
                    )

                    setBusy(
                        busy = false,
                        status = getString(R.string.device_setup_no_networks_status)
                    )

                    return@launch
                }

                showWifiNetworksBottomSheet(
                    networks = networks
                )

                setBusy(
                    busy = false,
                    status = getString(R.string.device_setup_select_home_wifi_status)
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                if (isViewReady()) {
                    showError(
                        exception.message
                            ?: getString(R.string.device_setup_scan_error)
                    )

                    setBusy(
                        busy = false,
                        status = getString(R.string.device_setup_scan_failed)
                    )
                }
            } finally {
                isScanningNetworks = false

                if (isViewReady() && !isSettingUp) {
                    setBusyControlsEnabled(true)
                }
            }
        }
    }

    private suspend fun getOrCreateSetupConnection(): DeviceSetupWifiConnector.SetupConnection {
        setupConnection?.let { connection ->
            return connection
        }

        val connection = wifiConnector.connectToSetupNetwork(
            ssid = setupSsid,
            password = SETUP_AP_PASSWORD,
            timeoutMs = 30_000L
        )

        setupConnection = connection
        return connection
    }

    private fun startSetup() {
        if (isSettingUp) {
            return
        }

        if (!ensureSetupContractReady()) {
            return
        }

        val input = readAndValidateHomeWifiInput()
            ?: return

        isSettingUp = true

        renderSetupProgress(
            activeStep = SetupUiStep.CONNECT
        )

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_preparing)
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                runSetupFlow(
                    input = input
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                if (isViewReady()) {
                    showError(
                        exception.message
                            ?: getString(R.string.device_setup_generic_failed)
                    )

                    setBusy(
                        busy = false,
                        status = getString(R.string.device_setup_failed)
                    )
                }
            } finally {
                isSettingUp = false

                if (isViewReady()) {
                    setBusyControlsEnabled(true)
                }
            }
        }
    }

    private fun readAndValidateHomeWifiInput(): HomeWifiInput? {
        val homeSsid = selectedHomeSsid.ifBlank {
            binding.etHomeWifiSsid.text
                ?.toString()
                ?.trim()
                .orEmpty()
        }

        val homePassword = binding.etHomeWifiPassword.text
            ?.toString()
            .orEmpty()

        if (!setupContractValid) {
            showError(
                getString(R.string.device_setup_invalid_setup_network)
            )
            return null
        }

        if (homeSsid.isBlank()) {
            binding.inputHomeWifiSsid.error = getString(
                R.string.device_setup_validation_select_wifi
            )
            return null
        }

        binding.inputHomeWifiSsid.error = null

        if (homePassword.isBlank()) {
            binding.inputHomeWifiPassword.error = getString(
                R.string.device_setup_validation_password
            )
            return null
        }

        binding.inputHomeWifiPassword.error = null

        return HomeWifiInput(
            ssid = homeSsid,
            password = homePassword
        )
    }

    private suspend fun runSetupFlow(
        input: HomeWifiInput
    ) {
        val connection = getOrCreateSetupConnection()

        if (!isViewReady()) {
            return
        }

        renderSetupProgress(
            activeStep = SetupUiStep.CONNECT
        )

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_sending_credentials)
        )

        val setupResult = setupClient.sendHomeWifiCredentials(
            network = connection.network,
            setupSsid = setupSsid,
            setupPassword = SETUP_AP_PASSWORD,
            homeSsid = input.ssid,
            homePassword = input.password,
            disableSetupAccessPoint = false
        )

        if (!setupResult.success) {
            throw IllegalStateException(
                setupResult.errorMessage
                    ?: getString(R.string.device_setup_not_accepted)
            )
        }

        if (!waitForDeviceClientConnection(connection, setupResult.responseBody)) {
            throw IllegalStateException(
                getString(R.string.device_setup_connection_failed)
            )
        }

        if (!isViewReady()) {
            return
        }

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_closing_setup_network)
        )

        closeSetupAccessPoint(
            connection = connection,
            input = input
        )

        closeSetupConnection()

        if (!isViewReady()) {
            return
        }

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_waiting_phone_home_wifi)
        )

        val phoneReturnedToHomeWifi = homeWifiWaiter.waitUntilHomeWifiReady(
            expectedSsid = input.ssid,
            setupSsid = setupSsid,
            timeoutMs = 75_000L
        )

        if (!phoneReturnedToHomeWifi) {
            throw IllegalStateException(
                getString(
                    R.string.device_setup_phone_not_home_wifi,
                    input.ssid
                )
            )
        }

        if (!isViewReady()) {
            return
        }

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_finding_device)
        )

        delay(7_000L)

        val discoveredDevice = waitForDeviceOnHomeNetwork()
            ?: throw IllegalStateException(
                getString(R.string.device_setup_device_not_found)
            )

        val savedDeviceId = deviceStoreWriter.saveDiscoveredDevice(
            device = discoveredDevice
        )

        if (!isViewReady()) {
            return
        }

        renderSetupProgress(
            activeStep = SetupUiStep.DONE
        )

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_success)
        )

        delay(700L)

        openDeviceMenu(
            deviceId = savedDeviceId
        )
    }

    private suspend fun waitForDeviceClientConnection(
        connection: DeviceSetupWifiConnector.SetupConnection,
        firstResponseBody: String?
    ): Boolean {
        val firstStatus = setupClient.parseDeviceWifiStatus(
            responseText = firstResponseBody
        )

        if (firstStatus.connected) {
            return true
        }

        if (!isViewReady()) {
            return false
        }

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_checking_connection)
        )

        repeat(15) {
            val status = runCatching {
                setupClient.readDeviceWifiStatus(
                    network = connection.network
                )
            }.getOrNull()

            if (status?.connected == true) {
                return true
            }

            if (isViewReady()) {
                binding.tvStatus.text = getString(
                    R.string.device_setup_joining_home_wifi
                )
            }

            delay(3_000L)
        }

        return false
    }

    private suspend fun closeSetupAccessPoint(
        connection: DeviceSetupWifiConnector.SetupConnection,
        input: HomeWifiInput
    ) {
        val closeApResult = setupClient.sendHomeWifiCredentials(
            network = connection.network,
            setupSsid = setupSsid,
            setupPassword = SETUP_AP_PASSWORD,
            homeSsid = input.ssid,
            homePassword = input.password,
            disableSetupAccessPoint = true
        )

        if (!closeApResult.success) {
            throw IllegalStateException(
                closeApResult.errorMessage
                    ?: getString(R.string.device_setup_close_ap_failed)
            )
        }
    }

    private suspend fun waitForDeviceOnHomeNetwork(): DiscoveredAquaDevice? {
        repeat(20) {
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

            delay(3_000L)
        }

        return null
    }

    private fun isExpectedDevice(
        device: DiscoveredAquaDevice,
        setupShortId: String
    ): Boolean {
        val productMatches = expectedProductId.isBlank() ||
            device.productId.equals(
                other = expectedProductId,
                ignoreCase = true
            )

        if (!productMatches) {
            return false
        }

        val categoryMatches = expectedCategory == AquaDeviceCategory.UNKNOWN ||
            device.category == expectedCategory

        if (!categoryMatches) {
            return false
        }

        val setupCodeMatches = expectedSetupCode.isBlank() ||
            device.setupCode.equals(
                other = expectedSetupCode,
                ignoreCase = true
            )

        if (!setupCodeMatches) {
            return false
        }

        if (setupShortId.isBlank()) {
            return true
        }

        return DeviceIdentityMatcher.matchesSetupShortId(
            discoveredDevice = device,
            setupShortId = setupShortId
        )
    }

    private fun ensureSetupContractReady(): Boolean {
        if (setupSsid.isBlank()) {
            showError(
                getString(R.string.device_setup_missing_setup_network)
            )
            return false
        }

        if (!setupContractValid) {
            showError(
                getString(R.string.device_setup_invalid_setup_network)
            )
            return false
        }

        return true
    }

    private fun showWifiNetworksBottomSheet(
        networks: List<AquaDeviceSetupClient.HomeWifiNetwork>
    ) {
        HomeWifiNetworksBottomSheetFragment.show(
            fragmentManager = parentFragmentManager,
            networks = networks.map { network ->
                HomeWifiNetworksBottomSheetFragment.HomeWifiNetworkItem(
                    ssid = network.ssid,
                    rssi = network.rssi
                )
            }
        )
    }

    private fun renderSetupProgress(
        activeStep: SetupUiStep
    ) {
        styleStep(
            card = binding.cardStepDevice,
            number = binding.tvStepDeviceNumber,
            label = binding.tvStepDeviceLabel,
            completed = true,
            active = false
        )

        styleStep(
            card = binding.cardStepWifi,
            number = binding.tvStepWifiNumber,
            label = binding.tvStepWifiLabel,
            completed = activeStep == SetupUiStep.CONNECT ||
                activeStep == SetupUiStep.DONE,
            active = activeStep == SetupUiStep.WIFI
        )

        styleStep(
            card = binding.cardStepConnect,
            number = binding.tvStepConnectNumber,
            label = binding.tvStepConnectLabel,
            completed = activeStep == SetupUiStep.DONE,
            active = activeStep == SetupUiStep.CONNECT
        )

        styleStep(
            card = binding.cardStepDone,
            number = binding.tvStepDoneNumber,
            label = binding.tvStepDoneLabel,
            completed = activeStep == SetupUiStep.DONE,
            active = activeStep == SetupUiStep.DONE
        )

        if (activeStep == SetupUiStep.DONE) {
            binding.statusDot.setBackgroundResource(R.drawable.bg_status_dot_green)
        } else {
            binding.statusDot.setBackgroundResource(R.drawable.bg_setup_dot_blue)
        }
    }

    private fun styleStep(
        card: MaterialCardView,
        number: TextView,
        label: TextView,
        completed: Boolean,
        active: Boolean
    ) {
        val backgroundColor: Int
        val strokeColor: Int
        val textColor: Int

        when {
            completed -> {
                backgroundColor = Color.parseColor("#123526")
                strokeColor = Color.parseColor("#31D07E")
                textColor = Color.parseColor("#31D07E")
            }

            active -> {
                backgroundColor = Color.parseColor("#143A5F")
                strokeColor = Color.parseColor("#2B95F6")
                textColor = Color.parseColor("#6CB7FF")
            }

            else -> {
                backgroundColor = Color.parseColor("#10233A")
                strokeColor = Color.parseColor("#243D5C")
                textColor = Color.parseColor("#8FA4BE")
            }
        }

        card.setCardBackgroundColor(backgroundColor)
        card.strokeColor = strokeColor
        number.setTextColor(textColor)
        label.setTextColor(textColor)
    }

    private fun openDeviceMenu(
        deviceId: Long
    ) {
        findNavController().navigateToDeviceRouter(
            deviceId = deviceId,
            deviceIp = "",
            deviceTitle = displayName,
            backStackMode = DeviceRouterBackStackMode.RETURN_TO_DEVICES
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
        setSetupInputControlsEnabled(
            enabled = enabled
        )

        binding.appHeader.btnBack.isEnabled = enabled
        binding.appHeader.btnBack.alpha = if (enabled) {
            1f
        } else {
            0.45f
        }
    }

    private fun setSetupInputControlsEnabled(
        enabled: Boolean
    ) {
        binding.etHomeWifiSsid.isEnabled = enabled
        binding.etHomeWifiPassword.isEnabled = enabled
        binding.btnStartSetup.isEnabled = enabled

        binding.btnStartSetup.alpha = if (enabled) {
            1f
        } else {
            0.55f
        }
    }

    private fun closeSetupConnection() {
        setupConnection?.close()
        setupConnection = null
    }

    private fun isViewReady(): Boolean {
        return _binding != null
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
        closeSetupConnection()
        _binding = null

        super.onDestroyView()
    }

    private companion object {
        const val SETUP_AP_PASSWORD = "adminadmin"
        const val DEFAULT_DEVICE_NAME = "Device"
        const val DEFAULT_FAMILY_NAME = "Aqua device"
    }
}
