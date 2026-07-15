package com.aqua.aqualight.ui.tabs.devices.add

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
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
import com.aqua.aqualight.composition.requireAppContainer
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

    private val viewModel: DeviceQrScanViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private val permissionController = DeviceAddPermissionController()

    private var _binding: FragmentDeviceQrScanBinding? = null
    private val binding get() = _binding!!

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var barcodeScanner: BarcodeScanner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var isTorchEnabled = false
    private var isProcessingFrame = false
    private var hasResult = false
    private var primaryAction: DeviceQrScanPrimaryAction? = null
    private var retryBlePreflightOnResume = false
    private var retryCameraOnResume = false

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
        viewModel.onBlePermissionResult(
            permissionController.hasBlePermissionsFromResult(requireContext(), result)
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
        retryCameraOnResume = false

        setupHeader()
        setupActions()
        observeViewModel()
        ensureCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        if (retryCameraOnResume) {
            retryCameraOnResume = false
            if (hasCameraPermission()) {
                restartScanner()
            } else {
                showCameraPermissionRequired()
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
        binding.btnTorch.setOnClickListener {
            toggleTorch()
        }

        binding.btnRequestCamera.setOnClickListener {
            when (primaryAction) {
                DeviceQrScanPrimaryAction.SCAN_AGAIN -> restartScanner()
                DeviceQrScanPrimaryAction.REQUEST_CAMERA_PERMISSION -> requestCameraPermission()
                DeviceQrScanPrimaryAction.OPEN_CAMERA_SETTINGS -> openCameraSettings()
                DeviceQrScanPrimaryAction.REQUEST_BLE_PERMISSION -> requestBlePermission()
                DeviceQrScanPrimaryAction.OPEN_BLUETOOTH_SETTINGS -> openBluetoothSettings()
                DeviceQrScanPrimaryAction.OPEN_APP_SETTINGS -> openAppSettings()
                null -> {
                    if (!hasCameraPermission()) {
                        requestCameraPermission()
                    } else {
                        restartScanner()
                    }
                }
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

    private fun requestCameraPermission() {
        permissionController.markCameraPermissionRequested(requireContext())
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun requestBlePermission() {
        permissionController.markBlePermissionRequested(requireContext())
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

    private fun openAppSettings() {
        retryBlePreflightOnResume = true
        openApplicationDetailsSettings()
    }

    private fun openCameraSettings() {
        retryCameraOnResume = true
        openApplicationDetailsSettings()
    }

    private fun openApplicationDetailsSettings() {
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

        primaryAction = when (permissionController.cameraNextAction(this)) {
            DeviceAddPermissionController.NextAction.GRANTED -> {
                restartScanner()
                return
            }
            DeviceAddPermissionController.NextAction.REQUEST_PERMISSION ->
                DeviceQrScanPrimaryAction.REQUEST_CAMERA_PERMISSION
            DeviceAddPermissionController.NextAction.OPEN_APP_SETTINGS ->
                DeviceQrScanPrimaryAction.OPEN_CAMERA_SETTINGS
        }
        binding.btnRequestCamera.isVisible = true
        binding.btnRequestCamera.text = primaryAction?.buttonText()
        binding.tvScanTitle.text = getString(R.string.device_qr_camera_permission_title)
        binding.tvScanStatus.text = getString(R.string.device_qr_camera_permission_message)
    }

    private fun restartScanner() {
        primaryAction = null
        viewModel.onScanAgain()
        hasResult = false
        isProcessingFrame = false
        startCamera()
    }

    private fun startCamera() {
        if (_binding == null || hasResult) return

        primaryAction = null
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
                }.onSuccess { camera ->
                    bindTorchControl(camera)
                }.onFailure { error ->
                    clearTorchControl()
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

    private fun bindTorchControl(camera: Camera) {
        if (_binding == null) return

        clearTorchControl()
        activeCamera = camera

        if (!camera.cameraInfo.hasFlashUnit()) {
            return
        }

        binding.btnTorch.isVisible = true
        binding.btnTorch.isEnabled = true
        renderTorchState(camera.cameraInfo.torchState.value == TorchState.ON)

        camera.cameraInfo.torchState.observe(viewLifecycleOwner) { torchState ->
            if (activeCamera === camera && _binding != null) {
                renderTorchState(torchState == TorchState.ON)
            }
        }
    }

    private fun toggleTorch() {
        val camera = activeCamera ?: return
        if (_binding == null || !camera.cameraInfo.hasFlashUnit()) return

        val torchRequest = camera.cameraControl.enableTorch(!isTorchEnabled)
        val mainExecutor = ContextCompat.getMainExecutor(requireContext())
        binding.btnTorch.isEnabled = false

        torchRequest.addListener(
            {
                if (activeCamera !== camera || _binding == null) {
                    return@addListener
                }

                binding.btnTorch.isEnabled = true
                runCatching { torchRequest.get() }
                    .onFailure {
                        renderTorchState(
                            camera.cameraInfo.torchState.value == TorchState.ON
                        )
                    }
            },
            mainExecutor
        )
    }

    private fun renderTorchState(enabled: Boolean) {
        val currentBinding = _binding ?: return
        isTorchEnabled = enabled
        currentBinding.btnTorch.isSelected = enabled
        currentBinding.btnTorch.contentDescription = currentBinding.root.context.getString(
            if (enabled) {
                R.string.device_qr_flashlight_turn_off
            } else {
                R.string.device_qr_flashlight_turn_on
            }
        )
    }

    private fun clearTorchControl() {
        activeCamera?.cameraInfo?.torchState?.removeObservers(viewLifecycleOwner)
        activeCamera = null
        isTorchEnabled = false

        _binding?.btnTorch?.apply {
            isVisible = false
            isEnabled = false
            isSelected = false
            contentDescription = context.getString(R.string.device_qr_flashlight_turn_on)
        }
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
            DeviceQrScanPrimaryAction.REQUEST_CAMERA_PERMISSION ->
                getString(R.string.device_qr_allow_camera)
            DeviceQrScanPrimaryAction.OPEN_CAMERA_SETTINGS ->
                getString(R.string.device_qr_preflight_open_app_settings)
            DeviceQrScanPrimaryAction.REQUEST_BLE_PERMISSION ->
                getString(R.string.device_qr_preflight_allow_bluetooth_permission)
            DeviceQrScanPrimaryAction.OPEN_BLUETOOTH_SETTINGS ->
                getString(R.string.device_qr_preflight_open_bluetooth_settings)
            DeviceQrScanPrimaryAction.OPEN_APP_SETTINGS ->
                getString(R.string.device_qr_preflight_open_app_settings)
        }
    }

    private fun stopCamera() {
        activeCamera?.cameraControl?.enableTorch(false)
        clearTorchControl()
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
}
