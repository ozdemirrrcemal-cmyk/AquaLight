package com.aqua.aqualight.ui.tabs.devices.add

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

        setupHeader()
        setupActions()
        observeViewModel()
        ensureCameraPermission()
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
            if (!hasCameraPermission()) {
                requestCameraPermission()
            } else {
                restartScanner()
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

        binding.tvScanTitle.text = state.title
        binding.tvScanStatus.text = state.message
        binding.btnRequestCamera.isVisible = state.showScanAgain
        if (state.showScanAgain) {
            binding.btnRequestCamera.text = getString(R.string.device_qr_preflight_scan_again)
        }
    }

    private fun handleEvent(event: DeviceQrScanEvent) {
        when (event) {
            DeviceQrScanEvent.RequestBlePermission -> {
                blePermissionLauncher.launch(
                    permissionController.blePermissions()
                )
            }

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
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showCameraPermissionRequired() {
        if (_binding == null) return

        binding.btnRequestCamera.isVisible = true
        binding.btnRequestCamera.text = getString(R.string.device_qr_allow_camera)
        binding.tvScanTitle.text = getString(R.string.device_qr_camera_permission_title)
        binding.tvScanStatus.text = getString(R.string.device_qr_camera_permission_message)
    }

    private fun restartScanner() {
        viewModel.onScanAgain()
        hasResult = false
        isProcessingFrame = false
        startCamera()
    }

    private fun startCamera() {
        if (_binding == null || hasResult) return

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
}
