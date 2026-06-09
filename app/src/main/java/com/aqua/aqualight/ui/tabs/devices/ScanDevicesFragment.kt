package com.aqua.aqualight.ui.tabs.devices

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.discovery.DeviceDiscoveryService
import com.aqua.aqualight.data.devices.discovery.DeviceScanReason
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import com.aqua.aqualight.databinding.FragmentScanDevicesBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderFilledIconAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ScanDevicesFragment : Fragment(R.layout.fragment_scan_devices) {

    private var _binding: FragmentScanDevicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ScanDevicesAdapter
    private lateinit var devicesStore: DevicesDataStoreManager

    private var scanJob: Job? = null
    private var isSavingDevice: Boolean = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentScanDevicesBinding.bind(view)
        devicesStore = DevicesDataStoreManager.create(requireContext())

        setupHeader()
        setupRecyclerView()
        startScan()
    }

    private fun setupHeader(
        title: String = getString(R.string.device_scan_header_list),
        rescanEnabled: Boolean = true
    ) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = title,
                filledIconAction = AquaHeaderFilledIconAction(
                    iconRes = R.drawable.ic_radar,
                    contentDescription = getString(R.string.device_scan_scan_again),
                    enabled = rescanEnabled,
                    onClick = {
                        startScan()
                    }
                )
            )
        )
    }

    private fun setupRecyclerView() {
        adapter = ScanDevicesAdapter { device ->
            saveSelectedDevice(device)
        }

        binding.rvDevices.layoutManager = LinearLayoutManager(
            requireContext()
        )

        binding.rvDevices.adapter = adapter
    }

    private fun startScan() {
        if (scanJob?.isActive == true) {
            return
        }

        scanJob = viewLifecycleOwner.lifecycleScope.launch {
            showScanningState()

            try {
                val scanResult = withTimeout(
                    SCAN_TIMEOUT_MS + SCAN_TIMEOUT_BUFFER_MS
                ) {
                    DeviceDiscoveryService.scan(
                        context = requireContext(),
                        timeoutMs = SCAN_TIMEOUT_MS,
                        reason = DeviceScanReason.MANUAL_SCAN
                    )
                }

                if (_binding == null) {
                    return@launch
                }

                if (scanResult.error != null) {
                    showErrorState()
                    return@launch
                }

                showResultState(
                    devices = scanResult.devices
                )
            } catch (exception: TimeoutCancellationException) {
                exception.printStackTrace()

                if (_binding != null) {
                    showTimeoutState()
                }
            } catch (exception: Exception) {
                exception.printStackTrace()

                if (_binding != null) {
                    showErrorState()
                }
            }
        }
    }

    private fun showScanningState() {
        setupHeader(
            title = getString(R.string.device_scan_header_scanning),
            rescanEnabled = false
        )

        binding.scanAnimation.visibility = View.VISIBLE
        binding.scanAnimation.playAnimation()

        binding.rvDevices.visibility = View.GONE
        binding.tvNoDevices.visibility = View.GONE

        adapter.submitList(emptyList())
    }

    private fun showResultState(
        devices: List<DiscoveredAquaDevice>
    ) {
        setupHeader(
            title = getString(R.string.device_scan_header_list),
            rescanEnabled = true
        )

        stopScanAnimation()

        if (devices.isEmpty()) {
            binding.rvDevices.visibility = View.GONE
            binding.tvNoDevices.visibility = View.VISIBLE

            binding.tvNoDevices.text = getString(
                R.string.device_scan_no_devices
            )

            adapter.submitList(emptyList())
            return
        }

        binding.rvDevices.visibility = View.VISIBLE
        binding.tvNoDevices.visibility = View.GONE

        adapter.submitList(devices)
    }

    private fun showTimeoutState() {
        setupHeader(
            title = getString(R.string.device_scan_header_list),
            rescanEnabled = true
        )

        stopScanAnimation()

        binding.rvDevices.visibility = View.GONE
        binding.tvNoDevices.visibility = View.VISIBLE

        binding.tvNoDevices.text = getString(
            R.string.device_scan_timeout
        )

        adapter.submitList(emptyList())
    }

    private fun showErrorState() {
        setupHeader(
            title = getString(R.string.device_scan_header_list),
            rescanEnabled = true
        )

        stopScanAnimation()

        binding.rvDevices.visibility = View.GONE
        binding.tvNoDevices.visibility = View.VISIBLE

        binding.tvNoDevices.text = getString(
            R.string.device_scan_error
        )

        adapter.submitList(emptyList())
    }

    private fun stopScanAnimation() {
        binding.scanAnimation.cancelAnimation()
        binding.scanAnimation.visibility = View.GONE
    }

    private fun saveSelectedDevice(
        device: DiscoveredAquaDevice
    ) {
        if (isSavingDevice) {
            return
        }

        if (!isValidDevice(device)) {
            showGlobalSnackBar(
                message = getString(R.string.device_scan_invalid_device),
                type = BaseActivity.SnackType.WARNING
            )
            return
        }

        if (!device.isSupported) {
            showGlobalSnackBar(
                message = "This device is not supported by this app version.",
                type = BaseActivity.SnackType.WARNING
            )
            return
        }

        val definition = AquaDeviceCatalog.findByType(
            type = device.deviceType
        )

        if (definition == null) {
            showGlobalSnackBar(
                message = "This device is not supported by this app version.",
                type = BaseActivity.SnackType.WARNING
            )
            return
        }

        val savedAquaName = definition.family.displayName.ifBlank {
            device.aquaName.ifBlank { "-" }
        }

        val savedName = definition.displayName.ifBlank {
            device.name.ifBlank { "Device" }
        }

        val serial = buildSerial(
            aquaName = savedAquaName,
            name = savedName,
            id = device.id
        )

        isSavingDevice = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val alreadyExists = devicesStore.deviceExists(
                    id = device.id
                )

                if (alreadyExists) {
                    devicesStore.updateDevicesLastSeen(
                        discovered = listOf(
                            device.toLastSeenUpdate()
                        )
                    )

                    showGlobalSnackBar(
                        message = getString(R.string.device_scan_already_added),
                        type = BaseActivity.SnackType.WARNING
                    )

                    if (_binding != null) {
                        findNavController().popBackStack()
                    }

                    return@launch
                }

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

                if (_binding != null) {
                    findNavController().popBackStack()
                }
            } catch (exception: Exception) {
                exception.printStackTrace()

                if (_binding != null) {
                    showGlobalSnackBar(
                        message = "Device could not be saved.",
                        type = BaseActivity.SnackType.ERROR
                    )
                }
            } finally {
                isSavingDevice = false
            }
        }
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

    private fun isValidDevice(
        device: DiscoveredAquaDevice
    ): Boolean {
        if (device.id <= 0L) {
            return false
        }

        if (device.ip.isBlank()) {
            return false
        }

        if (
            device.name.isBlank() &&
            device.aquaName.isBlank() &&
            device.productId.isNullOrBlank()
        ) {
            return false
        }

        return true
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

        val core = if (id != 0L) {
            id.toString()
        } else {
            ""
        }

        return if (core.isNotEmpty()) {
            "$aquaInitial$nameInitial-$core"
        } else {
            "$aquaInitial$nameInitial"
        }
    }

    private fun showGlobalSnackBar(
        message: String,
        type: BaseActivity.SnackType
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = type
        )
    }

    override fun onDestroyView() {
        scanJob?.cancel()
        scanJob = null

        _binding
            ?.scanAnimation
            ?.cancelAnimation()

        _binding
            ?.rvDevices
            ?.adapter = null

        _binding = null

        super.onDestroyView()
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 3_000L
        const val SCAN_TIMEOUT_BUFFER_MS = 1_000L
    }
}