package com.aqua.aqualight.ui.tabs.devices.add

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceQrScanBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

class DeviceQrScanFragment : Fragment(R.layout.fragment_device_qr_scan) {

    private val viewModel: DeviceQrScanViewModel by viewModels()
    private val permissionController = DeviceAddPermissionController()

    private var _binding: FragmentDeviceQrScanBinding? = null
    private val binding get() = _binding!!

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var barcodeScanner: BarcodeScanner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isProcessingFrame = false
    private var hasResult = false
    private var primaryAction: DeviceQrScanPrimaryAction? = null
    private var retryBlePreflightOnResume = false
    private var appSettingsActionTarget: AppSettingsTarget? = null
    private var pendingAppSettingsReturn: AppSettingsTarget? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            restartScanner()
        } else {
            showCameraPermissionRequired()
        }
    }

    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = permissionController.hasBlePermissionsFromResult(
            requireContext(),
            result
        )
        val permanentlyDenied = !granted &&
            permissionController.blePermissionAction(this) ==
            DevicePermissionAction.OPEN_APP_SETTINGS

        viewModel.onBlePermissionResult(
            granted = granted,
            permanentlyDenied = permanentlyDenied
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceQrScanBinding.bind(view)
        hasResult = false
        isProcessingFrame = false
        primaryAction = null
        retryBlePreflightOnResume = false
        appSettingsActionTarget = null
        pendingAppSettingsReturn = null

        setupHeader()
        setupActions()
        observeViewModel()
        ensureCameraPermission()
    }

    override fun onResume() {
        super.onResume()

        pendingAppSettingsReturn?.let { target ->
            pendingAppSettingsReturn = null
            when (target) {
                AppSettingsTarget.CAMERA -> {
                    if (hasCameraPermission()) {
                        restartScanner()
                    } else {
                        showCameraPermissionRequired()
                    }
                }

                AppSettingsTarget.BLE -> {
                    if (permissionController.hasBlePermissions(requireContext())) {
                        if (!viewModel.retryPendingBleScan()) {
                            restartScanner()
                        }
                    } else {
                        viewModel.onBlePermissionResult(
                            granted = false,
                            permanentlyDenied =
                                permissionController.blePermissionAction(this) ==
                                    DevicePermissionAction.OPEN_APP_SETTINGS
                        )
                    }
                }
            }
        }

        if (retryBlePreflightOnResume) {
            retryBlePreflightOnResume = false
            if (!viewModel.retryPendingBleScan()) {
                restartScanner()
            }
        }
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_qr_title),
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun setupActions() {
        binding.btnRequestCamera.setOnClickListener {
            when (primaryAction) {
                DeviceQrScanPrimaryAction.SCAN_AGAIN -> restartScanner()
                DeviceQrScanPrimaryAction.REQUEST_BLE_PERMISSION ->
                    handleBlePermissionAction()

                DeviceQrScanPrimaryAction.OPEN_BLUETOOTH_SETTINGS ->
                    openBluetoothSettings()

                DeviceQrScanPrimaryAction.OPEN_APP_SETTINGS -> {
                    val target = appSettingsActionTarget
                        ?: if (!hasCameraPermission() && !hasResult) {
                            AppSettingsTarget.CAMERA
                        } else {
                            AppSettingsTarget.BLE
                        }
                    openAppSettings(target)
                }

                null -> handleCameraPermissionAction()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun renderState(state: DeviceQrScanUiState) {
        if (_binding == null) return
        if (state.title.isBlank() && state.message.isBlank()) return

        primaryAction = state.primaryAction
        appSettingsActionTarget = if (
            state.primaryAction == DeviceQrScanPrimaryAction.OPEN_APP_SETTINGS
        ) {
            AppSettingsTarget.BLE
        } else {
            null
        }
        binding.tvScanTitle.text = state.title
        binding.tvScanStatus.text = state.message
        binding.btnRequestCamera.isVisible = state.primaryAction != null
        state.primaryAction?.let { action ->
            binding.btnRequestCamera.text = action.buttonText()
        }
    }

    private fun handleEvent(event: DeviceQrScanEvent) {
        when (event) {
            is DeviceQrScanEvent.OpenWifiProvisioning -> {
                openWifiProvisioning(event.result)
            }
        }
    }

    private fun ensureCameraPermission() {
        if (hasCameraPermission()) {
            startCamera()
        } else {
            showCameraPermissionRequired()
        }
    }

    private fun handleCameraPermissionAction() {
        when (permissionController.cameraPermissionAction(this)) {
            DevicePermissionAction.GRANTED -> restartScanner()
            DevicePermissionAction.REQUEST_PERMISSION -> requestCameraPermission()
            DevicePermissionAction.OPEN_APP_SETTINGS -> {
                appSettingsActionTarget = AppSettingsTarget.CAMERA
                openAppSettings(AppSettingsTarget.CAMERA)
            }
        }
    }

    private fun handleBlePermissionAction() {
        when (permissionController.blePermissionAction(this)) {
            DevicePermissionAction.GRANTED -> {
                viewModel.onBlePermissionResult(granted = true)
            }

            DevicePermissionAction.REQUEST_PERMISSION -> {
                requestBlePermission()
            }

            DevicePermissionAction.OPEN_APP_SETTINGS -> {
                viewModel.onBlePermissionResult(
                    granted = false,
                    permanentlyDenied = true
                )
                appSettingsActionTarget = AppSettingsTarget.BLE
                openAppSettings(AppSettingsTarget.BLE)
            }
        }
    }

    private fun requestCameraPermission() {
        permissionController.markCameraPermissionRequested(requireContext())
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun requestBlePermission() {
        permissionController.markBlePermissionsRequested(requireContext())
        blePermissionLauncher.launch(
            permissionController.blePermissions()
        )
    }

    private fun openBluetoothSettings() {
        retryBlePreflightOnResume = true
        runCatching {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }.onFailure {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openAppSettings(target: AppSettingsTarget) {
        pendingAppSettingsReturn = target
        val packageUri = Uri.fromParts("package", requireContext().packageName, null)
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        }.onFailure {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showCameraPermissionRequired() {
        if (_binding == null) return

        val permissionAction = permissionController.cameraPermissionAction(this)
        val shouldOpenSettings =
            permissionAction == DevicePermissionAction.OPEN_APP_SETTINGS

        primaryAction = if (shouldOpenSettings) {
            DeviceQrScanPrimaryAction.OPEN_APP_SETTINGS
        } else {
            null
        }
        appSettingsActionTarget = if (shouldOpenSettings) {
            AppSettingsTarget.CAMERA
        } else {
            null
        }
        binding.btnRequestCamera.isVisible = true
        binding.btnRequestCamera.text = if (shouldOpenSettings) {
            getString(R.string.device_permission_open_app_settings)
        } else {
            getString(R.string.device_qr_allow_camera)
        }
        binding.tvScanTitle.text = getString(R.string.device_qr_camera_permission_title)
        binding.tvScanStatus.text = getString(R.string.device_qr_camera_permission_message)
    }

    private fun restartScanner() {
        primaryAction = null
        appSettingsActionTarget = null
        viewModel.onScanAgain()
        hasResult = false
        isProcessingFrame = false
        startCamera()
    }

    private fun startCamera() {
        if (_binding == null || hasResult) return

        primaryAction = null
        appSettingsActionTarget = null
        stopCamera()
        binding.btnRequestCamera.isVisible = false
        binding.tvScanTitle.text = getString(R.string.device_qr_align_title)
        binding.tvScanStatus.text = getString(R.string.device_qr_align_message)

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        barcodeScanner = BarcodeScanning.getClient(options)

        val providerFuture = ProcessCameraProvider.getInstance(requireContext())

        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder()
                    .build()
                    .also { cameraPreview ->
                        cameraPreview.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            analyzeQrFrame(imageProxy)
                        }
                    }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        viewLifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }.onFailure { error ->
                    if (_binding != null) {
                        binding.tvScanTitle.text = getString(R.string.device_qr_camera_unavailable_title)
                        binding.tvScanStatus.text = error.message
                            ?: getString(R.string.device_qr_camera_unavailable_message)
                    }
                }
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeQrFrame(imageProxy: ImageProxy) {
        if (isProcessingFrame || hasResult || _binding == null) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessingFrame = true

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        val scanner = barcodeScanner
        if (scanner == null) {
            imageProxy.close()
            isProcessingFrame = false
            return
        }

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val rawValue = barcodes
                    .asSequence()
                    .mapNotNull { barcode -> barcode.rawValue }
                    .firstOrNull { raw -> raw.isNotBlank() }

                if (rawValue != null) {
                    handleQrPayload(rawValue)
                }
            }
            .addOnFailureListener { error ->
                if (_binding != null && !hasResult) {
                    binding.tvScanStatus.text = error.message
                        ?: getString(R.string.device_qr_read_failed)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
                isProcessingFrame = false
            }
    }

    private fun handleQrPayload(rawValue: String) {
        if (hasResult) return

        hasResult = true
        stopCamera()
        viewModel.onQrDetected(
            rawValue = rawValue,
            hasBlePermissions = permissionController.hasBlePermissions(requireContext())
        )
    }

    private fun openWifiProvisioning(result: DeviceQrPreflightSuccess) {
        findNavController().navigate(
            DeviceQrScanFragmentDirections.actionDeviceQrScanFragmentToDeviceWifiProvisioningFragment(
                candidateId = result.deviceUid,
                deviceTitle = result.deviceTitle,
                deviceSerial = result.deviceSerial,
                deviceModel = result.deviceModel,
                bleAddress = result.bleAddress,
                bleName = result.bleName,
                claimCode = result.claimCode,
                rawQrPayload = result.rawQrPayload
            )
        )
    }

    private fun DeviceQrScanPrimaryAction.buttonText(): String {
        return when (this) {
            DeviceQrScanPrimaryAction.SCAN_AGAIN ->
                getString(R.string.device_qr_preflight_scan_again)
            DeviceQrScanPrimaryAction.REQUEST_BLE_PERMISSION ->
                getString(R.string.device_qr_preflight_allow_bluetooth_permission)
            DeviceQrScanPrimaryAction.OPEN_BLUETOOTH_SETTINGS ->
                getString(R.string.device_qr_preflight_open_bluetooth_settings)
            DeviceQrScanPrimaryAction.OPEN_APP_SETTINGS ->
                getString(R.string.device_permission_open_app_settings)
        }
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        barcodeScanner?.close()
        barcodeScanner = null
    }

    override fun onDestroyView() {
        stopCamera()
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private enum class AppSettingsTarget {
        CAMERA,
        BLE
    }
}
